package com.iet.dashboard.academics.web;

import com.iet.dashboard.academics.domain.RemedialPlan;

import java.time.Instant;
import java.time.LocalDate;

public record RemedialPlanDto(
        Long id,
        Long offeringId,
        String courseCode,
        String courseName,
        String section,
        Long facultyId,
        String facultyName,
        String reason,
        String laggingTopics,
        String action,
        int extraSessions,
        LocalDate targetDate,
        String status,
        Instant createdAt
) {
    public static RemedialPlanDto fromEntity(RemedialPlan r) {
        return fromEntity(r, "");
    }

    public static RemedialPlanDto fromEntity(RemedialPlan r, String facultyName) {
        return new RemedialPlanDto(
                r.getId(),
                r.getOffering().getId(),
                r.getOffering().getCourse().getCode(),
                r.getOffering().getCourse().getName(),
                r.getOffering().getSection(),
                r.getFacultyId(),
                facultyName != null ? facultyName : "",
                r.getReason(),
                r.getLaggingTopics(),
                r.getAction(),
                r.getExtraSessions(),
                r.getTargetDate(),
                r.getStatus(),
                r.getCreatedAt());
    }
}
