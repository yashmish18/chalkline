package com.iet.dashboard.identity.service;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;

/**
 * Development login bypass. When {@code app.auth.bypass-key} is set, presenting that key
 * signs in as the seeded coordinator without any credential check — a deliberate
 * convenience seam for local demos while Entra ID is not configured.
 *
 * <p>This is a dev-only seam: the key must never be set in production, and a warning is
 * logged at startup while it is active.
 */
public class LoginBypass {

    private final String bypassKey;
    private final String bypassEmail;

    private static final Logger log = LoggerFactory.getLogger(LoginBypass.class);

    public LoginBypass(
            @Value("${app.auth.bypass-key:}") String bypassKey,
            @Value("${app.auth.bypass-email:coordinator@iet.edu}") String bypassEmail
    ) {
        this.bypassKey = bypassKey == null ? "" : bypassKey.trim();
        this.bypassEmail = bypassEmail;
        if (isEnabled()) {
            log.warn("Login bypass is ACTIVE (key set). This must never be enabled in production.");
        }
    }

    /** True when the presented key matches the configured bypass key. */
    public boolean matches(String presentedKey) {
        return isEnabled() && presentedKey != null && this.bypassKey.equals(presentedKey.trim());
    }

    /** Email of the account the bypass signs in as. */
    public String bypassEmail() {
        return bypassEmail;
    }

    private boolean isEnabled() {
        return !bypassKey.isBlank();
    }
}
