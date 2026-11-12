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
        int assignmentCount,
        /** The same hours, one line per course / section / component / batch. */
        java.util.List<Line> lines
) {
    /** One allocation's share of the load: L hours for a lecture, T for a tutorial, P for each practical batch. */
    public record Line(String courseCode, String courseName, String ltp, int semester, String section,
                       String component, String batch, Integer students, int hours) {}

    public FacultyLtpLoadDto(
            Long facultyId,
            String facultyName,
            int lectureHours,
            int tutorialHours,
            int practicalHours,
            int pblHours,
            int totalHours,
            int assignmentCount
    ) {
        this(facultyId, facultyName, lectureHours, tutorialHours, practicalHours, pblHours, totalHours, assignmentCount, java.util.List.of());
    }

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
