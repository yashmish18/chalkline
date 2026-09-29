package com.iet.dashboard.deadlines.web;

import com.iet.dashboard.deadlines.domain.DeadlineAssignment;

import java.time.Instant;

public record DeadlineAssignmentDto(
        Long id,
        Long deadlineId,
        String title,
        String description,
        String type,
        Instant dueAt,
        Long facultyId,
        String facultyName,
        Long offeringId,
        boolean isOverdue
) {
    public static DeadlineAssignmentDto fromEntity(DeadlineAssignment assignment, String facultyName) {
        var deadline = assignment.getDeadline();
        boolean overdue = deadline.getDueAt().isBefore(Instant.now());
        return new DeadlineAssignmentDto(
                assignment.getId(),
                deadline.getId(),
                deadline.getTitle(),
                deadline.getDescription(),
                deadline.getType().name(),
                deadline.getDueAt(),
                assignment.getFacultyId(),
                facultyName,
                assignment.getOfferingId(),
                overdue
        );
    }
}
