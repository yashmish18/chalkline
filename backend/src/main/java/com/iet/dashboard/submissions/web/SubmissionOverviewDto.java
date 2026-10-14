package com.iet.dashboard.submissions.web;

import com.iet.dashboard.deadlines.web.DeadlineAssignmentDto;

import java.time.Instant;

/**
 * One row per deadline assignment, flattened with enough deadline + faculty context for the
 * coordinator submissions board — no need to walk deadlines one by one in the UI.
 */
public record SubmissionOverviewDto(
        Long submissionId,
        Long assignmentId,
        Long deadlineId,
        String deadlineTitle,
        String deadlineType,
        Instant dueAt,
        boolean isOverdue,
        Long facultyId,
        String facultyName,
        String status,
        Instant submittedAt,
        int totalVersions,
        Long latestFileId,
        String latestFileName,
        String reviewerComment
) {
    public static SubmissionOverviewDto fromAssignment(DeadlineAssignmentDto assignment, SubmissionDto submission) {
        return new SubmissionOverviewDto(
                submission != null ? submission.id() : null,
                assignment.id(),
                assignment.deadlineId(),
                assignment.title(),
                assignment.type(),
                assignment.dueAt(),
                assignment.isOverdue(),
                assignment.facultyId(),
                assignment.facultyName(),
                submission != null ? submission.status() : "PENDING",
                submission != null ? submission.submittedAt() : null,
                submission != null ? submission.totalVersions() : 0,
                submission != null && submission.latestFile() != null ? submission.latestFile().id() : null,
                submission != null && submission.latestFile() != null ? submission.latestFile().originalName() : null,
                submission != null ? submission.reviewerComment() : null
        );
    }
}
