package com.iet.dashboard.academics.service;

import com.iet.dashboard.academics.domain.TimetableSlot;
import com.iet.dashboard.academics.repo.AcademicTermRepository;
import com.iet.dashboard.academics.repo.CourseOfferingRepository;
import com.iet.dashboard.academics.repo.CourseRepository;
import com.iet.dashboard.academics.repo.TeachingAssignmentRepository;
import com.iet.dashboard.academics.repo.TimetableSlotRepository;
import com.iet.dashboard.academics.web.AllocationImportResultDto.Issue;
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

import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.time.LocalTime;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * The timetable upload against the messy inputs it meets in practice: a faculty member's own PDF
 * export, files that are not what their name says, and sheets with unreadable or repeated rows.
 */
@DataJpaTest
@ActiveProfiles("test")
class TimetableImportEdgeCaseTest {

    private static final String HEADER = "Day,Start,End,Room,Course Code,Semester,Section,Component,Batch,Faculty Email\n";

    /** Taruna teaches CS1133: the lectures of sections A and B and both labs of section A. */
    private static final String ALLOCATIONS = """
            Faculty Name,Email,Course Code,Course Name,L-T-P,Semester,Section,Section Students,Component,Batch,Batch Students
            Ms. Taruna,taruna@iet.edu,CS1133,Data Structures,3-0-2,3,A,64,Lecture,,
            Ms. Taruna,taruna@iet.edu,CS1133,Data Structures,3-0-2,3,B,64,Lecture,,
            Ms. Taruna,taruna@iet.edu,CS1133,Data Structures,3-0-2,3,A,64,Practical,A1,32
            Ms. Taruna,taruna@iet.edu,CS1133,Data Structures,3-0-2,3,A,64,Practical,A2,32
            Dr. Amit Sinhal,amit@iet.edu,CS1139,Programming-I,3-0-2,1,C,64,Lecture,,
            """;

    @Autowired AppUserRepository users;
    @Autowired FacultyRepository faculty;
    @Autowired DepartmentRepository departments;
    @Autowired CourseRepository courses;
    @Autowired CourseOfferingRepository offerings;
    @Autowired TeachingAssignmentRepository assignments;
    @Autowired AcademicTermRepository terms;
    @Autowired TimetableSlotRepository slots;

    private TimetableImportService importer;
    private Long tarunaId;
    private Long amitId;

    @BeforeEach
    void setUp() {
        allocate(ALLOCATIONS);
        tarunaId = facultyId("Ms. Taruna");
        amitId = facultyId("Dr. Amit Sinhal");
        importer = new TimetableImportServiceImpl(slots, offerings, assignments, faculty, users);
    }

    private void allocate(String csv) {
        new AllocationImportServiceImpl(users, faculty, departments, courses, offerings, assignments, terms)
                .importAllocations(file("allocations.csv", csv));
    }

    private Long facultyId(String name) {
        return faculty.findAll().stream().filter(f -> f.getName().equals(name)).findFirst().orElseThrow().getId();
    }

    private static MockMultipartFile file(String name, String body) {
        return new MockMultipartFile("file", name, "text/csv", body.getBytes(StandardCharsets.UTF_8));
    }

    private static MockMultipartFile pdf(String resource, String uploadedAs) throws Exception {
        try (InputStream in = TimetableImportEdgeCaseTest.class.getResourceAsStream("/timetable-pdf/" + resource)) {
            return new MockMultipartFile("file", uploadedAs, "application/pdf", in.readAllBytes());
        }
    }

    // ---- A faculty member's own PDF -------------------------------------------------------------

    @Test
    void aFacultyMemberUploadingTheirOwnDepartmentPdfGetsEveryClassAsTheirs() throws Exception {
        TimetableImportResultDto result = importer.importTimetable(pdf("department-grid.pdf", "taruna.pdf"), tarunaId);

        assertThat(result.issues()).isEmpty();
        assertThat(result.rows()).isEqualTo(10);
        assertThat(result.slotsCreated()).isEqualTo(10);
        assertThat(result.facultyCovered()).isEqualTo(1);
        assertThat(slots.findAll()).allMatch(s -> s.getFacultyId().equals(tarunaId));
        assertThat(slots.findByFacultyIdWithDetails(tarunaId))
                .filteredOn(s -> s.getDayOfWeek().equals("MONDAY"))
                .allSatisfy(s -> {
                    assertThat(s.getComponent()).isEqualTo("PRACTICAL");
                    assertThat(s.getSubSection()).isEqualTo("A1");
                    assertThat(s.getRoom()).isEqualTo("LRC007");
                });
    }

