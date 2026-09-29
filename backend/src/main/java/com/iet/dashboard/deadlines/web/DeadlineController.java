package com.iet.dashboard.deadlines.web;

import com.iet.dashboard.deadlines.service.DeadlineService;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api")
public class DeadlineController {

    private final DeadlineService deadlineService;

    public DeadlineController(DeadlineService deadlineService) {
        this.deadlineService = deadlineService;
    }

    @GetMapping("/deadlines")
    public ResponseEntity<List<DeadlineDto>> getDeadlines() {
        return ResponseEntity.ok(deadlineService.getAllDeadlines());
    }

    @GetMapping("/deadlines/{id}")
    public ResponseEntity<DeadlineDto> getDeadlineById(@PathVariable Long id) {
        return ResponseEntity.ok(deadlineService.getDeadlineById(id));
    }

    @GetMapping("/deadlines/{id}/assignments")
    public ResponseEntity<List<DeadlineAssignmentDto>> getAssignmentsForDeadline(@PathVariable Long id) {
        return ResponseEntity.ok(deadlineService.getAssignmentsForDeadline(id));
    }

    @GetMapping("/my-deadlines")
    public ResponseEntity<List<DeadlineAssignmentDto>> getMyDeadlines() {
        return ResponseEntity.ok(deadlineService.getMyDeadlines());
    }

    @PostMapping("/deadlines")
    @PreAuthorize("hasRole('COORDINATOR')")
    public ResponseEntity<DeadlineDto> createDeadline(@Valid @RequestBody CreateDeadlineRequest request) {
        DeadlineDto created = deadlineService.createDeadline(request);
        return ResponseEntity.status(HttpStatus.CREATED).body(created);
    }
}
