package com.iet.dashboard.shared.admin;

import com.iet.dashboard.academics.domain.Assessment;
import com.iet.dashboard.academics.domain.CourseOffering;
import com.iet.dashboard.academics.domain.RemedialPlan;
import com.iet.dashboard.academics.domain.SyllabusTopic;
import com.iet.dashboard.academics.domain.TimetableSlot;
import com.iet.dashboard.academics.repo.AcademicTermRepository;
import com.iet.dashboard.academics.repo.AssessmentRepository;
import com.iet.dashboard.academics.repo.CourseOfferingRepository;
import com.iet.dashboard.academics.repo.CourseRepository;
import com.iet.dashboard.academics.repo.RemedialPlanRepository;
import com.iet.dashboard.academics.repo.SyllabusTopicRepository;
import com.iet.dashboard.academics.repo.TeachingAssignmentRepository;
import com.iet.dashboard.academics.repo.TimetableSlotRepository;
import com.iet.dashboard.academics.service.AllocationImportServiceImpl;
import com.iet.dashboard.deadlines.repo.DeadlineAssignmentRepository;
import com.iet.dashboard.deadlines.repo.DeadlineRepository;
import com.iet.dashboard.directory.repo.DepartmentRepository;
import com.iet.dashboard.directory.repo.FacultyRepository;
import com.iet.dashboard.identity.repo.AppUserRepository;
import com.iet.dashboard.notifications.repo.NotificationRepository;
import com.iet.dashboard.teaching.domain.SessionStatus;
import com.iet.dashboard.teaching.domain.TeachingSession;
import com.iet.dashboard.teaching.repo.TeachingSessionRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.context.ActiveProfiles;

import java.nio.charset.StandardCharsets;
import java.time.LocalDate;
import java.time.LocalTime;

import static org.assertj.core.api.Assertions.assertThat;

@DataJpaTest
@ActiveProfiles("test")
class ActivityResetServiceTest {

    @Autowired AppUserRepository users;
    @Autowired FacultyRepository faculty;
    @Autowired DepartmentRepository departments;
    @Autowired CourseRepository courses;
    @Autowired CourseOfferingRepository offerings;
    @Autowired TeachingAssignmentRepository assignments;
    @Autowired AcademicTermRepository terms;
    @Autowired TeachingSessionRepository sessions;
    @Autowired AssessmentRepository assessments;
    @Autowired RemedialPlanRepository plans;
    @Autowired TimetableSlotRepository slots;
    @Autowired DeadlineAssignmentRepository deadlineAssignments;
    @Autowired DeadlineRepository deadlines;
    @Autowired NotificationRepository notifications;
    @Autowired SyllabusTopicRepository syllabus;

    private ActivityResetService service;
    private CourseOffering offering;

    @BeforeEach
    void setUp() {
        service = new ActivityResetServiceImpl(sessions, assessments, plans, slots,
                deadlineAssignments, deadlines, notifications, syllabus);
        new AllocationImportServiceImpl(users, faculty, departments, courses, offerings, assignments, terms)
                .importAllocations(new MockMultipartFile("file", "a.csv", "text/csv", """
                        Faculty Name,Email,Course Code,Course Name,L-T-P,Semester,Section,Section Students,Component
                        Dr. Amit Sinhal,amit@iet.edu,CS1139,Programming-I,3-0-2,1,C,64,Lecture
                        """.getBytes(StandardCharsets.UTF_8)));
        offering = offerings.findAll().stream().filter(o -> o.getCourse().getCode().equals("CS1139"))
                .findFirst().orElseThrow();
        Long facultyId = faculty.findAll().get(0).getId();

        sessions.save(new TeachingSession(offering, facultyId, LocalDate.now(), "LECTURE",
                SessionStatus.HELD, 1, "Intro", "ALL", 1, null, null));
        assessments.save(new Assessment(offering, "CIE-1", "MID_TERM", 30, LocalDate.now(), 64, 68, 7, facultyId));
        plans.save(new RemedialPlan(offering, facultyId, "BEHIND_SYLLABUS", "Unit 3",
                "Two extra tutorials", 2, LocalDate.now().plusDays(14)));
        slots.save(new TimetableSlot(offering, facultyId, "MONDAY", LocalTime.of(9, 0),
                LocalTime.of(10, 0), "LT-1", "LECTURE", "ALL"));
        syllabus.save(new SyllabusTopic(offering.getCourse(), 1, "Unit 1", "Topic one", 1, 1));
    }

    @Test
    void resetClearsActivityButKeepsAllocationsAndSyllabus() {
        ActivityResetService.ResetResult result = service.resetActivity(false);

        assertThat(result.sessions()).isEqualTo(1);
        assertThat(sessions.count()).isZero();
        assertThat(assessments.count()).isZero();
        assertThat(plans.count()).isZero();
        assertThat(slots.count()).isZero();
        // The allocations and curriculum are the source of truth and must survive.
        assertThat(assignments.count()).isEqualTo(1);
        assertThat(offerings.count()).isEqualTo(1);
        assertThat(syllabus.count()).isEqualTo(1);
    }

    @Test
    void resetCanAlsoClearTheSyllabus() {
        ActivityResetService.ResetResult result = service.resetActivity(true);

        assertThat(result.syllabusTopics()).isEqualTo(1);
        assertThat(syllabus.count()).isZero();
        assertThat(assignments.count()).isEqualTo(1);
    }
}
