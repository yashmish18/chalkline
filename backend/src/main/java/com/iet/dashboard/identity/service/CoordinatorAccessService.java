package com.iet.dashboard.identity.service;

import com.iet.dashboard.identity.config.AppSettings;
import com.iet.dashboard.identity.domain.AppUser;
import com.iet.dashboard.identity.domain.CoordinatorInvite;
import com.iet.dashboard.identity.domain.Role;
import com.iet.dashboard.identity.repo.AppUserRepository;
import com.iet.dashboard.identity.repo.CoordinatorInviteRepository;
import com.iet.dashboard.notifications.email.EmailService;
import com.iet.dashboard.shared.error.ForbiddenException;
import com.iet.dashboard.shared.error.UnauthorizedException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.Base64;
import java.util.HexFormat;
import java.util.List;
import java.util.Locale;

/**
 * Who may act as coordinator, and how they get there.
 *
 * <ol>
 *   <li>Someone is <em>invited</em>: the allocation sheet says Role = Coordinator, or the server's
 *       {@code APP_COORDINATOR_EMAIL} names them. An invite is a one-time link emailed to them.</li>
 *   <li>They open the link while signed in with Microsoft <em>as that email</em> and choose a
 *       coordinator password. Only its BCrypt hash is stored. Now they are coordinator.</li>
 *   <li>Every new session starts with coordinator access locked; the password unlocks it.
 *       Repeated wrong passwords lock it for a while.</li>
 * </ol>
 *
 * <p>A coordinator session carries {@code ROLE_COORDINATOR} only once unlocked; before that it carries
 * {@link #LOCKED_AUTHORITY}, which reaches nothing but the sign-in endpoints and {@code /api/me}.
 */
@Service
@Transactional
public class CoordinatorAccessService {

    public static final String LOCKED_AUTHORITY = "ROLE_COORDINATOR_LOCKED";
    static final Duration INVITE_LIFETIME = Duration.ofHours(72);
    static final Duration RESEND_INTERVAL = Duration.ofMinutes(1);
    static final int MAX_FAILED_ATTEMPTS = 5;
    static final Duration LOCKOUT = Duration.ofMinutes(15);
    static final int MIN_PASSWORD_LENGTH = 12;
    /** BCrypt reads at most 72 bytes; anything longer would silently be truncated. */
    static final int MAX_PASSWORD_BYTES = 72;

    private static final Logger log = LoggerFactory.getLogger(CoordinatorAccessService.class);
    private static final SecureRandom RANDOM = new SecureRandom();

    /** What a session sees about its own coordinator access. */
    public enum Access { UNLOCKED, LOCKED, SETUP_REQUIRED }

    public enum UnlockResult { UNLOCKED, WRONG_PASSWORD, TEMPORARILY_LOCKED }

    /** {@code devLink} is filled only in dev mode, so a developer can follow it without a mail server. */
    public record InviteOutcome(boolean sent, String devLink) {}

    private final AppUserRepository users;
    private final CoordinatorInviteRepository invites;
    private final PasswordEncoder passwordEncoder;
    private final EmailService email;
    private final AppSettings settings;
    private final Clock clock;

    @org.springframework.beans.factory.annotation.Autowired
    public CoordinatorAccessService(AppUserRepository users, CoordinatorInviteRepository invites,
                                    PasswordEncoder passwordEncoder, EmailService email, AppSettings settings) {
        this(users, invites, passwordEncoder, email, settings, Clock.systemUTC());
    }

    CoordinatorAccessService(AppUserRepository users, CoordinatorInviteRepository invites,
                             PasswordEncoder passwordEncoder, EmailService email, AppSettings settings, Clock clock) {
        this.users = users;
        this.invites = invites;
        this.passwordEncoder = passwordEncoder;
        this.email = email;
        this.settings = settings;
        this.clock = clock;
    }

