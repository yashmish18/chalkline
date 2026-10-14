package com.iet.dashboard.academics.web;

import com.iet.dashboard.academics.service.AssessmentService;
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

/** Continuous assessments and remedial plans. Identity always comes from the session, never the request. */
@RestController
@RequestMapping("/api")
@PreAuthorize("hasAnyRole('FACULTY', 'COORDINATOR')")
public class AssessmentController {

    private final AssessmentService assessmentService;
    private final UserService userService;
    private final FacultyService facultyService;

    public AssessmentController(AssessmentService assessmentService, UserService userService, FacultyService facultyService) {
        this.assessmentService = assessmentService;
        this.userService = userService;
        this.facultyService = facultyService;
    }

    @GetMapping("/assessments")
    public List<AssessmentDto> assessments(@RequestParam Long offeringId) {
        return assessmentService.getAssessments(offeringId, actingFacultyId());
    }

    @PostMapping("/assessments")
    public ResponseEntity<AssessmentDto> record(@Valid @RequestBody RecordAssessmentRequest request) {
        return ResponseEntity.status(HttpStatus.CREATED).body(assessmentService.recordAssessment(request, actingFacultyId()));
    }

    @DeleteMapping("/assessments/{id}")
    public ResponseEntity<Void> delete(@PathVariable Long id) {
        assessmentService.deleteAssessment(id, actingFacultyId());
        return ResponseEntity.noContent().build();
    }

    @GetMapping("/remedial-plans")
    public List<RemedialPlanDto> remedialPlans(@RequestParam(required = false) Long offeringId) {
        return assessmentService.getRemedialPlans(offeringId, actingFacultyId());
    }

    @PostMapping("/remedial-plans")
    public ResponseEntity<RemedialPlanDto> submit(@Valid @RequestBody SubmitRemedialPlanRequest request) {
        return ResponseEntity.status(HttpStatus.CREATED).body(assessmentService.submitRemedialPlan(request, actingFacultyId()));
    }

    @PostMapping("/remedial-plans/{id}/complete")
    public RemedialPlanDto complete(@PathVariable Long id) {
        return assessmentService.completeRemedialPlan(id, actingFacultyId());
    }

    /** Null for the coordinator (may act on any section); otherwise the caller's own faculty id. */
    private Long actingFacultyId() {
        UserDto user = userService.getCurrentUser();
        if ("COORDINATOR".equals(user.role())) {
            return null;
        }
        return facultyService.getFacultyByUserId(user.id()).map(FacultyDto::id)
                .orElseThrow(() -> new ForbiddenException("No faculty profile for " + user.email()));
    }
}
