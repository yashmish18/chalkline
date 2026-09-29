package com.iet.dashboard.tracking.web;

import com.iet.dashboard.tracking.domain.AuditLog;

import java.time.Instant;

public record AuditLogDto(
        Long id,
        Long userId,
        String userName,
        String action,
        String targetEntity,
        Long targetId,
        String details,
        Instant createdAt
) {
    public static AuditLogDto fromEntity(AuditLog log, String userName) {
        return new AuditLogDto(
                log.getId(),
                log.getUserId(),
                userName,
                log.getAction(),
                log.getTargetEntity(),
                log.getTargetId(),
                log.getDetails(),
                log.getCreatedAt()
        );
    }
}
