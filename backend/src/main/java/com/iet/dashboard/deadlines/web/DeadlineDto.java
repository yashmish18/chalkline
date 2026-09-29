package com.iet.dashboard.deadlines.web;

import com.iet.dashboard.deadlines.domain.Deadline;

import java.time.Instant;

public record DeadlineDto(
        Long id,
        String title,
        String description,
        String type,
        Instant dueAt,
        Long createdBy,
        Instant createdAt,
        boolean isOverdue,
        long totalAssignmentsCount
) {
    public static DeadlineDto fromEntity(Deadline deadline, long totalAssignmentsCount) {
        boolean overdue = deadline.getDueAt().isBefore(Instant.now());
        return new DeadlineDto(
                deadline.getId(),
                deadline.getTitle(),
                deadline.getDescription(),
                deadline.getType().name(),
                deadline.getDueAt(),
                deadline.getCreatedBy(),
                deadline.getCreatedAt(),
                overdue,
                totalAssignmentsCount
        );
    }
}