    /**
     * The authority a fresh sign-in gets. A coordinator starts locked, except on a developer's
     * machine for an account that never set a password (so local sign-in keeps working).
     */
    public static GrantedAuthority signInAuthority(AppUser user, boolean devMode) {
        if (user.getRole() == Role.COORDINATOR) {
            boolean open = devMode && user.getCoordinatorPasswordHash() == null;
            return new SimpleGrantedAuthority(open ? Role.COORDINATOR.toAuthority() : LOCKED_AUTHORITY);
        }
        return new SimpleGrantedAuthority(user.getRole().toAuthority());
    }

    /** Coordinator access of this session's user; null for someone who is not (yet) a coordinator. */
    public static Access accessOf(AppUser user, Authentication session) {
        if (user.getRole() != Role.COORDINATOR) {
            return null;
        }
        boolean unlocked = session != null && session.getAuthorities().stream()
                .anyMatch(a -> Role.COORDINATOR.toAuthority().equals(a.getAuthority()));
        if (unlocked) {
            return Access.UNLOCKED;
        }
        return user.getCoordinatorPasswordHash() == null ? Access.SETUP_REQUIRED : Access.LOCKED;
    }

    /**
     * Emails a coordinator invite, replacing any earlier unused one. Nothing is sent to someone who
     * already finished setup, or to an inactive account.
     */
    public InviteOutcome invite(AppUser user) {
        if (!user.isActive() || (user.getRole() == Role.COORDINATOR && user.getCoordinatorPasswordHash() != null)) {
            return new InviteOutcome(false, null);
        }
        return issueLink(user);
    }

    /** Emails a fresh one-time link (replacing older ones). Completing it sets a new coordinator password. */
    private InviteOutcome issueLink(AppUser user) {
        Instant now = clock.instant();
        for (CoordinatorInvite old : invites.findByUserIdAndUsedAtIsNull(user.getId())) {
            invites.delete(old);
        }
        String token = newToken();
        invites.save(new CoordinatorInvite(user.getId(), sha256(token), now.plus(INVITE_LIFETIME)));
        String link = settings.publicUrl() + "/?coordinator-setup=" + token;

        String to = user.getEmail();
        String body = inviteText(user, link);
        // Send only once the invite is committed, so a link never points at a rolled-back row.
        afterCommit(() -> email.sendEmailAsync(to, "Activate your coordinator access", body));
        if (settings.devMode()) {
            log.info("[DEV] Coordinator setup link for {}: {}", to, link);
        }
        return new InviteOutcome(true, settings.devMode() ? link : null);
    }

    /** True when this user has an unused, unexpired invite. */
    @Transactional(readOnly = true)
    public boolean hasUsableInvite(AppUser user) {
        Instant now = clock.instant();
        return invites.findByUserIdAndUsedAtIsNull(user.getId()).stream().anyMatch(i -> i.isUsable(now));
    }

    /**
     * Sends the signed-in person a fresh link, at most once a minute: someone invited, or a
     * coordinator (which is also how a forgotten coordinator password is reset).
     */
    public InviteOutcome resendForCurrentUser() {
        AppUser user = currentUser();
        boolean invited = !invites.findByUserIdAndUsedAtIsNull(user.getId()).isEmpty();
        if (!invited && user.getRole() != Role.COORDINATOR) {
            throw new ForbiddenException("There is no coordinator invite for this account.");
        }
        Instant now = clock.instant();
        boolean tooSoon = invites.findByUserIdAndUsedAtIsNull(user.getId()).stream()
                .anyMatch(i -> i.getCreatedAt().isAfter(now.minus(RESEND_INTERVAL)));
        if (tooSoon) {
            throw new IllegalArgumentException("A link was sent less than a minute ago. Check your inbox (and spam).");
        }
        return issueLink(user);
    }

