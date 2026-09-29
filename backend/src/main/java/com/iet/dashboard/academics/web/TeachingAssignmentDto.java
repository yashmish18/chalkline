package com.iet.dashboard.academics.web;

import com.iet.dashboard.academics.domain.TeachingAssignment;

public record TeachingAssignmentDto(
        Long id,
        CourseOfferingDto offering,
        Long facultyId,
        String facultyName,
        String component
) {
    public static TeachingAssignmentDto fromEntity(TeachingAssignment assignment, String facultyName) {
        return new TeachingAssignmentDto(
                assignment.getId(),
                CourseOfferingDto.fromEntity(assignment.getOffering()),
                assignment.getFacultyId(),
                facultyName,
                assignment.getComponent().name()
        );
    }
}