    @Test
    void anUppercasePdfExtensionIsStillReadAsAPdf() throws Exception {
        TimetableImportResultDto result = importer.importTimetable(pdf("department-grid.pdf", "TARUNA.PDF"), tarunaId);

        assertThat(result.slotsCreated()).isEqualTo(10);
        assertThat(result.issues()).isEmpty();
    }

    /** The portal export drops the course code from lab cells; her single lab allocation fills it in. */
    @Test
    void codeLessPortalCellsResolveToTheFacultysSingleMatchingAllocation() throws Exception {
        TimetableImportResultDto result = importer.importTimetable(pdf("portal-grid.pdf", "portal.pdf"), tarunaId);

        assertThat(result.slotsCreated()).isEqualTo(10);
        assertThat(slots.findByFacultyIdWithDetails(tarunaId))
                .filteredOn(s -> s.getComponent().equals("PRACTICAL"))
                .hasSize(4)
                .allSatisfy(s -> assertThat(s.getOffering().getCourse().getCode()).isEqualTo("CS1133"));
        // The stray CS1142 entry is not hers: reported, not silently dropped or guessed.
        assertThat(result.issues()).singleElement().satisfies(i -> {
            assertThat(i.severity()).isEqualTo("ERROR");
            assertThat(i.message()).contains("CS1142");
        });
    }

    @Test
    void aCodeLessCellThatCouldMeanTwoAllocationsIsAnError() throws Exception {
        // A second course whose A1 lab she also teaches makes the code-less lab cells ambiguous.
        allocate("""
                Faculty Name,Email,Course Code,Course Name,L-T-P,Semester,Section,Section Students,Component,Batch,Batch Students
                Ms. Taruna,taruna@iet.edu,CS1142,Operating Systems,3-0-2,3,A,64,Practical,A1,32
                """);

        TimetableImportResultDto result = importer.importTimetable(pdf("portal-grid.pdf", "portal.pdf"), tarunaId);

        assertThat(result.issues())
                .filteredOn(i -> i.message().contains("no single allocation"))
                .extracting(Issue::severity)
                .containsExactly("ERROR", "ERROR");
        assertThat(slots.findByFacultyIdWithDetails(tarunaId))
                .noneMatch(s -> s.getSubSection().equals("A1"));
        // Her A2 lab is still unambiguous.
        assertThat(slots.findByFacultyIdWithDetails(tarunaId))
                .filteredOn(s -> s.getSubSection().equals("A2")).hasSize(2);
    }

    // ---- Whose slots an upload replaces ----------------------------------------------------------

    @Test
    void aFacultyPdfUploadLeavesOtherFacultySlotsUntouched() throws Exception {
        importer.importTimetable(file("master.csv", HEADER + """
                Mon,9:00,10:00,LT-1,CS1139,1,C,Lecture,,amit@iet.edu
                Thu,10:00,11:00,EB2-004,CS1133,3,A,Lecture,,taruna@iet.edu
                """), null);
        TimetableSlot amitBefore = slots.findByFacultyIdWithDetails(amitId).get(0);

        importer.importTimetable(pdf("department-grid.pdf", "taruna.pdf"), tarunaId);

        assertThat(slots.findByFacultyIdWithDetails(amitId)).singleElement().satisfies(s -> {
            assertThat(s.getId()).isEqualTo(amitBefore.getId());
            assertThat(s.getDayOfWeek()).isEqualTo("MONDAY");
            assertThat(s.getStartTime()).isEqualTo(LocalTime.of(9, 0));
            assertThat(s.getRoom()).isEqualTo("LT-1");
        });
        assertThat(slots.findByFacultyIdWithDetails(tarunaId)).hasSize(10)
                .noneMatch(s -> s.getDayOfWeek().equals("THURSDAY"));
    }

