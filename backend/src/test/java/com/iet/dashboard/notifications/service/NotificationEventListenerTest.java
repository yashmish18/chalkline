package com.iet.dashboard.notifications.service;

import com.iet.dashboard.directory.service.FacultyService;
import com.iet.dashboard.directory.web.FacultyDto;
import com.iet.dashboard.identity.domain.Role;
import com.iet.dashboard.identity.service.UserService;
import com.iet.dashboard.identity.web.UserDto;
import com.iet.dashboard.notifications.domain.NotificationType;
import com.iet.dashboard.shared.events.DeadlineAssignedEvent;
import com.iet.dashboard.shared.events.SubmissionReviewedEvent;
import com.iet.dashboard.shared.events.SubmissionSubmittedEvent;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.Instant;
import java.util.Collections;
import java.util.List;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyBoolean;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class NotificationEventListenerTest {

    @Mock
    private NotificationService notificationService;

    @Mock
    private FacultyService facultyService;

    @Mock
    private UserService userService;

    @InjectMocks
    private NotificationEventListener listener;

    @Test
    void onDeadlineAssigned_NotifiesEachFacultyOnceInAppAndEmail() {
        FacultyDto faculty = new FacultyDto(2L, 20L, "Dr. Amit", "Assoc Prof", "PhD", "IT", Collections.emptyList());
        when(facultyService.getFacultyById(2L)).thenReturn(faculty);

        listener.onDeadlineAssigned(new DeadlineAssignedEvent(
                7L, "Course File Submission", Instant.now().plusSeconds(86_400), List.of(2L, 2L)));

        verify(notificationService, times(1)).sendNotification(
                eq(20L), anyString(), anyString(), eq(NotificationType.DEADLINE_ASSIGNED), eq("/deadlines"), eq(true));
    }

    @Test
    void onSubmissionSubmitted_AlertsEveryCoordinatorInAppAndEmail() {
        when(userService.findUsersByRole(Role.COORDINATOR)).thenReturn(List.of(
                new UserDto(1L, "coordinator@iet.edu", "Dr. Rajesh Sharma", "COORDINATOR"),
                new UserDto(5L, "coordinator2@iet.edu", "Dr. Meera Iyer", "COORDINATOR")));

        listener.onSubmissionSubmitted(new SubmissionSubmittedEvent(
                99L, 7L, "Course File Submission", 2L, "Dr. Amit", false, 1));

        verify(notificationService).sendNotification(
                eq(1L), eq("New Submission: Course File Submission"), anyString(),
                eq(NotificationType.SUBMISSION_RECEIVED), eq("/compliance"), eq(true));
        verify(notificationService).sendNotification(
                eq(5L), anyString(), anyString(),
                eq(NotificationType.SUBMISSION_RECEIVED), eq("/compliance"), eq(true));
        verify(notificationService, times(2)).sendNotification(any(), anyString(), anyString(), any(), anyString(), anyBoolean());
    }

    @Test
    void onSubmissionSubmitted_FlagsLateUploads() {
        when(userService.findUsersByRole(Role.COORDINATOR)).thenReturn(List.of(
                new UserDto(1L, "coordinator@iet.edu", "Dr. Rajesh Sharma", "COORDINATOR")));

        listener.onSubmissionSubmitted(new SubmissionSubmittedEvent(
                99L, 7L, "Marks Upload", 2L, "Dr. Amit", true, 2));

        verify(notificationService).sendNotification(
                eq(1L), eq("Late Submission: Marks Upload"), anyString(),
                eq(NotificationType.SUBMISSION_RECEIVED), eq("/compliance"), eq(true));
    }

    @Test
    void onSubmissionSubmitted_ContinuesWhenCoordinatorLookupFails() {
        when(userService.findUsersByRole(Role.COORDINATOR)).thenThrow(new IllegalStateException("db down"));

        listener.onSubmissionSubmitted(new SubmissionSubmittedEvent(
                99L, 7L, "Marks Upload", 2L, "Dr. Amit", false, 1));

        verify(notificationService, never()).sendNotification(any(), anyString(), anyString(), any(), anyString(), anyBoolean());
    }

    @Test
    void onSubmissionReviewed_ReturnedSendsFeedbackToFaculty() {
        FacultyDto faculty = new FacultyDto(2L, 20L, "Dr. Amit", "Assoc Prof", "PhD", "IT", Collections.emptyList());
        when(facultyService.getFacultyById(2L)).thenReturn(faculty);

        listener.onSubmissionReviewed(new SubmissionReviewedEvent(11L, "Marks Upload", 2L, "RETURN", "Sign the sheet"));

        verify(notificationService).sendNotification(
                eq(20L), eq("Submission Returned: Marks Upload"), contains("Sign the sheet"),
                eq(NotificationType.SUBMISSION_RETURNED), eq("/deadlines"), eq(true));
    }

    @Test
    void onSubmissionReviewed_AcceptSendsAcceptanceToFaculty() {
        FacultyDto faculty = new FacultyDto(2L, 20L, "Dr. Amit", "Assoc Prof", "PhD", "IT", Collections.emptyList());
        when(facultyService.getFacultyById(2L)).thenReturn(faculty);

        listener.onSubmissionReviewed(new SubmissionReviewedEvent(11L, "Marks Upload", 2L, "ACCEPT", null));

        verify(notificationService).sendNotification(
                eq(20L), eq("Submission Accepted: Marks Upload"), anyString(),
                eq(NotificationType.SUBMISSION_ACCEPTED), eq("/deadlines"), eq(true));
    }
}
