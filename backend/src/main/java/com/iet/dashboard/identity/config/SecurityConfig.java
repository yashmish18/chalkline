package com.iet.dashboard.identity.config;

import com.iet.dashboard.identity.service.LoginBypass;
import com.iet.dashboard.identity.service.MicrosoftAccountOidcUserService;
import com.iet.dashboard.identity.service.MicrosoftAccountResolver;
import com.iet.dashboard.identity.service.UserService;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.config.annotation.method.configuration.EnableMethodSecurity;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.oauth2.client.registration.ClientRegistrationRepository;
import org.springframework.security.oauth2.core.OAuth2AuthenticationException;
import org.springframework.security.oauth2.core.oidc.user.OidcUser;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.AuthenticationSuccessHandler;
import org.springframework.security.web.context.HttpSessionSecurityContextRepository;
import org.springframework.security.web.csrf.CookieCsrfTokenRepository;
import org.springframework.security.web.csrf.CsrfToken;
import org.springframework.security.web.csrf.CsrfTokenRequestAttributeHandler;
import org.springframework.security.web.util.matcher.AntPathRequestMatcher;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;

@Configuration
@EnableWebSecurity
@EnableMethodSecurity(prePostEnabled = true)
public class SecurityConfig {

    private static final Logger log = LoggerFactory.getLogger(SecurityConfig.class);

    /** The bypass key only works in dev mode; on any other server it is ignored even if set. */
    @Bean
    public LoginBypass loginBypass(
            AppSettings settings,
            @Value("${app.auth.bypass-key:}") String bypassKey,
            @Value("${app.auth.bypass-email:coordinator@iet.edu}") String bypassEmail
    ) {
        return new LoginBypass(settings.devMode() ? bypassKey : "", bypassEmail);
    }

    /** Hashes coordinator passwords. Cost 12 keeps a guess around a quarter of a second. */
    @Bean
    public PasswordEncoder passwordEncoder() {
        return new BCryptPasswordEncoder(12);
    }

    @Bean
    public SecurityFilterChain securityFilterChain(
            HttpSecurity http,
            ObjectProvider<ClientRegistrationRepository> clientRegistrations,
            ObjectProvider<MicrosoftAccountResolver> microsoftAccounts,
            UserService userService,
            @Value("${app.security.csrf:true}") boolean csrfEnabled
    ) throws Exception {
        if (csrfEnabled) {
            // Single-page-app pattern: the token travels in a readable XSRF-TOKEN cookie and comes
            // back in the X-XSRF-TOKEN header (the frontend's fetch wrapper adds it).
            CsrfTokenRequestAttributeHandler handler = new CsrfTokenRequestAttributeHandler();
            handler.setCsrfRequestAttributeName(null);
            http.csrf(csrf -> csrf
                    .csrfTokenRepository(CookieCsrfTokenRepository.withHttpOnlyFalse())
                    .csrfTokenRequestHandler(handler));
            http.addFilterAfter(new CsrfCookieFilter(), org.springframework.security.web.csrf.CsrfFilter.class);
        } else {
            http.csrf(csrf -> csrf.disable());
        }

        http
            .sessionManagement(session -> session.sessionCreationPolicy(SessionCreationPolicy.IF_REQUIRED))
            .authorizeHttpRequests(auth -> auth
                .requestMatchers(new AntPathRequestMatcher("/api/auth/**")).permitAll()
                .requestMatchers(new AntPathRequestMatcher("/actuator/health")).permitAll()
                .requestMatchers(new AntPathRequestMatcher("/swagger-ui/**")).permitAll()
                .requestMatchers(new AntPathRequestMatcher("/v3/api-docs/**")).permitAll()
                .requestMatchers(new AntPathRequestMatcher("/error")).permitAll()
                .requestMatchers(new AntPathRequestMatcher("/api/me")).authenticated()
                // The admin's session before the admin password holds neither role, so it reaches
                // nothing beyond sign-in and /api/me.
                .requestMatchers(new AntPathRequestMatcher("/api/**")).hasAnyRole("COORDINATOR", "FACULTY", "DEAN")
                .anyRequest().permitAll()
            )
            .exceptionHandling(ex -> ex
                .authenticationEntryPoint((request, response, authException) -> {
                    response.setStatus(HttpServletResponse.SC_UNAUTHORIZED);
                    response.setContentType("application/json");
                    response.getWriter().write("{\"title\":\"Unauthorized\",\"status\":401,\"detail\":\"Sign in required\"}");
                })
                .accessDeniedHandler((request, response, accessDeniedException) -> {
                    response.setStatus(HttpServletResponse.SC_FORBIDDEN);
                    response.setContentType("application/json");
                    response.getWriter().write("{\"title\":\"Forbidden\",\"status\":403,\"detail\":\"Access Denied\"}");
                })
            );

        // Real Microsoft Entra ID sign-in, whenever an "entra" client registration is configured.
        if (clientRegistrations.getIfAvailable() != null) {
            // The OIDC user service turns away anyone without an active account before a session
            // exists; the success handler then signs the person in under their email and role.
            var microsoftSignIn = new MicrosoftAccountOidcUserService(microsoftAccounts.getObject());
            http.oauth2Login(oauth -> oauth
                    .userInfoEndpoint(info -> info.oidcUserService(microsoftSignIn))
                    .successHandler(entraSuccess(userService))
                    .failureHandler((request, response, exception) -> {
                        log.warn("Entra sign-in failed: {}", exception.getMessage());
                        response.sendRedirect("/?signin=" + (notRegistered(exception) ? "not-registered" : "failed"));
                    }));
        }

        return http.build();
    }