    @Test
    void aCoordinatorUploadReplacesEverySlot() {
        importer.importTimetable(file("master.csv", HEADER + """
                Mon,9:00,10:00,LT-1,CS1139,1,C,Lecture,,amit@iet.edu
                Thu,10:00,11:00,EB2-004,CS1133,3,A,Lecture,,taruna@iet.edu
                """), null);

        TimetableImportResultDto result = importer.importTimetable(file("master2.csv", HEADER
                + "Fri,12:00,13:00,EB2-206,CS1133,3,B,Lecture,,taruna@iet.edu\n"), null);

        assertThat(result.slotsCreated()).isEqualTo(1);
        assertThat(slots.findAll()).singleElement().satisfies(s -> {
            assertThat(s.getFacultyId()).isEqualTo(tarunaId);
            assertThat(s.getDayOfWeek()).isEqualTo("FRIDAY");
        });
    }

    /** A faculty upload means "these rows are mine": an email column naming someone else is ignored. */
    @Test
    void aFacultyUploadCannotCreateSlotsForSomeoneElse() {
        TimetableImportResultDto result = importer.importTimetable(file("mine.csv", HEADER
                + "Mon,9:00,10:00,LT-1,CS1139,1,C,Lecture,,amit@iet.edu\n"), tarunaId);

        assertThat(result.slotsCreated()).isZero();
        assertThat(result.issues()).singleElement().extracting(Issue::severity).isEqualTo("ERROR");
        assertThat(slots.findByFacultyIdWithDetails(amitId)).isEmpty();
    }

    // ---- Files that are not what they claim ------------------------------------------------------

