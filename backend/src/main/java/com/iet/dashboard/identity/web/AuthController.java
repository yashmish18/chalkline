package com.iet.dashboard.identity.web;

import com.iet.dashboard.identity.service.UserService;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpSession;
import jakarta.validation.Valid;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/auth")
public class AuthController {

    /** Seeded institutional account used until the Entra app registration is configured. */
    private static final String DEFAULT_INSTITUTIONAL_ACCOUNT = "coordinator@iet.edu";

    private final UserService userService;
    private final com.iet.dashboard.identity.service.LoginBypass loginBypass;
    private final com.iet.dashboard.identity.config.AppSettings settings;
    private final org.springframework.beans.factory.ObjectProvider<
            org.springframework.security.oauth2.client.registration.ClientRegistrationRepository> clientRegistrations;

    public AuthController(UserService userService, com.iet.dashboard.identity.service.LoginBypass loginBypass,
                          com.iet.dashboard.identity.config.AppSettings settings,
                          org.springframework.beans.factory.ObjectProvider<
                                  org.springframework.security.oauth2.client.registration.ClientRegistrationRepository> clientRegistrations) {
        this.userService = userService;
        this.loginBypass = loginBypass;
        this.settings = settings;
        this.clientRegistrations = clientRegistrations;
    }

    /** What this server offers, so the UI shows only the sign-in and admin actions that work here. */
    public record AuthConfig(String institutionName, boolean devLogin, boolean microsoftLogin,
                             String microsoftLoginUrl, boolean destructiveActions, boolean emailSignIn) {}

    @GetMapping("/config")
    public AuthConfig config() {
        boolean entra = clientRegistrations.getIfAvailable() != null
                && clientRegistrations.getIfAvailable().findByRegistrationId("entra") != null;
        return new AuthConfig(settings.institutionName(), settings.devMode(), entra,
                entra ? "/oauth2/authorization/entra" : null, settings.destructiveActions(), settings.emailSignIn());
    }

    /** Dev-only endpoints answer 404 on a production server, exactly as if they did not exist. */
    private void requireDevMode() {
        if (!settings.devMode()) {
            throw new com.iet.dashboard.shared.error.ResourceNotFoundException("Not found");
        }
    }

    @PostMapping("/dev-login")
    public ResponseEntity<UserDto> devLogin(@Valid @RequestBody DevLoginRequest request, HttpServletRequest httpRequest) {
        // Dev mode, or the temporary preview sign-in on a server; 404 otherwise.
        if (!settings.emailSignIn()) {
            throw new com.iet.dashboard.shared.error.ResourceNotFoundException("Not found");
        }
        UserDto user = userService.authenticateDev(request.email());
        // Force session creation and persist security context in session
        HttpSession session = httpRequest.getSession(true);
        session.setAttribute("SPRING_SECURITY_CONTEXT", SecurityContextHolder.getContext());
        return ResponseEntity.ok(user);
    }

    /**
     * Microsoft Entra ID sign-in seam used by the login screen's "Continue with Microsoft".
     *
     * <p>Once the Entra app registration is configured this endpoint will exchange the OAuth
     * code for the verified institutional identity. Until then it resolves the seeded
     * institutional account so the sign-in journey is complete end to end.
     */
    @PostMapping("/microsoft")
    public ResponseEntity<UserDto> microsoftLogin(
            @RequestBody(required = false) MicrosoftLoginRequest request,
            HttpServletRequest httpRequest) {
        // This endpoint trusts the email it is given, so it only exists in dev mode. Production
        // sign-in is the real Microsoft Entra flow at /oauth2/authorization/entra.
        requireDevMode();

        // Dev bypass: presenting the configured key signs in without credentials.
        String email;
        if (request != null && loginBypass.matches(request.bypassKey())) {
            email = loginBypass.bypassEmail();
        } else {
            email = (request != null && request.email() != null && !request.email().isBlank())
                    ? request.email().trim()
                    : DEFAULT_INSTITUTIONAL_ACCOUNT;
        }

        UserDto user = userService.authenticateSso(email);
        HttpSession session = httpRequest.getSession(true);
        session.setAttribute("SPRING_SECURITY_CONTEXT", SecurityContextHolder.getContext());
        return ResponseEntity.ok(user);
    }

    @PostMapping("/logout")
    public ResponseEntity<Void> logout(HttpServletRequest httpRequest) {
        HttpSession session = httpRequest.getSession(false);
        if (session != null) {
            session.invalidate();
        }
        SecurityContextHolder.clearContext();
        return ResponseEntity.noContent().build();
    }
}
