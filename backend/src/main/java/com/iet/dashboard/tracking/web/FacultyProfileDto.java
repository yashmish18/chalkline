package com.iet.dashboard.tracking.web;

import com.iet.dashboard.academics.web.FacultyLtpLoadDto;
import com.iet.dashboard.directory.web.SkillDto;

import java.time.Instant;
import java.util.List;

/**
 * Everything a coordinator needs about one faculty member in a single read: identity,
 * department, every course/section they own with their LTP share, the computed weekly load,
 * their compliance record, and their submission history.
 *
 * <p>Assembled on demand from existing read models — nothing here is stored.
 */
public record FacultyProfileDto(
        Long id,
        String name,
        String email,
        String designation,
        String qualification,
        String department,
        List<SkillDto> skills,
        List<CourseAssignment> courses,
        FacultyLtpLoadDto load,
        FacultyComplianceRowDto compliance,
        List<SubmissionRow> submissions
) {
    /** One teaching assignment: the course, its section, the owned component and hours. */
    public record CourseAssignment(
            Long assignmentId,
            Long offeringId,
            String courseCode,
            String courseName,
            String category,
            String ltp,
            String type,
            String academicYear,
            int yearOfStudy,
            int semester,
            String section,
            String component,
            String subSection,
            int contactHours
    ) {
    }

    /** One deadline this faculty member was targeted for, with its current submission state. */
    public record SubmissionRow(
            Long assignmentId,
            String deadlineTitle,
            String deadlineType,
            Instant dueAt,
            String status,
            boolean overdue,
            int totalVersions,
            Instant submittedAt
    ) {
    }
}
