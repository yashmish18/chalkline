package com.iet.dashboard.tracking.web;

import java.time.LocalDate;
import java.util.List;

public record OverviewSummaryDto(
        TermInfo term,
        OverviewKpis kpis,
        List<CourseCoverageRankingItem> worstFirstCoverage,
        List<WeeklyConductPoint> conductTrajectory,
        List<NeedsAttentionItem> needsAttention,
        List<FacultyPerformanceRow> facultyPerformance
) {
    /** The active semester as the coordinator configured it; null fields when none is set. */
    public record TermInfo(
            String name,
            LocalDate startDate,
            LocalDate endDate,
            Integer currentWeek,
            Integer totalWeeks,
            boolean started
    ) {}

    /** Percentages are null when there is nothing to average yet — never a fake 0%. */
    public record OverviewKpis(
            Integer averageCoveragePercentage,
            Integer averageConductRatePercentage,
            long coursesBehindPacing,
            long needsAttentionCount,
            long offeringsWithoutSyllabus,
            long facultyNotLogging,
            /** classes logged / classes the uploaded timetable scheduled so far */
            Integer loggingRatePercentage,
            long facultyWithoutTimetable,
            long totalStudents
    ) {}

    /**
     * One offering in the priority view. Ordered by how far behind the expected syllabus position
     * it is; offerings without a syllabus come last because they cannot be paced.
     */
    public record CourseCoverageRankingItem(
            Long offeringId,
            String courseCode,
            String courseName,
            int semester,
            String section,
            String facultyName,
            int coveredTopics,
            int totalTopics,
            int coveragePercentage,
            int expectedPercentage,
            String pacingStatus, // BEHIND, ON_TRACK, AHEAD
            int topicDelta,
            boolean syllabusConfigured,
            LocalDate lastSessionDate,
            int yearOfStudy,
            int studentCount
    ) {
        public CourseCoverageRankingItem(
                Long offeringId, String courseCode, String courseName, int semester,
                String section, String facultyName, int coveredTopics, int totalTopics,
                int coveragePercentage, int expectedPercentage, String pacingStatus,
                int topicDelta, boolean syllabusConfigured, LocalDate lastSessionDate) {
            this(offeringId, courseCode, courseName, semester, section, facultyName,
                    coveredTopics, totalTopics, coveragePercentage, expectedPercentage,
                    pacingStatus, topicDelta, syllabusConfigured, lastSessionDate,
                    (semester + 1) / 2, 0);
        }
    }

    public record WeeklyConductPoint(
            int weekNumber,
            int conductRate,
            long heldCount,
            long cancelledCount
    ) {}

    public record NeedsAttentionItem(
            String id,
            String type, // COVERAGE_BEHIND, NOT_LOGGING, OVERDUE_SUBMISSION, UNASSIGNED_COURSE
            String severity, // CRITICAL, WARNING, INFO
            String title,
            String subtitle,
            Long facultyId,
            String facultyName,
            String actionLabel
    ) {}

    /** How one faculty member is doing this term: load, logging discipline and pacing. */
    public record FacultyPerformanceRow(
            Long facultyId,
            String facultyName,
            int offerings,
            int weeklyHours,
            int pblHours,
            long sessionsHeld,
            long sessionsCancelled,
            LocalDate lastSessionDate,
            Integer daysSinceLastLog,
            int offeringsBehind,
            Integer averageCoverage,
            /** Classes the timetable scheduled since logging became due (past days only). */
            int scheduledSoFar,
            Integer loggingRate,
            String status // NOT_STARTED, NO_TIMETABLE, NOT_LOGGING, BEHIND, ON_TRACK, NO_DATA
    ) {}
}
