package com.iet.dashboard.identity.service;

import com.iet.dashboard.directory.domain.Faculty;
import com.iet.dashboard.directory.repo.FacultyRepository;
import com.iet.dashboard.identity.domain.AppUser;
import com.iet.dashboard.identity.domain.Role;
import com.iet.dashboard.identity.repo.AppUserRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.util.Arrays;
import java.util.List;
import java.util.Locale;
import org.springframework.beans.factory.annotation.Autowired;

/**
 * Makes sure the people named in {@code APP_COORDINATOR_EMAIL} (comma-separated) are coordinators.
 * Production sign-in is Microsoft Entra, which only admits existing accounts, so the very first
 * coordinator has to come from configuration (later ones can come from the allocation sheet).
 *
 * <p>The account is created or promoted, but coordinator access still needs the emailed
 * verification link and a coordinator password: until then the session stays locked. A link is
 * sent on startup only when the person has no password yet and no unexpired link.
 * Idempotent: an existing account is promoted, never duplicated.
 */
@Component
public class CoordinatorBootstrap implements ApplicationRunner {

    private static final Logger log = LoggerFactory.getLogger(CoordinatorBootstrap.class);

    private final AppUserRepository users;
    private final FacultyRepository faculty;
    private final CoordinatorAccessService access;
    private final List<String> emails;
    private final String name;

    @Autowired
    public CoordinatorBootstrap(AppUserRepository users, FacultyRepository faculty, CoordinatorAccessService access,
                                @Value("${app.coordinator.email:}") String email,
                                @Value("${app.coordinator.name:Coordinator}") String name) {
        this.users = users;
        this.faculty = faculty;
        this.access = access;
        this.emails = Arrays.stream(email == null ? new String[0] : email.split(","))
                .map(e -> e.trim().toLowerCase(Locale.ROOT))
                .filter(e -> !e.isEmpty())
                .distinct()
                .toList();
        this.name = name;
    }

    /** Without invites; for tests of the account bookkeeping alone. */
    public CoordinatorBootstrap(AppUserRepository users, FacultyRepository faculty, String email, String name) {
        this(users, faculty, null, email, name);
    }

    @Override
    @Transactional
    public void run(ApplicationArguments args) {
        for (String email : emails) {
            ensureCoordinator(email);
        }
    }

    private void ensureCoordinator(String email) {
        AppUser user = users.findByEmailIgnoreCase(email).orElseGet(() -> users.save(new AppUser(email, name, Role.COORDINATOR)));
        if (user.getRole() != Role.COORDINATOR || !user.isActive()) {
            user.setRole(Role.COORDINATOR);
            user.setActive(true);
            user = users.save(user);
        }
        if (faculty.findByUserId(user.getId()).isEmpty()) {
            faculty.save(new Faculty(user.getId(), user.getName(), "Coordinator", "", null));
        }
        if (access != null && user.getCoordinatorPasswordHash() == null && !access.hasUsableInvite(user)) {
            access.invite(user);
            log.info("Coordinator verification link emailed to {}", email);
        }
        log.info("Coordinator account ready for {}", email);
    }
}
