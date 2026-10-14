package com.iet.dashboard.integration;

import com.iet.dashboard.deadlines.domain.Deadline;
import com.iet.dashboard.deadlines.domain.DeadlineAssignment;
import com.iet.dashboard.deadlines.domain.DeadlineType;
import com.iet.dashboard.deadlines.repo.DeadlineAssignmentRepository;
import com.iet.dashboard.deadlines.repo.DeadlineRepository;
import com.iet.dashboard.deadlines.service.DeadlineServiceImpl;
import com.iet.dashboard.deadlines.web.CreateDeadlineRequest;
import com.iet.dashboard.deadlines.web.DeadlineAssignmentDto;
import com.iet.dashboard.deadlines.web.DeadlineDto;
import com.iet.dashboard.directory.service.FacultyService;
import com.iet.dashboard.directory.web.FacultyDto;
import com.iet.dashboard.identity.service.UserService;
import com.iet.dashboard.identity.web.UserDto;
import com.iet.dashboard.notifications.domain.Notification;
import com.iet.dashboard.notifications.domain.NotificationType;
import com.iet.dashboard.notifications.repo.NotificationRepository;
import com.iet.dashboard.notifications.service.NotificationServiceImpl;
import com.iet.dashboard.notifications.web.NotificationDto;
import com.iet.dashboard.submissions.domain.Submission;
import com.iet.dashboard.submissions.domain.SubmissionStatus;
import com.iet.dashboard.submissions.repo.SubmissionFileRepository;
import com.iet.dashboard.submissions.repo.SubmissionRepository;
import com.iet.dashboard.submissions.service.SubmissionServiceImpl;
import com.iet.dashboard.submissions.storage.FileStorage;
import com.iet.dashboard.submissions.web.ReviewRequest;
import com.iet.dashboard.submissions.web.SubmissionDto;
import com.iet.dashboard.tracking.service.TrackingServiceImpl;
import com.iet.dashboard.tracking.web.DepartmentMetricsDto;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.mock.web.MockMultipartFile;

