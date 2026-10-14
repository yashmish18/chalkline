package com.iet.dashboard.identity.service;

import com.iet.dashboard.directory.domain.Faculty;
import com.iet.dashboard.directory.repo.FacultyRepository;
import com.iet.dashboard.identity.domain.AppUser;
import com.iet.dashboard.identity.domain.Role;
import com.iet.dashboard.identity.repo.AppUserRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.util.Arrays;
import java.util.List;
import java.util.Locale;

/**
 * Makes the people named in {@code APP_ADMIN_EMAIL} (comma-separated) the admin: an active
 * coordinator account with a faculty profile, flagged so that every session also asks for the admin
 * password (chosen at their first sign-in). Production sign-in only admits existing accounts, so
 * the admin has to come from configuration; everyone else comes from the allocation sheet the
 * admin uploads.
 *
 * <p>Idempotent: an existing account is promoted, never duplicated. With
 * {@code APP_ADMIN_RESET_PASSWORD=true} the admin password is cleared, so the next sign-in sets a new one.
 */
@Component
public class AdminBootstrap implements ApplicationRunner {

    private static final Logger log = LoggerFactory.getLogger(AdminBootstrap.class);

    private final AppUserRepository users;
    private final FacultyRepository faculty;
    private final List<String> emails;
    private final String name;
    private final boolean resetPassword;

    @Autowired
    public AdminBootstrap(AppUserRepository users, FacultyRepository faculty,
                          @Value("${app.admin-account.email:}") String email,
                          @Value("${app.coordinator.name:Coordinator}") String name,
                          @Value("${app.admin-account.reset-password:false}") boolean resetPassword) {
        this.users = users;
        this.faculty = faculty;
        this.emails = Arrays.stream(email == null ? new String[0] : email.split(","))
                .map(e -> e.trim().toLowerCase(Locale.ROOT))
                .filter(e -> !e.isEmpty())
                .distinct()
                .toList();
        this.name = name;
        this.resetPassword = resetPassword;
    }

    public AdminBootstrap(AppUserRepository users, FacultyRepository faculty, String email, String name) {
        this(users, faculty, email, name, false);
    }

    @Override
    @Transactional
    public void run(ApplicationArguments args) {
        for (String email : emails) {
            ensureAdmin(email);
        }
    }

    private void ensureAdmin(String email) {
        AppUser user = users.findByEmailIgnoreCase(email).orElseGet(() -> users.save(new AppUser(email, name, Role.COORDINATOR)));
        user.setRole(Role.COORDINATOR);
        user.setActive(true);
        user.setAdmin(true);
        if (resetPassword && user.getCoordinatorPasswordHash() != null) {
            user.setCoordinatorPasswordHash(null);
            user.setCoordinatorFailedAttempts(0);
            user.setCoordinatorLockedUntil(null);
            log.warn("Admin password cleared for {}; they choose a new one at next sign-in. Set APP_ADMIN_RESET_PASSWORD=false.", email);
        }
        user = users.save(user);
        if (faculty.findByUserId(user.getId()).isEmpty()) {
            faculty.save(new Faculty(user.getId(), user.getName(), "Coordinator", "", null));
        }
        log.info("Admin account ready for {}{}", email,
                user.getCoordinatorPasswordHash() == null ? " (admin password is set at first sign-in)" : "");
    }
}
