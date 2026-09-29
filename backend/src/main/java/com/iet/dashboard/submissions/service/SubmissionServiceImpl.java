package com.iet.dashboard.submissions.service;

import com.iet.dashboard.deadlines.service.DeadlineService;
import com.iet.dashboard.deadlines.web.DeadlineAssignmentDto;
import com.iet.dashboard.directory.service.FacultyService;
import com.iet.dashboard.directory.web.FacultyDto;
import com.iet.dashboard.identity.service.UserService;
import com.iet.dashboard.identity.web.UserDto;
import com.iet.dashboard.shared.error.ForbiddenException;
import com.iet.dashboard.shared.error.ResourceNotFoundException;
import com.iet.dashboard.shared.error.ValidationException;
import com.iet.dashboard.submissions.domain.Submission;
import com.iet.dashboard.submissions.domain.SubmissionFile;
import com.iet.dashboard.submissions.domain.SubmissionStatus;
import com.iet.dashboard.submissions.repo.SubmissionFileRepository;
import com.iet.dashboard.submissions.repo.SubmissionRepository;
import com.iet.dashboard.submissions.storage.FileStorage;
import com.iet.dashboard.submissions.web.ReviewRequest;
import com.iet.dashboard.submissions.web.SubmissionDto;
import com.iet.dashboard.submissions.web.SubmissionFileDto;
import org.apache.tika.Tika;
import org.springframework.core.io.Resource;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Instant;
import java.util.*;
import java.util.stream.Collectors;

@Service
@Transactional
public class SubmissionServiceImpl implements SubmissionService {

    private static final long MAX_FILE_SIZE = 25 * 1024 * 1024; // 25MB
    private static final Set<String> ALLOWED_MIME_TYPES = Set.of(
            "application/pdf",
            "application/vnd.openxmlformats-officedocument.wordprocessingml.document",
            "application/msword",
            "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet",
            "application/vnd.ms-excel",
            "image/png",
            "image/jpeg",
            "application/zip",
            "text/plain",
            "application/x-tika-ooxml",
            "application/x-zip-compressed"
    );

    private final SubmissionRepository submissionRepository;
    private final SubmissionFileRepository submissionFileRepository;
    private final FileStorage fileStorage;
    private final DeadlineService deadlineService;
    private final UserService userService;
    private final FacultyService facultyService;
    private final org.springframework.context.ApplicationEventPublisher eventPublisher;
    private final Tika tika = new Tika();

    public SubmissionServiceImpl(
            SubmissionRepository submissionRepository,
            SubmissionFileRepository submissionFileRepository,
            FileStorage fileStorage,
            DeadlineService deadlineService,
            UserService userService,
            FacultyService facultyService,
            org.springframework.context.ApplicationEventPublisher eventPublisher
    ) {
        this.submissionRepository = submissionRepository;
        this.submissionFileRepository = submissionFileRepository;
        this.fileStorage = fileStorage;
        this.deadlineService = deadlineService;
        this.userService = userService;
        this.facultyService = facultyService;
        this.eventPublisher = eventPublisher;
    }

    @Override
    @Transactional(readOnly = true)
    public SubmissionDto getSubmissionByAssignmentId(Long assignmentId) {
        DeadlineAssignmentDto assignment = deadlineService.getAssignmentById(assignmentId);
        assertUserCanAccessAssignment(assignment);

        Submission submission = submissionRepository.findByDeadlineAssignmentId(assignmentId).orElse(null);
        if (submission == null) {
            return SubmissionDto.pending(assignmentId);
        }

        return SubmissionDto.fromEntity(submission);
    }

