package com.iet.dashboard.academics.service;

import com.iet.dashboard.academics.domain.TimetableSlot;
import com.iet.dashboard.academics.repo.AcademicTermRepository;
import com.iet.dashboard.academics.repo.CourseOfferingRepository;
import com.iet.dashboard.academics.repo.CourseRepository;
import com.iet.dashboard.academics.repo.TeachingAssignmentRepository;
import com.iet.dashboard.academics.repo.TimetableSlotRepository;
import com.iet.dashboard.academics.web.TimetableImportResultDto;
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
import java.time.LocalTime;

import static org.assertj.core.api.Assertions.assertThat;

@DataJpaTest
@ActiveProfiles("test")
class TimetableImportServiceTest {

    private static final String HEADER = "Day,Start,End,Room,Course Code,Semester,Section,Component,Batch,Faculty Email\n";

    @Autowired AppUserRepository users;
    @Autowired FacultyRepository faculty;
    @Autowired DepartmentRepository departments;
    @Autowired CourseRepository courses;
    @Autowired CourseOfferingRepository offerings;
    @Autowired TeachingAssignmentRepository assignments;
    @Autowired AcademicTermRepository terms;
    @Autowired TimetableSlotRepository slots;

    private TimetableImportService importer;
    private Long amitId;

    @BeforeEach
    void setUp() {
        new AllocationImportServiceImpl(users, faculty, departments, courses, offerings, assignments, terms)
                .importAllocations(file("allocations.csv", """
                        Faculty Name,Email,Course Code,Course Name,L-T-P,Semester,Section,Section Students,Component,Batch,Batch Students
                        Dr. Amit Sinhal,amit@iet.edu,CS1139,Programming-I,3-0-2,1,C,64,Lecture,,
                        Dr. Amit Sinhal,amit@iet.edu,CS1139,Programming-I,3-0-2,1,C,64,Practical,C1,32
                        Dr. Surbhi Chhabra,surbhi@iet.edu,CS1139,Programming-I,3-0-2,1,C,64,Practical,C2,32
                        """));
        amitId = faculty.findAll().stream().filter(f -> f.getName().equals("Dr. Amit Sinhal")).findFirst().orElseThrow().getId();
        importer = new TimetableImportServiceImpl(slots, offerings, assignments, faculty, users);
    }

    private static MockMultipartFile file(String name, String body) {
        return new MockMultipartFile("file", name, "text/csv", body.getBytes(StandardCharsets.UTF_8));
    }

    @Test
    void mapsEachSlotToTheFacultyAllocation() {
        TimetableImportResultDto result = importer.importTimetable(file("tt.csv", HEADER + """
                Mon,9:00,10:00,LT-1,CS1139,1,C,Lecture,,amit@iet.edu
                Wednesday,2:00 PM,4:00 PM,Lab 3,CS1139,1,C,Practical,C1,amit@iet.edu
                tue,11:00,13:00,Lab 3,CS1139,1,C,Practical,C2,surbhi@iet.edu
                """), null);

        assertThat(result.issues()).isEmpty();
        assertThat(result.slotsCreated()).isEqualTo(3);
        assertThat(result.facultyCovered()).isEqualTo(2);
        TimetableSlot lab = slots.findByFacultyIdWithDetails(amitId).stream()
                .filter(s -> s.getDayOfWeek().equals("WEDNESDAY")).findFirst().orElseThrow();
        assertThat(lab.getStartTime()).isEqualTo(LocalTime.of(14, 0));
        assertThat(lab.getSubSection()).isEqualTo("C1");
        assertThat(lab.getComponent()).isEqualTo("PRACTICAL");
    }

    @Test
    void rejectsSlotsThatNoAllocationCovers() {
        TimetableImportResultDto result = importer.importTimetable(file("tt.csv", HEADER + """
                Mon,9:00,10:00,LT-1,CS1139,1,C,Practical,C2,amit@iet.edu
                Funday,9:00,10:00,LT-1,CS1139,1,C,Lecture,,amit@iet.edu
                Mon,9:00,10:00,LT-1,CS9999,1,C,Lecture,,amit@iet.edu
                """), null);

        assertThat(result.slotsCreated()).isZero();
        assertThat(result.issues()).extracting(i -> i.row()).containsExactly(2, 3, 4);
    }

    @Test
    void aFacultyUploadReplacesOnlyTheirOwnSlots() {
        importer.importTimetable(file("tt.csv", HEADER + """
                Mon,9:00,10:00,LT-1,CS1139,1,C,Lecture,,amit@iet.edu
                Tue,11:00,13:00,Lab 3,CS1139,1,C,Practical,C2,surbhi@iet.edu
                """), null);

        // Amit uploads his own sheet without the email column: rows are his by definition.
        importer.importTimetable(file("mine.csv", """
                Day,Start,End,Room,Course Code,Semester,Section,Component,Batch
                Thu,10:00,11:00,LT-2,CS1139,1,C,Lecture,
                Fri,10:00,11:00,LT-2,CS1139,1,C,Lecture,
                """), amitId);

        assertThat(slots.findByFacultyIdWithDetails(amitId)).extracting(TimetableSlot::getDayOfWeek)
                .containsExactlyInAnyOrder("THURSDAY", "FRIDAY");
        assertThat(slots.findAll()).hasSize(3);
    }

    /** The files in docs/samples are what we hand to coordinators; they must import cleanly. */
    @Test
    void theSampleFilesInDocsImportWithoutIssues() throws Exception {
        java.nio.file.Path samples = java.nio.file.Path.of("..", "docs", "samples");
        var allocations = new AllocationImportServiceImpl(users, faculty, departments, courses, offerings, assignments, terms)
                .importAllocations(file("allocations-odd-2026.csv", java.nio.file.Files.readString(samples.resolve("allocations-odd-2026.csv"))));
        assertThat(allocations.issues()).isEmpty();
        assertThat(allocations.rows()).isEqualTo(215);

        TimetableImportResultDto timetable = importer.importTimetable(
                file("timetable-sample.csv", java.nio.file.Files.readString(samples.resolve("timetable-sample.csv"))), null);
        assertThat(timetable.issues()).isEmpty();
        assertThat(timetable.slotsCreated()).isEqualTo(19);
        assertThat(timetable.facultyCovered()).isEqualTo(2);
    }
}
