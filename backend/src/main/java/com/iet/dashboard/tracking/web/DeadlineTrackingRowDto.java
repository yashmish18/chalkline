package com.iet.dashboard.tracking.web;

import java.time.Instant;

public record DeadlineTrackingRowDto(
        Long deadlineId,
        String title,
        String type,
        Instant dueAt,
        int totalAssigned,
        int submittedCount,
        int acceptedCount,
        int overdueCount,
        double complianceRate
) {}
