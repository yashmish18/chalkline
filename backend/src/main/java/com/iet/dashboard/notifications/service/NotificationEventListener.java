package com.iet.dashboard.notifications.service;

import com.iet.dashboard.directory.service.FacultyService;
import com.iet.dashboard.directory.web.FacultyDto;
import com.iet.dashboard.notifications.domain.NotificationType;
import com.iet.dashboard.shared.events.DeadlineAssignedEvent;
import com.iet.dashboard.shared.events.SubmissionReviewedEvent;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.event.EventListener;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Component;

import java.util.HashSet;
import java.util.Set;

@Component
public class NotificationEventListener {

    private static final Logger log = LoggerFactory.getLogger(NotificationEventListener.class);

    private final NotificationService notificationService;
    private final FacultyService facultyService;

    public NotificationEventListener(NotificationService notificationService, FacultyService facultyService) {
        this.notificationService = notificationService;
        this.facultyService = facultyService;
    }

    @Async
    @EventListener
    public void onDeadlineAssigned(DeadlineAssignedEvent event) {
        log.info("Processing DeadlineAssignedEvent for deadline: {}", event.title());
        Set<Long> uniqueFaculty = new HashSet<>(event.facultyIds());
        for (Long facultyId : uniqueFaculty) {
            try {
                FacultyDto faculty = facultyService.getFacultyById(facultyId);
                if (faculty.userId() != null) {
                    notificationService.sendNotification(
                            faculty.userId(),
                            "New Deadline Assigned: " + event.title(),
                            "You have been assigned to deadline '" + event.title() + "'. Due date: " + event.dueAt(),
                            NotificationType.DEADLINE_ASSIGNED,
                            "/deadlines",
                            true
                    );
                }
            } catch (Exception e) {
                log.warn("Failed to notify faculty #{} for deadline {}: {}", facultyId, event.deadlineId(), e.getMessage());
            }
        }
    }

    @Async
    @EventListener
    public void onSubmissionReviewed(SubmissionReviewedEvent event) {
        log.info("Processing SubmissionReviewedEvent: {}", event.action());
        try {
            FacultyDto faculty = facultyService.getFacultyById(event.facultyId());
            if (faculty.userId() != null) {
                boolean isAccepted = "ACCEPT".equalsIgnoreCase(event.action());
                NotificationType type = isAccepted ? NotificationType.SUBMISSION_ACCEPTED : NotificationType.SUBMISSION_RETURNED;
                String title = isAccepted ? "Submission Accepted: " + event.deadlineTitle() : "Submission Returned: " + event.deadlineTitle();
                String message = isAccepted
                        ? "Your submission for '" + event.deadlineTitle() + "' has been approved by the department coordinator."
                        : "Your submission for '" + event.deadlineTitle() + "' was returned with feedback: " + event.comment();

                notificationService.sendNotification(
                        faculty.userId(),
                        title,
                        message,
                        type,
                        "/deadlines",
                        true
                );
            }
        } catch (Exception e) {
            log.warn("Failed to notify faculty #{} for review action: {}", event.facultyId(), e.getMessage());
        }
    }
}
