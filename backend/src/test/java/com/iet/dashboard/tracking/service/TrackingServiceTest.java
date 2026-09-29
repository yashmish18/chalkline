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
import com.iet.dashboard.tracking.repo.AuditLogRepository;
import com.iet.dashboard.tracking.web.DeadlineTrackingRowDto;
import com.iet.dashboard.tracking.web.DepartmentMetricsDto;
import com.iet.dashboard.tracking.web.FacultyComplianceRowDto;
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

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class TrackingServiceTest {

    @Mock
    private AuditLogRepository auditLogRepository;

    @Mock
    private DeadlineService deadlineService;

    @Mock
    private FacultyService facultyService;

    @Mock
    private SubmissionService submissionService;

    @Mock
    private UserService userService;

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
    void testGetDepartmentMetrics_CalculatesRates() {
        when(deadlineService.getAllDeadlines()).thenReturn(List.of(deadline1));
        when(facultyService.getAllFaculty(null)).thenReturn(List.of(faculty1));
        when(deadlineService.getAssignmentsForDeadline(10L)).thenReturn(List.of(assignment1));

        SubmissionDto sub = new SubmissionDto(1L, 100L, "SUBMITTED", Instant.now(), null, Instant.now(), null, 1, Collections.emptyList());
        when(submissionService.getSubmissionByAssignmentId(100L)).thenReturn(sub);

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
        when(submissionService.getSubmissionByAssignmentId(100L)).thenReturn(sub);

        List<DeadlineTrackingRowDto> summary = trackingService.getDeadlineTrackingSummary();

        assertEquals(1, summary.size());
        assertEquals("Course File", summary.get(0).title());
        assertEquals(1, summary.get(0).acceptedCount());
        assertEquals(100.0, summary.get(0).complianceRate());
    }

    @Test
    void testGetFacultyComplianceSummary() {
        when(facultyService.getAllFaculty(null)).thenReturn(List.of(faculty1));
        when(deadlineService.getAllDeadlines()).thenReturn(List.of(deadline1));
        when(deadlineService.getAssignmentsForDeadline(10L)).thenReturn(List.of(assignment1));

        SubmissionDto sub = new SubmissionDto(1L, 100L, "PENDING", null, null, Instant.now(), null, 0, Collections.emptyList());
        when(submissionService.getSubmissionByAssignmentId(100L)).thenReturn(sub);

        List<FacultyComplianceRowDto> summary = trackingService.getFacultyComplianceSummary();

        assertEquals(1, summary.size());
        assertEquals("Dr. Amit", summary.get(0).facultyName());
        assertEquals(0, summary.get(0).submittedCount());
        assertEquals("CRITICAL", summary.get(0).complianceStatus());
    }

    @Test
    void testGenerateSubmissionsExcelReport() throws IOException {
        when(deadlineService.getAllDeadlines()).thenReturn(List.of(deadline1));
        when(facultyService.getAllFaculty(null)).thenReturn(List.of(faculty1));
        when(deadlineService.getAssignmentsForDeadline(10L)).thenReturn(List.of(assignment1));

        SubmissionDto sub = new SubmissionDto(1L, 100L, "SUBMITTED", Instant.now(), null, Instant.now(), null, 1, Collections.emptyList());
        when(submissionService.getSubmissionByAssignmentId(100L)).thenReturn(sub);
        when(userService.getCurrentUser()).thenReturn(new UserDto(1L, "coord@iet.edu", "Dr. Coord", "COORDINATOR"));

        byte[] reportBytes = trackingService.generateSubmissionsExcelReport();

        assertNotNull(reportBytes);
        assertTrue(reportBytes.length > 0);

        // Verify it can be opened by POI XSSFWorkbook
        try (XSSFWorkbook wb = new XSSFWorkbook(new ByteArrayInputStream(reportBytes))) {
            assertEquals("Submissions Audit", wb.getSheetAt(0).getSheetName());
            assertEquals(4, wb.getSheetAt(0).getPhysicalNumberOfRows());
            assertEquals("INSTITUTE OF ENGINEERING & TECHNOLOGY (IET)", wb.getSheetAt(0).getRow(0).getCell(0).getStringCellValue());
        }

        verify(auditLogRepository).save(any());
    }
}
