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
}
