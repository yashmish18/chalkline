package com.iet.dashboard.shared.admin;

import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;

/**
 * Coordinator-only data management endpoints.
 */
@RestController
@RequestMapping("/api/admin")
public class DatabaseWipeController {

    private final DatabaseWipeService wipeService;
    private final com.iet.dashboard.identity.config.AppSettings settings;

    public DatabaseWipeController(DatabaseWipeService wipeService, com.iet.dashboard.identity.config.AppSettings settings) {
        this.wipeService = wipeService;
        this.settings = settings;
    }

    /**
     * Empties the database and the uploaded files. Keeps only the coordinator login and profile so
     * someone can still sign in; deletes everything else including faculty sign-in accounts.
     */
    @DeleteMapping("/data")
    @PreAuthorize("hasRole('COORDINATOR')")
    public ResponseEntity<Map<String, Integer>> wipe() {
        requireDestructiveActions(settings);
        return ResponseEntity.ok(wipeService.wipeAll());
    }

    /** Bulk deletes exist for dev and testing; on a production server they answer 404. */
    public static void requireDestructiveActions(com.iet.dashboard.identity.config.AppSettings settings) {
        if (!settings.destructiveActions()) {
            throw new com.iet.dashboard.shared.error.ResourceNotFoundException("Not found");
        }
    }
}
