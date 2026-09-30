package com.iet.dashboard.academics.service;

import com.iet.dashboard.academics.domain.Course;
import com.iet.dashboard.academics.domain.CourseComponent;
import com.iet.dashboard.academics.domain.CourseOffering;
import com.iet.dashboard.academics.domain.TeachingAssignment;
import com.iet.dashboard.academics.repo.CourseOfferingRepository;
import com.iet.dashboard.academics.repo.CourseRepository;
import com.iet.dashboard.academics.repo.TeachingAssignmentRepository;
import com.iet.dashboard.academics.web.CourseDto;
import com.iet.dashboard.academics.web.CreateCourseRequest;
import com.iet.dashboard.academics.web.CreateTeachingAssignmentRequest;
import com.iet.dashboard.academics.web.TeachingAssignmentDto;
import com.iet.dashboard.directory.service.FacultyService;
import com.iet.dashboard.directory.web.FacultyDto;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.Collections;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class AcademicsServiceTest {

    @Mock
    private CourseRepository courseRepository;

    @Mock
    private CourseOfferingRepository offeringRepository;

    @Mock
    private TeachingAssignmentRepository assignmentRepository;

    @Mock
    private FacultyService facultyService;

    @Mock
    private com.iet.dashboard.academics.repo.AcademicTermRepository termRepository;

    @Mock
    private com.iet.dashboard.academics.repo.SyllabusTopicRepository syllabusRepository;

    @Mock
    private com.iet.dashboard.academics.repo.TimetableSlotRepository timetableSlotRepository;

    @InjectMocks
    private AcademicsServiceImpl academicsService;

    @Test
    void testCreateCourse_Success() {
        CreateCourseRequest request = new CreateCourseRequest("CS601", "Cloud Computing", "THEORY");
        when(courseRepository.existsByCodeIgnoreCase("CS601")).thenReturn(false);

        Course saved = new Course("CS601", "Cloud Computing", "THEORY");
        saved.setId(10L);
        when(courseRepository.save(any(Course.class))).thenReturn(saved);

        CourseDto result = academicsService.createCourse(request);

        assertNotNull(result);
        assertEquals("CS601", result.code());
    }

    @Test
    void testCreateCourse_DuplicateCode_ThrowsException() {
        CreateCourseRequest request = new CreateCourseRequest("CS601", "Cloud Computing", "THEORY");
        when(courseRepository.existsByCodeIgnoreCase("CS601")).thenReturn(true);

        assertThrows(IllegalArgumentException.class, () -> academicsService.createCourse(request));
    }

    @Test
    void testCreateTeachingAssignment_Success() {
        CreateTeachingAssignmentRequest request = new CreateTeachingAssignmentRequest(1L, 2L, "THEORY");

        Course course = new Course("CS501", "Networks", "THEORY");
        CourseOffering offering = new CourseOffering(course, "2026-2027", 3, 5, "A");
        offering.setId(1L);

        FacultyDto faculty = new FacultyDto(2L, 2L, "Dr. Amit", "Assoc Prof", "PhD", "IT", Collections.emptyList());

        when(offeringRepository.findById(1L)).thenReturn(Optional.of(offering));
        when(facultyService.getFacultyById(2L)).thenReturn(faculty);

        TeachingAssignment saved = new TeachingAssignment(offering, 2L, CourseComponent.THEORY);
        saved.setId(100L);
        when(assignmentRepository.save(any(TeachingAssignment.class))).thenReturn(saved);

        TeachingAssignmentDto result = academicsService.createTeachingAssignment(request);

        assertNotNull(result);
        assertEquals(100L, result.id());
        assertEquals("Dr. Amit", result.facultyName());
        assertEquals("THEORY", result.component());
    }

    @Test
    void testGetActiveTerm() {
        com.iet.dashboard.academics.domain.AcademicTerm term = new com.iet.dashboard.academics.domain.AcademicTerm(
                "Odd 2026-27", "2026-2027", java.time.LocalDate.of(2026, 7, 15), java.time.LocalDate.of(2026, 12, 15), true
        );
        term.setId(1L);
        when(termRepository.findByActiveTrue()).thenReturn(Optional.of(term));

        com.iet.dashboard.academics.web.AcademicTermDto result = academicsService.getActiveTerm();
        assertNotNull(result);
        assertEquals("Odd 2026-27", result.name());
        assertTrue(result.active());
    }

    @Test
    void testGetSyllabusForCourse() {
        Course course = new Course("CS501", "Networks", "THEORY");
        course.setId(1L);
        com.iet.dashboard.academics.domain.SyllabusTopic topic = new com.iet.dashboard.academics.domain.SyllabusTopic(
                course, 1, "Unit 1", "OSI Model", 1, 1
        );
        topic.setId(10L);
        when(syllabusRepository.findByCourseIdOrderByLectureOrderAsc(1L)).thenReturn(List.of(topic));

        List<com.iet.dashboard.academics.web.SyllabusTopicDto> topics = academicsService.getSyllabusForCourse(1L);
        assertEquals(1, topics.size());
        assertEquals("OSI Model", topics.get(0).topicName());
    }

    @Test
    void testAssertFacultyAssignedToOffering_SuccessAndForbidden() {
        Course course = new Course("CS501", "Networks", "THEORY");
        CourseOffering offering = new CourseOffering(course, "2026-2027", 3, 5, "A");
        TeachingAssignment assignment = new TeachingAssignment(offering, 2L, CourseComponent.THEORY);

        when(assignmentRepository.findByOfferingId(1L)).thenReturn(List.of(assignment));

        // Faculty #2 is assigned -> should not throw
        assertDoesNotThrow(() -> academicsService.assertFacultyAssignedToOffering(2L, 1L));

        // Faculty #99 is NOT assigned -> throws ForbiddenException
        assertThrows(com.iet.dashboard.shared.error.ForbiddenException.class,
                () -> academicsService.assertFacultyAssignedToOffering(99L, 1L));
    }

    @Test
    void testGetTimetableForFaculty() {
        Course course = new Course("CS501", "Networks", "THEORY", 4, "3-0-2", 3, 0, 2, "Networking course");
        CourseOffering offering = new CourseOffering(course, "2026-2027", 3, 5, "A");
        offering.setId(1L);

        com.iet.dashboard.academics.domain.TimetableSlot slot = new com.iet.dashboard.academics.domain.TimetableSlot(
                offering, 2L, "MONDAY", java.time.LocalTime.of(10, 0), java.time.LocalTime.of(11, 0),
                "Room 304", "LECTURE", "ALL"
        );
        slot.setId(10L);

        when(timetableSlotRepository.findByFacultyIdWithDetails(2L)).thenReturn(List.of(slot));

        List<com.iet.dashboard.academics.web.TimetableSlotDto> result = academicsService.getTimetableForFaculty(2L);
        assertEquals(1, result.size());
        assertEquals("CS501", result.get(0).courseCode());
        assertEquals("3-0-2", result.get(0).ltp());
        assertEquals("MONDAY", result.get(0).dayOfWeek());
        assertEquals("Room 304", result.get(0).room());
    }

    @Test
    void testDeleteTeachingAssignment_Success() {
        when(assignmentRepository.existsById(50L)).thenReturn(true);
        academicsService.deleteTeachingAssignment(50L);
        verify(assignmentRepository).deleteById(50L);
    }

    @Test
    void testDeleteCourse_Success() {
        when(courseRepository.existsById(10L)).thenReturn(true);
        academicsService.deleteCourse(10L);
        verify(courseRepository).deleteById(10L);
    }
}
