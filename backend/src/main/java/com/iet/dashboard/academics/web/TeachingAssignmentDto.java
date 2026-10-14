package com.iet.dashboard.academics.web;

import com.iet.dashboard.academics.domain.TeachingAssignment;

public record TeachingAssignmentDto(
        Long id,
        CourseOfferingDto offering,
        Long facultyId,
        String facultyName,
        String component,
        String subSection,
        int contactHours,
        /** Students this assignment teaches: the batch size, else the section strength. */
        int studentCount
) {
    public TeachingAssignmentDto(Long id, CourseOfferingDto offering, Long facultyId, String facultyName, String component,
                                 String subSection, int contactHours) {
        this(id, offering, facultyId, facultyName, component, subSection, contactHours,
                offering != null ? offering.studentCount() : 0);
    }

    public TeachingAssignmentDto(Long id, CourseOfferingDto offering, Long facultyId, String facultyName, String component) {
        this(id, offering, facultyId, facultyName, component, "ALL", 0);
    }

    public TeachingAssignmentDto(Long id, CourseOfferingDto offering, Long facultyId, String facultyName, String component, String subSection) {
        this(id, offering, facultyId, facultyName, component, subSection, 0);
    }

    public static TeachingAssignmentDto fromEntity(TeachingAssignment assignment, String facultyName) {
        return new TeachingAssignmentDto(
                assignment.getId(),
                CourseOfferingDto.fromEntity(assignment.getOffering()),
                assignment.getFacultyId(),
                facultyName,
                assignment.getComponent().name(),
                assignment.getSubSection(),
                assignment.getContactHours(),
                assignment.getStudentCount() != null
                        ? assignment.getStudentCount()
                        : assignment.getOffering().getStudentCount()
        );
    }
}