import java.io.ByteArrayInputStream;
import java.io.InputStream;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.Collections;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class FullWorkflowIntegrationTest {

    @Mock
    private DeadlineRepository deadlineRepository;
    @Mock
    private DeadlineAssignmentRepository assignmentRepository;
    @Mock
    private SubmissionRepository submissionRepository;
    @Mock
    private SubmissionFileRepository submissionFileRepository;
    @Mock
    private NotificationRepository notificationRepository;
    @Mock
    private FacultyService facultyService;
    @Mock
    private UserService userService;
    @Mock
    private FileStorage fileStorage;
    @Mock
    private ApplicationEventPublisher eventPublisher;

    private DeadlineServiceImpl deadlineService;
    private SubmissionServiceImpl submissionService;
    private TrackingServiceImpl trackingService;

    private UserDto coordinatorUser;
    private UserDto facultyUser;
    private FacultyDto facultyDto;

    @BeforeEach
    void setUp() {
        coordinatorUser = new UserDto(1L, "coordinator@iet.edu", "Dr. Sharma", "COORDINATOR");
        facultyUser = new UserDto(2L, "amit.verma@iet.edu", "Dr. Amit Verma", "FACULTY");
        facultyDto = new FacultyDto(10L, 2L, "Dr. Amit Verma", "Associate Professor", "Ph.D.", "IT", Collections.emptyList());

        deadlineService = new DeadlineServiceImpl(
                deadlineRepository,
                assignmentRepository,
                facultyService,
                null, // AcademicsService
                userService,
                eventPublisher
        );

        submissionService = new SubmissionServiceImpl(
                submissionRepository,
                submissionFileRepository,
                fileStorage,
                deadlineService,
                userService,
                facultyService,
                eventPublisher
        );

        trackingService = new TrackingServiceImpl(
                deadlineService,
                facultyService,
                submissionService,
                userService,
                mock(com.iet.dashboard.academics.service.AcademicsService.class),
                mock(com.iet.dashboard.academics.service.PacingCalculationService.class),
                mock(com.iet.dashboard.teaching.repo.TeachingSessionRepository.class),
                mock(com.iet.dashboard.academics.repo.TimetableSlotRepository.class)
        );
    }

    @Test
    void testCompleteLifecycle_DeadlineCreation_Submission_Review_Resubmission_Approval_Reporting() throws Exception {
        // Step 1: Coordinator creates deadline
        when(userService.getCurrentUser()).thenReturn(coordinatorUser);
        Instant dueAt = Instant.now().plus(7, ChronoUnit.DAYS);

        Deadline deadline = new Deadline("Course File Audit", "Upload syllabus and lecture notes", DeadlineType.COURSE_FILE, dueAt, 1L);
        deadline.setId(100L);
        when(deadlineRepository.save(any(Deadline.class))).thenReturn(deadline);
        when(facultyService.getAllFaculty(null, null)).thenReturn(List.of(facultyDto));

        CreateDeadlineRequest request = new CreateDeadlineRequest(
                "Course File Audit", "Upload syllabus and lecture notes", "COURSE_FILE",
                dueAt, "ALL_FACULTY", null, null, null
        );

        DeadlineDto createdDeadline = deadlineService.createDeadline(request);
        assertNotNull(createdDeadline);
        assertEquals(100L, createdDeadline.id());
        verify(eventPublisher).publishEvent(any(com.iet.dashboard.shared.events.DeadlineAssignedEvent.class)); // Notification event published

        // Step 2: Faculty submits file (Version 1)
        DeadlineAssignment assignment = new DeadlineAssignment(deadline, 10L, null);
        assignment.setId(500L);
        when(assignmentRepository.findById(500L)).thenReturn(Optional.of(assignment));
        when(facultyService.getFacultyById(10L)).thenReturn(facultyDto);

        when(userService.getCurrentUser()).thenReturn(facultyUser);
        when(facultyService.getFacultyByUserId(2L)).thenReturn(Optional.of(facultyDto));
        when(fileStorage.store(any(InputStream.class), eq("lecture_notes.pdf"))).thenReturn("stored_lecture_notes_v1.pdf");

        Submission submission = new Submission(500L);
        submission.setId(800L);
        when(submissionRepository.findByDeadlineAssignmentId(500L)).thenReturn(Optional.of(submission));
        when(submissionRepository.save(any(Submission.class))).thenAnswer(inv -> inv.getArgument(0));

        byte[] pdfBytes = "%PDF-1.4\nSample lecture notes".getBytes();
        MockMultipartFile fileV1 = new MockMultipartFile("file", "lecture_notes.pdf", "application/pdf", pdfBytes);

        SubmissionDto subV1 = submissionService.submitFile(500L, fileV1);
        assertEquals(SubmissionStatus.SUBMITTED.name(), subV1.status());
        assertEquals(1, subV1.totalVersions());
        assertEquals("lecture_notes.pdf", subV1.latestFile().originalName());

        // Step 3: Coordinator reviews and returns for revision
        when(userService.getCurrentUser()).thenReturn(coordinatorUser);
        when(submissionRepository.findByIdWithFiles(800L)).thenReturn(Optional.of(submission));

        ReviewRequest returnRequest = new ReviewRequest("RETURN", "Please attach the mapping with Bloom's taxonomy");
        SubmissionDto reviewedReturn = submissionService.reviewSubmission(800L, returnRequest);
        assertEquals(SubmissionStatus.RETURNED.name(), reviewedReturn.status());
        assertEquals("Please attach the mapping with Bloom's taxonomy", reviewedReturn.reviewerComment());

        // Step 4: Faculty resubmits revised file (Version 2)
        when(userService.getCurrentUser()).thenReturn(facultyUser);
        when(fileStorage.store(any(InputStream.class), eq("lecture_notes_revised.pdf"))).thenReturn("stored_lecture_notes_v2.pdf");

        byte[] pdfV2Bytes = "%PDF-1.4\nRevised lecture notes with Bloom taxonomy".getBytes();
        MockMultipartFile fileV2 = new MockMultipartFile("file", "lecture_notes_revised.pdf", "application/pdf", pdfV2Bytes);

        SubmissionDto subV2 = submissionService.submitFile(500L, fileV2);
        assertEquals(SubmissionStatus.SUBMITTED.name(), subV2.status());
        assertEquals(2, subV2.totalVersions());
        assertEquals(2, subV2.latestFile().version());
        assertNull(subV2.reviewerComment()); // Return comment cleared on resubmit

        // Step 5: Coordinator accepts submission
        when(userService.getCurrentUser()).thenReturn(coordinatorUser);
        ReviewRequest acceptRequest = new ReviewRequest("ACCEPT", "Excellent work, comprehensive Bloom mapping verified.");
        SubmissionDto reviewedAccept = submissionService.reviewSubmission(800L, acceptRequest);
        assertEquals(SubmissionStatus.ACCEPTED.name(), reviewedAccept.status());

        // Step 6: Coordinator views Department Metrics
        when(deadlineRepository.findAllOrderByDueAt()).thenReturn(List.of(deadline));
        when(assignmentRepository.findByDeadlineId(100L)).thenReturn(List.of(assignment));

        DepartmentMetricsDto metrics = trackingService.getDepartmentMetrics();
        assertEquals(1, metrics.totalAssignments());
        assertEquals(1, metrics.totalSubmissions());
        assertEquals(100.0, metrics.submissionRate());
        assertEquals(100.0, metrics.onTimeRate());
        assertEquals(0, metrics.criticalFacultyCount());

        // Step 7: Coordinator exports submissions Excel report
        byte[] excelBytes = trackingService.generateSubmissionsExcelReport();
        assertNotNull(excelBytes);
        assertTrue(excelBytes.length > 0);

        try (XSSFWorkbook wb = new XSSFWorkbook(new ByteArrayInputStream(excelBytes))) {
            assertEquals("Submissions Audit", wb.getSheetAt(0).getSheetName());
            assertEquals("INSTITUTE OF ENGINEERING & TECHNOLOGY (IET)", wb.getSheetAt(0).getRow(0).getCell(0).getStringCellValue());
        }

    }
}
