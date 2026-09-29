package com.iet.dashboard.tracking.web;

import java.util.List;

public record OverviewSummaryDto(
        OverviewKpis kpis,
        List<CourseCoverageRankingItem> worstFirstCoverage,
        List<WeeklyConductPoint> conductTrajectory,
        List<NeedsAttentionItem> needsAttention
) {
    public record OverviewKpis(
            int averageCoveragePercentage,
            int averageConductRatePercentage,
            long coursesBehindPacing,
            long needsAttentionCount
    ) {}

    public record CourseCoverageRankingItem(
            Long offeringId,
            String courseCode,
            String courseName,
            String section,
            String facultyName,
            int coveredTopics,
            int totalTopics,
            int coveragePercentage,
            int expectedPercentage,
            String pacingStatus, // BEHIND, ON_TRACK, AHEAD
            int topicDelta
    ) {}

    public record WeeklyConductPoint(
            int weekNumber,
            int conductRate,
            long heldCount,
            long cancelledCount
    ) {}

    public record NeedsAttentionItem(
            String id,
            String type, // COVERAGE_BEHIND, CONDUCT_LOW, OVERDUE_SUBMISSION
            String severity, // CRITICAL, WARNING, INFO
            String title,
            String subtitle,
            Long facultyId,
            String facultyName,
            String actionLabel
    ) {}
}
