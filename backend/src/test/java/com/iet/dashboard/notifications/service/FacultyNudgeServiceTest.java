package com.iet.dashboard.notifications.service;

import com.iet.dashboard.academics.service.AcademicsService;
import com.iet.dashboard.academics.web.CourseDto;
import com.iet.dashboard.academics.web.CourseOfferingDto;
import com.iet.dashboard.academics.web.TeachingAssignmentDto;
import com.iet.dashboard.directory.service.FacultyService;
import com.iet.dashboard.directory.web.FacultyDto;
import com.iet.dashboard.identity.domain.Role;
import com.iet.dashboard.identity.service.UserService;
import com.iet.dashboard.identity.web.UserDto;
import com.iet.dashboard.notifications.domain.NotificationType;
import com.iet.dashboard.teaching.service.TeachingSessionService;
import com.iet.dashboard.teaching.web.PendingClassDto;
import org.junit.jupiter.api.Test;

import java.time.Clock;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyBoolean;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.contains;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class FacultyNudgeServiceTest {

    private final FacultyService faculty = mock(FacultyService.class);
    private final AcademicsService academics = mock(AcademicsService.class);
    private final TeachingSessionService sessions = mock(TeachingSessionService.class);
    private final NotificationService notifications = mock(NotificationService.class);
    private final UserService users = mock(UserService.class);
    private final FacultyNudgeService nudges = new FacultyNudgeService(faculty, academics, sessions, notifications, users);

    private static final FacultyDto TARUNA = new FacultyDto(5L, 50L, "Dr. S Taruna", "Professor", "", "CSE", List.of(), 0, "t@jklu.edu.in");
    private static final FacultyDto IDLE = new FacultyDto(6L, 60L, "Dr. No Course", "Professor", "", "CSE", List.of(), 0, "n@jklu.edu.in");

    private void on(LocalDate day) {
        ZoneId ist = ZoneId.of("Asia/Kolkata");
        nudges.setClock(Clock.fixed(day.atTime(8, 30).atZone(ist).toInstant(), ist));
    }

    private void tarunaHasNothingDone(LocalDate today) {
        CourseDto dbms = new CourseDto(1L, "CS1133", "DBMS", "THEORY", "CORE", 4, "3-0-4", 3, 0, 4, "");
        CourseOfferingDto offering = new CourseOfferingDto(1L, dbms, "2026-2027", 2, 3, "A", 1L, "Odd");
        when(faculty.getAllFaculty(null, null)).thenReturn(List.of(TARUNA, IDLE));
        when(academics.getTeachingAssignmentsForFaculty(5L)).thenReturn(List.of(new TeachingAssignmentDto(1L, offering, 5L, "Dr. S Taruna", "LECTURE", "ALL", 3)));
        when(academics.getTeachingAssignmentsForFaculty(6L)).thenReturn(List.of());
        when(academics.getTimetableForFaculty(5L)).thenReturn(List.of());
        when(academics.getSyllabusForCourse(1L)).thenReturn(List.of());
        when(sessions.getPendingClasses(eq(5L), any(), any())).thenReturn(List.of(
                new PendingClassDto(today.minusDays(9), 1L, "CS1133", "DBMS", "A", "ALL", "LECTURE", "09:00 – 09:50", "EB2-004")));
    }

    @Test
    void eachFacultyMemberIsToldExactlyWhatOfTheirsIsMissing() {
        LocalDate tuesday = LocalDate.of(2026, 10, 27);
        on(tuesday);
        tarunaHasNothingDone(tuesday);

        int sent = nudges.remindFacultyOfMissingWork();

        assertThat(sent).isEqualTo(3);
        verify(notifications).sendNotification(eq(50L), eq("Upload your timetable"), anyString(), eq(NotificationType.GENERAL), eq("/timetable"), eq(true));
        verify(notifications).sendNotification(eq(50L), eq("Add your course syllabus"), contains("CS1133"), any(), eq("/my-courses"), eq(true));
        verify(notifications).sendNotification(eq(50L), eq("1 class not logged"), contains("2026-10-18"), any(), eq("/today"), eq(true));
        verify(notifications, never()).sendNotification(eq(60L), anyString(), anyString(), any(), anyString(), anyBoolean());
        verify(sessions).getPendingClasses(5L, null, tuesday.minusDays(3));
    }

    @Test
    void onMondayCoordinatorsGetOneListOfWhatIsStillMissing() {
        LocalDate monday = LocalDate.of(2026, 10, 26);
        on(monday);
        tarunaHasNothingDone(monday);
        when(users.findUsersByRole(Role.COORDINATOR)).thenReturn(List.of(new UserDto(1L, "coord@jklu.edu.in", "Coord", "COORDINATOR")));

        nudges.remindFacultyOfMissingWork();

        verify(notifications).sendNotification(eq(1L), eq("Weekly: 1 faculty still have missing work"),
                contains("Dr. S Taruna: no timetable; no syllabus for CS1133; 1 class unlogged for over a week"),
                eq(NotificationType.GENERAL), eq("/overview"), eq(true));
    }
}
