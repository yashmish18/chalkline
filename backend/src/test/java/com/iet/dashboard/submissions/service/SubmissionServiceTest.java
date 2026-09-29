package com.iet.dashboard.submissions.service;

import com.iet.dashboard.deadlines.service.DeadlineService;
import com.iet.dashboard.deadlines.web.DeadlineAssignmentDto;
import com.iet.dashboard.directory.service.FacultyService;
import com.iet.dashboard.directory.web.FacultyDto;
import com.iet.dashboard.identity.service.UserService;
import com.iet.dashboard.identity.web.UserDto;
import com.iet.dashboard.shared.error.ForbiddenException;
import com.iet.dashboard.shared.error.ValidationException;
import com.iet.dashboard.submissions.domain.Submission;
import com.iet.dashboard.submissions.domain.SubmissionFile;
import com.iet.dashboard.submissions.domain.SubmissionStatus;
import com.iet.dashboard.submissions.repo.SubmissionFileRepository;
import com.iet.dashboard.submissions.repo.SubmissionRepository;
import com.iet.dashboard.submissions.storage.FileStorage;
import com.iet.dashboard.submissions.web.ReviewRequest;
import com.iet.dashboard.submissions.web.SubmissionDto;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.mock.web.MockMultipartFile;

import java.io.IOException;
import java.io.InputStream;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.Collections;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class SubmissionServiceTest {

    @Mock
    private SubmissionRepository submissionRepository;

    @Mock
    private SubmissionFileRepository submissionFileRepository;

    @Mock
    private FileStorage fileStorage;

    @Mock
    private DeadlineService deadlineService;

    @Mock
    private UserService userService;

    @Mock
    private FacultyService facultyService;

    @Mock
    private org.springframework.context.ApplicationEventPublisher eventPublisher;

    @InjectMocks
    private SubmissionServiceImpl submissionService;

    private UserDto facultyUser;
    private UserDto coordinatorUser;
    private FacultyDto facultyDto;
    private DeadlineAssignmentDto futureAssignment;

    @BeforeEach
    void setUp() {
        facultyUser = new UserDto(2L, "amit@iet.edu", "Dr. Amit", "FACULTY");
        coordinatorUser = new UserDto(1L, "coord@iet.edu", "Dr. Coord", "COORDINATOR");
        facultyDto = new FacultyDto(10L, 2L, "Dr. Amit", "Assoc Prof", "PhD", "IT", Collections.emptyList());

        futureAssignment = new DeadlineAssignmentDto(
                50L, 100L, "Course Plan", "Upload course plan", "COURSE_FILE",
                Instant.now().plus(5, ChronoUnit.DAYS), 10L, "Dr. Amit", null, false
        );
    }

    @Test
    void testSubmitFile_Success_OnTime() throws IOException {
        when(userService.getCurrentUser()).thenReturn(facultyUser);
        when(deadlineService.getAssignmentById(50L)).thenReturn(futureAssignment);
        when(facultyService.getFacultyByUserId(2L)).thenReturn(Optional.of(facultyDto));
        when(fileStorage.store(any(InputStream.class), eq("syllabus.pdf"))).thenReturn("stored_uuid_syllabus.pdf");

        // Simple PDF bytes (%PDF-1.4 header)
        byte[] pdfBytes = "%PDF-1.4\n%test pdf content".getBytes();
        MockMultipartFile file = new MockMultipartFile("file", "syllabus.pdf", "application/pdf", pdfBytes);

        Submission submission = new Submission(50L);
        submission.setId(1L);
        when(submissionRepository.findByDeadlineAssignmentId(50L)).thenReturn(Optional.of(submission));
        when(submissionRepository.save(any(Submission.class))).thenAnswer(inv -> inv.getArgument(0));

        SubmissionDto result = submissionService.submitFile(50L, file);

        assertNotNull(result);
        assertEquals(SubmissionStatus.SUBMITTED.name(), result.status());
        assertNotNull(result.submittedAt());
        assertEquals(1, result.totalVersions());
        assertNotNull(result.latestFile());
        assertEquals("stored_uuid_syllabus.pdf", submission.getFiles().get(0).getStorageKey());
        assertFalse(result.latestFile().sha256().isEmpty());
    }

    @Test
    void testSubmitFile_Success_Late() throws IOException {
        DeadlineAssignmentDto pastAssignment = new DeadlineAssignmentDto(
                50L, 100L, "Course Plan", "Upload course plan", "COURSE_FILE",
                Instant.now().minus(2, ChronoUnit.DAYS), 10L, "Dr. Amit", null, true
        );

        when(userService.getCurrentUser()).thenReturn(facultyUser);
        when(deadlineService.getAssignmentById(50L)).thenReturn(pastAssignment);
        when(facultyService.getFacultyByUserId(2L)).thenReturn(Optional.of(facultyDto));
        when(fileStorage.store(any(InputStream.class), eq("late_plan.pdf"))).thenReturn("stored_late_plan.pdf");

        byte[] pdfBytes = "%PDF-1.4\n%late pdf".getBytes();
        MockMultipartFile file = new MockMultipartFile("file", "late_plan.pdf", "application/pdf", pdfBytes);

        Submission submission = new Submission(50L);
        when(submissionRepository.findByDeadlineAssignmentId(50L)).thenReturn(Optional.of(submission));
        when(submissionRepository.save(any(Submission.class))).thenAnswer(inv -> inv.getArgument(0));

        SubmissionDto result = submissionService.submitFile(50L, file);

        assertEquals(SubmissionStatus.LATE.name(), result.status());
    }

    @Test
    void testSubmitFile_VersionIncrement() throws IOException {
        when(userService.getCurrentUser()).thenReturn(facultyUser);
        when(deadlineService.getAssignmentById(50L)).thenReturn(futureAssignment);
        when(facultyService.getFacultyByUserId(2L)).thenReturn(Optional.of(facultyDto));
        when(fileStorage.store(any(InputStream.class), anyString())).thenReturn("stored_v2.pdf");

        Submission submission = new Submission(50L);
        SubmissionFile v1 = new SubmissionFile(submission, 1, "k1", "f1.pdf", "application/pdf", 100, "hash1");
        submission.addFile(v1);

        when(submissionRepository.findByDeadlineAssignmentId(50L)).thenReturn(Optional.of(submission));
        when(submissionRepository.save(any(Submission.class))).thenAnswer(inv -> inv.getArgument(0));

        byte[] pdfBytes = "%PDF-1.4\n%version 2 content".getBytes();
        MockMultipartFile file = new MockMultipartFile("file", "v2.pdf", "application/pdf", pdfBytes);

        SubmissionDto result = submissionService.submitFile(50L, file);

        assertEquals(2, result.totalVersions());
        assertEquals(2, result.latestFile().version());
    }

    @Test
    void testSubmitFile_IDOR_ThrowsForbiddenException() {
        // Current user is faculty #99, but assignment belongs to faculty #10
        FacultyDto otherFaculty = new FacultyDto(99L, 2L, "Dr. Other", "Lecturer", "PhD", "IT", Collections.emptyList());
        when(userService.getCurrentUser()).thenReturn(facultyUser);
        when(deadlineService.getAssignmentById(50L)).thenReturn(futureAssignment);
        when(facultyService.getFacultyByUserId(2L)).thenReturn(Optional.of(otherFaculty));

        byte[] pdfBytes = "%PDF-1.4\n%content".getBytes();
        MockMultipartFile file = new MockMultipartFile("file", "test.pdf", "application/pdf", pdfBytes);

        assertThrows(ForbiddenException.class, () -> submissionService.submitFile(50L, file));
    }

    @Test
    void testSubmitFile_EmptyFile_ThrowsValidationException() {
        MockMultipartFile emptyFile = new MockMultipartFile("file", "empty.pdf", "application/pdf", new byte[0]);

        assertThrows(ValidationException.class, () -> submissionService.submitFile(50L, emptyFile));
    }

    @Test
    void testReviewSubmission_Accept_Success() {
        when(userService.getCurrentUser()).thenReturn(coordinatorUser);

        Submission submission = new Submission(50L);
        submission.setId(7L);
        submission.setStatus(SubmissionStatus.SUBMITTED);
        when(submissionRepository.findByIdWithFiles(7L)).thenReturn(Optional.of(submission));
        when(submissionRepository.save(any(Submission.class))).thenAnswer(inv -> inv.getArgument(0));

        ReviewRequest request = new ReviewRequest("ACCEPT", "Approved without comments");
        SubmissionDto result = submissionService.reviewSubmission(7L, request);

        assertEquals(SubmissionStatus.ACCEPTED.name(), result.status());
        assertEquals("Approved without comments", result.reviewerComment());
    }

    @Test
    void testReviewSubmission_Return_Success() {
        when(userService.getCurrentUser()).thenReturn(coordinatorUser);

        Submission submission = new Submission(50L);
        submission.setId(7L);
        submission.setStatus(SubmissionStatus.SUBMITTED);
        when(submissionRepository.findByIdWithFiles(7L)).thenReturn(Optional.of(submission));
        when(submissionRepository.save(any(Submission.class))).thenAnswer(inv -> inv.getArgument(0));

        ReviewRequest request = new ReviewRequest("RETURN", "Please include rubric assessment table");
        SubmissionDto result = submissionService.reviewSubmission(7L, request);

        assertEquals(SubmissionStatus.RETURNED.name(), result.status());
        assertEquals("Please include rubric assessment table", result.reviewerComment());
    }

    @Test
    void testReviewSubmission_ReturnWithoutComment_ThrowsValidationException() {
        when(userService.getCurrentUser()).thenReturn(coordinatorUser);

        Submission submission = new Submission(50L);
        submission.setId(7L);
        when(submissionRepository.findByIdWithFiles(7L)).thenReturn(Optional.of(submission));

        ReviewRequest request = new ReviewRequest("RETURN", "   ");
        assertThrows(ValidationException.class, () -> submissionService.reviewSubmission(7L, request));
    }

    @Test
    void testReviewSubmission_NonCoordinator_ThrowsForbiddenException() {
        when(userService.getCurrentUser()).thenReturn(facultyUser);

        ReviewRequest request = new ReviewRequest("ACCEPT", "Looks good");
        assertThrows(ForbiddenException.class, () -> submissionService.reviewSubmission(7L, request));
    }

    @Test
    void testGetSubmissionByAssignmentId_WhenPending() {
        when(userService.getCurrentUser()).thenReturn(facultyUser);
        when(deadlineService.getAssignmentById(50L)).thenReturn(futureAssignment);
        when(facultyService.getFacultyByUserId(2L)).thenReturn(Optional.of(facultyDto));
        when(submissionRepository.findByDeadlineAssignmentId(50L)).thenReturn(Optional.empty());

        SubmissionDto dto = submissionService.getSubmissionByAssignmentId(50L);

        assertNotNull(dto);
        assertEquals(50L, dto.deadlineAssignmentId());
        assertEquals("PENDING", dto.status());
        assertEquals(0, dto.totalVersions());
        assertNull(dto.id());
    }

    @Test
    void testGetSubmissionByAssignmentId_WhenExists() {
        when(userService.getCurrentUser()).thenReturn(facultyUser);
        when(deadlineService.getAssignmentById(50L)).thenReturn(futureAssignment);
        when(facultyService.getFacultyByUserId(2L)).thenReturn(Optional.of(facultyDto));

        Submission submission = new Submission(50L);
        submission.setId(99L);
        submission.setStatus(SubmissionStatus.SUBMITTED);
        when(submissionRepository.findByDeadlineAssignmentId(50L)).thenReturn(Optional.of(submission));

        SubmissionDto dto = submissionService.getSubmissionByAssignmentId(50L);

        assertNotNull(dto);
        assertEquals(99L, dto.id());
        assertEquals("SUBMITTED", dto.status());
    }
}
