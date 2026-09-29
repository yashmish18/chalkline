package com.iet.dashboard.teaching.web;

import com.iet.dashboard.teaching.domain.SessionStatus;
import jakarta.validation.constraints.NotNull;

import java.time.LocalDate;
import java.util.List;

public record LogSessionRequest(
        @NotNull(message = "Offering ID is required")
        Long offeringId,

        @NotNull(message = "Session date is required")
        LocalDate sessionDate,

        String sessionType, // LECTURE, LAB, TUTORIAL

        @NotNull(message = "Session status is required")
        SessionStatus status, // HELD, CANCELLED, SWAPPED

        List<Long> topicIds,

        String note,

        String reasonNotHeld
) {
}
