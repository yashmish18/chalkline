package com.iet.dashboard.academics.web;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

/**
 * Opens several sections of one course at once. Sections are auto-lettered A, B, C… so the
 * coordinator states how many sections a year runs (1st year: 10, 3rd year: 4) instead of
 * creating them one by one. Section count is validated server-side (1–26).
 */
public record CreateOfferingsBulkRequest(
        @NotNull(message = "Course ID is required")
        Long courseId,

        @NotBlank(message = "Academic year is required")
        String academicYear,

        @NotNull(message = "Year of study is required")
        @Min(value = 1, message = "Year of study must be at least 1")
        @Max(value = 5, message = "Year of study must be at most 5")
        Integer yearOfStudy,

        @NotNull(message = "Semester is required")
        @Min(value = 1, message = "Semester must be between 1 and 8")
        @Max(value = 8, message = "Semester must be between 1 and 8")
        Integer semester,

        @NotNull(message = "Number of sections is required")
        @Min(value = 1, message = "At least one section is required")
        @Max(value = 26, message = "At most 26 sections (A–Z) can be created")
        Integer sectionCount,

        Long termId
) {
}
