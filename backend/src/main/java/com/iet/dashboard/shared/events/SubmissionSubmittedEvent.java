package com.iet.dashboard.shared.events;

/**
 * Published when a faculty member uploads (or resubmits) a file against a deadline
 * assignment. The coordinator side listens to this to raise an in-app + email alert, so
 * review never depends on the coordinator happening to open the tracking screen.
 */
public record SubmissionSubmittedEvent(
        Long submissionId,
        Long deadlineId,
        String deadlineTitle,
        Long facultyId,
        String facultyName,
        boolean late,
        int version
) {
}
