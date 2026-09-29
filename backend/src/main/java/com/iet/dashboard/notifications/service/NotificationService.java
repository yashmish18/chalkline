package com.iet.dashboard.notifications.service;

import com.iet.dashboard.notifications.domain.NotificationType;
import com.iet.dashboard.notifications.web.NotificationDto;

import java.util.List;

public interface NotificationService {

    List<NotificationDto> getNotificationsForCurrentUser();

    long getUnreadCountForCurrentUser();

    NotificationDto markAsRead(Long notificationId);

    void markAllAsRead();

    void sendNotification(Long userId, String title, String message, NotificationType type, String targetUrl, boolean sendEmail);

    int checkAndTriggerReminders();

    void sendDirectReminder(Long facultyId, String title, String message);
}
