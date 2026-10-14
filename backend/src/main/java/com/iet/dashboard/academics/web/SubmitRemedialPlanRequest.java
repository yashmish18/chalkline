package com.iet.dashboard.academics.web;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

import java.time.LocalDate;

public record SubmitRemedialPlanRequest(
        @NotNull Long offeringId,
        @NotBlank String reason,
        String laggingTopics,
        @NotBlank String action,
        int extraSessions,
        @NotNull LocalDate targetDate
) {}
