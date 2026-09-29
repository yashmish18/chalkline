package com.iet.dashboard.academics.web;

import com.iet.dashboard.academics.service.AcademicsService;
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

    public AcademicsController(AcademicsService academicsService) {
        this.academicsService = academicsService;
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
}