    @Override
    public SubmissionDto submitFile(Long assignmentId, MultipartFile file) {
        if (file == null || file.isEmpty()) {
            throw new ValidationException("Uploaded file cannot be empty");
        }
        if (file.getSize() > MAX_FILE_SIZE) {
            throw new ValidationException("File size exceeds 25MB limit");
        }

        DeadlineAssignmentDto assignment = deadlineService.getAssignmentById(assignmentId);
        assertUserCanSubmitForAssignment(assignment);

        byte[] fileBytes;
        try {
            fileBytes = file.getBytes();
        } catch (IOException e) {
            throw new ValidationException("Failed to read uploaded file: " + e.getMessage());
        }

        // Real MIME sniffing via Apache Tika
        String detectedMime;
        try {
            detectedMime = tika.detect(new ByteArrayInputStream(fileBytes), file.getOriginalFilename());
        } catch (IOException e) {
            throw new ValidationException("Failed to analyze file MIME type: " + e.getMessage());
        }
        if (!ALLOWED_MIME_TYPES.contains(detectedMime.toLowerCase())) {
            throw new ValidationException("Disallowed file type: " + detectedMime + ". Allowed types: PDF, Word, Excel, PNG, JPG, ZIP, TXT");
        }

        // SHA-256 calculation
        String sha256 = calculateSha256(fileBytes);

        // Store to versioned file storage
        String storageKey;
        try {
            storageKey = fileStorage.store(new ByteArrayInputStream(fileBytes), file.getOriginalFilename());
        } catch (IOException e) {
            throw new RuntimeException("Storage error while saving submission: " + e.getMessage(), e);
        }

        Submission submission = submissionRepository.findByDeadlineAssignmentId(assignmentId)
                .orElseGet(() -> new Submission(assignmentId));

        int nextVersion = submission.getFiles().isEmpty()
                ? 1
                : submission.getFiles().stream().mapToInt(SubmissionFile::getVersion).max().orElse(0) + 1;

        SubmissionFile submissionFile = new SubmissionFile(
                submission,
                nextVersion,
                storageKey,
                file.getOriginalFilename(),
                detectedMime,
                fileBytes.length,
                sha256
        );

        submission.addFile(submissionFile);
        submission.setSubmittedAt(Instant.now());

        // Check if submission is on time or late
        boolean isLate = Instant.now().isAfter(assignment.dueAt());
        submission.setStatus(isLate ? SubmissionStatus.LATE : SubmissionStatus.SUBMITTED);
        submission.setReviewerComment(null); // Clear previous return comment on new submission

        Submission saved = submissionRepository.save(submission);
        return SubmissionDto.fromEntity(saved);
    }

    @Override
    public SubmissionDto reviewSubmission(Long submissionId, ReviewRequest request) {
        UserDto currentUser = userService.getCurrentUser();
        if (!"COORDINATOR".equals(currentUser.role())) {
            throw new ForbiddenException("Only coordinators can review submissions");
        }

        Submission submission = submissionRepository.findByIdWithFiles(submissionId)
                .orElseThrow(() -> new ResourceNotFoundException("Submission not found with id: " + submissionId));

        if ("RETURN".equalsIgnoreCase(request.action())) {
            if (request.comment() == null || request.comment().trim().isEmpty()) {
                throw new ValidationException("Comment is required when returning a submission");
            }
            submission.setStatus(SubmissionStatus.RETURNED);
            submission.setReviewerComment(request.comment().trim());
        } else if ("ACCEPT".equalsIgnoreCase(request.action())) {
            submission.setStatus(SubmissionStatus.ACCEPTED);
            submission.setReviewerComment(request.comment());
        } else {
            throw new ValidationException("Invalid review action: " + request.action());
        }

        submission.setUpdatedAt(Instant.now());
        Submission saved = submissionRepository.save(submission);

        if (eventPublisher != null) {
            try {
                DeadlineAssignmentDto assignment = deadlineService.getAssignmentById(saved.getDeadlineAssignmentId());
                eventPublisher.publishEvent(new com.iet.dashboard.shared.events.SubmissionReviewedEvent(
                        assignment.id(),
                        assignment.title(),
                        assignment.facultyId(),
                        request.action(),
                        request.comment()
                ));
            } catch (Exception e) {
                // Non-blocking for event publication
            }
        }

        return SubmissionDto.fromEntity(saved);
    }

