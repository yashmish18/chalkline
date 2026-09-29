package com.iet.dashboard.submissions.web;

import com.iet.dashboard.submissions.service.SubmissionService;
import jakarta.validation.Valid;
import org.springframework.core.io.Resource;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.util.List;

@RestController
@RequestMapping("/api")
public class SubmissionController {

    private final SubmissionService submissionService;

    public SubmissionController(SubmissionService submissionService) {
        this.submissionService = submissionService;
    }

    @GetMapping("/deadline-assignments/{assignmentId}/submission")
    public ResponseEntity<SubmissionDto> getSubmissionForAssignment(@PathVariable Long assignmentId) {
        return ResponseEntity.ok(submissionService.getSubmissionByAssignmentId(assignmentId));
    }

    @PostMapping(value = "/deadline-assignments/{assignmentId}/submissions", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public ResponseEntity<SubmissionDto> submitFile(
            @PathVariable Long assignmentId,
            @RequestParam("file") MultipartFile file
    ) {
        return ResponseEntity.ok(submissionService.submitFile(assignmentId, file));
    }

    @PostMapping("/submissions/{id}/review")
    @PreAuthorize("hasRole('COORDINATOR')")
    public ResponseEntity<SubmissionDto> reviewSubmission(
            @PathVariable Long id,
            @Valid @RequestBody ReviewRequest request
    ) {
        return ResponseEntity.ok(submissionService.reviewSubmission(id, request));
    }

    @GetMapping("/submissions/{id}/history")
    public ResponseEntity<List<SubmissionFileDto>> getSubmissionFileHistory(@PathVariable Long id) {
        return ResponseEntity.ok(submissionService.getFileHistory(id));
    }

    @GetMapping("/deadlines/{deadlineId}/submissions")
    @PreAuthorize("hasRole('COORDINATOR')")
    public ResponseEntity<List<SubmissionDto>> getSubmissionsForDeadline(@PathVariable Long deadlineId) {
        return ResponseEntity.ok(submissionService.getSubmissionsForDeadline(deadlineId));
    }

    @GetMapping("/submission-files/{fileId}/download")
    public ResponseEntity<Resource> downloadFile(@PathVariable Long fileId) {
        SubmissionFileDto meta = submissionService.getFileMetadata(fileId);
        Resource resource = submissionService.downloadFile(fileId);

        String contentType = meta.contentType();
        if (contentType == null || contentType.isBlank()) {
            contentType = "application/octet-stream";
        }

        return ResponseEntity.ok()
                .contentType(MediaType.parseMediaType(contentType))
                .header(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=\"" + meta.originalName() + "\"")
                .body(resource);
    }
}
