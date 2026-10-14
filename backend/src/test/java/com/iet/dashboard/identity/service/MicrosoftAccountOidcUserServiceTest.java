package com.iet.dashboard.identity.service;

import com.iet.dashboard.identity.domain.AppUser;
import com.iet.dashboard.identity.domain.Role;
import com.iet.dashboard.identity.repo.AppUserRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.oauth2.client.oidc.userinfo.OidcUserRequest;
import org.springframework.security.oauth2.client.registration.ClientRegistration;
import org.springframework.security.oauth2.core.AuthorizationGrantType;
import org.springframework.security.oauth2.core.ClientAuthenticationMethod;
import org.springframework.security.oauth2.core.OAuth2AccessToken;
import org.springframework.security.oauth2.core.OAuth2AuthenticationException;
import org.springframework.security.oauth2.core.oidc.OidcIdToken;
import org.springframework.security.oauth2.core.oidc.user.DefaultOidcUser;
import org.springframework.security.oauth2.core.oidc.user.OidcUser;

import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.assertj.core.api.Assertions.catchThrowableOfType;
import static org.mockito.Mockito.when;

/**
 * What a successful Entra sign-in becomes here: the local account's role, on a principal that is
 * still named by its email. The role is the whole point — it is what routes a coordinator to the
 * coordinator dashboard and a faculty member to theirs.
 */
@ExtendWith(MockitoExtension.class)
class MicrosoftAccountOidcUserServiceTest {

    private static final String NAME_ATTRIBUTE = "preferred_username";

    @Mock
    private AppUserRepository users;

    private MicrosoftAccountOidcUserService service;

    @BeforeEach
    void setUp() {
        service = new MicrosoftAccountOidcUserService(new MicrosoftAccountResolver(users));
    }

    @Test
    void signsInACoordinatorCarryingTheCoordinatorRole() {
        AppUser coordinator = new AppUser("coordinator@iet.edu", "Coordinator", Role.COORDINATOR);
        when(users.findByEmailIgnoreCase("coordinator@iet.edu")).thenReturn(Optional.of(coordinator));

        OidcUser admitted = service.admitted(entraUser("coordinator@iet.edu"), request());

        assertThat(admitted.getName()).isEqualTo("coordinator@iet.edu");
        assertThat(admitted.getAuthorities())
                .extracting(GrantedAuthority::getAuthority)
                .containsExactly("ROLE_COORDINATOR");
    }

    @Test
    void signsInAFacultyMemberCarryingTheFacultyRole() {
        AppUser faculty = new AppUser("taruna@iet.edu", "Taruna", Role.FACULTY);
        when(users.findByEmailIgnoreCase("taruna@iet.edu")).thenReturn(Optional.of(faculty));

        OidcUser admitted = service.admitted(entraUser("taruna@iet.edu"), request());

        assertThat(admitted.getAuthorities())
                .extracting(GrantedAuthority::getAuthority)
                .containsExactly("ROLE_FACULTY");
    }

    @Test
    void refusesAnEmailWithNoAccountAndSaysWhy() {
        when(users.findByEmailIgnoreCase("stranger@iet.edu")).thenReturn(Optional.empty());

        OAuth2AuthenticationException refused = catchThrowableOfType(
                () -> service.admitted(entraUser("stranger@iet.edu"), request()),
                OAuth2AuthenticationException.class);

        assertThat(refused.getError().getErrorCode())
                .isEqualTo(MicrosoftAccountOidcUserService.NOT_REGISTERED);
    }

    @Test
    void refusesADeactivatedAccount() {
        AppUser former = new AppUser("former@iet.edu", "Former", Role.COORDINATOR);
        former.setActive(false);
        when(users.findByEmailIgnoreCase("former@iet.edu")).thenReturn(Optional.of(former));

        assertThatThrownBy(() -> service.admitted(entraUser("former@iet.edu"), request()))
                .isInstanceOf(OAuth2AuthenticationException.class)
                .hasMessageContaining("No active account");
    }

    private static OidcUser entraUser(String email) {
        OidcIdToken idToken = new OidcIdToken(
                "id-token-value",
                Instant.now(),
                Instant.now().plusSeconds(600),
                Map.of("sub", "entra-object-id", NAME_ATTRIBUTE, email));
        return new DefaultOidcUser(
                List.of(new SimpleGrantedAuthority("SCOPE_openid")), idToken, null, NAME_ATTRIBUTE);
    }

    /** The registration the prod profile builds from ENTRA_* (see application-prod.yml). */
    private static OidcUserRequest request() {
        ClientRegistration registration = ClientRegistration.withRegistrationId("entra")
                .clientName("Microsoft Entra ID")
                .clientId("client-id")
                .clientSecret("client-secret")
                .clientAuthenticationMethod(ClientAuthenticationMethod.CLIENT_SECRET_BASIC)
                .authorizationGrantType(AuthorizationGrantType.AUTHORIZATION_CODE)
                .redirectUri("{baseUrl}/login/oauth2/code/{registrationId}")
                .authorizationUri("https://login.microsoftonline.com/tenant/oauth2/v2.0/authorize")
                .tokenUri("https://login.microsoftonline.com/tenant/oauth2/v2.0/token")
                .userNameAttributeName(NAME_ATTRIBUTE)
                .scope("openid", "profile", "email")
                .build();

        OidcIdToken idToken = new OidcIdToken(
                "id-token-value",
                Instant.now(),
                Instant.now().plusSeconds(600),
                Map.of("sub", "entra-object-id", NAME_ATTRIBUTE, "coordinator@iet.edu"));

        return new OidcUserRequest(
                registration,
                new OAuth2AccessToken(
                        OAuth2AccessToken.TokenType.BEARER, "access-token", Instant.now(), Instant.now().plusSeconds(600)),
                idToken);
    }
}
