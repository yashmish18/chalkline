package com.iet.dashboard.tracking.web;

public record FacultyComplianceRowDto(
        Long facultyId,
        String facultyName,
        String designation,
        String department,
        int totalAssigned,
        int submittedCount,
        int acceptedCount,
        int pendingCount,
        double complianceRate,
        String complianceStatus
) {}
