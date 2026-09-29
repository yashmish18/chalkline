package com.iet.dashboard.submissions.web;

import com.iet.dashboard.submissions.domain.Submission;

import java.time.Instant;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;
import java.util.stream.Collectors;

public record SubmissionDto(
        Long id,
        Long deadlineAssignmentId,
        String status,
        Instant submittedAt,
        String reviewerComment,
        Instant updatedAt,
        SubmissionFileDto latestFile,
        int totalVersions,
        List<SubmissionFileDto> files
) {
    public static SubmissionDto fromEntity(Submission submission) {
        List<SubmissionFileDto> fileDtos = (submission.getFiles() != null)
                ? submission.getFiles().stream()
                        .map(SubmissionFileDto::fromEntity)
                        .sorted(Comparator.comparingInt(SubmissionFileDto::version).reversed())
                        .collect(Collectors.toList())
                : Collections.emptyList();

        SubmissionFileDto latest = fileDtos.isEmpty() ? null : fileDtos.get(0);

        return new SubmissionDto(
                submission.getId(),
                submission.getDeadlineAssignmentId(),
                submission.getStatus().name(),
                submission.getSubmittedAt(),
                submission.getReviewerComment(),
                submission.getUpdatedAt(),
                latest,
                fileDtos.size(),
                fileDtos
        );
    }

    public static SubmissionDto pending(Long assignmentId) {
        return new SubmissionDto(
                null,
                assignmentId,
                "PENDING",
                null,
                null,
                null,
                null,
                0,
                Collections.emptyList()
        );
    }
}
