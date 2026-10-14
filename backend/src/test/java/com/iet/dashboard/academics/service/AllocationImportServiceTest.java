package com.iet.dashboard.academics.service;

import com.iet.dashboard.academics.domain.CourseComponent;
import com.iet.dashboard.academics.domain.CourseOffering;
import com.iet.dashboard.academics.domain.TeachingAssignment;
import com.iet.dashboard.academics.repo.AcademicTermRepository;
import com.iet.dashboard.academics.repo.CourseOfferingRepository;
import com.iet.dashboard.academics.repo.CourseRepository;
import com.iet.dashboard.academics.repo.TeachingAssignmentRepository;
import com.iet.dashboard.academics.web.AllocationImportResultDto;
import com.iet.dashboard.directory.domain.Faculty;
import com.iet.dashboard.directory.repo.DepartmentRepository;
import com.iet.dashboard.directory.repo.FacultyRepository;
import com.iet.dashboard.identity.repo.AppUserRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.context.ActiveProfiles;

import java.nio.charset.StandardCharsets;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

@DataJpaTest
@ActiveProfiles("test")
class AllocationImportServiceTest {

    private static final String HEADER =
            "Faculty Name,Email,Designation,Department,Course Code,Course Name,L-T-P,Semester,Section,Section Students,Component,Batch,Batch Students,Weekly Hours,PBL Hours\n";

    @Autowired AppUserRepository users;
    @Autowired FacultyRepository faculty;
    @Autowired DepartmentRepository departments;
    @Autowired CourseRepository courses;
    @Autowired CourseOfferingRepository offerings;
    @Autowired TeachingAssignmentRepository assignments;
    @Autowired AcademicTermRepository terms;

    private AllocationImportService importer;

    @BeforeEach
    void setUp() {
        importer = new AllocationImportServiceImpl(users, faculty, departments, courses, offerings, assignments, terms);
    }

    private static MockMultipartFile csv(String body) {
        return new MockMultipartFile("file", "allocations.csv", "text/csv", (HEADER + body).getBytes(StandardCharsets.UTF_8));
    }

    @Test
    void importsOneRowPerAssignmentWithSectionAndBatchStrength() {
        AllocationImportResultDto result = importer.importAllocations(csv("""
                Dr. Amit Sinhal,amit.sinhal@iet.edu,Professor,CSE,CS1139,Programming-I,3-0-2,1,C,64,Lecture,,,,2
                Dr. Amit Sinhal,amit.sinhal@iet.edu,Professor,CSE,CS1139,Programming-I,3-0-2,1,C,64,Practical,C1,32,,2
                Dr. Surbhi Chhabra,,,CSE,CS1139,Programming-I,3-0-2,1,C,64,Practical,C2,32,,
                """));

        assertThat(result.issues()).singleElement().satisfies(i -> {
            assertThat(i.severity()).isEqualTo("WARN");
            assertThat(i.message()).as("a guessed email is flagged").contains("surbhi.chhabra@jklu.edu.in");
        });
        assertThat(result.facultyCreated()).isEqualTo(2);
        assertThat(result.assignmentsCreated()).isEqualTo(3);

        CourseOffering section = offerings.findAll().get(0);
        assertThat(section.getStudentCount()).isEqualTo(64);
        assertThat(section.getCourse().getPracticalHours()).isEqualTo(2);

        List<TeachingAssignment> all = assignments.findAll();
        TeachingAssignment lecture = all.stream().filter(a -> a.getComponent() == CourseComponent.LECTURE).findFirst().orElseThrow();
        assertThat(lecture.getSubSection()).isEqualTo("ALL");
        assertThat(lecture.getContactHours()).isEqualTo(3);
        assertThat(lecture.getStudentCount()).isNull();
        assertThat(all).filteredOn(a -> a.getSubSection().equals("C2")).singleElement()
                .satisfies(a -> {
                    assertThat(a.getStudentCount()).isEqualTo(32);
                    assertThat(a.getContactHours()).isEqualTo(2);
                });

        Faculty amit = faculty.findAll().stream().filter(f -> f.getName().equals("Dr. Amit Sinhal")).findFirst().orElseThrow();
        assertThat(amit.getPblHours()).isEqualTo(2);
        assertThat(amit.getDepartmentName()).isEqualTo("CSE");
        assertThat(section.getCourse().getDepartmentId()).as("course takes the sheet's department").isEqualTo(amit.getDepartmentId());
        assertThat(departments.findAll()).extracting(d -> d.getCode()).containsExactly("CSE");
        assertThat(users.findByEmailIgnoreCase("surbhi.chhabra@jklu.edu.in")).as("email derived from name").isPresent();
    }

