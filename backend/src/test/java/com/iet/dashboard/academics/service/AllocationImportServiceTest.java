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

    @Autowired com.iet.dashboard.directory.repo.SkillRepository skillRepo;
    @Autowired com.iet.dashboard.academics.repo.TimetableSlotRepository slotRepo;
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
        importer = new AllocationImportServiceImpl(users, faculty, departments, courses, offerings, assignments, terms, skillRepo, slotRepo);
    }

    private static MockMultipartFile csv(String body) {
        return new MockMultipartFile("file", "allocations.csv", "text/csv", (HEADER + body).getBytes(StandardCharsets.UTF_8));
    }

    @Test
    void importsOneRowPerAssignmentWithSectionAndBatchStrength() {
        AllocationImportResultDto result = importer.importAllocations(csv("""
                Dr. Amit Sinhal,amit.sinhal@iet.edu,Professor,CSE,CS1139,Programming-I,3-0-2,1,C,64,Lecture,,,3,2
                Dr. Amit Sinhal,amit.sinhal@iet.edu,Professor,CSE,CS1139,Programming-I,3-0-2,1,C,64,Practical,C1,32,2,2
                Dr. Surbhi Chhabra,surbhi@iet.edu,,CSE,CS1139,Programming-I,3-0-2,1,C,64,Practical,C2,32,,
                """));

        assertThat(result.issues()).isEmpty();
        assertThat(result.imported()).isTrue();
        assertThat(result.facultyCreated()).isEqualTo(2);
        assertThat(result.assignmentsCreated()).isEqualTo(3);

        CourseOffering section = offerings.findAll().get(0);
        assertThat(section.getStudentCount()).isEqualTo(64);
        assertThat(section.getCourse().getPracticalHours()).isEqualTo(2);

        List<TeachingAssignment> all = assignments.findAll();
        TeachingAssignment lecture = all.stream().filter(a -> a.getComponent() == CourseComponent.LECTURE).findFirst().orElseThrow();
        assertThat(lecture.getSubSection()).isEqualTo("ALL");
        assertThat(lecture.getContactHours()).as("hours come from L-T-P").isEqualTo(3);
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
        Faculty surbhi = faculty.findAll().stream().filter(f -> f.getName().equals("Dr. Surbhi Chhabra")).findFirst().orElseThrow();
        assertThat(surbhi.getDesignation()).as("no designation given, none invented").isEmpty();
    }

    @Test
    void nothingIsGuessedAndOneBadRowStopsTheWholeImport() {
        AllocationImportResultDto result = importer.importAllocations(csv("""
                Dr. Good Row,good@iet.edu,,CSE,CS1139,Programming-I,3-0-2,1,C,64,Lecture,,,,
                Dr. No Email,,,CSE,CS1139,Programming-I,3-0-2,1,D,64,Lecture,,,,
                Dr. Bad Email,bad-at-iet.edu,,CSE,CS1139,Programming-I,3-0-2,1,E,64,Lecture,,,,
                Dr. No Name Course,a@iet.edu,,CSE,CS1140,,3-0-2,1,C,64,Lecture,,,,
                Dr. No Students,b@iet.edu,,CSE,CS1139,Programming-I,3-0-2,1,F,,Lecture,,,,
                Dr. Typo Students,c@iet.edu,,CSE,CS1139,Programming-I,3-0-2,1,G,6O,Lecture,,,,
                Dr. Bad Ltp,d@iet.edu,,CSE,CS1141,Maths,3 0 2,1,C,64,Lecture,,,,
                Dr. Seminar,e@iet.edu,,CSE,CS1139,Programming-I,3-0-2,1,H,64,Seminar,,,,
                Dr. No Batch,f@iet.edu,,CSE,CS1139,Programming-I,3-0-2,1,I,64,Practical,,,,
                Dr. No Batch Count,g@iet.edu,,CSE,CS1139,Programming-I,3-0-2,1,J,64,Practical,J1,,,
                Dr. Lecture Batch,h@iet.edu,,CSE,CS1139,Programming-I,3-0-2,1,K,64,Lecture,K1,30,,
                Dr. Zero Tut,i@iet.edu,,CSE,CS1139,Programming-I,3-0-2,1,L,64,Tutorial,,,,
                Dr. Wrong Hours,j@iet.edu,,CSE,CS1139,Programming-I,3-0-2,1,M,64,Lecture,,,4,
                """));

        assertThat(result.imported()).isFalse();
        assertThat(result.assignmentsCreated()).isZero();
        assertThat(users.findAll()).as("nothing saved").isEmpty();
        assertThat(courses.findAll()).isEmpty();
        assertThat(result.issues()).filteredOn(i -> i.row() > 0)
                .extracting(AllocationImportResultDto.Issue::row)
                .containsExactly(3, 4, 5, 6, 7, 8, 9, 10, 11, 12, 13, 14);
        assertThat(result.issues()).allMatch(i -> i.severity().equals("ERROR"));
    }

    @Test
    void aValueRepeatedAcrossRowsMustBeTheSameOnEveryRow() {
        AllocationImportResultDto result = importer.importAllocations(csv("""
                Dr. A,a@iet.edu,Professor,CSE,CS1139,Programming-I,3-0-2,1,C,64,Lecture,,,,
                Dr. B,b@iet.edu,,CSE,CS1139,Programming I,3-0-2,1,D,64,Lecture,,,,
                Dr. C,c@iet.edu,,CSE,CS1139,Programming-I,3-0-4,1,E,64,Lecture,,,,
                Dr. D,d@iet.edu,,CSE,CS1139,Programming-I,3-0-2,1,C,62,Practical,C1,30,,
                Dr. A,a@iet.edu,Associate Professor,CSE,CS1139,Programming-I,3-0-2,1,F,64,Lecture,,,,
                Dr. A Typo,a@iet.edu,,CSE,CS1139,Programming-I,3-0-2,1,G,64,Lecture,,,,
                Dr. E,e@iet.edu,,CSE,CS1139,Programming-I,3-0-2,1,C,64,Practical,C1,31,,
                Dr. F,f@iet.edu,,CSE,CS1139,Programming-I,3-0-2,1,C,64,Practical,C2,40,,
                """));

        assertThat(result.imported()).isFalse();
        assertThat(result.issues()).filteredOn(i -> i.row() > 0).extracting(i -> i.message())
                .anyMatch(m -> m.contains("name of course CS1139") && m.contains("Programming I"))
                .anyMatch(m -> m.contains("L-T-P of course CS1139"))
                .anyMatch(m -> m.contains("Section Students of CS1139 Sem 1 Sec C") && m.contains("62"))
                .anyMatch(m -> m.contains("designation of a@iet.edu"))
                .anyMatch(m -> m.contains("name of a@iet.edu") && m.contains("Dr. A Typo"))
                .anyMatch(m -> m.contains("Batch Students of CS1139 Sem 1 Sec C batch C1"))
                .anyMatch(m -> m.contains("add up to 70") && m.contains("section's 64"));
    }

    @Test
    void aFacultyOnlyRowAddsAPersonWithSkillsAndNoCourse() {
        String header = "Faculty Name,Email,Designation,Department,Skills,Course Code\n";
        AllocationImportResultDto result = importer.importAllocations(new MockMultipartFile("file", "a.csv", "text/csv",
                (header + "Dr. New Joiner,new.joiner@iet.edu,Assistant Professor,CSE,Machine Learning; NLP; machine learning,\n")
                        .getBytes(StandardCharsets.UTF_8)));

        assertThat(result.issues()).isEmpty();
        assertThat(result.facultyCreated()).isEqualTo(1);
        assertThat(assignments.findAll()).isEmpty();
        Faculty joiner = faculty.findAll().get(0);
        assertThat(joiner.getDesignation()).isEqualTo("Assistant Professor");
        assertThat(joiner.getSkills()).extracting(s -> s.getName()).containsExactlyInAnyOrder("Machine Learning", "NLP");
    }

    @Test
    void visitingFacultyNoteBecomesTheDesignationNotPartOfTheName() {
        importer.importAllocations(csv("Dr. Kishtiz Verma (Visiting Faculty),kv@iet.edu,,CSE,CS1250,Large Language Models,3-0-0,7,ALL,120,Lecture,,,,\n"));

        Faculty kv = faculty.findAll().get(0);
        assertThat(kv.getName()).isEqualTo("Dr. Kishtiz Verma");
        assertThat(kv.getDesignation()).isEqualTo("Visiting Faculty");
    }

    @Test
    void reimportingUpdatesInPlaceAndRemovesAllocationsThatLeftTheSheet() {
        String a3 = "Dr. S Taruna,taruna@iet.edu,,CSE,CS1133,DBMS,3-0-4,3,A,60,Practical,A3,30,,\n";
        importer.importAllocations(csv(a3 + "Dr. S Taruna,taruna@iet.edu,,CSE,CS1133,DBMS,3-0-4,3,A,60,Lecture,,,,\n"));
        Long offeringId = offerings.findAll().get(0).getId();
        Long tarunaId = faculty.findAll().get(0).getId();
        slotRepo.save(new com.iet.dashboard.academics.domain.TimetableSlot(offerings.findAll().get(0), tarunaId, "MONDAY",
                java.time.LocalTime.of(14, 0), java.time.LocalTime.of(16, 0), "LRC007", "PRACTICAL", "A3"));

        AllocationImportResultDto again = importer.importAllocations(csv("""
                Dr. S Taruna,taruna@iet.edu,,CSE,CS1133,DBMS,3-0-4,3,A,60,Lecture,,,,
                Dr. S Taruna,taruna@iet.edu,,CSE,CS1133,DBMS,3-0-4,3,A,60,Practical,A1,30,,
                Dr. S Taruna,taruna@iet.edu,,CSE,CS1133,DBMS,3-0-4,3,A,60,Practical,A2,30,,
                """));

        assertThat(again.assignmentsCreated()).isEqualTo(2);
        assertThat(again.assignmentsUpdated()).isEqualTo(1);
        assertThat(again.assignmentsRemoved()).as("A3 left the sheet").isEqualTo(1);
        assertThat(assignments.findAll()).extracting(TeachingAssignment::getSubSection).containsExactlyInAnyOrder("ALL", "A1", "A2");
        assertThat(slotRepo.findAll()).as("the A3 lab slot went with it").isEmpty();
        assertThat(offerings.findById(offeringId)).isPresent();
    }

    /** The Role column decides who is coordinator; the admin and the uploader are never demoted. */
    @Test
    void theRoleColumnAssignsCoordinatorAndFacultyButNeverDemotesTheAdminOrTheUploader() {
        var admin = new com.iet.dashboard.identity.domain.AppUser("s.taruna@jklu.edu.in", "Dr. S Taruna",
                com.iet.dashboard.identity.domain.Role.COORDINATOR);
        admin.setAdmin(true);
        users.save(admin);
        users.save(new com.iet.dashboard.identity.domain.AppUser("old.coord@jklu.edu.in", "Dr. Old Coord",
                com.iet.dashboard.identity.domain.Role.COORDINATOR));
        users.save(new com.iet.dashboard.identity.domain.AppUser("me@jklu.edu.in", "Dr. Me",
                com.iet.dashboard.identity.domain.Role.COORDINATOR));
        org.springframework.security.core.context.SecurityContextHolder.getContext().setAuthentication(
                new org.springframework.security.authentication.UsernamePasswordAuthenticationToken("me@jklu.edu.in", null, java.util.List.of()));
        String header = HEADER.trim() + ",Role\n";
        try {
            AllocationImportResultDto result = importer.importAllocations(new MockMultipartFile("file", "a.csv", "text/csv", (header + """
                    Dr. S Taruna,s.taruna@jklu.edu.in,Professor,CSE,CS2105,Data Structures,3-1-2,3,B,60,Lecture,,,3,2,Faculty
                    Dr. Amit Sinhal,amit.sinhal@jklu.edu.in,Professor,CSE,CS1139,Programming-I,3-0-2,1,C,64,Lecture,,,3,2,Coordinator
                    Dr. Amit Sinhal,amit.sinhal@jklu.edu.in,Professor,CSE,CS1139,Programming-I,3-0-2,1,C,64,Practical,C1,32,2,2,Coordinator
                    Dr. Old Coord,old.coord@jklu.edu.in,Professor,CSE,CS1139,Programming-I,3-0-2,1,D,64,Lecture,,,3,2,Faculty
                    Dr. Me,me@jklu.edu.in,Professor,CSE,CS1139,Programming-I,3-0-2,1,E,64,Lecture,,,3,2,Faculty
                    Dr. Blank Role,blank.role@jklu.edu.in,Professor,CSE,CS1139,Programming-I,3-0-2,1,G,64,Lecture,,,3,2,
                    """).getBytes(StandardCharsets.UTF_8)));

            assertThat(result.issues()).filteredOn(i -> !i.severity().equals("INFO")).isEmpty();
            java.util.function.Function<String, com.iet.dashboard.identity.domain.Role> role =
                    email -> users.findByEmailIgnoreCase(email).orElseThrow().getRole();
            assertThat(role.apply("amit.sinhal@jklu.edu.in")).as("the Role column")
                    .isEqualTo(com.iet.dashboard.identity.domain.Role.COORDINATOR);
            assertThat(role.apply("old.coord@jklu.edu.in")).isEqualTo(com.iet.dashboard.identity.domain.Role.FACULTY);
            assertThat(role.apply("s.taruna@jklu.edu.in")).as("the admin").isEqualTo(com.iet.dashboard.identity.domain.Role.COORDINATOR);
            assertThat(role.apply("me@jklu.edu.in")).as("the uploader").isEqualTo(com.iet.dashboard.identity.domain.Role.COORDINATOR);
            assertThat(role.apply("blank.role@jklu.edu.in")).isEqualTo(com.iet.dashboard.identity.domain.Role.FACULTY);
            assertThat(result.rolesChanged()).isEqualTo(2);

            AllocationImportResultDto dean = importer.importAllocations(new MockMultipartFile("file", "b.csv", "text/csv", (header
                    + "Dr. Neha Rao,neha.rao@jklu.edu.in,Professor,CSE,CS1139,Programming-I,3-0-2,1,F,64,Lecture,,,3,2,Dean\n")
                    .getBytes(StandardCharsets.UTF_8)));
            assertThat(dean.imported()).as("an unknown role is not quietly made faculty").isFalse();
            assertThat(dean.issues()).anyMatch(i -> i.row() == 2 && i.message().contains("Dean"));
        } finally {
            org.springframework.security.core.context.SecurityContextHolder.clearContext();
        }
    }
}
