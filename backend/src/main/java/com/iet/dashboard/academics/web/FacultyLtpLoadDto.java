package com.iet.dashboard.academics.web;

/**
 * Computed weekly LTP load for a faculty member.
 *
 * <p>Derived on the fly by summing {@code teaching_assignment.contact_hours} per component
 * bucket — it is a projection, never a stored or typed number. Buckets follow the
 * assignment's component: {@code LECTURE}/{@code THEORY} → lecture, {@code TUTORIAL} →
 * tutorial, {@code PRACTICAL}/{@code LAB} → practical.
 */
public record FacultyLtpLoadDto(
        Long facultyId,
        String facultyName,
        int lectureHours,
        int tutorialHours,
        int practicalHours,
        int pblHours,
        int totalHours,
        int assignmentCount
) {
    public FacultyLtpLoadDto(
            Long facultyId,
            String facultyName,
            int lectureHours,
            int tutorialHours,
            int practicalHours,
            int totalHours,
            int assignmentCount
    ) {
        this(facultyId, facultyName, lectureHours, tutorialHours, practicalHours, 0, totalHours, assignmentCount);
    }
}
