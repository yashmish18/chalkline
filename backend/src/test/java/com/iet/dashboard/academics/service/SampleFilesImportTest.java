package com.iet.dashboard.academics.service;

import com.iet.dashboard.academics.repo.AcademicTermRepository;
import com.iet.dashboard.academics.repo.CourseOfferingRepository;
import com.iet.dashboard.academics.repo.CourseRepository;
import com.iet.dashboard.academics.repo.TeachingAssignmentRepository;
import com.iet.dashboard.academics.repo.TimetableSlotRepository;
import com.iet.dashboard.academics.web.AllocationImportResultDto;
import com.iet.dashboard.academics.web.TimetableImportResultDto;
import com.iet.dashboard.directory.repo.DepartmentRepository;
import com.iet.dashboard.directory.repo.FacultyRepository;
import com.iet.dashboard.identity.repo.AppUserRepository;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.context.ActiveProfiles;

import java.nio.file.Files;
import java.nio.file.Path;

import static org.assertj.core.api.Assertions.assertThat;

/** The sample files handed to users (docs/samples/complete) must import without a single problem. */
@DataJpaTest
@ActiveProfiles("test")
class SampleFilesImportTest {

    private static final Path SAMPLES = Path.of("..", "docs", "samples", "complete");

    @Autowired AppUserRepository users;
    @Autowired FacultyRepository faculty;
    @Autowired DepartmentRepository departments;
    @Autowired CourseRepository courses;
    @Autowired CourseOfferingRepository offerings;
    @Autowired TeachingAssignmentRepository assignments;
    @Autowired AcademicTermRepository terms;
    @Autowired TimetableSlotRepository slots;

    private static MockMultipartFile sample(String name) throws Exception {
        return new MockMultipartFile("file", name, "text/csv", Files.readAllBytes(SAMPLES.resolve(name)));
    }

    @Test
    void theCompleteSampleSetImportsCleanly() throws Exception {
        AllocationImportResultDto allocations = new AllocationImportServiceImpl(
                users, faculty, departments, courses, offerings, assignments, terms)
                .importAllocations(sample("allocations-complete.csv"));
        assertThat(allocations.issues()).isEmpty();
        assertThat(allocations.facultyCreated()).isEqualTo(6);
        assertThat(allocations.assignmentsCreated()).isEqualTo(18);
        assertThat(departments.findAll()).extracting(d -> d.getCode()).containsExactlyInAnyOrder("CSE", "ECE");
        assertThat(users.findAll()).allMatch(u -> u.getEmail().endsWith("@jklu.edu.in"));

        var timetable = new TimetableImportServiceImpl(slots, offerings, assignments, faculty, users);
        TimetableImportResultDto master = timetable.importTimetable(sample("timetable-master-complete.csv"), null);
        assertThat(master.issues()).isEmpty();
        assertThat(master.slotsCreated()).isEqualTo(30);
        assertThat(master.facultyCovered()).isEqualTo(6);

        Long mayank = faculty.findAll().stream().filter(f -> f.getName().equals("Dr. Mayank Kundalwal"))
                .findFirst().orElseThrow().getId();
        TimetableImportResultDto personal = timetable.importTimetable(sample("timetable-personal-mayank.csv"), mayank);
        assertThat(personal.issues()).isEmpty();
        assertThat(personal.slotsCreated()).isEqualTo(6);
        assertThat(slots.findAll()).as("his own upload leaves everyone else's classes").hasSize(30);
    }
}
