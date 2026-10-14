package com.iet.dashboard.identity.config;

import java.util.Arrays;
import java.util.List;
import java.util.Locale;
import org.springframework.beans.factory.annotation.Autowired;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

/**
 * Switches that differ between a developer's machine and the production server. Every one defaults
 * to the safe production value; the {@code dev} profile turns the conveniences on.
 */
@Component
public class AppSettings {

    private static final Logger log = LoggerFactory.getLogger(AppSettings.class);

    private final boolean devMode;
    private final boolean destructiveActions;
    private final String institutionName;
    private final String publicUrl;
    private final String emailDomain;
    private final List<String> allowedEmailDomains;
    private final boolean previewSignIn;

    @Autowired
    public AppSettings(
            @Value("${app.auth.dev-mode:false}") boolean devMode,
            @Value("${app.admin.destructive-actions:false}") boolean destructiveActions,
            @Value("${app.institution.name:Institute of Engineering & Technology}") String institutionName,
            @Value("${app.public-url:http://localhost:5173}") String publicUrl,
            @Value("${app.email-domain:jklu.edu.in}") String emailDomain,
            @Value("${app.auth.allowed-email-domains:}") String allowedEmailDomains,
            @Value("${app.auth.preview-sign-in:false}") boolean previewSignIn) {
        this.devMode = devMode;
        this.destructiveActions = destructiveActions;
        this.institutionName = institutionName;
        this.publicUrl = publicUrl == null ? "" : publicUrl.trim().replaceAll("/+$", "");
        this.emailDomain = emailDomain == null || emailDomain.isBlank()
                ? "jklu.edu.in" : emailDomain.trim().toLowerCase(Locale.ROOT).replaceFirst("^@", "");
        this.allowedEmailDomains = Arrays.stream(allowedEmailDomains == null ? new String[0] : allowedEmailDomains.split(","))
                .map(d -> d.trim().toLowerCase(Locale.ROOT).replaceFirst("^@", ""))
                .filter(d -> !d.isEmpty())
                .toList();
        this.previewSignIn = previewSignIn;
        if (previewSignIn && !devMode) {
            log.warn("Preview sign-in is ON: anyone who types a registered email signs in as that person "
                    + "(the admin still needs the admin password). Turn it off once Microsoft sign-in works.");
        }
        if (devMode) {
            log.warn("Dev mode is ON: password-less dev sign-in is enabled. Never run production with app.auth.dev-mode=true.");
        }
        if (destructiveActions) {
            log.warn("Destructive admin actions (delete all data, reset activity, delete all faculty) are ENABLED.");
        }
    }

    /** Password-less sign-in by email, the login bypass key, and the user switcher. */
    public boolean devMode() {
        return devMode;
    }

    /**
     * Temporary sign-in by email alone on a server, for testing before Microsoft sign-in is set
     * up ({@code APP_PREVIEW_SIGNIN=true}). Off by default.
     */
    public boolean previewSignIn() {
        return previewSignIn;
    }

    /** Sign-in by typing an email: always in dev mode, and in preview mode on a server. */
    public boolean emailSignIn() {
        return devMode || previewSignIn;
    }

    /** "Delete all data", "Reset activity" and "Delete all faculty". */
    public boolean destructiveActions() {
        return destructiveActions;
    }

    /** Settings for tests and tools: everything at its safe production default. */
    public AppSettings(boolean devMode, boolean destructiveActions) {
        this(devMode, destructiveActions, "Institute of Engineering & Technology", "http://localhost:5173", "jklu.edu.in", "", false);
    }

    public AppSettings(boolean devMode, boolean destructiveActions, String institutionName, String publicUrl,
                       String emailDomain, String allowedEmailDomains) {
        this(devMode, destructiveActions, institutionName, publicUrl, emailDomain, allowedEmailDomains, false);
    }

    public String institutionName() {
        return institutionName;
    }

    /** The address people open the app at, without a trailing slash; emailed links start here. */
    public String publicUrl() {
        return publicUrl;
    }

    /** The institutional email domain, e.g. {@code jklu.edu.in}. */
    public String emailDomain() {
        return emailDomain;
    }

    /** True when a sign-in email belongs to an allowed domain (any domain when none are configured). */
    public boolean isAllowedEmail(String email) {
        if (allowedEmailDomains.isEmpty()) {
            return true;
        }
        String domain = email == null ? "" : email.substring(email.lastIndexOf('@') + 1).toLowerCase(Locale.ROOT);
        return allowedEmailDomains.contains(domain);
    }
}