    /**
     * Completes an invite for the signed-in person: the link must be theirs, unused and unexpired.
     * Makes them coordinator with this password.
     */
    public AppUser completeSetup(String token, String password) {
        AppUser user = currentUser();
        CoordinatorInvite invite = invites.findByTokenHash(sha256(token == null ? "" : token.trim()))
                .filter(i -> i.isUsable(clock.instant()))
                .orElseThrow(() -> new IllegalArgumentException(
                        "This coordinator link is invalid, already used, or expired. Ask for a new one."));
        if (!invite.getUserId().equals(user.getId())) {
            throw new ForbiddenException("This link was sent to a different person. Sign in with the "
                    + "Microsoft account it was emailed to.");
        }
        checkPasswordRules(password, user.getEmail());

        user.setCoordinatorPasswordHash(passwordEncoder.encode(password));
        user.setCoordinatorFailedAttempts(0);
        user.setCoordinatorLockedUntil(null);
        user.setRole(Role.COORDINATOR);
        invite.markUsed(clock.instant());
        invites.save(invite);
        log.info("Coordinator access activated for {}", user.getEmail());
        return users.save(user);
    }

    /** Checks the coordinator password of the signed-in person. Failures are counted, not thrown. */
    public UnlockResult unlock(String password) {
        AppUser user = currentUser();
        if (user.getRole() != Role.COORDINATOR || user.getCoordinatorPasswordHash() == null) {
            throw new ForbiddenException("This account has no coordinator password yet.");
        }
        Instant now = clock.instant();
        if (user.getCoordinatorLockedUntil() != null && now.isBefore(user.getCoordinatorLockedUntil())) {
            return UnlockResult.TEMPORARILY_LOCKED;
        }
        if (password != null && passwordEncoder.matches(password, user.getCoordinatorPasswordHash())) {
            user.setCoordinatorFailedAttempts(0);
            user.setCoordinatorLockedUntil(null);
            users.save(user);
            return UnlockResult.UNLOCKED;
        }
        int failures = user.getCoordinatorFailedAttempts() + 1;
        if (failures >= MAX_FAILED_ATTEMPTS) {
            user.setCoordinatorFailedAttempts(0);
            user.setCoordinatorLockedUntil(now.plus(LOCKOUT));
            log.warn("Coordinator access for {} locked for {} after {} wrong passwords", user.getEmail(), LOCKOUT, failures);
        } else {
            user.setCoordinatorFailedAttempts(failures);
        }
        users.save(user);
        return failures >= MAX_FAILED_ATTEMPTS ? UnlockResult.TEMPORARILY_LOCKED : UnlockResult.WRONG_PASSWORD;
    }

    /** When a temporarily locked account opens again; null when it is not locked. */
    @Transactional(readOnly = true)
    public Instant lockedUntil() {
        Instant until = currentUser().getCoordinatorLockedUntil();
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

    private AppUser currentUser() {
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        if (auth == null || !auth.isAuthenticated() || "anonymousUser".equals(auth.getPrincipal())) {
            throw new UnauthorizedException("Sign in first.");
        }
        return users.findByEmailIgnoreCase(auth.getName())
                .filter(AppUser::isActive)
                .orElseThrow(() -> new UnauthorizedException("No active account for this sign-in."));
    }

    private String inviteText(AppUser user, String link) {
        return String.join("\n", List.of(
                "Hello " + user.getName() + ",",
                "",
                "You have been made a coordinator of the " + settings.institutionName() + " Faculty Dashboard.",
                "",
                "To activate coordinator access:",
                "  1. Open this link: " + link,
                "  2. Sign in with your Microsoft account (" + user.getEmail() + ").",
                "  3. Choose a coordinator password. You will enter it whenever you open coordinator tools.",
                "",
                "The link works once and expires in " + INVITE_LIFETIME.toHours() + " hours.",
                "If you did not expect this email, ignore it: nothing changes until the link is used."));
    }

    private static void afterCommit(Runnable action) {
        if (TransactionSynchronizationManager.isSynchronizationActive()) {
            TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
                @Override
                public void afterCommit() {
                    action.run();
                }
            });
        } else {
            action.run();
        }
    }

    private static String newToken() {
        byte[] bytes = new byte[32];
        RANDOM.nextBytes(bytes);
        return Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);
    }

    static String sha256(String value) {
        try {
            return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(value.getBytes(StandardCharsets.UTF_8)));
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException(e);
        }
    }
}
