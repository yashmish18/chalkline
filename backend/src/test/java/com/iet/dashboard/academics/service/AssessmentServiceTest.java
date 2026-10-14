package com.iet.dashboard.academics.service;

import com.iet.dashboard.academics.repo.AcademicTermRepository;
import com.iet.dashboard.academics.repo.AssessmentRepository;
import com.iet.dashboard.academics.repo.CourseOfferingRepository;
import com.iet.dashboard.academics.repo.CourseRepository;
import com.iet.dashboard.academics.repo.RemedialPlanRepository;
import com.iet.dashboard.academics.repo.TeachingAssignmentRepository;
import com.iet.dashboard.academics.web.AssessmentDto;
import com.iet.dashboard.academics.web.RecordAssessmentRequest;
import com.iet.dashboard.academics.web.RemedialPlanDto;
import com.iet.dashboard.academics.web.SubmitRemedialPlanRequest;
import com.iet.dashboard.directory.repo.DepartmentRepository;
import com.iet.dashboard.directory.repo.FacultyRepository;
import com.iet.dashboard.identity.repo.AppUserRepository;
import com.iet.dashboard.shared.error.ForbiddenException;
import com.iet.dashboard.shared.error.ValidationException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.context.ActiveProfiles;

import java.nio.charset.StandardCharsets;
import java.time.LocalDate;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@DataJpaTest
@ActiveProfiles("test")
class AssessmentServiceTest {

    @Autowired AppUserRepository users;
    @Autowired FacultyRepository faculty;
    @Autowired DepartmentRepository departments;
    @Autowired CourseRepository courses;
    @Autowired CourseOfferingRepository offerings;
    @Autowired TeachingAssignmentRepository assignments;
    @Autowired AcademicTermRepository terms;
    @Autowired AssessmentRepository assessmentRepository;
    @Autowired RemedialPlanRepository remedialRepository;

    private AssessmentService service;
    private Long offeringId;
    private Long amitId;
    private Long surbhiId;

    @BeforeEach
    void setUp() {
        new AllocationImportServiceImpl(users, faculty, departments, courses, offerings, assignments, terms)
                .importAllocations(new MockMultipartFile("file", "a.csv", "text/csv", """
                        Faculty Name,Email,Course Code,Course Name,L-T-P,Semester,Section,Section Students,Component
                        Dr. Amit Sinhal,amit@iet.edu,CS1139,Programming-I,3-0-2,1,C,64,Lecture
                        Dr. Surbhi Chhabra,surbhi@iet.edu,CS1131,Data Structures,3-0-2,3,D,60,Lecture
                        """.getBytes(StandardCharsets.UTF_8)));
        offeringId = offerings.findAll().stream().filter(o -> o.getCourse().getCode().equals("CS1139")).findFirst().orElseThrow().getId();
        amitId = faculty.findAll().stream().filter(f -> f.getName().equals("Dr. Amit Sinhal")).findFirst().orElseThrow().getId();
        surbhiId = faculty.findAll().stream().filter(f -> f.getName().equals("Dr. Surbhi Chhabra")).findFirst().orElseThrow().getId();
        service = new AssessmentServiceImpl(assessmentRepository, remedialRepository, offerings, assignments,
                org.mockito.Mockito.mock(com.iet.dashboard.directory.service.FacultyService.class));
    }

    private static RecordAssessmentRequest midTerm() {
        return new RecordAssessmentRequest(null, "CIE-1 Mid-term", "MID_TERM", 30, LocalDate.of(2026, 9, 20), 64, 68, 7);
    }

    @Test
    void theAssignedFacultyRecordsAnAssessment() {
        RecordAssessmentRequest r = midTerm();
        AssessmentDto saved = service.recordAssessment(new RecordAssessmentRequest(offeringId, r.name(), r.kind(), r.weightPct(),
                r.heldOn(), r.studentsAssessed(), r.averagePct(), r.below40Count()), amitId);

        assertThat(saved.id()).isNotNull();
        assertThat(saved.weightPct()).isEqualTo(30);
        assertThat(service.getAssessments(offeringId, amitId)).hasSize(1);
    }

    @Test
    void someoneWhoDoesNotTeachTheSectionCannotRecordForIt() {
        RecordAssessmentRequest r = midTerm();
        assertThatThrownBy(() -> service.recordAssessment(new RecordAssessmentRequest(offeringId, r.name(), r.kind(), r.weightPct(),
                r.heldOn(), r.studentsAssessed(), r.averagePct(), r.below40Count()), surbhiId))
                .isInstanceOf(ForbiddenException.class);
    }

    @Test
    void rejectsImpossibleNumbers() {
        assertThatThrownBy(() -> service.recordAssessment(new RecordAssessmentRequest(offeringId, "Quiz", "QUIZ", 120,
                LocalDate.now(), 64, 50, 3), amitId)).isInstanceOf(ValidationException.class);
        assertThatThrownBy(() -> service.recordAssessment(new RecordAssessmentRequest(offeringId, "Quiz", "QUIZ", 10,
                LocalDate.now(), 64, 50, 80), amitId)).isInstanceOf(ValidationException.class);
    }

    @Test
    void aRemedialPlanIsFiledAndThenCompleted() {
        RemedialPlanDto plan = service.submitRemedialPlan(new SubmitRemedialPlanRequest(offeringId, "BEHIND_SYLLABUS",
                "Unit 3: pointers", "Two extra tutorials on Saturdays", 2, LocalDate.of(2026, 10, 30)), amitId);

        assertThat(plan.status()).isEqualTo("SUBMITTED");
        assertThat(service.getRemedialPlans(offeringId, null)).hasSize(1);

        RemedialPlanDto done = service.completeRemedialPlan(plan.id(), amitId);
        assertThat(done.status()).isEqualTo("COMPLETED");
        assertThatThrownBy(() -> service.completeRemedialPlan(plan.id(), surbhiId)).isInstanceOf(ForbiddenException.class);
    }

    @Test
    void theCoordinatorReviewsEveryFiledPlanWithItsSection() {
        service.submitRemedialPlan(new SubmitRemedialPlanRequest(offeringId, "BEHIND_SYLLABUS",
                "Unit 3: pointers", "Two extra tutorials on Saturdays", 2, LocalDate.of(2026, 10, 30)), amitId);

        List<RemedialPlanDto> all = service.getRemedialPlans(null, null);

        assertThat(all).hasSize(1);
        assertThat(all.get(0).courseCode()).isEqualTo("CS1139");
        assertThat(all.get(0).section()).isEqualTo("C");
        // A filing faculty member still cannot read the whole department's plans.
        assertThatThrownBy(() -> service.getRemedialPlans(null, amitId)).isInstanceOf(ForbiddenException.class);
    }
}
