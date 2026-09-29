package com.iet.dashboard.shared.events;

public record SubmissionReviewedEvent(
        Long assignmentId,
        String deadlineTitle,
        Long facultyId,
        String action,
        String comment
) {}
