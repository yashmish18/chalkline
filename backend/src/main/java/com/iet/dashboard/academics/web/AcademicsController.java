package com.iet.dashboard.academics.web;

import com.iet.dashboard.academics.service.AcademicsService;
import com.iet.dashboard.academics.service.PacingCalculationService;
import com.iet.dashboard.directory.service.FacultyService;
import com.iet.dashboard.directory.web.FacultyDto;
import com.iet.dashboard.identity.service.UserService;
import com.iet.dashboard.identity.web.UserDto;
import com.iet.dashboard.shared.error.ForbiddenException;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api")
public class AcademicsController {

    private final AcademicsService academicsService;
    private final PacingCalculationService pacingService;
    private final UserService userService;
    private final FacultyService facultyService;

    public AcademicsController(
            AcademicsService academicsService,
            PacingCalculationService pacingService,
            UserService userService,
            FacultyService facultyService
    ) {
        this.academicsService = academicsService;
        this.pacingService = pacingService;
        this.userService = userService;
        this.facultyService = facultyService;
    }

    @GetMapping("/courses")
    public ResponseEntity<List<CourseDto>> getCourses() {
        return ResponseEntity.ok(academicsService.getAllCourses());
    }

    @PostMapping("/courses")
    @PreAuthorize("hasRole('COORDINATOR')")
    public ResponseEntity<CourseDto> createCourse(@Valid @RequestBody CreateCourseRequest request) {
        return ResponseEntity.status(HttpStatus.CREATED).body(academicsService.createCourse(request));
    }

    @DeleteMapping("/courses/{id}")
    @PreAuthorize("hasRole('COORDINATOR')")
    public ResponseEntity<Void> deleteCourse(@PathVariable Long id) {
        academicsService.deleteCourse(id);
        return ResponseEntity.noContent().build();
    }

    @GetMapping("/offerings")
    public ResponseEntity<List<CourseOfferingDto>> getOfferings(@RequestParam(required = false) Integer semester) {
        return ResponseEntity.ok(academicsService.getAllOfferings(semester));
    }

    @GetMapping("/teaching-assignments")
    public ResponseEntity<List<TeachingAssignmentDto>> getAllAssignments() {
        return ResponseEntity.ok(academicsService.getAllTeachingAssignments());
    }

    @GetMapping("/faculty/{facultyId}/teaching-assignments")
    public ResponseEntity<List<TeachingAssignmentDto>> getAssignmentsForFaculty(@PathVariable Long facultyId) {
        return ResponseEntity.ok(academicsService.getTeachingAssignmentsForFaculty(facultyId));
    }

    @PostMapping("/teaching-assignments")
    @PreAuthorize("hasRole('COORDINATOR')")
    public ResponseEntity<TeachingAssignmentDto> createAssignment(@Valid @RequestBody CreateTeachingAssignmentRequest request) {
        return ResponseEntity.status(HttpStatus.CREATED).body(academicsService.createTeachingAssignment(request));
    }

    @DeleteMapping("/teaching-assignments/{id}")
    @PreAuthorize("hasRole('COORDINATOR')")
    public ResponseEntity<Void> deleteAssignment(@PathVariable Long id) {
        academicsService.deleteTeachingAssignment(id);
        return ResponseEntity.noContent().build();
    }

    @GetMapping("/terms")
    public ResponseEntity<List<AcademicTermDto>> getTerms() {
        return ResponseEntity.ok(academicsService.getAllTerms());
    }

    @GetMapping("/terms/active")
    public ResponseEntity<AcademicTermDto> getActiveTerm() {
        return ResponseEntity.ok(academicsService.getActiveTerm());
    }

    @PostMapping("/terms/{id}/activate")
    @PreAuthorize("hasRole('COORDINATOR')")
    public ResponseEntity<AcademicTermDto> activateTerm(@PathVariable Long id) {
        return ResponseEntity.ok(academicsService.setActiveTerm(id));
    }

    @GetMapping("/courses/{courseId}/syllabus")
    public ResponseEntity<List<SyllabusTopicDto>> getCourseSyllabus(@PathVariable Long courseId) {
        return ResponseEntity.ok(academicsService.getSyllabusForCourse(courseId));
    }

    @GetMapping("/academics/my-courses")
    @PreAuthorize("hasAnyRole('FACULTY', 'COORDINATOR')")
    public ResponseEntity<List<CourseProgressDto>> getMyCourses(
            @RequestParam(value = "facultyId", required = false) Long queryFacultyId
    ) {
        UserDto currentUser = userService.getCurrentUser();
        Long targetFacultyId;
        if ("COORDINATOR".equals(currentUser.role()) && queryFacultyId != null) {
            targetFacultyId = queryFacultyId;
        } else {
            targetFacultyId = resolveFacultyId(currentUser);
        }
        return ResponseEntity.ok(pacingService.getMyCoursesProgress(targetFacultyId));
    }

    @GetMapping("/academics/offerings/{offeringId}/progress")
    @PreAuthorize("hasAnyRole('FACULTY', 'COORDINATOR')")
    public ResponseEntity<CourseProgressDto> getOfferingProgress(@PathVariable Long offeringId) {
        UserDto currentUser = userService.getCurrentUser();
        boolean isCoordinator = "COORDINATOR".equals(currentUser.role());
        Long facultyId = isCoordinator ? null : resolveFacultyId(currentUser);
        return ResponseEntity.ok(pacingService.calculateProgressForOffering(offeringId, facultyId, isCoordinator));
    }

    @GetMapping("/academics/timetable")
    @PreAuthorize("hasAnyRole('FACULTY', 'COORDINATOR')")
    public ResponseEntity<List<TimetableSlotDto>> getTimetable(
            @RequestParam(value = "facultyId", required = false) Long queryFacultyId
    ) {
        UserDto currentUser = userService.getCurrentUser();
        Long targetFacultyId;
        if ("COORDINATOR".equals(currentUser.role()) && queryFacultyId != null) {
            targetFacultyId = queryFacultyId;
        } else {
            targetFacultyId = resolveFacultyId(currentUser);
        }
        return ResponseEntity.ok(academicsService.getTimetableForFaculty(targetFacultyId));
    }

    private Long resolveFacultyId(UserDto currentUser) {
        return facultyService.getFacultyByUserId(currentUser.id())
                .map(FacultyDto::id)
                .orElseGet(() -> {
                    if ("COORDINATOR".equals(currentUser.role())) {
                        return 1L;
                    }
                    throw new ForbiddenException("Faculty profile not found for user: " + currentUser.email());
                });
    }
}
