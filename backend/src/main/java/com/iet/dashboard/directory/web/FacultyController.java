package com.iet.dashboard.directory.web;

import com.iet.dashboard.directory.service.FacultyService;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api")
public class FacultyController {

    private final FacultyService facultyService;

    public FacultyController(FacultyService facultyService) {
        this.facultyService = facultyService;
    }

    @GetMapping("/faculty")
    public ResponseEntity<List<FacultyDto>> getFaculty(@RequestParam(required = false) String skill) {
        return ResponseEntity.ok(facultyService.getAllFaculty(skill));
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
}
