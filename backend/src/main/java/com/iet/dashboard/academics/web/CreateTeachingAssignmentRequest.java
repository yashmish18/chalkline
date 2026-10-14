package com.iet.dashboard.academics.web;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

public record CreateTeachingAssignmentRequest(
        @NotNull(message = "Offering ID is required")
        Long offeringId,

        @NotNull(message = "Faculty ID is required")
        Long facultyId,

        @NotBlank(message = "Component (ALL, THEORY, LECTURE, TUTORIAL or PRACTICAL) is required")
        String component,

        String subSection,

        /**
         * Weekly contact hours this faculty member owns for the component. Optional — when null
         * or non-positive the course LTP hours for the component are used.
         */
        Integer contactHours
) {
    public CreateTeachingAssignmentRequest(Long offeringId, Long facultyId, String component) {
        this(offeringId, facultyId, component, "ALL", null);
    }

    public CreateTeachingAssignmentRequest(Long offeringId, Long facultyId, String component, String subSection) {
        this(offeringId, facultyId, component, subSection, null);
    }
}
