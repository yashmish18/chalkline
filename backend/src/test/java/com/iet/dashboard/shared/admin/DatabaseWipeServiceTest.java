package com.iet.dashboard.shared.admin;

import com.iet.dashboard.academics.domain.AcademicTerm;
import com.iet.dashboard.academics.domain.Course;
import com.iet.dashboard.academics.domain.CourseComponent;
import com.iet.dashboard.academics.domain.CourseOffering;
import com.iet.dashboard.academics.domain.SyllabusTopic;
import com.iet.dashboard.academics.domain.TeachingAssignment;
import com.iet.dashboard.academics.repo.AcademicDocumentRepository;
import com.iet.dashboard.academics.repo.AcademicTermRepository;
import com.iet.dashboard.academics.repo.AssessmentRepository;
import com.iet.dashboard.academics.repo.CourseOfferingRepository;
import com.iet.dashboard.academics.repo.CourseRepository;
import com.iet.dashboard.academics.repo.RemedialPlanRepository;
import com.iet.dashboard.academics.repo.SyllabusTopicRepository;
import com.iet.dashboard.academics.repo.TeachingAssignmentRepository;
import com.iet.dashboard.academics.repo.TimetableSlotRepository;
import com.iet.dashboard.deadlines.domain.Deadline;
import com.iet.dashboard.deadlines.domain.DeadlineType;
import com.iet.dashboard.deadlines.repo.DeadlineAssignmentRepository;
import com.iet.dashboard.deadlines.repo.DeadlineRepository;
import com.iet.dashboard.directory.domain.Department;
import com.iet.dashboard.directory.domain.Faculty;
import com.iet.dashboard.directory.domain.Skill;
import com.iet.dashboard.directory.repo.DepartmentRepository;
import com.iet.dashboard.directory.repo.FacultyRepository;
import com.iet.dashboard.directory.repo.SkillRepository;
import com.iet.dashboard.identity.domain.AppUser;
import com.iet.dashboard.identity.domain.Role;
import com.iet.dashboard.identity.repo.AppUserRepository;
import com.iet.dashboard.notifications.domain.Notification;
import com.iet.dashboard.notifications.domain.NotificationType;
import com.iet.dashboard.notifications.repo.NotificationRepository;
import com.iet.dashboard.submissions.repo.SubmissionFileRepository;
import com.iet.dashboard.submissions.repo.SubmissionRepository;
import com.iet.dashboard.teaching.domain.SessionStatus;
import com.iet.dashboard.teaching.domain.TeachingSession;
import com.iet.dashboard.teaching.repo.TeachingSessionRepository;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.context.annotation.Import;

import java.time.Instant;
import java.time.LocalDate;
import java.util.Map;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.*;

@DataJpaTest
@ActiveProfiles("test")
@Import({DatabaseWipeServiceImpl.class, com.iet.dashboard.submissions.storage.LocalDiskStorage.class})
class DatabaseWipeServiceTest {

    @org.junit.jupiter.api.io.TempDir
    static java.nio.file.Path uploads;

    @org.springframework.test.context.DynamicPropertySource
    static void uploadDir(org.springframework.test.context.DynamicPropertyRegistry registry) {
        registry.add("app.storage.upload-dir", () -> uploads.toString());
    }

    @Autowired
    private DatabaseWipeService wipeService;

    @Autowired private AppUserRepository appUserRepository;
    @Autowired private FacultyRepository facultyRepository;
    @Autowired private SkillRepository skillRepository;
    @Autowired private DepartmentRepository departmentRepository;
    @Autowired private AcademicTermRepository academicTermRepository;
    @Autowired private CourseRepository courseRepository;
    @Autowired private CourseOfferingRepository offeringRepository;
    @Autowired private TeachingAssignmentRepository assignmentRepository;
    @Autowired private TeachingSessionRepository sessionRepository;
    @Autowired private SyllabusTopicRepository syllabusRepository;
    @Autowired private DeadlineRepository deadlineRepository;
    @Autowired private DeadlineAssignmentRepository deadlineAssignmentRepository;
    @Autowired private NotificationRepository notificationRepository;
    @Autowired private AssessmentRepository assessmentRepository;
    @Autowired private RemedialPlanRepository remedialRepository;
    @Autowired private TimetableSlotRepository slotRepository;
    @Autowired private SubmissionRepository submissionRepository;
    @Autowired private SubmissionFileRepository submissionFileRepository;
    @Autowired private AcademicDocumentRepository documentRepository;

