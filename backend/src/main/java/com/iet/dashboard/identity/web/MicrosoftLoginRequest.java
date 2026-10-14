package com.iet.dashboard.identity.web;

/**
 * Optional payload for the SSO sign-in seam. `email` selects an account explicitly
 * (dev switching); `bypassKey` is the dev login-bypass key — when it matches the server's
 * configured `app.auth.bypass-key`, sign-in happens without credentials.
 */
public record MicrosoftLoginRequest(String email, String bypassKey) {

    public MicrosoftLoginRequest(String email) {
        this(email, null);
    }
}
