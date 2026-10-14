package com.iet.dashboard.identity.service;

import com.iet.dashboard.identity.config.AppSettings;
import com.iet.dashboard.identity.domain.AppUser;
import com.iet.dashboard.identity.domain.Role;
import com.iet.dashboard.identity.repo.AppUserRepository;
import com.iet.dashboard.shared.error.ForbiddenException;
import com.iet.dashboard.shared.error.UnauthorizedException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.nio.charset.StandardCharsets;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.Locale;

/**
 * The admin's second factor.
 *
 * <p>The admin is the account named in {@code APP_ADMIN_EMAIL}. Signing in with Microsoft proves
 * who they are; the admin password (chosen at their first sign-in, stored only as a BCrypt hash)
 * is then asked once per session before the dashboard opens. Five wrong passwords lock it for
 * fifteen minutes.
 *
 * <p>An admin session carries {@link #LOCKED_AUTHORITY} until the password is entered; that reaches
 * nothing but the sign-in endpoints and {@code /api/me}. Coordinators from the allocation sheet and
 * faculty sign in with Microsoft alone.
 */
@Service
@Transactional
public class AdminAccessService {

    public static final String LOCKED_AUTHORITY = "ROLE_ADMIN_LOCKED";
    static final int MAX_FAILED_ATTEMPTS = 5;
    static final Duration LOCKOUT = Duration.ofMinutes(15);
    static final int MIN_PASSWORD_LENGTH = 12;
    /** BCrypt reads at most 72 bytes; anything longer would silently be truncated. */
    static final int MAX_PASSWORD_BYTES = 72;

    private static final Logger log = LoggerFactory.getLogger(AdminAccessService.class);

    /** What an admin session sees about itself. */
    public enum Access { UNLOCKED, LOCKED, SETUP_REQUIRED }

    public enum UnlockResult { UNLOCKED, WRONG_PASSWORD, TEMPORARILY_LOCKED }

    private final AppUserRepository users;
    private final PasswordEncoder passwordEncoder;
    private final AppSettings settings;
    private final Clock clock;

    @Autowired
    public AdminAccessService(AppUserRepository users, PasswordEncoder passwordEncoder, AppSettings settings) {
        this(users, passwordEncoder, settings, Clock.systemUTC());
    }

    AdminAccessService(AppUserRepository users, PasswordEncoder passwordEncoder, Clock clock) {
        this(users, passwordEncoder, new AppSettings(false, false), clock);
    }

    AdminAccessService(AppUserRepository users, PasswordEncoder passwordEncoder, AppSettings settings, Clock clock) {
        this.users = users;
        this.passwordEncoder = passwordEncoder;
        this.settings = settings;
        this.clock = clock;
    }

    /** The authority a fresh sign-in gets: the admin always starts locked. */
    public static GrantedAuthority signInAuthority(AppUser user) {
        return new SimpleGrantedAuthority(user.isAdmin() ? LOCKED_AUTHORITY : user.getRole().toAuthority());
    }

    /** The admin session's state; null for everyone else. */
    public static Access accessOf(AppUser user, Authentication session) {
        if (!user.isAdmin()) {
            return null;
        }
        boolean unlocked = session != null && session.getAuthorities().stream()
                .anyMatch(a -> Role.COORDINATOR.toAuthority().equals(a.getAuthority()));
        if (unlocked) {
            return Access.UNLOCKED;
        }
        return user.getCoordinatorPasswordHash() == null ? Access.SETUP_REQUIRED : Access.LOCKED;
    }

    /** First sign-in: the admin chooses the admin password. Refused once one exists. */
    public AppUser setInitialPassword(String password) {
        AppUser admin = currentAdmin();
        if (admin.getCoordinatorPasswordHash() != null) {
            throw new ForbiddenException("The admin password is already set.");
        }
        // With preview sign-in nobody's identity is proven, so whoever typed the admin email first
        // could claim the account. The password then has to come from the server instead.
        if (settings.previewSignIn() && !settings.devMode()) {
            throw new ForbiddenException("Preview sign-in is on, so the admin password must be set on the server "
                    + "(APP_ADMIN_INITIAL_PASSWORD), then restart.");
        }
        checkPasswordRules(password, admin.getEmail());
        admin.setCoordinatorPasswordHash(passwordEncoder.encode(password));
        admin.setCoordinatorFailedAttempts(0);
        admin.setCoordinatorLockedUntil(null);
        log.info("Admin password set for {}", admin.getEmail());
        return users.save(admin);
    }

    /** Checks the admin password. Failures are counted, not thrown, so the count is saved. */
    public UnlockResult unlock(String password) {
        AppUser admin = currentAdmin();
        if (admin.getCoordinatorPasswordHash() == null) {
            throw new ForbiddenException("Set the admin password first.");
        }
        Instant now = clock.instant();
        if (admin.getCoordinatorLockedUntil() != null && now.isBefore(admin.getCoordinatorLockedUntil())) {
            return UnlockResult.TEMPORARILY_LOCKED;
        }
        if (password != null && passwordEncoder.matches(password, admin.getCoordinatorPasswordHash())) {
            admin.setCoordinatorFailedAttempts(0);
            admin.setCoordinatorLockedUntil(null);
            users.save(admin);
            return UnlockResult.UNLOCKED;
        }
        int failures = admin.getCoordinatorFailedAttempts() + 1;
        if (failures >= MAX_FAILED_ATTEMPTS) {
            admin.setCoordinatorFailedAttempts(0);
            admin.setCoordinatorLockedUntil(now.plus(LOCKOUT));
            log.warn("Admin access for {} locked for {} after {} wrong passwords", admin.getEmail(), LOCKOUT, failures);
        } else {
            admin.setCoordinatorFailedAttempts(failures);
        }
        users.save(admin);
        return failures >= MAX_FAILED_ATTEMPTS ? UnlockResult.TEMPORARILY_LOCKED : UnlockResult.WRONG_PASSWORD;
    }

    /** When a temporarily locked admin can try again; null when not locked. */
    @Transactional(readOnly = true)
    public Instant lockedUntil() {
        Instant until = currentAdmin().getCoordinatorLockedUntil();
        return until != null && clock.instant().isBefore(until) ? until : null;
    }

    static void checkPasswordRules(String password, String email) {
        if (password == null || password.length() < MIN_PASSWORD_LENGTH) {
            throw new IllegalArgumentException("Use at least " + MIN_PASSWORD_LENGTH + " characters.");
        }
        if (password.getBytes(StandardCharsets.UTF_8).length > MAX_PASSWORD_BYTES) {
            throw new IllegalArgumentException("Use at most " + MAX_PASSWORD_BYTES + " characters.");
        }
        if (password.isBlank()) {
            throw new IllegalArgumentException("The password can't be only spaces.");
        }
        String localPart = email.contains("@") ? email.substring(0, email.indexOf('@')) : email;
        if (localPart.length() >= 4 && password.toLowerCase(Locale.ROOT).contains(localPart.toLowerCase(Locale.ROOT))) {
            throw new IllegalArgumentException("Don't use your email address in the password.");
        }
    }

    private AppUser currentAdmin() {
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        if (auth == null || !auth.isAuthenticated() || "anonymousUser".equals(auth.getPrincipal())) {
            throw new UnauthorizedException("Sign in first.");
        }
        AppUser user = users.findByEmailIgnoreCase(auth.getName())
                .filter(AppUser::isActive)
                .orElseThrow(() -> new UnauthorizedException("No active account for this sign-in."));
        if (!user.isAdmin()) {
            throw new ForbiddenException("Only the admin has an admin password.");
        }
        return user;
    }
}
