package com.iet.dashboard.tracking.service;

import com.iet.dashboard.deadlines.service.DeadlineService;
import com.iet.dashboard.deadlines.web.DeadlineAssignmentDto;
import com.iet.dashboard.deadlines.web.DeadlineDto;
import com.iet.dashboard.directory.service.FacultyService;
import com.iet.dashboard.directory.web.FacultyDto;
import com.iet.dashboard.identity.service.UserService;
import com.iet.dashboard.identity.web.UserDto;
import com.iet.dashboard.submissions.service.SubmissionService;
import com.iet.dashboard.submissions.web.SubmissionDto;
import com.iet.dashboard.tracking.web.DeadlineTrackingRowDto;
import com.iet.dashboard.tracking.web.DepartmentMetricsDto;
import com.iet.dashboard.academics.web.CourseDto;
import com.iet.dashboard.academics.web.CourseOfferingDto;
import com.iet.dashboard.academics.web.FacultyLtpLoadDto;
import com.iet.dashboard.academics.web.TeachingAssignmentDto;
import com.iet.dashboard.tracking.web.FacultyComplianceRowDto;
import com.iet.dashboard.tracking.web.FacultyProfileDto;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.Collections;
import java.util.List;
import java.time.LocalDate;
import com.iet.dashboard.academics.web.AcademicTermDto;
import com.iet.dashboard.academics.web.CourseProgressDto;
import com.iet.dashboard.teaching.domain.SessionStatus;
import com.iet.dashboard.teaching.domain.TeachingSession;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class TrackingServiceTest {

    @Mock
    private DeadlineService deadlineService;

    @Mock
    private FacultyService facultyService;

    @Mock
    private SubmissionService submissionService;

    @Mock
    private UserService userService;

    @Mock
    private com.iet.dashboard.academics.service.AcademicsService academicsService;

    @Mock
    private com.iet.dashboard.academics.service.PacingCalculationService pacingService;

    @Mock
    private com.iet.dashboard.teaching.repo.TeachingSessionRepository sessionRepository;

    @Mock
    private com.iet.dashboard.academics.repo.TimetableSlotRepository timetableSlotRepository;

    @InjectMocks
    private TrackingServiceImpl trackingService;

    private FacultyDto faculty1;
    private DeadlineDto deadline1;
    private DeadlineAssignmentDto assignment1;

    @BeforeEach
    void setUp() {
        faculty1 = new FacultyDto(1L, 2L, "Dr. Amit", "Assoc Prof", "PhD", "IT", Collections.emptyList());
        Instant dueAt = Instant.now().plus(3, ChronoUnit.DAYS);
        deadline1 = new DeadlineDto(10L, "Course File", "Desc", "COURSE_FILE", dueAt, 1L, Instant.now(), false, 1);
        assignment1 = new DeadlineAssignmentDto(100L, 10L, "Course File", "Desc", "COURSE_FILE", dueAt, 1L, "Dr. Amit", null, false);
    }

    @Test
    void testGetFacultyProfile_AssemblesCoursesLoadComplianceAndHistory() {
        CourseDto course = new CourseDto(1L, "CS501", "Networks", "THEORY", "CORE", 4, "3-0-2", 3, 0, 2, null);
        CourseOfferingDto offering = new CourseOfferingDto(1L, course, "2026-2027", 3, 5, "A", 1L, "Semester 1");
        TeachingAssignmentDto assignment = new TeachingAssignmentDto(
                10L, offering, 1L, "Dr. Amit", "ALL", "ALL", 5);

        when(facultyService.getFacultyById(1L)).thenReturn(faculty1);
        when(academicsService.getTeachingAssignmentsForFaculty(1L)).thenReturn(List.of(assignment));
        when(academicsService.getFacultyLtpLoad()).thenReturn(List.of(
                new FacultyLtpLoadDto(1L, "Dr. Amit", 3, 0, 2, 5, 1)));

        when(facultyService.getAllFaculty(null, null)).thenReturn(List.of(faculty1));
        when(deadlineService.getAllDeadlines()).thenReturn(List.of(deadline1));
        when(deadlineService.getAssignmentsForDeadline(10L)).thenReturn(List.of(assignment1));
        when(submissionService.getSubmissionStatusesForDeadline(10L)).thenReturn(java.util.Map.of(100L, "ACCEPTED"));
        when(submissionService.getSubmissionByAssignmentId(100L)).thenReturn(
                new SubmissionDto(5L, 100L, "ACCEPTED", Instant.now(), null, Instant.now(), null, 1, Collections.emptyList()));

        FacultyProfileDto profile = trackingService.getFacultyProfile(1L);

        assertEquals("Dr. Amit", profile.name());
        assertEquals("IT", profile.department());
        assertEquals(1, profile.courses().size());
        assertEquals("CS501", profile.courses().get(0).courseCode());
        assertEquals("ALL", profile.courses().get(0).component());
        assertEquals(5, profile.courses().get(0).contactHours());
        assertEquals(5, profile.load().totalHours());
        assertNotNull(profile.compliance());
        assertEquals(100.0, profile.compliance().complianceRate());
        assertEquals(1, profile.submissions().size());
        assertEquals("ACCEPTED", profile.submissions().get(0).status());
    }

    @Test
    void testGetDepartmentMetrics_CalculatesRates() {
        when(deadlineService.getAllDeadlines()).thenReturn(List.of(deadline1));
        when(facultyService.getAllFaculty(null, null)).thenReturn(List.of(faculty1));
        when(deadlineService.getAssignmentsForDeadline(10L)).thenReturn(List.of(assignment1));

        SubmissionDto sub = new SubmissionDto(1L, 100L, "SUBMITTED", Instant.now(), null, Instant.now(), null, 1, Collections.emptyList());
        when(submissionService.getSubmissionStatusesForDeadline(10L)).thenReturn(java.util.Map.of(100L, sub.status()));

        DepartmentMetricsDto metrics = trackingService.getDepartmentMetrics();

        assertNotNull(metrics);
        assertEquals(1, metrics.totalAssignments());
        assertEquals(1, metrics.totalSubmissions());
        assertEquals(100.0, metrics.submissionRate());
        assertEquals(100.0, metrics.onTimeRate());
        assertEquals(0, metrics.overdueCount());
        assertEquals(0, metrics.criticalFacultyCount());
    }

    @Test
    void testGetDeadlineTrackingSummary() {
        when(deadlineService.getAllDeadlines()).thenReturn(List.of(deadline1));
        when(deadlineService.getAssignmentsForDeadline(10L)).thenReturn(List.of(assignment1));

        SubmissionDto sub = new SubmissionDto(1L, 100L, "ACCEPTED", Instant.now(), null, Instant.now(), null, 1, Collections.emptyList());
        when(submissionService.getSubmissionStatusesForDeadline(10L)).thenReturn(java.util.Map.of(100L, sub.status()));

        List<DeadlineTrackingRowDto> summary = trackingService.getDeadlineTrackingSummary();

        assertEquals(1, summary.size());
        assertEquals("Course File", summary.get(0).title());
        assertEquals(1, summary.get(0).acceptedCount());
        assertEquals(100.0, summary.get(0).complianceRate());
    }

    @Test
    void testGetFacultyComplianceSummary() {
        when(facultyService.getAllFaculty(null, null)).thenReturn(List.of(faculty1));
        when(deadlineService.getAllDeadlines()).thenReturn(List.of(deadline1));
        when(deadlineService.getAssignmentsForDeadline(10L)).thenReturn(List.of(assignment1));

        SubmissionDto sub = new SubmissionDto(1L, 100L, "PENDING", null, null, Instant.now(), null, 0, Collections.emptyList());
        when(submissionService.getSubmissionStatusesForDeadline(10L)).thenReturn(java.util.Map.of(100L, sub.status()));

        List<FacultyComplianceRowDto> summary = trackingService.getFacultyComplianceSummary();

        assertEquals(1, summary.size());
        assertEquals("Dr. Amit", summary.get(0).facultyName());
        assertEquals(0, summary.get(0).submittedCount());
        assertEquals("CRITICAL", summary.get(0).complianceStatus());
    }

    @Test
    void testGenerateSubmissionsExcelReport() throws IOException {
        when(deadlineService.getAllDeadlines()).thenReturn(List.of(deadline1));
        when(facultyService.getAllFaculty(null, null)).thenReturn(List.of(faculty1));
        when(deadlineService.getAssignmentsForDeadline(10L)).thenReturn(List.of(assignment1));

        SubmissionDto sub = new SubmissionDto(1L, 100L, "SUBMITTED", Instant.now(), null, Instant.now(), null, 1, Collections.emptyList());
        when(submissionService.getSubmissionByAssignmentId(100L)).thenReturn(sub);

        byte[] reportBytes = trackingService.generateSubmissionsExcelReport();

        assertNotNull(reportBytes);
        assertTrue(reportBytes.length > 0);

        // Verify it can be opened by POI XSSFWorkbook
        try (XSSFWorkbook wb = new XSSFWorkbook(new ByteArrayInputStream(reportBytes))) {
            assertEquals("Submissions Audit", wb.getSheetAt(0).getSheetName());
            assertEquals(4, wb.getSheetAt(0).getPhysicalNumberOfRows());
            assertEquals("INSTITUTE OF ENGINEERING & TECHNOLOGY (IET)", wb.getSheetAt(0).getRow(0).getCell(0).getStringCellValue());
        }

    }

    @Test
    void testGetOverviewSummary() {
        com.iet.dashboard.academics.web.CourseDto course = new com.iet.dashboard.academics.web.CourseDto(1L, "CS501", "Networks", "IT", 4);
        com.iet.dashboard.academics.web.CourseOfferingDto offering = new com.iet.dashboard.academics.web.CourseOfferingDto(
                1L, course, "2026-2027", 3, 5, "A", 1L, "Odd 2026-27"
        );
        when(academicsService.getAllOfferings(null, null)).thenReturn(List.of(offering));

        com.iet.dashboard.academics.web.TeachingAssignmentDto assignment = new com.iet.dashboard.academics.web.TeachingAssignmentDto(
                100L, offering, 2L, "Dr. Amit", "THEORY"
        );
        when(academicsService.getAllTeachingAssignments(null)).thenReturn(List.of(assignment));

        com.iet.dashboard.academics.web.CourseProgressDto progress = new com.iet.dashboard.academics.web.CourseProgressDto(
                1L, 1L, "CS501", "Networks", "Sec A", "THEORY", 4, "Odd 2026-27", 11,
                15, 10, 67, 14, -4, "BEHIND", "Behind by 4 topics",
                10L, 10L, 100, Collections.emptyList()
        );
        when(pacingService.calculateProgressForOfferings(any())).thenReturn(java.util.Map.of(1L, progress));
        when(deadlineService.getAllDeadlines()).thenReturn(Collections.emptyList());

        com.iet.dashboard.tracking.web.OverviewSummaryDto summary = trackingService.getOverviewSummary();

        assertNotNull(summary);
        assertEquals(1, summary.worstFirstCoverage().size());
        assertEquals("CS501", summary.worstFirstCoverage().get(0).courseCode());
        assertEquals(67, summary.kpis().averageCoveragePercentage());
        assertEquals(1, summary.kpis().coursesBehindPacing());
        assertEquals(1, summary.needsAttention().size());
        assertEquals("COVERAGE_BEHIND", summary.needsAttention().get(0).type());
    }

    @Test
    void testGetComplianceMatrix() {
        com.iet.dashboard.academics.web.CourseDto course = new com.iet.dashboard.academics.web.CourseDto(1L, "CS501", "Networks", "IT", 4);
        com.iet.dashboard.academics.web.CourseOfferingDto offering = new com.iet.dashboard.academics.web.CourseOfferingDto(
                1L, course, "2026-2027", 3, 5, "A", 1L, "Odd 2026-27"
        );
        when(academicsService.getAllOfferings(null, null)).thenReturn(List.of(offering));
        when(academicsService.getAllTeachingAssignments(null)).thenReturn(Collections.emptyList());

        com.iet.dashboard.academics.web.CourseProgressDto progress = new com.iet.dashboard.academics.web.CourseProgressDto(
                1L, 1L, "CS501", "Networks", "Sec A", "THEORY", 4, "Odd 2026-27", 11,
                15, 12, 80, 14, -2, "BEHIND", "Behind by 2 topics",
                10L, 10L, 100, Collections.emptyList()
        );
        when(pacingService.calculateProgressForOfferings(any())).thenReturn(java.util.Map.of(1L, progress));
        when(deadlineService.getAllDeadlines()).thenReturn(Collections.emptyList());

        com.iet.dashboard.tracking.web.ComplianceMatrixDto matrix = trackingService.getComplianceMatrix();

        assertNotNull(matrix);
        assertEquals(5, matrix.checkpoints().size());
        assertEquals(1, matrix.rows().size());
        assertEquals("CS501", matrix.rows().get(0).courseCode());
        assertEquals("DONE", matrix.rows().get(0).cells().get(0).status()); // 80% coverage >= 60%
    }

    @Test
    void testGetDepartmentMetrics_ZeroAssignmentsReturnsZeroRates() {
        when(deadlineService.getAllDeadlines()).thenReturn(Collections.emptyList());
        when(facultyService.getAllFaculty(null, null)).thenReturn(List.of(faculty1));

        DepartmentMetricsDto metrics = trackingService.getDepartmentMetrics();

        assertNotNull(metrics);
        assertEquals(0, metrics.totalAssignments());
        assertEquals(0, metrics.totalSubmissions());
        assertEquals(0.0, metrics.submissionRate());
        assertEquals(0.0, metrics.onTimeRate());
    }

    @Test
    void testGetIrregularitiesAudit_DetectsMissingFacultyAndLab() {
        CourseDto course1 = new CourseDto(1L, "CS501", "Networks Lab", "PRACTICAL", "CORE", 2, "0-0-2", 0, 0, 2, null);
        CourseOfferingDto offering1 = new CourseOfferingDto(1L, course1, "2026-2027", 3, 5, "A", 1L, "Semester 1");

        when(academicsService.getAllOfferings(null, null)).thenReturn(List.of(offering1));
        when(academicsService.getAllTeachingAssignments(null)).thenReturn(Collections.emptyList());
        when(academicsService.getSyllabusByCourse()).thenReturn(java.util.Map.of());

        com.iet.dashboard.tracking.web.IrregularityAuditDto audit = trackingService.getIrregularitiesAudit();

        assertNotNull(audit);
        assertTrue(audit.totalIssues() >= 1);
        assertEquals(1, audit.criticalCount());
        assertEquals("MISSING_FACULTY", audit.items().get(0).issueType());
    }

    // ---- Overview: faculty-performance cockpit ----

    private static CourseProgressDto progressFor(long offeringId, String code, int total, int covered, int delta, String status) {
        int pct = total > 0 ? Math.round(covered * 100f / total) : 0;
        return new CourseProgressDto(offeringId, offeringId, code, code, "Sec A", "LECTURE", 3, "Odd", 6,
                total, covered, pct, covered - delta, delta, status, status, 0L, 0L, 0, Collections.emptyList());
    }

    @Test
    void overviewRanksBySyllabusDeltaAndSinksOfferingsWithoutASyllabus() {
        CourseDto course = new CourseDto(1L, "CS501", "Networks", "IT", 4);
        CourseOfferingDto noSyllabus = new CourseOfferingDto(1L, course, "2026-2027", 1, 1, "F");
        CourseOfferingDto slightlyBehind = new CourseOfferingDto(2L, course, "2026-2027", 1, 1, "G");
        CourseOfferingDto farBehind = new CourseOfferingDto(3L, course, "2026-2027", 3, 5, "H");
        when(academicsService.getAllOfferings(null, null)).thenReturn(List.of(noSyllabus, slightlyBehind, farBehind));
        when(academicsService.getAllTeachingAssignments(null)).thenReturn(List.of(
                new TeachingAssignmentDto(1L, farBehind, 7L, "Dr. Lecture", "LECTURE"),
                new TeachingAssignmentDto(2L, farBehind, 8L, "Dr. Lab", "PRACTICAL")));
        when(pacingService.calculateProgressForOfferings(any())).thenReturn(java.util.Map.of(
                1L, progressFor(1L, "CS501", 0, 0, 0, "ON_TRACK"),
                2L, progressFor(2L, "CS501", 20, 9, -1, "BEHIND"),
                3L, progressFor(3L, "CS501", 20, 12, -5, "BEHIND")));
        when(deadlineService.getAllDeadlines()).thenReturn(Collections.emptyList());

        var summary = trackingService.getOverviewSummary();

        assertEquals(List.of(3L, 2L, 1L), summary.worstFirstCoverage().stream()
                .map(com.iet.dashboard.tracking.web.OverviewSummaryDto.CourseCoverageRankingItem::offeringId).toList());
        var worst = summary.worstFirstCoverage().get(0);
        assertEquals(5, worst.semester());
        assertEquals("H", worst.section());
        assertEquals("Dr. Lecture, Dr. Lab", worst.facultyName());
        assertFalse(summary.worstFirstCoverage().get(2).syllabusConfigured());
        // 0/0 is "no syllabus", not 0% coverage: average is over the two configured offerings only
        assertEquals(53, summary.kpis().averageCoveragePercentage());
        assertEquals(1, summary.kpis().offeringsWithoutSyllabus());
        assertNull(summary.kpis().averageConductRatePercentage(), "no sessions logged yet");
    }

    @Test
    void overviewFlagsFacultyWhoHaveStoppedLogging() {
        CourseDto course = new CourseDto(1L, "CS501", "Networks", "IT", 4);
        CourseOfferingDto offering = new CourseOfferingDto(1L, course, "2026-2027", 1, 1, "A");
        when(academicsService.getAllOfferings(null, null)).thenReturn(List.of(offering));
        when(academicsService.getAllTeachingAssignments(null)).thenReturn(List.of(
                new TeachingAssignmentDto(1L, offering, 7L, "Dr. Active", "LECTURE"),
                new TeachingAssignmentDto(2L, offering, 8L, "Dr. Silent", "PRACTICAL")));
        when(pacingService.calculateProgressForOfferings(any())).thenReturn(java.util.Map.of(1L, progressFor(1L, "CS501", 10, 4, 0, "ON_TRACK")));
        when(deadlineService.getAllDeadlines()).thenReturn(Collections.emptyList());
        LocalDate today = LocalDate.now();
        when(academicsService.getActiveTerm()).thenReturn(new AcademicTermDto(1L, "Odd", "2026-2027", today.minusWeeks(6), today.plusWeeks(10), true));
        when(academicsService.getFacultyLtpLoad()).thenReturn(List.of(
                new FacultyLtpLoadDto(7L, "Dr. Active", 3, 0, 0, 2, 5, 1),
                new FacultyLtpLoadDto(8L, "Dr. Silent", 0, 0, 2, 0, 2, 1)));

        com.iet.dashboard.academics.domain.CourseOffering entity = org.mockito.Mockito.mock(com.iet.dashboard.academics.domain.CourseOffering.class);
        when(entity.getId()).thenReturn(1L);
        TeachingSession held = new TeachingSession(entity, 7L, today.minusDays(1), "LECTURE", SessionStatus.HELD, null, null);
        TeachingSession cancelled = new TeachingSession(entity, 7L, today.minusDays(2), "LECTURE", SessionStatus.CANCELLED, null, "Exam duty");
        when(sessionRepository.findAll()).thenReturn(List.of(held, cancelled));
        // Both have a daily slot uploaded three weeks ago, so classes were due last week.
        java.time.Instant uploaded = java.time.Instant.now().minus(21, ChronoUnit.DAYS);
        List<com.iet.dashboard.academics.domain.TimetableSlot> slots = new java.util.ArrayList<>();
        for (java.time.DayOfWeek d : java.time.DayOfWeek.values()) {
            for (long f : new long[]{7L, 8L}) {
                var slot = org.mockito.Mockito.mock(com.iet.dashboard.academics.domain.TimetableSlot.class);
                org.mockito.Mockito.lenient().when(slot.getFacultyId()).thenReturn(f);
                org.mockito.Mockito.lenient().when(slot.getDayOfWeek()).thenReturn(d.name());
                org.mockito.Mockito.lenient().when(slot.getCreatedAt()).thenReturn(uploaded);
                slots.add(slot);
            }
        }
        when(timetableSlotRepository.findAll()).thenReturn(slots);

        var summary = trackingService.getOverviewSummary();

        var rows = summary.facultyPerformance();
        assertEquals("Dr. Silent", rows.get(0).facultyName(), "not-logging faculty sort first");
        assertEquals("NOT_LOGGING", rows.get(0).status());
        assertNull(rows.get(0).lastSessionDate());
        var active = rows.get(1);
        assertEquals("ON_TRACK", active.status());
        assertEquals(1, active.sessionsHeld());
        assertEquals(1, active.sessionsCancelled());
        assertEquals(today.minusDays(1), active.lastSessionDate());
        assertEquals(5, active.weeklyHours());
        assertEquals(50, summary.kpis().averageConductRatePercentage());
        assertEquals(1, summary.kpis().facultyNotLogging());
        assertTrue(summary.needsAttention().stream().anyMatch(i -> "NOT_LOGGING".equals(i.type()) && i.facultyId() == 8L));
    }

    @Test
    void lateButSubmittedWorkIsNotReportedAsOverdue() {
        when(academicsService.getAllOfferings(null, null)).thenReturn(Collections.emptyList());
        when(academicsService.getAllTeachingAssignments(null)).thenReturn(Collections.emptyList());
        DeadlineDto past = new DeadlineDto(11L, "Marks", "d", "MARKS_SUBMISSION", Instant.now().minus(2, ChronoUnit.DAYS), 1L, Instant.now(), true, 1);
        DeadlineAssignmentDto da = new DeadlineAssignmentDto(200L, 11L, "Marks", "d", "MARKS_SUBMISSION", past.dueAt(), 1L, "Dr. Amit", null, true);
        when(deadlineService.getAllDeadlines()).thenReturn(List.of(past));
        when(deadlineService.getAssignmentsForDeadline(11L)).thenReturn(List.of(da));
        when(submissionService.getSubmissionStatusesForDeadline(11L)).thenReturn(java.util.Map.of(200L, "LATE"));

        assertTrue(trackingService.getOverviewSummary().needsAttention().isEmpty());
    }

    @Test
    void irregularitiesSortGapsBeforeMissingSyllabusNotes() {
        CourseDto lab = new CourseDto(1L, "CS501", "Networks", "THEORY", "CORE", 4, "3-0-2", 3, 0, 2, null);
        CourseOfferingDto offering = new CourseOfferingDto(1L, lab, "2026-2027", 1, 1, "A");
        when(academicsService.getAllOfferings(null, null)).thenReturn(List.of(offering));
        when(academicsService.getAllTeachingAssignments(null)).thenReturn(List.of(
                new TeachingAssignmentDto(1L, offering, 7L, "Dr. A", "LECTURE")));
        when(academicsService.getSyllabusByCourse()).thenReturn(java.util.Map.of());

        var items = trackingService.getIrregularitiesAudit().items();

        assertEquals(List.of("WARNING", "INFO"), items.stream().map(i -> i.severity()).toList());
    }

    // ---- Fair logging expectations ----

    @Test
    void justUploadedAllocationsWithoutATimetableAreNotFlaggedAsNotLogging() {
        CourseDto course = new CourseDto(1L, "CS501", "Networks", "IT", 4);
        CourseOfferingDto offering = new CourseOfferingDto(1L, course, "2026-2027", 1, 1, "A");
        when(academicsService.getAllOfferings(null, null)).thenReturn(List.of(offering));
        when(academicsService.getAllTeachingAssignments(null)).thenReturn(List.of(new TeachingAssignmentDto(1L, offering, 7L, "Dr. New", "LECTURE")));
        when(pacingService.calculateProgressForOfferings(any())).thenReturn(java.util.Map.of(1L, progressFor(1L, "CS501", 0, 0, 0, "ON_TRACK")));
        when(deadlineService.getAllDeadlines()).thenReturn(Collections.emptyList());
        LocalDate today = LocalDate.now();
        when(academicsService.getActiveTerm()).thenReturn(new AcademicTermDto(1L, "Odd", "2026-2027", today.minusWeeks(6), today.plusWeeks(10), true));

        var summary = trackingService.getOverviewSummary();

        assertEquals("NO_TIMETABLE", summary.facultyPerformance().get(0).status());
        assertEquals(0, summary.kpis().facultyNotLogging());
        assertEquals(1, summary.kpis().facultyWithoutTimetable());
        assertTrue(summary.needsAttention().stream().noneMatch(i -> "NOT_LOGGING".equals(i.type())));
        assertEquals(7, summary.term().currentWeek());
    }

    @Test
    void beforeTheSemesterStartsNobodyIsJudged() {
        CourseDto course = new CourseDto(1L, "CS501", "Networks", "IT", 4);
        CourseOfferingDto offering = new CourseOfferingDto(1L, course, "2026-2027", 1, 1, "A");
        when(academicsService.getAllOfferings(null, null)).thenReturn(List.of(offering));
        when(academicsService.getAllTeachingAssignments(null)).thenReturn(List.of(new TeachingAssignmentDto(1L, offering, 7L, "Dr. New", "LECTURE")));
        when(pacingService.calculateProgressForOfferings(any())).thenReturn(java.util.Map.of(1L, progressFor(1L, "CS501", 0, 0, 0, "ON_TRACK")));
        when(deadlineService.getAllDeadlines()).thenReturn(Collections.emptyList());
        LocalDate today = LocalDate.now();
        when(academicsService.getActiveTerm()).thenReturn(new AcademicTermDto(1L, "Even", "2026-2027", today.plusDays(10), today.plusWeeks(16), true));

        var summary = trackingService.getOverviewSummary();

        assertEquals("NOT_STARTED", summary.facultyPerformance().get(0).status());
        assertFalse(summary.term().started());
        assertNull(summary.term().currentWeek());
    }

    @Test
    void expectedClassesCountOnlyFromTheDayTheTimetableWasUploaded() {
        LocalDate today = LocalDate.of(2026, 10, 8); // Thursday
        var mondaySlot = org.mockito.Mockito.mock(com.iet.dashboard.academics.domain.TimetableSlot.class);
        when(mondaySlot.getDayOfWeek()).thenReturn("MONDAY");
        // Uploaded on Tuesday 6 Oct: Monday 5 Oct is before the upload and is not owed.
        when(mondaySlot.getCreatedAt()).thenReturn(LocalDate.of(2026, 10, 6).atStartOfDay(java.time.ZoneId.systemDefault()).toInstant());

        assertEquals(0, TrackingServiceImpl.scheduledBetween(List.of(mondaySlot), LocalDate.of(2026, 7, 1), null, null, today));
        assertEquals(1, TrackingServiceImpl.scheduledBetween(List.of(mondaySlot), LocalDate.of(2026, 7, 1), null, null, today.plusDays(5)));
    }

    @Test
    void milestonesMatchByDeadlineTypeAndSectionNotByTitleWords() {
        CourseDto course = new CourseDto(1L, "CS501", "Networks", "IT", 4);
        CourseOfferingDto secA = new CourseOfferingDto(1L, course, "2026-2027", 1, 1, "A");
        CourseOfferingDto secB = new CourseOfferingDto(2L, course, "2026-2027", 1, 1, "B");
        when(academicsService.getAllOfferings(null, null)).thenReturn(List.of(secA, secB));
        when(academicsService.getAllTeachingAssignments(null)).thenReturn(List.of(
                new TeachingAssignmentDto(1L, secA, 7L, "Dr. A", "LECTURE"),
                new TeachingAssignmentDto(2L, secB, 7L, "Dr. A", "LECTURE")));
        when(pacingService.calculateProgressForOfferings(any())).thenReturn(java.util.Map.of(
                1L, progressFor(1L, "CS501", 0, 0, 0, "ON_TRACK"), 2L, progressFor(2L, "CS501", 0, 0, 0, "ON_TRACK")));
        Instant past = Instant.now().minus(2, ChronoUnit.DAYS);
        // Title mentions "course file" but the type is the question bank: only the type counts.
        DeadlineDto qb = new DeadlineDto(20L, "Upload course file notes", "", "MID_SEM_QUESTION_BANK", past, 1L, Instant.now(), true, 2);
        when(deadlineService.getAllDeadlines()).thenReturn(List.of(qb));
        when(deadlineService.getAssignmentsForDeadline(20L)).thenReturn(List.of(
                new DeadlineAssignmentDto(301L, 20L, "", "", "MID_SEM_QUESTION_BANK", past, 7L, "Dr. A", 1L, true),
                new DeadlineAssignmentDto(302L, 20L, "", "", "MID_SEM_QUESTION_BANK", past, 7L, "Dr. A", 2L, true)));
        when(submissionService.getSubmissionStatusesForDeadline(20L)).thenReturn(java.util.Map.of(301L, "SUBMITTED", 302L, "PENDING"));

        var rows = trackingService.getComplianceMatrix().rows();

        // cells: [syllabus, question bank, marks, course file]
        assertEquals("DONE", rows.get(0).cells().get(1).status());
        assertEquals("OVERDUE", rows.get(1).cells().get(1).status());
        assertEquals("No deadline set", rows.get(0).cells().get(3).note());
    }
}
