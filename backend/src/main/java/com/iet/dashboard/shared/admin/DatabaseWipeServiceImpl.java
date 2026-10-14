package com.iet.dashboard.shared.admin;

import com.iet.dashboard.academics.repo.AcademicDocumentRepository;
import com.iet.dashboard.academics.repo.AssessmentRepository;
import com.iet.dashboard.academics.repo.CourseOfferingRepository;
import com.iet.dashboard.academics.repo.CourseRepository;
import com.iet.dashboard.academics.repo.RemedialPlanRepository;
import com.iet.dashboard.academics.repo.SyllabusTopicRepository;
import com.iet.dashboard.academics.repo.TeachingAssignmentRepository;
import com.iet.dashboard.academics.repo.TimetableSlotRepository;
import com.iet.dashboard.deadlines.repo.DeadlineAssignmentRepository;
import com.iet.dashboard.deadlines.repo.DeadlineRepository;
import com.iet.dashboard.directory.repo.DepartmentRepository;
import com.iet.dashboard.directory.repo.FacultyRepository;
import com.iet.dashboard.directory.repo.SkillRepository;
import com.iet.dashboard.identity.domain.AppUser;
import com.iet.dashboard.identity.domain.Role;
import com.iet.dashboard.identity.repo.AppUserRepository;
import com.iet.dashboard.notifications.repo.NotificationRepository;
import com.iet.dashboard.submissions.repo.SubmissionFileRepository;
import com.iet.dashboard.submissions.repo.SubmissionRepository;
import com.iet.dashboard.teaching.repo.TeachingSessionRepository;
import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceContext;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * Empties every operational table in dependency order (children before parents), then the uploaded
 * files on disk. Keeps only the coordinator's sign-in account and faculty profile — everything else,
 * including other faculty's login accounts, is removed. Ordered defensively so it holds on both
 * the dev schema (CASCADE / SET NULL) and a strict NO ACTION schema.
 */
@Service
@Transactional
public class DatabaseWipeServiceImpl implements DatabaseWipeService {

    private final SubmissionFileRepository submissionFileRepository;
    private final SubmissionRepository submissionRepository;
    private final TeachingSessionRepository sessionRepository;
    private final AssessmentRepository assessmentRepository;
    private final RemedialPlanRepository remedialRepository;
    private final TimetableSlotRepository slotRepository;
    private final DeadlineAssignmentRepository deadlineAssignmentRepository;
    private final DeadlineRepository deadlineRepository;
    private final NotificationRepository notificationRepository;
    private final SyllabusTopicRepository syllabusRepository;
    private final TeachingAssignmentRepository assignmentRepository;
    private final CourseOfferingRepository offeringRepository;
    private final CourseRepository courseRepository;
    private final AcademicDocumentRepository documentRepository;
    private final FacultyRepository facultyRepository;
    private final SkillRepository skillRepository;
    private final DepartmentRepository departmentRepository;
    private final AppUserRepository appUserRepository;
    private final com.iet.dashboard.academics.repo.AcademicTermRepository termRepository;
    private final com.iet.dashboard.submissions.storage.FileStorage fileStorage;

    @PersistenceContext
    private EntityManager entityManager;

    public DatabaseWipeServiceImpl(
            SubmissionFileRepository submissionFileRepository,
            SubmissionRepository submissionRepository,
            TeachingSessionRepository sessionRepository,
            AssessmentRepository assessmentRepository,
            RemedialPlanRepository remedialRepository,
            TimetableSlotRepository slotRepository,
            DeadlineAssignmentRepository deadlineAssignmentRepository,
            DeadlineRepository deadlineRepository,
            NotificationRepository notificationRepository,
            SyllabusTopicRepository syllabusRepository,
            TeachingAssignmentRepository assignmentRepository,
            CourseOfferingRepository offeringRepository,
            CourseRepository courseRepository,
            AcademicDocumentRepository documentRepository,
            FacultyRepository facultyRepository,
            SkillRepository skillRepository,
            DepartmentRepository departmentRepository,
            AppUserRepository appUserRepository,
            com.iet.dashboard.academics.repo.AcademicTermRepository termRepository,
            com.iet.dashboard.submissions.storage.FileStorage fileStorage) {
        this.submissionFileRepository = submissionFileRepository;
        this.submissionRepository = submissionRepository;
        this.sessionRepository = sessionRepository;
        this.assessmentRepository = assessmentRepository;
        this.remedialRepository = remedialRepository;
        this.slotRepository = slotRepository;
        this.deadlineAssignmentRepository = deadlineAssignmentRepository;
        this.deadlineRepository = deadlineRepository;
        this.notificationRepository = notificationRepository;
        this.syllabusRepository = syllabusRepository;
        this.assignmentRepository = assignmentRepository;
        this.offeringRepository = offeringRepository;
        this.courseRepository = courseRepository;
        this.documentRepository = documentRepository;
        this.facultyRepository = facultyRepository;
        this.skillRepository = skillRepository;
        this.departmentRepository = departmentRepository;
        this.appUserRepository = appUserRepository;
        this.termRepository = termRepository;
        this.fileStorage = fileStorage;
    }

