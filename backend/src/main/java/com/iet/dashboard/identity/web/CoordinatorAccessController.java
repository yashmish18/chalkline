package com.iet.dashboard.identity.web;

import com.iet.dashboard.identity.domain.AppUser;
import com.iet.dashboard.identity.domain.Role;
import com.iet.dashboard.identity.service.CoordinatorAccessService;
import com.iet.dashboard.identity.service.UserService;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.http.HttpStatus;
import org.springframework.http.ProblemDetail;
import org.springframework.http.ResponseEntity;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContext;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.time.Instant;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.Map;

/**
 * Coordinator setup (from the emailed link) and the coordinator password prompt.
 * Every endpoint acts on the signed-in person only; nothing here takes a user id.
 */
@RestController
@RequestMapping("/api/auth/coordinator")
public class CoordinatorAccessController {

    public record SetupRequest(String token, String password) {}

    public record UnlockRequest(String password) {}

    private static final DateTimeFormatter CLOCK_TIME =
            DateTimeFormatter.ofPattern("HH:mm").withZone(ZoneId.of("Asia/Kolkata"));

    private final CoordinatorAccessService access;
    private final UserService userService;

    public CoordinatorAccessController(CoordinatorAccessService access, UserService userService) {
        this.access = access;
        this.userService = userService;
    }

    /** Opens the emailed link: sets the coordinator password and makes this person coordinator. */
    @PostMapping("/setup")
    public UserDto setup(@RequestBody SetupRequest request, HttpServletRequest http) {
        AppUser user = access.completeSetup(request.token(), request.password());
        return unlockSession(user.getEmail(), http);
    }

    /** The coordinator password prompt shown after each Microsoft sign-in. */
    @PostMapping("/unlock")
    public ResponseEntity<?> unlock(@RequestBody UnlockRequest request, HttpServletRequest http) {
        return switch (access.unlock(request.password())) {
            case UNLOCKED -> ResponseEntity.ok(unlockSession(userService.getCurrentUser().email(), http));
            case WRONG_PASSWORD -> problem(HttpStatus.BAD_REQUEST, "Wrong coordinator password.");
            case TEMPORARILY_LOCKED -> problem(HttpStatus.TOO_MANY_REQUESTS,
                    "Too many wrong passwords. Coordinator access is locked until " + until(access.lockedUntil()) + ".");
        };
    }

    /** Emails the signed-in person a fresh setup link. In dev mode the link is also returned. */
    @PostMapping("/resend")
    public Map<String, Object> resend() {
        CoordinatorAccessService.InviteOutcome outcome = access.resendForCurrentUser();
        return outcome.devLink() == null ? Map.of("sent", outcome.sent())
                : Map.of("sent", outcome.sent(), "devLink", outcome.devLink());
    }

    /**
     * Swaps the locked session for an unlocked one under a new session id, so a session id seen
     * before the password was entered is worth nothing afterwards.
     */
    private UserDto unlockSession(String email, HttpServletRequest http) {
        var authentication = new UsernamePasswordAuthenticationToken(
                email, null, List.of(new SimpleGrantedAuthority(Role.COORDINATOR.toAuthority())));
        SecurityContext context = SecurityContextHolder.createEmptyContext();
        context.setAuthentication(authentication);
        SecurityContextHolder.setContext(context);
        http.getSession(true);
        http.changeSessionId();
        http.getSession().setAttribute("SPRING_SECURITY_CONTEXT", context);
        return userService.getCurrentUser();
    }

    private static String until(Instant when) {
        return when == null ? "a few minutes from now" : CLOCK_TIME.format(when);
    }

    private static ResponseEntity<ProblemDetail> problem(HttpStatus status, String detail) {
        return ResponseEntity.status(status).body(ProblemDetail.forStatusAndDetail(status, detail));
    }
}
