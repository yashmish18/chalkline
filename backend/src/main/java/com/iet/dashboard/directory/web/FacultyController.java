package com.iet.dashboard.directory.web;

import com.iet.dashboard.directory.service.FacultyService;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.List;

import com.iet.dashboard.identity.service.UserService;
import com.iet.dashboard.identity.web.UserDto;
import com.iet.dashboard.tracking.service.TrackingService;
import com.iet.dashboard.tracking.web.FacultyProfileDto;

@RestController
@RequestMapping("/api")
public class FacultyController {

    private final FacultyService facultyService;
    private final UserService userService;
    private final TrackingService trackingService;
    private final com.iet.dashboard.identity.config.AppSettings settings;

    public FacultyController(
            FacultyService facultyService,
            UserService userService,
            TrackingService trackingService,
            com.iet.dashboard.identity.config.AppSettings settings
    ) {
        this.facultyService = facultyService;
        this.userService = userService;
        this.trackingService = trackingService;
        this.settings = settings;
    }

    @GetMapping("/faculty")
    public ResponseEntity<List<FacultyDto>> getFaculty(
            @RequestParam(required = false) String skill,
            @RequestParam(required = false) Long departmentId) {
        return ResponseEntity.ok(facultyService.getAllFaculty(skill, departmentId));
    }

    @GetMapping("/departments")
    public ResponseEntity<List<DepartmentDto>> getDepartments() {
        return ResponseEntity.ok(facultyService.getAllDepartments());
    }

    @GetMapping("/faculty/me")
    public ResponseEntity<FacultyDto> getCurrentFaculty() {
        UserDto currentUser = userService.getCurrentUser();
        if (currentUser == null) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED).build();
        }
        return facultyService.getFacultyByUserId(currentUser.id())
                .map(ResponseEntity::ok)
                .orElseGet(() -> ResponseEntity.notFound().build());
    }

    @GetMapping("/faculty/me/profile")
    public ResponseEntity<FacultyProfileDto> getCurrentFacultyProfile() {
        UserDto currentUser = userService.getCurrentUser();
        if (currentUser == null) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED).build();
        }
        return facultyService.getFacultyByUserId(currentUser.id())
                .map(f -> ResponseEntity.ok(trackingService.getFacultyProfile(f.id())))
                .orElseGet(() -> ResponseEntity.notFound().build());
    }

    @GetMapping("/faculty/{id}")
    public ResponseEntity<FacultyDto> getFacultyById(@PathVariable Long id) {
        return ResponseEntity.ok(facultyService.getFacultyById(id));
    }

    @GetMapping("/skills")
    public ResponseEntity<List<SkillDto>> getSkills() {
        return ResponseEntity.ok(facultyService.getAllSkills());
    }

    @PostMapping("/faculty")
    @PreAuthorize("hasRole('COORDINATOR')")
    public ResponseEntity<FacultyDto> createFaculty(@Valid @RequestBody CreateFacultyRequest request) {
        FacultyDto created = facultyService.createFaculty(request);
        return ResponseEntity.status(HttpStatus.CREATED).body(created);
    }

    @PutMapping("/faculty/{id}/skills")
    public ResponseEntity<FacultyDto> updateSkills(
            @PathVariable Long id,
            @Valid @RequestBody UpdateFacultySkillsRequest request
    ) {
        FacultyDto updated = facultyService.updateFacultySkills(id, request.skillIds());
        return ResponseEntity.ok(updated);
    }

    @PutMapping("/faculty/{id}/skills-by-name")
    public ResponseEntity<FacultyDto> updateSkillsByName(
            @PathVariable Long id,
            @Valid @RequestBody UpdateFacultySkillsByNameRequest request
    ) {
        FacultyDto updated = facultyService.updateFacultySkillsByNames(id, request.skills());
        return ResponseEntity.ok(updated);
    }

    @DeleteMapping("/faculty/{id}")
    @PreAuthorize("hasRole('COORDINATOR')")
    public ResponseEntity<Void> deleteFaculty(@PathVariable Long id) {
        facultyService.deleteFaculty(id);
        return ResponseEntity.noContent().build();
    }

    @DeleteMapping("/faculty")
    @PreAuthorize("hasRole('COORDINATOR')")
    public ResponseEntity<BulkDeleteResult> deleteAllFaculty() {
        com.iet.dashboard.shared.admin.DatabaseWipeController.requireDestructiveActions(settings);
        return ResponseEntity.ok(new BulkDeleteResult(facultyService.deleteAllFaculty()));
    }

    public record BulkDeleteResult(int deleted) {
    }
}
