package com.iet.dashboard.deadlines.service;

import com.iet.dashboard.academics.service.AcademicsService;
import com.iet.dashboard.academics.web.CourseDto;
import com.iet.dashboard.academics.web.CourseOfferingDto;
import com.iet.dashboard.academics.web.TeachingAssignmentDto;
import com.iet.dashboard.deadlines.domain.Deadline;
import com.iet.dashboard.deadlines.domain.DeadlineAssignment;
import com.iet.dashboard.deadlines.domain.DeadlineType;
import com.iet.dashboard.deadlines.repo.DeadlineAssignmentRepository;
import com.iet.dashboard.deadlines.repo.DeadlineRepository;
import com.iet.dashboard.deadlines.web.CreateDeadlineRequest;
import com.iet.dashboard.deadlines.web.DeadlineAssignmentDto;
import com.iet.dashboard.deadlines.web.DeadlineDto;
import com.iet.dashboard.directory.service.FacultyService;
import com.iet.dashboard.directory.web.FacultyDto;
import com.iet.dashboard.identity.service.UserService;
import com.iet.dashboard.identity.web.UserDto;
import com.iet.dashboard.shared.error.ResourceNotFoundException;
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
class DeadlineServiceTest {

    @Mock
    private DeadlineRepository deadlineRepository;

    @Mock
    private DeadlineAssignmentRepository assignmentRepository;

    @Mock
    private FacultyService facultyService;

    @Mock
    private AcademicsService academicsService;

    @Mock
    private UserService userService;

    @InjectMocks
    private DeadlineServiceImpl deadlineService;

    @Test
    void testCreateDeadline_AllFaculty_Success() {
        Instant dueAt = Instant.now().plus(7, ChronoUnit.DAYS);
        CreateDeadlineRequest request = new CreateDeadlineRequest(
                "Course Plan Submission", "Upload complete course plan", "COURSE_FILE",
                dueAt, "ALL_FACULTY", null, null, null
        );

        when(userService.getCurrentUser()).thenReturn(new UserDto(1L, "coord@iet.edu", "Dr. Coord", "COORDINATOR"));

        Deadline savedDeadline = new Deadline("Course Plan Submission", "Upload complete course plan",
                DeadlineType.COURSE_FILE, dueAt, 1L);
        savedDeadline.setId(10L);
        when(deadlineRepository.save(any(Deadline.class))).thenReturn(savedDeadline);

        FacultyDto f1 = new FacultyDto(1L, 2L, "Dr. Amit", "Assoc Prof", "PhD", "IT", Collections.emptyList());
        FacultyDto f2 = new FacultyDto(2L, 3L, "Prof. Priya", "Asst Prof", "MTech", "IT", Collections.emptyList());
        when(facultyService.getAllFaculty(null)).thenReturn(List.of(f1, f2));

        DeadlineDto result = deadlineService.createDeadline(request);

        assertNotNull(result);
        assertEquals(10L, result.id());
        assertEquals("Course Plan Submission", result.title());
        assertEquals(2, result.totalAssignmentsCount());
        verify(assignmentRepository).saveAll(any());
    }

    @Test
    void testCreateDeadline_BySemester_Success() {
        Instant dueAt = Instant.now().plus(5, ChronoUnit.DAYS);
        CreateDeadlineRequest request = new CreateDeadlineRequest(
                "Lab Manual Submission", "Upload lab manual", "COURSE_FILE",
                dueAt, "BY_SEMESTER", null, 5, null
        );

        when(userService.getCurrentUser()).thenReturn(new UserDto(1L, "coord@iet.edu", "Dr. Coord", "COORDINATOR"));

        Deadline saved = new Deadline("Lab Manual Submission", "Upload lab manual", DeadlineType.COURSE_FILE, dueAt, 1L);
        saved.setId(20L);
        when(deadlineRepository.save(any(Deadline.class))).thenReturn(saved);

        CourseDto course = new CourseDto(1L, "CS503", "Web Lab", "LAB");
        CourseOfferingDto offering = new CourseOfferingDto(1L, course, "2026-2027", 3, 5, "A");
        TeachingAssignmentDto ta = new TeachingAssignmentDto(1L, offering, 3L, "Prof. Priya", "LAB");

        when(academicsService.getAllTeachingAssignments()).thenReturn(List.of(ta));

        DeadlineDto result = deadlineService.createDeadline(request);

        assertNotNull(result);
        assertEquals(20L, result.id());
        assertEquals(1, result.totalAssignmentsCount());
    }

    @Test
    void testGetMyDeadlines_ReturnsScopedAssignments() {
        when(userService.getCurrentUser()).thenReturn(new UserDto(2L, "amit@iet.edu", "Dr. Amit", "FACULTY"));
        FacultyDto faculty = new FacultyDto(1L, 2L, "Dr. Amit", "Assoc Prof", "PhD", "IT", Collections.emptyList());
        when(facultyService.getFacultyByUserId(2L)).thenReturn(Optional.of(faculty));

        Deadline deadline = new Deadline("Course File", "Submit file", DeadlineType.COURSE_FILE,
                Instant.now().plus(2, ChronoUnit.DAYS), 1L);
        deadline.setId(100L);
        DeadlineAssignment da = new DeadlineAssignment(deadline, 1L, 1L);
        da.setId(50L);

        when(assignmentRepository.findByFacultyIdWithDeadline(1L)).thenReturn(List.of(da));

        List<DeadlineAssignmentDto> result = deadlineService.getMyDeadlines();

        assertEquals(1, result.size());
        assertEquals(50L, result.get(0).id());
        assertEquals("Course File", result.get(0).title());
        assertEquals("Dr. Amit", result.get(0).facultyName());
    }

    @Test
    void testGetDeadlineById_NotFound_ThrowsException() {
        when(deadlineRepository.findById(999L)).thenReturn(Optional.empty());

        assertThrows(ResourceNotFoundException.class, () -> deadlineService.getDeadlineById(999L));
    }
}