    @Test
    void anEmptyFileIsRefused() {
        assertThatThrownBy(() -> importer.importTimetable(
                new MockMultipartFile("file", "tt.pdf", "application/pdf", new byte[0]), tarunaId))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("empty");
        assertThatThrownBy(() -> importer.importTimetable(null, null))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void aNonPdfRenamedToPdfIsRefusedAndKeepsExistingSlots() {
        importer.importTimetable(file("master.csv", HEADER
                + "Mon,9:00,10:00,LT-1,CS1139,1,C,Lecture,,amit@iet.edu\n"), null);

        assertThatThrownBy(() -> importer.importTimetable(file("timetable.pdf", HEADER
                + "Mon,9:00,10:00,LT-1,CS1139,1,C,Lecture,,amit@iet.edu\n"), amitId))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("could not be read");
        assertThat(slots.findAll()).hasSize(1);
    }

    @Test
    void aSheetWithoutTheRequiredColumnsIsRefused() {
        assertThatThrownBy(() -> importer.importTimetable(file("tt.csv", """
                Day,Start,Room,Course Code,Section
                Mon,9:00,LT-1,CS1139,C
                """), amitId))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("Missing column \"end\"");
    }

    @Test
    void aHeaderOnlySheetIsRefused() {
        assertThatThrownBy(() -> importer.importTimetable(file("tt.csv", HEADER), null))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("no rows");
    }

    @Test
    void aMasterSheetMustSayWhoseRowsTheyAre() {
        String noFacultyColumn = """
                Day,Start,End,Room,Course Code,Semester,Section,Component,Batch
                Mon,9:00,10:00,LT-1,CS1139,1,C,Lecture,
                """;
        assertThatThrownBy(() -> importer.importTimetable(file("tt.csv", noFacultyColumn), null))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("Faculty Email");
        // The same sheet is fine as a personal upload, where every row is the uploader's.
        assertThat(importer.importTimetable(file("tt.csv", noFacultyColumn), amitId).slotsCreated()).isEqualTo(1);
    }

    @Test
    void aMasterSheetMayNameFacultyInsteadOfGivingAnEmail() {
        TimetableImportResultDto result = importer.importTimetable(file("tt.csv", """
                Day,Start,End,Room,Course Code,Semester,Section,Component,Batch,Faculty Name
                Mon,9:00,10:00,LT-1,CS1139,1,C,Lecture,,dr. amit sinhal
                Tue,9:00,10:00,LT-1,CS1139,1,C,Lecture,,Dr. Nobody
                """), null);

        assertThat(result.slotsCreated()).isEqualTo(1);
        assertThat(result.issues()).extracting(Issue::row).containsExactly(3);
    }

    // ---- Unreadable rows -------------------------------------------------------------------------

    @Test
    void unreadableDaysAndTimesAreReportedPerRow() {
        TimetableImportResultDto result = importer.importTimetable(file("tt.csv", HEADER + """
                Mo,9:00,10:00,LT-1,CS1139,1,C,Lecture,,amit@iet.edu
                Mon,9 o'clock,10:00,LT-1,CS1139,1,C,Lecture,,amit@iet.edu
                Mon,25:00,26:00,LT-1,CS1139,1,C,Lecture,,amit@iet.edu
                Mon,9:75,10:00,LT-1,CS1139,1,C,Lecture,,amit@iet.edu
                Mon,,10:00,LT-1,CS1139,1,C,Lecture,,amit@iet.edu
                Mon,10:00,9:00,LT-1,CS1139,1,C,Lecture,,amit@iet.edu
                Mon,10:00,10:00,LT-1,CS1139,1,C,Lecture,,amit@iet.edu
                MONDAY,12:00 PM,1:00 PM,LT-1,CS1139,1,C,Lecture,,amit@iet.edu
                """), null);

        assertThat(result.issues()).extracting(Issue::row).containsExactly(2, 3, 4, 5, 6, 7, 8);
        assertThat(result.issues()).allMatch(i -> i.severity().equals("ERROR") && i.message().contains("Day or time"));
        assertThat(slots.findAll()).singleElement().satisfies(s -> {
            assertThat(s.getStartTime()).isEqualTo(LocalTime.NOON);
            assertThat(s.getEndTime()).isEqualTo(LocalTime.of(13, 0));
        });
    }

    @Test
    void midnightAndNoonAreReadFromTwelveHourTimes() {
        assertThat(TimetableImportServiceImpl.time("12:00 AM")).isEqualTo(LocalTime.MIDNIGHT);
        assertThat(TimetableImportServiceImpl.time("12 pm")).isEqualTo(LocalTime.NOON);
        assertThat(TimetableImportServiceImpl.time("9.30")).isEqualTo(LocalTime.of(9, 30));
        assertThat(TimetableImportServiceImpl.time("")).isNull();
        assertThat(TimetableImportServiceImpl.day("Thu")).isEqualTo(java.time.DayOfWeek.THURSDAY);
        assertThat(TimetableImportServiceImpl.day("  ")).isNull();
        assertThat(TimetableImportServiceImpl.day("Holiday")).isNull();
    }

    @Test
    void anUnknownFacultyEmailIsReported() {
        TimetableImportResultDto result = importer.importTimetable(file("tt.csv", HEADER
                + "Mon,9:00,10:00,LT-1,CS1139,1,C,Lecture,,ghost@iet.edu\n"), null);

        assertThat(result.slotsCreated()).isZero();
        assertThat(result.issues()).singleElement().satisfies(i -> {
            assertThat(i.row()).isEqualTo(2);
            assertThat(i.message()).contains("Faculty not found");
        });
    }

    /** A row pasted twice must not put the same class on someone's day twice. */
    @Test
    void aRepeatedRowCreatesOneSlotAndIsReported() {
        TimetableImportResultDto result = importer.importTimetable(file("tt.csv", HEADER + """
                Mon,9:00,10:00,LT-1,CS1139,1,C,Lecture,,amit@iet.edu
                Mon,09:00,10:00,LT-1,CS1139,1,C,Lecture,,AMIT@iet.edu
                """), null);

        assertThat(result.slotsCreated()).isEqualTo(1);
        assertThat(slots.findAll()).hasSize(1);
        assertThat(result.issues()).singleElement().satisfies(i -> {
            assertThat(i.row()).isEqualTo(3);
            assertThat(i.severity()).isEqualTo("WARN");
        });
    }
}
