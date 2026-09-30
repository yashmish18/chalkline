package com.iet.dashboard.teaching.web;

import com.iet.dashboard.directory.service.FacultyService;
import com.iet.dashboard.directory.web.FacultyDto;
import com.iet.dashboard.identity.service.UserService;
import com.iet.dashboard.identity.web.UserDto;
import com.iet.dashboard.shared.error.ForbiddenException;
import com.iet.dashboard.teaching.service.TeachingSessionService;
import jakarta.validation.Valid;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDate;
import java.util.List;

@RestController
@RequestMapping("/api/teaching-sessions")
public class TeachingSessionController {

    private final TeachingSessionService teachingSessionService;
    private final UserService userService;
    private final FacultyService facultyService;

    public TeachingSessionController(
            TeachingSessionService teachingSessionService,
            UserService userService,
            FacultyService facultyService
    ) {
        this.teachingSessionService = teachingSessionService;
        this.userService = userService;
        this.facultyService = facultyService;
    }

    @PostMapping
    @PreAuthorize("hasAnyRole('FACULTY', 'COORDINATOR')")
    public ResponseEntity<TeachingSessionDto> logSession(@Valid @RequestBody LogSessionRequest request) {
        UserDto currentUser = userService.getCurrentUser();
        Long facultyId = resolveFacultyId(currentUser);
        return ResponseEntity.ok(teachingSessionService.logSession(request, facultyId));
    }

    @GetMapping("/today")
    @PreAuthorize("hasAnyRole('FACULTY', 'COORDINATOR')")
    public ResponseEntity<List<TodayClassDto>> getTodayClasses(
            @RequestParam(value = "date", required = false)
            @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate date
    ) {
        UserDto currentUser = userService.getCurrentUser();
        Long facultyId = resolveFacultyId(currentUser);
        LocalDate targetDate = date != null ? date : LocalDate.now();
        return ResponseEntity.ok(teachingSessionService.getTodayClassesForFaculty(facultyId, targetDate));
    }

    @GetMapping("/offering/{offeringId}")
    @PreAuthorize("hasAnyRole('FACULTY', 'COORDINATOR')")
    public ResponseEntity<List<TeachingSessionDto>> getOfferingSessions(@PathVariable Long offeringId) {
        UserDto currentUser = userService.getCurrentUser();
        boolean isCoordinator = "COORDINATOR".equals(currentUser.role());
        Long facultyId = isCoordinator ? null : resolveFacultyId(currentUser);
        return ResponseEntity.ok(teachingSessionService.getSessionsForOffering(offeringId, facultyId, isCoordinator));
    }

    @DeleteMapping("/{id}")
    @PreAuthorize("hasAnyRole('FACULTY', 'COORDINATOR')")
    public ResponseEntity<Void> deleteSession(@PathVariable Long id) {
        UserDto currentUser = userService.getCurrentUser();
        boolean isCoordinator = "COORDINATOR".equals(currentUser.role());
        Long facultyId = resolveFacultyId(currentUser);
        teachingSessionService.deleteSession(id, facultyId, isCoordinator);
        return ResponseEntity.noContent().build();
    }

    private Long resolveFacultyId(UserDto currentUser) {
        return facultyService.getFacultyByUserId(currentUser.id())
                .map(FacultyDto::id)
                .orElseGet(() -> {
                    if ("COORDINATOR".equals(currentUser.role())) {
                        return 1L; // Coordinator fallback to faculty ID 1
                    }
                    throw new ForbiddenException("Faculty profile not linked to user account: " + currentUser.email());
                });
    }
}
