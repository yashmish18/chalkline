package com.iet.dashboard.shared.admin;

import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * Operator switch for re-testing: clears the logged activity so class logs, assessments, remedial
 * plans, deadlines and notifications can be built up again from a clean slate.
 */
@RestController
@RequestMapping("/api/admin")
public class ActivityResetController {

    private final ActivityResetService resetService;
    private final com.iet.dashboard.identity.config.AppSettings settings;

    public ActivityResetController(ActivityResetService resetService, com.iet.dashboard.identity.config.AppSettings settings) {
        this.resetService = resetService;
        this.settings = settings;
    }

    @PostMapping("/reset-activity")
    @PreAuthorize("hasRole('COORDINATOR')")
    public ResponseEntity<ActivityResetService.ResetResult> reset(
            @RequestParam(name = "includeSyllabus", defaultValue = "false") boolean includeSyllabus) {
        requireDestructiveActions(settings);
        return ResponseEntity.ok(resetService.resetActivity(includeSyllabus));
    }

    /** Bulk deletes exist for dev and testing; on a production server they answer 404. */
    static void requireDestructiveActions(com.iet.dashboard.identity.config.AppSettings settings) {
        if (!settings.destructiveActions()) {
            throw new com.iet.dashboard.shared.error.ResourceNotFoundException("Not found");
        }
    }
}
