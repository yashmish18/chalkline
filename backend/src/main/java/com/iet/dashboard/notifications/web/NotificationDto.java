package com.iet.dashboard.notifications.web;

import com.iet.dashboard.notifications.domain.Notification;

import java.time.Instant;

public record NotificationDto(
        Long id,
        Long userId,
        String title,
        String message,
        String type,
        String targetUrl,
        Instant readAt,
        Instant createdAt,
        boolean isRead
) {
    public static NotificationDto fromEntity(Notification notification) {
        return new NotificationDto(
                notification.getId(),
                notification.getUserId(),
                notification.getTitle(),
                notification.getMessage(),
                notification.getType().name(),
                notification.getTargetUrl(),
                notification.getReadAt(),
                notification.getCreatedAt(),
                notification.isRead()
        );
    }
}
