package com.iet.dashboard.identity.service;

import com.iet.dashboard.identity.config.AppSettings;
import org.springframework.beans.factory.annotation.Autowired;

import com.iet.dashboard.identity.domain.AppUser;
import com.iet.dashboard.identity.repo.AppUserRepository;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;

/**
 * Decides whether a Microsoft Entra identity is allowed in.
 *
 * <p>Sign-in through Entra only ever <em>admits</em> an account that already exists here and is
 * active — the same rule the password-less dev seam follows. Accounts are created by the
 * allocation import, or by the coordinator bootstrap for the coordinator themself; nobody
 * self-registers by signing in. That is what keeps the role on the account authoritative: an
 * entra identity supplies an email, and nothing else, so it can never promote itself.
 */
@Component
public class MicrosoftAccountResolver {

    /**
     * Where the institutional email hides in an Entra ID token, most reliable first.
     * {@code preferred_username} is the UPN Entra puts in the ID token for the {@code profile}
     * scope; {@code email} only appears when the {@code email} scope is granted, and {@code upn}
     * is the legacy claim older tenants still emit.
     */
    private static final List<String> EMAIL_CLAIMS = List.of("email", "preferred_username", "upn");

    private final AppUserRepository users;
    private final AppSettings settings;

    @Autowired
    public MicrosoftAccountResolver(AppUserRepository users, AppSettings settings) {
        this.users = users;
        this.settings = settings;
    }

    /** Any email domain is accepted; for tests. */
    public MicrosoftAccountResolver(AppUserRepository users) {
        this(users, new AppSettings(false, false));
    }

    /** The active local account behind these claims, or empty when there is none or its domain is not allowed. */
    public Optional<AppUser> resolve(Map<String, Object> claims, String fallbackName) {
        return emailFrom(claims, fallbackName)
                .filter(settings::isAllowedEmail)
                .flatMap(email -> users.findByEmailIgnoreCase(email))
                .filter(AppUser::isActive);
    }

    /** The sign-in email, or empty when the token carries none we can match on. */
    public static Optional<String> emailFrom(Map<String, Object> claims, String fallbackName) {
        for (String claim : EMAIL_CLAIMS) {
            String value = claimValue(claims, claim);
            // A claim that is not an email (e.g. a bare login name) is skipped, as the success handler does.
            if (value.contains("@")) {
                return Optional.of(value.toLowerCase(Locale.ROOT));
            }
        }
        String fallback = trim(fallbackName);
        return fallback.isEmpty() ? Optional.empty() : Optional.of(fallback.toLowerCase(Locale.ROOT));
    }

    private static String claimValue(Map<String, Object> claims, String claim) {
        return claims == null ? "" : trim(claims.get(claim));
    }

    private static String trim(Object raw) {
        return raw instanceof String text ? text.trim() : "";
    }
}
