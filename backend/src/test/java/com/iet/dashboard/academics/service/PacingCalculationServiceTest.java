package com.iet.dashboard.academics.service;

import com.iet.dashboard.academics.web.AcademicTermDto;
import com.iet.dashboard.academics.web.CourseDto;
import com.iet.dashboard.academics.web.CourseOfferingDto;
import com.iet.dashboard.academics.web.CourseProgressDto;
import com.iet.dashboard.academics.web.SyllabusTopicDto;
import com.iet.dashboard.academics.web.TeachingAssignmentDto;
import com.iet.dashboard.shared.error.ForbiddenException;
import com.iet.dashboard.teaching.domain.SessionStatus;
import com.iet.dashboard.teaching.repo.TeachingSessionRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.LocalDate;
import java.util.List;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class PacingCalculationServiceTest {

    @Mock
    private AcademicsService academicsService;

    @Mock
    private TeachingSessionRepository sessionRepository;

    @InjectMocks
    private PacingCalculationServiceImpl pacingService;

    @Test
    void testCalculateProgressForOffering_OnTrack() {
        CourseDto course = new CourseDto(1L, "CS501", "Computer Networks", "IT", 4);
        CourseOfferingDto offering = new CourseOfferingDto(
                1L, course, "2026-2027", 3, 5, "A", 1L, "Odd 2026-27"
        );

        // Term starting 2 weeks ago -> week 3
        AcademicTermDto term = new AcademicTermDto(
                1L, "Odd 2026-27", "2026-2027", LocalDate.now().minusWeeks(2), LocalDate.now().plusMonths(3), true
        );

        // 3 topics: #1 (week 1), #2 (week 2), #3 (week 5)
        SyllabusTopicDto t1 = new SyllabusTopicDto(1L, 1L, 1, "Unit 1", "Intro", 1, 1);
        SyllabusTopicDto t2 = new SyllabusTopicDto(2L, 1L, 1, "Unit 1", "OSI Model", 2, 2);
        SyllabusTopicDto t3 = new SyllabusTopicDto(3L, 1L, 2, "Unit 2", "Media", 5, 3);

        when(academicsService.getOfferingById(1L)).thenReturn(offering);
        when(academicsService.getActiveTerm()).thenReturn(term);
        when(academicsService.getSyllabusForCourse(1L)).thenReturn(List.of(t1, t2, t3));

        // Topics 1 and 2 are covered
        when(sessionRepository.findCoveredTopicIdsForOffering(1L)).thenReturn(Set.of(1L, 2L));
        when(sessionRepository.countTotalSessionsForOffering(1L)).thenReturn(4L);
        when(sessionRepository.countByOfferingIdAndStatus(1L, SessionStatus.HELD)).thenReturn(4L);

        CourseProgressDto progress = pacingService.calculateProgressForOffering(1L, 2L, false);

        assertNotNull(progress);
        assertEquals("CS501", progress.courseCode());
        assertEquals(3, progress.totalTopics());
        assertEquals(2, progress.coveredTopics());
        assertEquals(67, progress.coveragePercentage()); // 2/3 = 66.66% -> 67%
        assertEquals(2, progress.expectedTopicsByCurrentWeek()); // week 3: t1(1) and t2(2) are <= 3
        assertEquals(0, progress.topicDelta()); // 2 covered - 2 expected = 0
        assertEquals("ON_TRACK", progress.pacingStatus());
        assertTrue(progress.pacingLabel().contains("On Track"));
        assertEquals(100, progress.conductRatePercentage());
        assertEquals(3, progress.topics().size());
        assertTrue(progress.topics().get(0).isCovered());
        assertTrue(progress.topics().get(1).isCovered());
        assertFalse(progress.topics().get(2).isCovered());

        verify(academicsService).assertFacultyAssignedToOffering(2L, 1L);
    }

    @Test
    void testCalculateProgressForOffering_BehindPacing() {
        CourseDto course = new CourseDto(1L, "CS501", "Computer Networks", "IT", 4);
        CourseOfferingDto offering = new CourseOfferingDto(
                1L, course, "2026-2027", 3, 5, "A", 1L, "Odd 2026-27"
        );

        AcademicTermDto term = new AcademicTermDto(
                1L, "Odd 2026-27", "2026-2027", LocalDate.now().minusWeeks(4), LocalDate.now().plusMonths(3), true
        );

        // 3 topics: #1 (week 1), #2 (week 2), #3 (week 3)
        SyllabusTopicDto t1 = new SyllabusTopicDto(1L, 1L, 1, "Unit 1", "Intro", 1, 1);
        SyllabusTopicDto t2 = new SyllabusTopicDto(2L, 1L, 1, "Unit 1", "OSI Model", 2, 2);
        SyllabusTopicDto t3 = new SyllabusTopicDto(3L, 1L, 2, "Unit 2", "Media", 3, 3);

        when(academicsService.getOfferingById(1L)).thenReturn(offering);
        when(academicsService.getActiveTerm()).thenReturn(term);
        when(academicsService.getSyllabusForCourse(1L)).thenReturn(List.of(t1, t2, t3));

        // Only topic 1 is covered, but 3 are expected by week 5
        when(sessionRepository.findCoveredTopicIdsForOffering(1L)).thenReturn(Set.of(1L));
        when(sessionRepository.countTotalSessionsForOffering(1L)).thenReturn(3L);
        when(sessionRepository.countByOfferingIdAndStatus(1L, SessionStatus.HELD)).thenReturn(2L); // 1 cancelled

        CourseProgressDto progress = pacingService.calculateProgressForOffering(1L, 2L, false);

        assertNotNull(progress);
        assertEquals(1, progress.coveredTopics());
        assertEquals(3, progress.expectedTopicsByCurrentWeek());
        assertEquals(-2, progress.topicDelta());
        assertEquals("BEHIND", progress.pacingStatus());
        assertEquals("Behind by 2 topics", progress.pacingLabel());
        assertEquals(67, progress.conductRatePercentage()); // 2/3 = 66.66% -> 67%
    }

    @Test
    void testCalculateProgressForOffering_ForbiddenWhenNotAssigned() {
        doThrow(new ForbiddenException("Not assigned to course"))
                .when(academicsService).assertFacultyAssignedToOffering(99L, 1L);

        assertThrows(ForbiddenException.class,
                () -> pacingService.calculateProgressForOffering(1L, 99L, false));
    }

    @Test
    void testGetMyCoursesProgress() {
        CourseDto course = new CourseDto(1L, "CS501", "Networks", "IT", 4);
        CourseOfferingDto offering = new CourseOfferingDto(
                1L, course, "2026-2027", 3, 5, "A", 1L, "Odd 2026-27"
        );
        TeachingAssignmentDto assignment = new TeachingAssignmentDto(
                10L, offering, 2L, "Dr. Amit", "THEORY"
        );

        when(academicsService.getTeachingAssignmentsForFaculty(2L)).thenReturn(List.of(assignment));
        when(academicsService.getOfferingById(1L)).thenReturn(offering);
        when(academicsService.getActiveTerm()).thenReturn(null);
        when(academicsService.getSyllabusForCourse(1L)).thenReturn(List.of());
        when(sessionRepository.findCoveredTopicIdsForOffering(1L)).thenReturn(Set.of());

        List<CourseProgressDto> results = pacingService.getMyCoursesProgress(2L);
        assertNotNull(results);
        assertEquals(1, results.size());
        assertEquals("CS501", results.get(0).courseCode());
        assertEquals("THEORY", results.get(0).component());
    }
}
