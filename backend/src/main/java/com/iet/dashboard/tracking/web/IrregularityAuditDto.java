package com.iet.dashboard.tracking.web;

import java.util.List;

public record IrregularityAuditDto(
        int totalIssues,
        int criticalCount,
        int warningCount,
        List<OfferingIrregularityItem> items
) {
    public record OfferingIrregularityItem(
            Long offeringId,
            String courseCode,
            String courseName,
            String section,
            String ltp,
            String issueType, // MISSING_FACULTY, UNASSIGNED_LAB, NO_SYLLABUS, LTP_MISMATCH
            String severity,  // CRITICAL, WARNING, INFO
            String description,
            String assignedFaculty
    ) {}
}