    @Test
    void wipeClearsEverythingButTheCoordinatorLoginAndProfile() throws Exception {
        // Accounts: coordinator, a faculty member with a profile, and an orphan login.
        AppUser coordinatorUser = appUserRepository.save(
                new AppUser("coordinator@iet.edu", "Coordinator", Role.COORDINATOR));
        AppUser staffUser = appUserRepository.save(
                new AppUser("staff@iet.edu", "Dr. Staff", Role.FACULTY));
        AppUser orphanUser = appUserRepository.save(
                new AppUser("orphan@iet.edu", "Ghost", Role.FACULTY));

        Department department = departmentRepository.save(new Department("IT", "Information Technology"));
        Skill skill = skillRepository.save(new Skill("Networks", "Systems"));

        Faculty coordinatorProfile = facultyRepository.save(
                new Faculty(coordinatorUser.getId(), "Coordinator", "Coordinator", "", department));
        Faculty staffProfile = facultyRepository.save(
                new Faculty(staffUser.getId(), "Dr. Staff", "Professor", "PhD", department));
        staffProfile.setSkills(new java.util.HashSet<>(Set.of(skill)));
        facultyRepository.save(staffProfile);

        AcademicTerm term = academicTermRepository.save(new AcademicTerm(
                "Sem 1", "2026-2027", LocalDate.of(2026, 8, 3), LocalDate.of(2026, 12, 18), true));

        Course course = courseRepository.save(new Course("CS101", "Intro to CS", "THEORY"));
        CourseOffering offering = offeringRepository.save(
                new CourseOffering(course, "2026-2027", 1, 1, "A", term));
        assignmentRepository.save(new TeachingAssignment(offering, staffProfile.getId(), CourseComponent.LECTURE));

        SyllabusTopic topic = syllabusRepository.save(new SyllabusTopic(course, 1, "Unit 1", "Basics", 1, 1));
        TeachingSession session = sessionRepository.save(new TeachingSession(
                offering, staffProfile.getId(), LocalDate.of(2026, 8, 5), "LECTURE",
                SessionStatus.HELD, "covered basics", null));
        session.addTopic(topic);
        sessionRepository.save(session);

        deadlineRepository.save(new Deadline(
                "Course file", "Submit drafts", DeadlineType.COURSE_FILE,
                Instant.now().plusSeconds(86_400), staffUser.getId()));
        notificationRepository.save(new Notification(
                staffUser.getId(), "Deadline assigned", "Course file due", NotificationType.DEADLINE_ASSIGNED, "/deadlines"));

        // An uploaded file on disk (a submission or academic document).
        java.nio.file.Files.writeString(uploads.resolve("0b6c-old-upload.pdf"), "%PDF-1.4");

        // Act.
        Map<String, Integer> cleared = wipeService.wipeAll();

        // Only the coordinator's login and profile survive, so someone can still sign in.
        assertEquals(1, appUserRepository.count());
        AppUser survivor = appUserRepository.findAll().get(0);
        assertEquals(coordinatorUser.getId(), survivor.getId(), "only the coordinator's login may survive");
        assertEquals(1, facultyRepository.count());
        Faculty profile = facultyRepository.findAll().get(0);
        assertEquals(coordinatorUser.getId(), profile.getUserId(), "only the coordinator's profile may survive");
        assertNull(profile.getDepartment(), "the surviving profile must not pin a department row");
        assertEquals(0, academicTermRepository.count(), "the semester settings go too");
        try (var left = java.nio.file.Files.list(uploads)) {
            assertEquals(0, left.count(), "uploaded files are deleted from disk");
        }
        assertEquals(1, (int) cleared.get("files"));

        // Everything else is gone — including the orphan login with no profile.
        assertEquals(0, skillRepository.count());
        assertEquals(0, departmentRepository.count());
        assertEquals(0, courseRepository.count());
        assertEquals(0, offeringRepository.count());
        assertEquals(0, assignmentRepository.count());
        assertEquals(0, sessionRepository.count());
        assertEquals(0, syllabusRepository.count());
        assertEquals(0, deadlineRepository.count());
        assertEquals(0, deadlineAssignmentRepository.count());
        assertEquals(0, notificationRepository.count());
        assertEquals(0, assessmentRepository.count());
        assertEquals(0, remedialRepository.count());
        assertEquals(0, slotRepository.count());
        assertEquals(0, submissionRepository.count());
        assertEquals(0, submissionFileRepository.count());
        assertEquals(0, documentRepository.count());

        // Headline counts reported back to the UI.
        assertEquals(1, (int) cleared.get("faculty"), "one non-coordinator profile removed");
        assertEquals(2, (int) cleared.get("accounts"), "staff and orphan logins removed");
        assertEquals(1, (int) cleared.get("sections"));
        assertEquals(1, (int) cleared.get("courses"));

        // Sanity: the seeded rows actually existed before the wipe (guards a silently empty seed).
        assertTrue(orphanUser.getId() != staffUser.getId());
        assertTrue(coordinatorProfile.getId() != staffProfile.getId());
    }
}
