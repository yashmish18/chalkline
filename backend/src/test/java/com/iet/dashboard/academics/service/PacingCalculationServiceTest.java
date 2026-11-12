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

    private static final CourseOfferingDto OFFERING = new CourseOfferingDto(
            1L, new CourseDto(1L, "CS501", "Computer Networks", "IT", 4), "2026-2027", 3, 5, "A", 1L, "Odd 2026-27");
    /** Mon 7 Sep to Fri 30 Oct 2026: eight teaching weeks. */
    private static final AcademicTermDto TERM = new AcademicTermDto(
            1L, "Odd 2026-27", "2026-2027", LocalDate.of(2026, 9, 7), LocalDate.of(2026, 10, 30), true);

    private static List<SyllabusTopicDto> topics(int n) {
        return java.util.stream.IntStream.rangeClosed(1, n)
                .mapToObj(i -> new SyllabusTopicDto((long) i, 1L, 1, "Unit 1", "Topic " + i, 0, i)).toList();
    }

    /** Today is Mon 5 Oct: 4 weeks gone. Lectures Mon/Wed/Fri → 12 of the term's 24 timetabled lectures are past. */
    private CourseProgressDto progress(AcademicTermDto term, List<SyllabusTopicDto> syllabus, Set<Long> covered, List<String> lectureDays) {
        pacingService.setClock(java.time.Clock.fixed(java.time.Instant.parse("2026-10-05T06:00:00Z"), java.time.ZoneId.of("Asia/Kolkata")));
        when(academicsService.getOfferingById(1L)).thenReturn(OFFERING);
        when(academicsService.getActiveTerm()).thenReturn(term);
        when(academicsService.getSyllabusForCourse(1L)).thenReturn(syllabus);
        lenient().when(academicsService.getLectureDaysByOffering()).thenReturn(java.util.Map.of(1L, lectureDays));
        when(sessionRepository.findCoveredTopicIdsForOffering(1L)).thenReturn(covered);
        return pacingService.calculateProgressForOffering(1L, null, true);
    }

    private static Set<Long> first(int n) {
        return java.util.stream.LongStream.rangeClosed(1, n).boxed().collect(java.util.stream.Collectors.toSet());
    }

    /** Expected topics come from the uploaded timetable and the semester dates, not from invented weeks. */
    @Test
    void paceIsMeasuredAgainstTheTimetabledLecturesAlreadyPast() {
        List<String> mwf = List.of("MONDAY", "WEDNESDAY", "FRIDAY");
        CourseProgressDto behind = progress(TERM, topics(10), first(2), mwf);
        assertEquals(5, behind.expectedTopicsByCurrentWeek());   // 10 topics × 12/24 lectures past
        assertEquals(-3, behind.topicDelta());
        assertEquals("BEHIND", behind.pacingStatus());
        assertEquals("Behind by 3 topics (2 of 10 covered; 12 of 24 timetabled lectures are past)", behind.pacingLabel());
        assertEquals(5, behind.currentWeek());

        assertEquals("ON_TRACK", progress(TERM, topics(10), first(5), mwf).pacingStatus());
        assertEquals("ON_TRACK", progress(TERM, topics(10), first(6), mwf).pacingStatus());   // one topic of rounding slack
        CourseProgressDto ahead = progress(TERM, topics(10), first(7), mwf);
        assertEquals("AHEAD", ahead.pacingStatus());
        assertTrue(ahead.pacingLabel().startsWith("Ahead by 2 topics"), ahead.pacingLabel());
    }

    /** A holiday's lectures are not owed: Mon 14 Sep (past) and Fri 23 Oct (to come) drop out of 24. */
    @Test
    void holidaysAreNotCountedAsTimetabledLectures() {
        AcademicTermDto withHolidays = new AcademicTermDto(1L, "Odd 2026-27", "2026-2027", TERM.startDate(), TERM.endDate(), true, 3,
                List.of(new com.iet.dashboard.academics.web.AcademicTermDto.Holiday(LocalDate.of(2026, 9, 14), "Hindi Diwas"), new com.iet.dashboard.academics.web.AcademicTermDto.Holiday(LocalDate.of(2026, 10, 23), "Dussehra")));
        CourseProgressDto p = progress(withHolidays, topics(10), first(5), List.of("MONDAY", "WEDNESDAY", "FRIDAY"));
        assertEquals(5, p.expectedTopicsByCurrentWeek());   // 10 × 11/22
        assertTrue(p.pacingLabel().endsWith("(5 of 10 covered; 11 of 22 timetabled lectures are past)"), p.pacingLabel());
    }

    /** Without the facts pace needs, the status says what is missing instead of a green "on track". */
    @Test
    void saysWhatIsMissingInsteadOfGuessing() {
        CourseProgressDto noSyllabus = progress(TERM, List.of(), Set.of(), List.of("MONDAY"));
        assertEquals("NO_SYLLABUS", noSyllabus.pacingStatus());
        assertEquals("No syllabus uploaded", noSyllabus.pacingLabel());

        CourseProgressDto noLectures = progress(TERM, topics(10), first(2), List.of());
        assertEquals("NOT_MEASURED", noLectures.pacingStatus());
        assertEquals("No lectures for this section in the timetable", noLectures.pacingLabel());

        AcademicTermDto later = new AcademicTermDto(1L, "Odd", "2026-2027", LocalDate.of(2026, 11, 2), LocalDate.of(2026, 12, 18), true);
        CourseProgressDto notStarted = progress(later, topics(10), Set.of(), List.of("MONDAY"));
        assertEquals("NOT_MEASURED", notStarted.pacingStatus());
        assertEquals("The semester starts on 2026-11-02", notStarted.pacingLabel());

        CourseProgressDto noTerm = progress(null, topics(10), Set.of(), List.of("MONDAY"));
        assertEquals("NOT_MEASURED", noTerm.pacingStatus());
        assertEquals("Set the semester dates to measure pace", noTerm.pacingLabel());
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
