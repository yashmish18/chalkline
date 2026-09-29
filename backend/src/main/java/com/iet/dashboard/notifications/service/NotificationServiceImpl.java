package com.iet.dashboard.notifications.service;

import com.iet.dashboard.deadlines.service.DeadlineService;
import com.iet.dashboard.deadlines.web.DeadlineAssignmentDto;
import com.iet.dashboard.deadlines.web.DeadlineDto;
import com.iet.dashboard.directory.service.FacultyService;
import com.iet.dashboard.directory.web.FacultyDto;
import com.iet.dashboard.identity.service.UserService;
import com.iet.dashboard.identity.web.UserDto;
import com.iet.dashboard.notifications.domain.Notification;
import com.iet.dashboard.notifications.domain.NotificationType;
import com.iet.dashboard.notifications.email.EmailService;
import com.iet.dashboard.notifications.repo.NotificationRepository;
import com.iet.dashboard.notifications.web.NotificationDto;
import com.iet.dashboard.shared.error.ResourceNotFoundException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.stream.Collectors;

@Service
@Transactional
public class NotificationServiceImpl implements NotificationService {

    private static final Logger log = LoggerFactory.getLogger(NotificationServiceImpl.class);

    private final NotificationRepository notificationRepository;
    private final EmailService emailService;
    private final UserService userService;
    private final DeadlineService deadlineService;
    private final FacultyService facultyService;

    public NotificationServiceImpl(
            NotificationRepository notificationRepository,
            EmailService emailService,
            UserService userService,
            DeadlineService deadlineService,
            FacultyService facultyService
    ) {
        this.notificationRepository = notificationRepository;
        this.emailService = emailService;
        this.userService = userService;
        this.deadlineService = deadlineService;
        this.facultyService = facultyService;
    }

    @Override
    @Transactional(readOnly = true)
    public List<NotificationDto> getNotificationsForCurrentUser() {
        UserDto currentUser = userService.getCurrentUser();
        return notificationRepository.findByUserIdOrderByCreatedAtDesc(currentUser.id()).stream()
                .map(NotificationDto::fromEntity)
                .collect(Collectors.toList());
    }

    @Override
    @Transactional(readOnly = true)
    public long getUnreadCountForCurrentUser() {
        UserDto currentUser = userService.getCurrentUser();
        return notificationRepository.countByUserIdAndReadAtIsNull(currentUser.id());
    }

    @Override
    public NotificationDto markAsRead(Long notificationId) {
        UserDto currentUser = userService.getCurrentUser();
        Notification notification = notificationRepository.findByIdAndUserId(notificationId, currentUser.id())
                .orElseThrow(() -> new ResourceNotFoundException("Notification not found with id: " + notificationId));

        if (notification.getReadAt() == null) {
            notification.setReadAt(Instant.now());
            notification = notificationRepository.save(notification);
        }
        return NotificationDto.fromEntity(notification);
    }

    @Override
    public void markAllAsRead() {
        UserDto currentUser = userService.getCurrentUser();
        notificationRepository.markAllAsReadForUser(currentUser.id(), Instant.now());
    }

    @Override
    public void sendNotification(Long userId, String title, String message, NotificationType type, String targetUrl, boolean sendEmail) {
        // Save in-app notification
        Notification notification = new Notification(userId, title, message, type, targetUrl);
        notificationRepository.save(notification);

        // Dispatch async email if requested
        if (sendEmail) {
            userService.findById(userId).ifPresent(user -> {
                if (user.email() != null && !user.email().isBlank()) {
                    emailService.sendEmailAsync(user.email(), title, message);
                }
            });
        }
    }

    @Override
    @Scheduled(cron = "${app.notifications.cron:0 0 9 * * ?}")
    public int checkAndTriggerReminders() {
        log.info("Checking upcoming and overdue deadlines for automated reminder alerts...");
        List<DeadlineDto> deadlines = deadlineService.getAllDeadlines();
        Instant now = Instant.now();
        int triggeredCount = 0;

        for (DeadlineDto deadline : deadlines) {
            Duration duration = Duration.between(now, deadline.dueAt());
            long hoursRemaining = duration.toHours();

            // Upcoming reminders (within 5 days or within 24 hours)
            boolean isUpcoming = hoursRemaining > 0 && hoursRemaining <= 120;
            // Overdue alert (past due by less than 7 days to avoid spamming historical records)
            boolean isRecentlyOverdue = hoursRemaining < 0 && hoursRemaining >= -168;

            if (isUpcoming || isRecentlyOverdue) {
                List<DeadlineAssignmentDto> assignments = deadlineService.getAssignmentsForDeadline(deadline.id());
                for (DeadlineAssignmentDto assignment : assignments) {
                    try {
                        FacultyDto faculty = facultyService.getFacultyById(assignment.facultyId());
                        if (faculty.userId() != null) {
                            if (isUpcoming) {
                                sendNotification(
                                        faculty.userId(),
                                        "Upcoming Deadline: " + deadline.title(),
                                        "Your assignment for '" + deadline.title() + "' is due on " + deadline.dueAt() + ". Please submit required documentation.",
                                        NotificationType.UPCOMING_REMINDER,
                                        "/deadlines",
                                        true
                                );
                            } else {
                                sendNotification(
                                        faculty.userId(),
                                        "OVERDUE: " + deadline.title(),
                                        "The deadline for '" + deadline.title() + "' expired on " + deadline.dueAt() + ". Please complete your submission immediately.",
                                        NotificationType.OVERDUE_ALERT,
                                        "/deadlines",
                                        true
                                );
                            }
                            triggeredCount++;
                        }
                    } catch (Exception e) {
                        log.warn("Could not send automated reminder for assignment {}: {}", assignment.id(), e.getMessage());
                    }
                }
            }
        }
        log.info("Finished automated reminder scan. Total alerts triggered: {}", triggeredCount);
        return triggeredCount;
    }
}
