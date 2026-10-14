package com.iet.dashboard.academics.web;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

import java.time.LocalDate;

public record RecordAssessmentRequest(
        @NotNull Long offeringId,
        @NotBlank String name,
        @NotBlank String kind,
        int weightPct,
        @NotNull LocalDate heldOn,
        Integer studentsAssessed,
        Integer averagePct,
        Integer below40Count
) {}
