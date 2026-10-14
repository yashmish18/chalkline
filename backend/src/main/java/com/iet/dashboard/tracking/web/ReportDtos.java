package com.iet.dashboard.tracking.web;

import java.time.Instant;
import java.time.LocalDate;
import java.util.List;

/** The data behind the semester, faculty and course reports. Every number is computed, none typed. */
public final class ReportDtos {

    private ReportDtos() {
    }

    public record Meta(
            String termName,
            LocalDate termStart,
            LocalDate termEnd,
            Integer currentWeek,
            Integer totalWeeks,
            int requiredAssessments,
            Instant generatedAt
    ) {}

    /** One scorecard line: what is expected, what is true, and whether that is acceptable. */
    public record ScoreLine(
            String metric,
            String target,
            String actual,
            String status, // OK, WATCH, CRITICAL, NO_DATA
            String note
    ) {}

    /** A case that needs the coordinator: who, what, how many students, and what has to happen. */
    public record CriticalCase(
            String ref,
            String severity, // CRITICAL, WARNING
            String category,
            String entity,
            Long facultyId,
            String facultyName,
            int studentsAffected,
            String description,
            String requiredAction
    ) {}

    /** A section whose allocation does not match its L-T-P plan. */
    public record Mismatch(
            Long offeringId,
            String courseCode,
            String courseName,
            int yearOfStudy,
            int semester,
            String section,
            String ltp,
            String component,
            int plannedHours,
            int assignedHours,
            String type, // MISSING_OWNER, HOURS_SHORT, OVER_ALLOCATION, BATCH_COVERAGE_GAP
            String detail,
            String faculty
    ) {}

    /** One section of a course in the course matrix. */
    public record SectionPerformance(
            Long offeringId,
            String courseCode,
            String courseName,
            int yearOfStudy,
            int semester,
            String section,
            int students,
            String components,
            String faculty,
            int coveredTopics,
            int totalTopics,
            int coveragePct,
            String pacingStatus, // AHEAD, ON_TRACK, BEHIND, NO_SYLLABUS
            int topicDelta,
            long held,
            long cancelled,
            int scheduled,
            int assessmentsHeld,
            Integer latestAveragePct,
            String remedialStatus // NOT_REQUIRED, REQUIRED, FILED, COMPLETED
    ) {}

    /** One thing a faculty member teaches, in the faculty breakdown. */
    public record TeachingLine(
            Long offeringId,
            int yearOfStudy,
            String courseCode,
            String courseName,
            String section,
            String component,
            int weeklyHours,
            int students,
            long held,
            int scheduled,
            int coveragePct,
            String pacingStatus,
            int assessmentsHeld,
            String remedialStatus
    ) {}

    public record FacultyBreakdown(
            Long facultyId,
            String name,
            String designation,
            String department,
            int lectureHours,
            int tutorialHours,
            int practicalHours,
            int pblHours,
            int totalHours,
            int studentsTaught,
            List<Integer> years,
            long held,
            long cancelled,
            int scheduled,
            Integer loggingRate,
            String status,
            List<TeachingLine> lines
    ) {}

    /** How many sections have held at least N assessments, for N = 1..required. */
    public record AssessmentProgress(
            int index,
            int sectionsDone,
            int sectionsTotal,
            Integer averagePct
    ) {}

    public record RemedialPlanLine(
            Long id,
            String courseCode,
            String section,
            String facultyName,
            String reason,
            String action,
            int extraSessions,
            LocalDate targetDate,
            String status
    ) {}

    public record DataSources(
            int faculty,
            int allocations,
            int sections,
            long sessionsLogged,
            int timetableSlots,
            int syllabusTopics,
            int assessments,
            int remedialPlans
    ) {}

    public record SemesterReport(
            Meta meta,
            List<ScoreLine> scorecard,
            List<CriticalCase> criticalCases,
            List<Mismatch> mismatches,
            List<SectionPerformance> sections,
            List<FacultyBreakdown> faculty,
            List<AssessmentProgress> assessments,
            List<RemedialPlanLine> remedialPlans,
            DataSources dataSources
    ) {}

    /** A single logged class, for the faculty's daily log. */
    public record LogEntry(
            LocalDate date,
            String courseCode,
            String section,
            String subSection,
            String sessionType,
            String status,
            int hours,
            String topics,
            String note
    ) {}

    public record FacultyReport(
            Meta meta,
            FacultyBreakdown summary,
            List<CriticalCase> cases,
            List<LogEntry> recentLogs
    ) {}

    public record CourseReport(
            Meta meta,
            String courseCode,
            String courseName,
            String ltp,
            int totalStudents,
            List<SectionPerformance> sections,
            List<Mismatch> mismatches
    ) {}
}
