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

        Integer unitNumber,

        List<Long> topicIds,

        String customTopicName,

        String subSection, // ALL, A1, A2, etc.

        Integer lecturesTaken,

        String note,

        String reasonNotHeld
) {
    public LogSessionRequest(
            Long offeringId,
            LocalDate sessionDate,
            String sessionType,
            SessionStatus status,
            List<Long> topicIds,
            String note,
            String reasonNotHeld
    ) {
        this(offeringId, sessionDate, sessionType, status, null, topicIds, null, "ALL", 1, note, reasonNotHeld);
    }
}
