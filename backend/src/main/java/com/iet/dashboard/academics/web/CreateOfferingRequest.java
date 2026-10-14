package com.iet.dashboard.academics.web;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

/**
 * Creates a course offering: a catalog course taught to one section in one academic
 * year/semester. Allocations, timetable slots and deadlines hang off the offering, so this
 * is the step that turns "a course exists" into "this section is being taught".
 */
public record CreateOfferingRequest(
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

        @NotBlank(message = "Section is required")
        String section,

        Long termId
) {
}