    /**
     * After Microsoft has verified who the person is, they get in only if the allocation sheet (or
     * the coordinator) created an active account for that email. Everyone else is turned away.
     */
    private static AuthenticationSuccessHandler entraSuccess(UserService userService) {
        HttpSessionSecurityContextRepository repository = new HttpSessionSecurityContextRepository();
        return (request, response, authentication) -> {
            String email = emailOf(authentication);
            try {
                if (email == null) {
                    throw new IllegalStateException("Microsoft did not return an email address");
                }
                userService.authenticateSso(email);
                repository.saveContext(SecurityContextHolder.getContext(), request, response);
                response.sendRedirect("/");
            } catch (RuntimeException e) {
                log.warn("Entra sign-in refused for {}: {}", email, e.getMessage());
                SecurityContextHolder.clearContext();
                var session = request.getSession(false);
                if (session != null) {
                    session.invalidate();
                }
                response.sendRedirect("/?signin=not-registered");
            }
        };
    }

    /** True when Entra signed someone in but this department has no active account for them. */
    private static boolean notRegistered(Exception exception) {
        for (Throwable cause = exception; cause != null; cause = cause.getCause()) {
            if (cause instanceof OAuth2AuthenticationException oauth
                    && MicrosoftAccountOidcUserService.NOT_REGISTERED.equals(oauth.getError().getErrorCode())) {
                return true;
            }
        }
        return false;
    }

    private static String emailOf(Authentication authentication) {
        if (authentication.getPrincipal() instanceof OidcUser oidc) {
            for (String claim : new String[]{"email", "preferred_username", "upn"}) {
                Object value = oidc.getClaims().get(claim);
                if (value instanceof String s && s.contains("@")) {
                    return s.trim().toLowerCase();
                }
            }
        }
        return null;
    }

    /** Makes sure the XSRF-TOKEN cookie is issued on every response, so the SPA always has one. */
    private static final class CsrfCookieFilter extends OncePerRequestFilter {
        @Override
        protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain chain)
                throws ServletException, IOException {
            CsrfToken token = (CsrfToken) request.getAttribute(CsrfToken.class.getName());
            if (token != null) {
                token.getToken();
            }
            chain.doFilter(request, response);
        }
    }
}
