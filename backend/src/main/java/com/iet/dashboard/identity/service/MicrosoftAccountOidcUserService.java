package com.iet.dashboard.identity.service;

import com.iet.dashboard.identity.domain.AppUser;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.oauth2.client.oidc.userinfo.OidcUserRequest;
import org.springframework.security.oauth2.client.oidc.userinfo.OidcUserService;
import org.springframework.security.oauth2.client.userinfo.OAuth2UserService;
import org.springframework.security.oauth2.core.OAuth2AuthenticationException;
import org.springframework.security.oauth2.core.OAuth2Error;
import org.springframework.security.oauth2.core.oidc.user.DefaultOidcUser;
import org.springframework.security.oauth2.core.oidc.user.OidcUser;

import java.util.Set;

/**
 * Completes a Microsoft Entra sign-in for this application.
 *
 * <p>It reads the identity, then refuses it unless it belongs to an active account that already
 * exists here (see {@link MicrosoftAccountResolver}), and rebuilds the authenticated principal
 * carrying that account's role. Carrying the role is what makes the rest of the application work
 * unchanged: a coordinator reaches the coordinator dashboard, a faculty member reaches theirs,
 * and {@code @PreAuthorize("hasRole('COORDINATOR')")} answers correctly — exactly as it does for
 * password-less dev sign-in.
 *
 * <p>The principal keeps its configured name attribute, so {@code Authentication.getName()} stays
 * the email and every existing "who am I" lookup keeps working.
 */
public class MicrosoftAccountOidcUserService extends OidcUserService {

    /** Raised when Entra authenticated someone this department has no account for. */
    public static final String NOT_REGISTERED = "not_registered";

    private final MicrosoftAccountResolver resolver;

    public MicrosoftAccountOidcUserService(MicrosoftAccountResolver resolver) {
        this.resolver = resolver;
    }

    @Override
    public OidcUser loadUser(OidcUserRequest userRequest) throws OAuth2AuthenticationException {
        return admitted(super.loadUser(userRequest), userRequest);
    }

    /**
     * The decision itself, separated from the userinfo round trip so it can be exercised directly.
     */
    OidcUser admitted(OidcUser upstream, OidcUserRequest userRequest) {
        AppUser account = resolver.resolve(upstream.getClaims(), upstream.getName())
                .orElseThrow(() -> new OAuth2AuthenticationException(new OAuth2Error(
                        NOT_REGISTERED,
                        "No active account for this Microsoft email. Ask the coordinator to add you through the allocation sheet.",
                        null)));

        Set<GrantedAuthority> authorities =
                Set.of(new SimpleGrantedAuthority(account.getRole().toAuthority()));
        String nameAttribute = userRequest.getClientRegistration()
                .getProviderDetails().getUserInfoEndpoint().getUserNameAttributeName();

        return new DefaultOidcUser(authorities, upstream.getIdToken(), upstream.getUserInfo(), nameAttribute);
    }
}
