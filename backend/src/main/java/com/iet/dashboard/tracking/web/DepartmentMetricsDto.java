package com.iet.dashboard.tracking.web;

public record DepartmentMetricsDto(
        long totalAssignments,
        long totalSubmissions,
        double submissionRate,
        long onTimeSubmissions,
        double onTimeRate,
        long overdueCount,
        long criticalFacultyCount,
        long totalFaculty
) {}
