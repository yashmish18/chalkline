package com.iet.dashboard.deadlines.web;

import jakarta.validation.constraints.Future;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import java.time.Instant;
import java.util.List;

public record CreateDeadlineRequest(
        @NotBlank(message = "Title is required")
        String title,

        String description,

        @NotBlank(message = "Deadline type is required")
        String type, // COURSE_FILE, EVALUATION, MARKS_UPLOAD, SYLLABUS_REPORT, OTHER

        @NotNull(message = "Due date is required")
        Instant dueAt,

        @NotBlank(message = "Target rule is required")
        String targetRule, // ALL_FACULTY, SELECTED_FACULTY, BY_SEMESTER, BY_OFFERING

        List<Long> selectedFacultyIds,
        Integer semester,
        List<Long> selectedOfferingIds
) {
}