    @Override
    public Map<String, Integer> wipeAll() {
        Map<String, Integer> cleared = new LinkedHashMap<>();

        Set<Long> coordinatorUserIds = appUserRepository.findByRole(Role.COORDINATOR).stream()
                .map(AppUser::getId)
                .collect(Collectors.toSet());

        // Flush pending inserts first: native SQL does not trigger Hibernate's auto-flush, so
        // join-table rows written earlier in this transaction must land before we delete them.
        entityManager.flush();

        // Pure join tables have no entity of their own — clear them before any parent row goes.
        nativeDelete("DELETE FROM session_topic_log");
        nativeDelete("DELETE FROM faculty_skill");

        // Leaf activity data first, so no child row blocks a parent delete.
        cleared.put("classLogs", clear(sessionRepository::deleteAllInBatch, sessionRepository.count()));
        cleared.put("submissions", clear(() -> {
            submissionFileRepository.deleteAllInBatch();
            submissionRepository.deleteAllInBatch();
        }, submissionFileRepository.count() + submissionRepository.count()));
        cleared.put("assessments", clear(assessmentRepository::deleteAllInBatch, assessmentRepository.count()));
        cleared.put("remedialPlans", clear(remedialRepository::deleteAllInBatch, remedialRepository.count()));
        cleared.put("timetable", clear(slotRepository::deleteAllInBatch, slotRepository.count()));
        cleared.put("deadlines", clear(() -> {
            deadlineAssignmentRepository.deleteAllInBatch();
            deadlineRepository.deleteAllInBatch();
        }, deadlineAssignmentRepository.count() + deadlineRepository.count()));
        cleared.put("notifications", clear(notificationRepository::deleteAllInBatch, notificationRepository.count()));
        cleared.put("syllabus", clear(syllabusRepository::deleteAllInBatch, syllabusRepository.count()));
        cleared.put("allocations", clear(assignmentRepository::deleteAllInBatch, assignmentRepository.count()));
        cleared.put("sections", clear(offeringRepository::deleteAllInBatch, offeringRepository.count()));
        cleared.put("courses", clear(() -> {
            documentRepository.deleteAllInBatch();
            courseRepository.deleteAllInBatch();
        }, documentRepository.count() + courseRepository.count()));

        // Faculty profiles and their sign-in accounts — every profile except the coordinator's.
        List<FacultyRow> removable = facultyRepository.findAll().stream()
                .filter(f -> !coordinatorUserIds.contains(f.getUserId()))
                .map(f -> new FacultyRow(f.getId(), f.getUserId()))
                .toList();
        List<Long> removableProfileIds = removable.stream().map(FacultyRow::profileId).filter(java.util.Objects::nonNull).toList();
        List<Long> removableUserIds = removable.stream().map(FacultyRow::userId).filter(java.util.Objects::nonNull).distinct().toList();

        if (!removableProfileIds.isEmpty()) {
            facultyRepository.deleteAllByIdInBatch(removableProfileIds);
        }
        cleared.put("faculty", removableProfileIds.size());

        // The surviving coordinator profile must not pin a department row to life.
        facultyRepository.findAll().stream()
                .filter(f -> f.getDepartment() != null)
                .forEach(f -> f.setDepartment(null));
        facultyRepository.flush();

        cleared.put("skills", clear(skillRepository::deleteAllInBatch, skillRepository.count()));
        cleared.put("departments", clear(departmentRepository::deleteAllInBatch, departmentRepository.count()));

        // Finally the accounts themselves: every non-coordinator login.
        List<Long> removableAccounts = appUserRepository.findAll().stream()
                .map(AppUser::getId)
                .filter(id -> !coordinatorUserIds.contains(id))
                .toList();
        if (!removableAccounts.isEmpty()) {
            appUserRepository.deleteAllByIdInBatch(removableAccounts);
        }
        cleared.put("accounts", removableAccounts.size());

        // The semester settings go too (sections and documents that pointed at them are gone already).
        cleared.put("semesters", clear(termRepository::deleteAllInBatch, termRepository.count()));

        // Surface any FK we missed inside this transaction instead of committing a broken state.
        entityManager.flush();

        // Last, the uploaded files on disk: only once every row that pointed at them is gone.
        try {
            cleared.put("files", fileStorage.deleteAll());
        } catch (java.io.IOException e) {
            throw new IllegalStateException("Database cleared, but uploaded files could not be deleted: " + e.getMessage(), e);
        }
        return cleared;
    }

    private void nativeDelete(String sql) {
        entityManager.createNativeQuery(sql).executeUpdate();
    }

    /** Runs a bulk delete and reports how many rows it removed (count taken before the delete). */
    private int clear(Runnable deleteAll, long rowsBefore) {
        deleteAll.run();
        return (int) rowsBefore;
    }

    private record FacultyRow(Long profileId, Long userId) {
    }
}
