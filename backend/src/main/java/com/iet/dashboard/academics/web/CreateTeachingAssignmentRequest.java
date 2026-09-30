package com.iet.dashboard.academics.web;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

public record CreateTeachingAssignmentRequest(
        @NotNull(message = "Offering ID is required")
        Long offeringId,

        @NotNull(message = "Faculty ID is required")
        Long facultyId,

        @NotBlank(message = "Component (THEORY or LAB) is required")
        String component,

        String subSection
) {
    public CreateTeachingAssignmentRequest(Long offeringId, Long facultyId, String component) {
        this(offeringId, facultyId, component, "ALL");
    }
}