    @Test
    void visitingFacultyNoteBecomesTheDesignationNotPartOfTheName() {
        importer.importAllocations(csv("Dr. Kishtiz Verma (Visiting Faculty),,,CSE,CS1250,Large Language Models,3-0-0,7,ALL,120,Lecture,,,,\n"));

        Faculty kv = faculty.findAll().get(0);
        assertThat(kv.getName()).isEqualTo("Dr. Kishtiz Verma");
        assertThat(kv.getDesignation()).isEqualTo("Visiting Faculty");
    }

    @Test
    void reimportingTheSameSheetUpdatesInsteadOfDuplicating() {
        String row = "Dr. Amit Sinhal,amit.sinhal@iet.edu,,CSE,CS1139,Programming-I,3-0-2,1,C,64,Practical,C1,32,,\n";
        importer.importAllocations(csv(row));
        AllocationImportResultDto again = importer.importAllocations(csv(row.replace(",32,", ",30,")));

        assertThat(again.assignmentsCreated()).isZero();
        assertThat(again.assignmentsUpdated()).isEqualTo(1);
        assertThat(assignments.findAll()).singleElement().extracting(TeachingAssignment::getStudentCount).isEqualTo(30);
    }

    @Test
    void reportsUnreadableRowsInsteadOfGuessing() {
        AllocationImportResultDto result = importer.importAllocations(csv("""
                Dr. A,,,CSE,CS1139,Programming-I,3-0-2,,C,64,Lecture,,,,
                Dr. B,,,CSE,CS1139,Programming-I,3-0-2,1,C,64,Seminar,,,,
                """));

        assertThat(result.assignmentsCreated()).isZero();
        assertThat(result.issues()).extracting(AllocationImportResultDto.Issue::row).containsExactly(2, 3);
        assertThat(result.issues()).allMatch(i -> i.severity().equals("ERROR"));
    }

    /** Role = Coordinator only sends a verification link (once per person); it grants nothing by itself. */
    @Test
    void aCoordinatorRoleInTheSheetSendsOneVerificationLinkAndGrantsNothingYet() {
        var access = org.mockito.Mockito.mock(com.iet.dashboard.identity.service.CoordinatorAccessService.class);
        org.mockito.Mockito.when(access.invite(org.mockito.ArgumentMatchers.any()))
                .thenReturn(new com.iet.dashboard.identity.service.CoordinatorAccessService.InviteOutcome(true, null));
        var withInvites = new AllocationImportServiceImpl(users, faculty, departments, courses, offerings, assignments, terms,
                access, new com.iet.dashboard.identity.config.AppSettings(false, false));
        String header = HEADER.trim() + ",Role\n";

        AllocationImportResultDto result = withInvites.importAllocations(new MockMultipartFile("file", "a.csv", "text/csv", (header + """
                Dr. S Taruna,s.taruna@jklu.edu.in,Professor,CSE,CS2105,Data Structures,3-1-2,3,B,60,Lecture,,,3,2,Coordinator
                Dr. S Taruna,s.taruna@jklu.edu.in,Professor,CSE,CS2105,Data Structures,3-1-2,3,B,60,Tutorial,,,1,2,coordinator
                Dr. Amit Sinhal,amit.sinhal@jklu.edu.in,Professor,CSE,CS1139,Programming-I,3-0-2,1,C,64,Lecture,,,3,2,Faculty
                Dr. Neha Rao,neha.rao@jklu.edu.in,Professor,CSE,CS1139,Programming-I,3-0-2,1,D,64,Lecture,,,3,2,Dean
                """).getBytes(StandardCharsets.UTF_8)));

        org.mockito.Mockito.verify(access, org.mockito.Mockito.times(1)).invite(
                org.mockito.ArgumentMatchers.argThat(u -> u.getEmail().equals("s.taruna@jklu.edu.in")));
        org.mockito.Mockito.verifyNoMoreInteractions(access);
        assertThat(result.coordinatorInvites()).isEqualTo(1);
        assertThat(users.findByEmailIgnoreCase("s.taruna@jklu.edu.in")).get()
                .extracting(u -> u.getRole()).isEqualTo(com.iet.dashboard.identity.domain.Role.FACULTY);
        assertThat(result.issues()).anySatisfy(i -> {
            assertThat(i.row()).isEqualTo(5);
            assertThat(i.severity()).isEqualTo("WARN");
            assertThat(i.message()).contains("Dean");
        });
    }
}
