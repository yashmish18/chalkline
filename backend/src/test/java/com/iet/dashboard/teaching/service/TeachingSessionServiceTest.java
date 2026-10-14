package com.iet.dashboard.teaching.service;

import com.iet.dashboard.academics.domain.Course;
import com.iet.dashboard.academics.domain.CourseOffering;
import com.iet.dashboard.academics.domain.SyllabusTopic;
import com.iet.dashboard.academics.repo.CourseOfferingRepository;
import com.iet.dashboard.academics.repo.SyllabusTopicRepository;
import com.iet.dashboard.academics.service.AcademicsService;
import com.iet.dashboard.academics.web.AcademicTermDto;
import com.iet.dashboard.academics.web.CourseDto;
import com.iet.dashboard.academics.web.CourseOfferingDto;
import com.iet.dashboard.academics.web.SyllabusTopicDto;
import com.iet.dashboard.academics.web.TeachingAssignmentDto;
import com.iet.dashboard.shared.error.ForbiddenException;
import com.iet.dashboard.shared.error.ValidationException;
import com.iet.dashboard.teaching.domain.SessionStatus;
import com.iet.dashboard.teaching.domain.TeachingSession;
import com.iet.dashboard.teaching.repo.TeachingSessionRepository;
import com.iet.dashboard.teaching.web.LogSessionRequest;
import com.iet.dashboard.teaching.web.TeachingSessionDto;
import com.iet.dashboard.teaching.web.TodayClassDto;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.LocalDate;
import java.util.Collections;
import java.util.List;
import java.util.Optional;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class TeachingSessionServiceTest {

    @Mock
    private TeachingSessionRepository sessionRepository;

    @Mock
    private CourseOfferingRepository offeringRepository;

    @Mock
    private SyllabusTopicRepository topicRepository;

    @Mock
    private AcademicsService academicsService;

    @InjectMocks
    private TeachingSessionServiceImpl sessionService;

    @Test
    void testLogSession_Success() {
        Course course = new Course("CS501", "Computer Networks", "THEORY");
        CourseOffering offering = new CourseOffering(course, "2026-2027", 3, 5, "A");
        offering.setId(1L);

        SyllabusTopic topic = new SyllabusTopic(course, 1, "Unit 1", "OSI Model", 1, 1);
        topic.setId(10L);

        LogSessionRequest request = new LogSessionRequest(
                1L,
                LocalDate.of(2026, 9, 30),
                "LECTURE",
                SessionStatus.HELD,
                List.of(10L),
                "Covered physical layer in detail",
                null
        );

        when(offeringRepository.findById(1L)).thenReturn(Optional.of(offering));
        when(topicRepository.findAllById(List.of(10L))).thenReturn(List.of(topic));

        TeachingSession savedSession = new TeachingSession(
                offering, 2L, LocalDate.of(2026, 9, 30), "LECTURE", SessionStatus.HELD,
                "Covered physical layer in detail", null
        );
        savedSession.setId(100L);
        savedSession.addTopic(topic);

        when(sessionRepository.save(any(TeachingSession.class))).thenReturn(savedSession);

        TeachingSessionDto result = sessionService.logSession(request, 2L);

        assertNotNull(result);
        assertEquals(100L, result.id());
        assertEquals(1L, result.offeringId());
        assertEquals("CS501", result.courseCode());
        assertEquals(SessionStatus.HELD, result.status());
        assertEquals(1, result.topicsCovered().size());
        assertEquals("OSI Model", result.topicsCovered().get(0).topicName());

        verify(academicsService).assertFacultyAssignedToOffering(2L, 1L);
    }

    @Test
    void testLogSession_BeforeSemesterStart_ThrowsValidation() {
        Course course = new Course("CS501", "Computer Networks", "THEORY");
        CourseOffering offering = new CourseOffering(course, "2026-2027", 3, 5, "A");
        offering.setId(1L);

        LogSessionRequest request = new LogSessionRequest(
                1L,
                LocalDate.of(2026, 7, 15),
                "LECTURE",
                SessionStatus.HELD,
                List.of(),
                "Backdated class",
                null
        );

        when(academicsService.getActiveTerm())
                .thenReturn(new AcademicTermDto(1L, "Odd Semester", "2026-2027",
                        LocalDate.of(2026, 8, 3), LocalDate.of(2026, 12, 20), true));

        ValidationException thrown = assertThrows(ValidationException.class,
                () -> sessionService.logSession(request, 2L));
        assertTrue(thrown.getMessage().contains("2026-08-03"), thrown.getMessage());
        verify(sessionRepository, never()).save(any(TeachingSession.class));
    }

    @Test
    void testLogSession_IDOR_ThrowsForbidden() {
        LogSessionRequest request = new LogSessionRequest(
                1L,
                LocalDate.of(2026, 9, 30),
                "LECTURE",
                SessionStatus.HELD,
                List.of(10L),
                "Notes",
                null
        );

        doThrow(new ForbiddenException("Not assigned"))
                .when(academicsService).assertFacultyAssignedToOffering(99L, 1L);

        assertThrows(ForbiddenException.class, () -> sessionService.logSession(request, 99L));
        verify(sessionRepository, never()).save(any());
    }

    @Test
    void testLogSession_CancelledWithoutReason_ThrowsValidationException() {
        LogSessionRequest request = new LogSessionRequest(
                1L,
                LocalDate.of(2026, 9, 30),
                "LECTURE",
                SessionStatus.CANCELLED,
                Collections.emptyList(),
                "Notes",
                "  " // Blank reason
        );

        assertThrows(ValidationException.class, () -> sessionService.logSession(request, 2L));
    }

    @Test
    void testGetTodayClassesForFaculty_CalculatesNextTopicCorrectly() {
        CourseDto courseDto = new CourseDto(1L, "CS501", "Computer Networks", "THEORY", "CORE", 4, "3-0-2", 3, 0, 2, "Computer Networks desc");
        CourseOfferingDto offeringDto = new CourseOfferingDto(
                1L, courseDto, "2026-2027", 3, 5, "A", 1L, "Odd 2026-27"
        );
        TeachingAssignmentDto assignment = new TeachingAssignmentDto(
                10L, offeringDto, 2L, "Dr. Amit Verma", "THEORY", "A1", 3
        );

        when(academicsService.getTeachingAssignmentsForFaculty(2L)).thenReturn(List.of(assignment));
        when(sessionRepository.findByFacultyIdAndSessionDate(eq(2L), any(LocalDate.class))).thenReturn(Collections.emptyList());
        when(academicsService.getTimetableForFaculty(2L)).thenReturn(Collections.emptyList());

        // Topics: #1, #2, #3
        SyllabusTopicDto topic1 = new SyllabusTopicDto(1L, 1L, 1, "Unit 1", "Intro", 1, 1);
        SyllabusTopicDto topic2 = new SyllabusTopicDto(2L, 1L, 1, "Unit 1", "OSI Model", 1, 2);
        SyllabusTopicDto topic3 = new SyllabusTopicDto(3L, 1L, 2, "Unit 2", "Data Link", 2, 3);
        when(academicsService.getSyllabusForCourse(1L)).thenReturn(List.of(topic1, topic2, topic3));

        // Suppose topic #1 is already covered
        when(sessionRepository.findCoveredTopicIdsForOffering(1L)).thenReturn(Set.of(1L));

        List<TodayClassDto> classes = sessionService.getTodayClassesForFaculty(2L, LocalDate.now());

        assertEquals(1, classes.size());
        TodayClassDto todayClass = classes.get(0);
        assertEquals("CS501", todayClass.courseCode());
        assertEquals("A", todayClass.section(), "raw section; the UI formats it");
        assertFalse(todayClass.scheduled(), "no timetable uploaded, so no time is invented");
        assertEquals("", todayClass.timeSlot());
        assertEquals("A1", todayClass.subSection());
        assertEquals("3-0-2", todayClass.ltp());
        assertEquals("Computer Networks desc", todayClass.courseDescription());
        assertEquals(3, todayClass.totalPlanned());
        assertEquals(1, todayClass.completedSoFar());
        assertFalse(todayClass.isLogged());
        // Pre-selected next topic should be topic 2 (OSI Model)
        assertEquals(2L, todayClass.nextTopicId());
        assertEquals("OSI Model", todayClass.nextTopic());
        assertEquals(2, todayClass.availableUnits().size());
        assertEquals("Unit 1", todayClass.availableUnits().get(0).unitTitle());
        assertEquals(2, todayClass.availableUnits().get(0).topics().size());
    }

    @Test
    void testGetSessionsForOffering_CoordinatorAllowed_FacultyChecked() {
        when(sessionRepository.findByOfferingIdWithTopics(1L)).thenReturn(Collections.emptyList());

        // Coordinator: no assertion needed
        List<TeachingSessionDto> sessions = sessionService.getSessionsForOffering(1L, null, true);
        assertNotNull(sessions);
        verify(academicsService, never()).assertFacultyAssignedToOffering(any(), any());

        // Faculty: assertFacultyAssignedToOffering is called
        sessionService.getSessionsForOffering(1L, 2L, false);
        verify(academicsService).assertFacultyAssignedToOffering(2L, 1L);
    }

    @Test
    void testDeleteSession_Success() {
        CourseOffering offering = new CourseOffering();
        offering.setId(1L);
        com.iet.dashboard.teaching.domain.TeachingSession session = new com.iet.dashboard.teaching.domain.TeachingSession(
                offering, 2L, LocalDate.now(), "LECTURE", com.iet.dashboard.teaching.domain.SessionStatus.HELD, 1, "custom topic", "ALL", 1, "notes", null
        );
        session.setId(99L);
        when(sessionRepository.findById(99L)).thenReturn(Optional.of(session));

        sessionService.deleteSession(99L, 2L, false);
        verify(sessionRepository).delete(session);
    }

    @Test
    void todayListsExactlyThatWeekdaysTimetableSlotsInOrder() {
        CourseDto course = new CourseDto(1L, "CS501", "Computer Networks", "THEORY", "CORE", 4, "3-0-2", 3, 0, 2, "");
        CourseOfferingDto offering = new CourseOfferingDto(1L, course, "2026-2027", 3, 5, "A", 1L, "Odd");
        when(academicsService.getTeachingAssignmentsForFaculty(2L)).thenReturn(List.of(
                new TeachingAssignmentDto(10L, offering, 2L, "Dr. Amit", "LECTURE", "ALL", 3),
                new TeachingAssignmentDto(11L, offering, 2L, "Dr. Amit", "PRACTICAL", "A1", 2)));
        LocalDate monday = LocalDate.of(2026, 10, 5);
        when(sessionRepository.findByFacultyIdAndSessionDate(2L, monday)).thenReturn(Collections.emptyList());
        when(academicsService.getTimetableForFaculty(2L)).thenReturn(List.of(
                new com.iet.dashboard.academics.web.TimetableSlotDto(1L, 1L, "CS501", "", "", "", "A", "ALL", "LECTURE", "MONDAY", "09:00", "10:00", "LT-1", 2L),
                new com.iet.dashboard.academics.web.TimetableSlotDto(2L, 1L, "CS501", "", "", "", "A", "A1", "PRACTICAL", "MONDAY", "14:00", "16:00", "Lab 3", 2L),
                new com.iet.dashboard.academics.web.TimetableSlotDto(3L, 1L, "CS501", "", "", "", "A", "ALL", "LECTURE", "TUESDAY", "09:00", "10:00", "LT-1", 2L)));
        when(academicsService.getSyllabusForCourse(1L)).thenReturn(Collections.emptyList());
        when(sessionRepository.findCoveredTopicIdsForOffering(1L)).thenReturn(Set.of());

        List<TodayClassDto> classes = sessionService.getTodayClassesForFaculty(2L, monday);

        assertEquals(2, classes.size());
        assertEquals("09:00 – 10:00", classes.get(0).timeSlot());
        assertEquals("LT-1", classes.get(0).room());
        assertEquals("A1", classes.get(1).subSection());
        assertEquals("PRACTICAL", classes.get(1).component());
        assertTrue(classes.stream().allMatch(TodayClassDto::scheduled));
    }

    @Test
    void loggingTheLectureDoesNotMarkTheTutorialOfTheSameSectionLogged() {
        CourseDto course = new CourseDto(1L, "CS501", "Computer Networks", "THEORY", "CORE", 4, "3-0-2", 3, 0, 2, "");
        CourseOfferingDto offering = new CourseOfferingDto(1L, course, "2026-2027", 3, 5, "A", 1L, "Odd");
        when(academicsService.getTeachingAssignmentsForFaculty(2L)).thenReturn(List.of(
                new TeachingAssignmentDto(10L, offering, 2L, "Dr. Amit", "LECTURE", "ALL", 3),
                new TeachingAssignmentDto(11L, offering, 2L, "Dr. Amit", "TUTORIAL", "ALL", 1)));
        LocalDate day = LocalDate.of(2026, 10, 6);

        // The faculty member logged the lecture for the whole section that day.
        CourseOffering entity = new CourseOffering(new Course("CS501", "Computer Networks", "THEORY"), "2026-2027", 3, 5, "A");
        entity.setId(1L);
        TeachingSession lecture = new TeachingSession(entity, 2L, day, "LECTURE", SessionStatus.HELD, 1, "Intro", "ALL", 1, null, null);
        when(sessionRepository.findByFacultyIdAndSessionDate(2L, day)).thenReturn(List.of(lecture));
        when(academicsService.getTimetableForFaculty(2L)).thenReturn(Collections.emptyList());
        when(academicsService.getSyllabusForCourse(1L)).thenReturn(Collections.emptyList());
        when(sessionRepository.findCoveredTopicIdsForOffering(1L)).thenReturn(Set.of());

        List<TodayClassDto> classes = sessionService.getTodayClassesForFaculty(2L, day);

        TodayClassDto lec = classes.stream().filter(c -> "LECTURE".equals(c.component())).findFirst().orElseThrow();
        TodayClassDto tut = classes.stream().filter(c -> "TUTORIAL".equals(c.component())).findFirst().orElseThrow();
        assertTrue(lec.isLogged(), "the lecture that was logged shows as logged");
        assertFalse(tut.isLogged(), "the tutorial must not be marked logged by the lecture");
    }
}