    @Override
    @Transactional(readOnly = true)
    public Resource downloadFile(Long fileId) {
        SubmissionFile subFile = submissionFileRepository.findById(fileId)
                .orElseThrow(() -> new ResourceNotFoundException("Submission file not found with id: " + fileId));

        Submission submission = subFile.getSubmission();
        DeadlineAssignmentDto assignment = deadlineService.getAssignmentById(submission.getDeadlineAssignmentId());
        assertUserCanAccessAssignment(assignment);

        try {
            return fileStorage.loadAsResource(subFile.getStorageKey());
        } catch (IOException e) {
            throw new ResourceNotFoundException("File storage artifact not found: " + e.getMessage());
        }
    }

    @Override
    @Transactional(readOnly = true)
    public SubmissionFileDto getFileMetadata(Long fileId) {
        SubmissionFile subFile = submissionFileRepository.findById(fileId)
                .orElseThrow(() -> new ResourceNotFoundException("Submission file not found with id: " + fileId));

        Submission submission = subFile.getSubmission();
        DeadlineAssignmentDto assignment = deadlineService.getAssignmentById(submission.getDeadlineAssignmentId());
        assertUserCanAccessAssignment(assignment);

        return SubmissionFileDto.fromEntity(subFile);
    }

    @Override
    @Transactional(readOnly = true)
    public List<SubmissionFileDto> getFileHistory(Long submissionId) {
        Submission submission = submissionRepository.findById(submissionId)
                .orElseThrow(() -> new ResourceNotFoundException("Submission not found with id: " + submissionId));

        DeadlineAssignmentDto assignment = deadlineService.getAssignmentById(submission.getDeadlineAssignmentId());
        assertUserCanAccessAssignment(assignment);

        return submissionFileRepository.findBySubmissionIdOrderByVersionDesc(submissionId).stream()
                .map(SubmissionFileDto::fromEntity)
                .collect(Collectors.toList());
    }

    @Override
    @Transactional(readOnly = true)
    public List<SubmissionDto> getSubmissionsForDeadline(Long deadlineId) {
        UserDto currentUser = userService.getCurrentUser();
        if (!"COORDINATOR".equals(currentUser.role())) {
            throw new ForbiddenException("Only coordinators can view all submissions for a deadline");
        }

        List<DeadlineAssignmentDto> assignments = deadlineService.getAssignmentsForDeadline(deadlineId);
        List<SubmissionDto> dtos = new ArrayList<>();
        for (DeadlineAssignmentDto a : assignments) {
            Submission sub = submissionRepository.findByDeadlineAssignmentId(a.id()).orElse(null);
            dtos.add(sub != null ? SubmissionDto.fromEntity(sub) : SubmissionDto.pending(a.id()));
        }
        return dtos;
    }

    private void assertUserCanAccessAssignment(DeadlineAssignmentDto assignment) {
        UserDto currentUser = userService.getCurrentUser();
        if ("COORDINATOR".equals(currentUser.role())) {
            return;
        }

        FacultyDto faculty = facultyService.getFacultyByUserId(currentUser.id())
                .orElseThrow(() -> new ForbiddenException("Faculty profile not found for current user"));

        if (!assignment.facultyId().equals(faculty.id())) {
            throw new ForbiddenException("You cannot access submissions for another faculty member");
        }
    }

    private void assertUserCanSubmitForAssignment(DeadlineAssignmentDto assignment) {
        UserDto currentUser = userService.getCurrentUser();
        if ("COORDINATOR".equals(currentUser.role())) {
            return; // Coordinator can submit on behalf if needed in admin flow
        }

        FacultyDto faculty = facultyService.getFacultyByUserId(currentUser.id())
                .orElseThrow(() -> new ForbiddenException("Faculty profile not found for current user"));

        if (!assignment.facultyId().equals(faculty.id())) {
            throw new ForbiddenException("You cannot submit for an assignment not assigned to you");
        }
    }

    private String calculateSha256(byte[] data) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            byte[] hash = digest.digest(data);
            return HexFormat.of().formatHex(hash);
        } catch (NoSuchAlgorithmException e) {
            throw new RuntimeException("SHA-256 algorithm not available", e);
        }
    }
}
