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
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.Collections;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class NotificationServiceTest {

    @Mock
    private NotificationRepository notificationRepository;

    @Mock
    private EmailService emailService;

    @Mock
    private UserService userService;

    @Mock
    private DeadlineService deadlineService;

    @Mock
    private FacultyService facultyService;

    @InjectMocks
    private NotificationServiceImpl notificationService;

    private UserDto facultyUser;

    @BeforeEach
    void setUp() {
        facultyUser = new UserDto(2L, "amit@iet.edu", "Dr. Amit", "FACULTY");
    }

    @Test
    void testGetNotificationsForCurrentUser() {
        when(userService.getCurrentUser()).thenReturn(facultyUser);

        Notification n = new Notification(2L, "Deadline Alert", "Please submit your file", NotificationType.UPCOMING_REMINDER, "/deadlines");
        n.setId(10L);
        when(notificationRepository.findByUserIdOrderByCreatedAtDesc(2L)).thenReturn(List.of(n));

        List<NotificationDto> result = notificationService.getNotificationsForCurrentUser();

        assertEquals(1, result.size());
        assertEquals("Deadline Alert", result.get(0).title());
        assertFalse(result.get(0).isRead());
    }

    @Test
    void testGetUnreadCountForCurrentUser() {
        when(userService.getCurrentUser()).thenReturn(facultyUser);
        when(notificationRepository.countByUserIdAndReadAtIsNull(2L)).thenReturn(3L);

        long count = notificationService.getUnreadCountForCurrentUser();

        assertEquals(3L, count);
    }

    @Test
    void testMarkAsRead() {
        when(userService.getCurrentUser()).thenReturn(facultyUser);

        Notification n = new Notification(2L, "Notice", "Details", NotificationType.GENERAL, "/deadlines");
        n.setId(5L);
        when(notificationRepository.findByIdAndUserId(5L, 2L)).thenReturn(Optional.of(n));
        when(notificationRepository.save(any(Notification.class))).thenAnswer(inv -> inv.getArgument(0));

        NotificationDto result = notificationService.markAsRead(5L);

        assertNotNull(result);
        assertTrue(result.isRead());
        assertNotNull(result.readAt());
    }

    @Test
    void testMarkAllAsRead() {
        when(userService.getCurrentUser()).thenReturn(facultyUser);

        notificationService.markAllAsRead();

        verify(notificationRepository).markAllAsReadForUser(eq(2L), any(Instant.class));
    }

    @Test
    void testSendNotification_WithEmail() {
        when(userService.findById(2L)).thenReturn(Optional.of(facultyUser));

        notificationService.sendNotification(
                2L,
                "New Assignment",
                "You have been assigned to Course File submission",
                NotificationType.DEADLINE_ASSIGNED,
                "/deadlines",
                true
        );

        verify(notificationRepository).save(any(Notification.class));
        verify(emailService).sendEmailAsync(eq("amit@iet.edu"), eq("New Assignment"), anyString());
    }

    @Test
    void testCheckAndTriggerReminders() {
        Instant dueSoon = Instant.now().plus(24, ChronoUnit.HOURS);
        DeadlineDto deadline = new DeadlineDto(1L, "Course File", "Upload report", "COURSE_FILE",
                dueSoon, 1L, Instant.now().minus(2, ChronoUnit.DAYS), false, 1);

        when(deadlineService.getAllDeadlines()).thenReturn(List.of(deadline));

        DeadlineAssignmentDto assignment = new DeadlineAssignmentDto(
                10L, 1L, "Course File", "Upload report", "COURSE_FILE",
                dueSoon, 5L, "Dr. Amit", null, false
        );
        when(deadlineService.getAssignmentsForDeadline(1L)).thenReturn(List.of(assignment));

        FacultyDto faculty = new FacultyDto(5L, 2L, "Dr. Amit", "Prof", "PhD", "IT", Collections.emptyList());
        when(facultyService.getFacultyById(5L)).thenReturn(faculty);
        when(userService.findById(2L)).thenReturn(Optional.of(facultyUser));

        int count = notificationService.checkAndTriggerReminders();

        assertEquals(1, count);
        verify(notificationRepository).save(any(Notification.class));
        verify(emailService).sendEmailAsync(eq("amit@iet.edu"), contains("Upcoming Deadline"), anyString());
    }
}
