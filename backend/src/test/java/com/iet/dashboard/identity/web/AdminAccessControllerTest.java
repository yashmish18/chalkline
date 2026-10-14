package com.iet.dashboard.identity.web;

import com.iet.dashboard.identity.config.AppSettings;
import com.iet.dashboard.identity.config.SecurityConfig;
import com.iet.dashboard.identity.domain.AppUser;
import com.iet.dashboard.identity.domain.Role;
import com.iet.dashboard.identity.service.AdminAccessService;
import com.iet.dashboard.identity.service.UserService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.mock.web.MockHttpSession;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContext;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.test.web.servlet.request.RequestPostProcessor;

import java.time.Instant;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/** The admin password prompt, and what an admin session can reach before it. */
@WebMvcTest(controllers = {AdminAccessController.class, UserController.class})
@Import({SecurityConfig.class, AppSettings.class})
class AdminAccessControllerTest {

    private static final String EMAIL = "s.taruna@jklu.edu.in";

    @Autowired MockMvc mvc;
    @MockBean AdminAccessService access;
    @MockBean UserService userService;

    private static RequestPostProcessor locked() {
        return user(EMAIL).authorities(new SimpleGrantedAuthority(AdminAccessService.LOCKED_AUTHORITY));
    }

    @Test
    void aLockedAdminSessionReachesOnlyMeAndSignIn() throws Exception {
        when(userService.getCurrentUser()).thenReturn(new UserDto(1L, EMAIL, "Dr. S Taruna", "COORDINATOR", "LOCKED"));

        mvc.perform(get("/api/me").with(locked()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.adminAccess").value("LOCKED"));
        mvc.perform(get("/api/allocations").with(locked())).andExpect(status().isForbidden());
        mvc.perform(get("/api/faculty").with(locked())).andExpect(status().isForbidden());
    }

    @Test
    void theRightPasswordUnlocksUnderANewSessionId() throws Exception {
        when(access.unlock("right-password-123")).thenReturn(AdminAccessService.UnlockResult.UNLOCKED);
        when(userService.getCurrentUser()).thenReturn(new UserDto(1L, EMAIL, "Dr. S Taruna", "COORDINATOR", "UNLOCKED"));
        MockHttpSession session = new MockHttpSession();
        String before = session.getId();

        MvcResult result = mvc.perform(post("/api/auth/admin/unlock").session(session).with(locked()).with(csrf())
                        .contentType(MediaType.APPLICATION_JSON).content("{\"password\":\"right-password-123\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.adminAccess").value("UNLOCKED"))
                .andReturn();

        var stored = (SecurityContext) result.getRequest().getSession().getAttribute("SPRING_SECURITY_CONTEXT");
        assertThat(stored.getAuthentication().getAuthorities())
                .extracting(a -> a.getAuthority()).containsExactly(Role.COORDINATOR.toAuthority());
        assertThat(result.getRequest().getSession().getId()).isNotEqualTo(before);
    }

    @Test
    void aWrongPasswordIsABadRequestNotASignOut() throws Exception {
        when(access.unlock("nope")).thenReturn(AdminAccessService.UnlockResult.WRONG_PASSWORD);

        mvc.perform(post("/api/auth/admin/unlock").with(locked()).with(csrf())
                        .contentType(MediaType.APPLICATION_JSON).content("{\"password\":\"nope\"}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.detail").value("Wrong admin password."));
    }

    @Test
    void tooManyWrongPasswordsSaysUntilWhen() throws Exception {
        when(access.unlock("x")).thenReturn(AdminAccessService.UnlockResult.TEMPORARILY_LOCKED);
        when(access.lockedUntil()).thenReturn(Instant.parse("2026-10-14T05:15:00Z"));

        mvc.perform(post("/api/auth/admin/unlock").with(locked()).with(csrf())
                        .contentType(MediaType.APPLICATION_JSON).content("{\"password\":\"x\"}"))
                .andExpect(status().isTooManyRequests())
                .andExpect(jsonPath("$.detail").value(org.hamcrest.Matchers.containsString("10:45")));
    }

    @Test
    void choosingThePasswordAtFirstSignInUnlocksTheSession() throws Exception {
        AppUser taruna = new AppUser(EMAIL, "Dr. S Taruna", Role.COORDINATOR);
        when(access.setInitialPassword("Correct-Horse-Battery-9")).thenReturn(taruna);
        when(userService.getCurrentUser()).thenReturn(new UserDto(1L, EMAIL, "Dr. S Taruna", "COORDINATOR", "UNLOCKED"));

        mvc.perform(post("/api/auth/admin/setup").with(locked()).with(csrf())
                        .contentType(MediaType.APPLICATION_JSON).content("{\"password\":\"Correct-Horse-Battery-9\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.adminAccess").value("UNLOCKED"));
    }

    @Test
    void writesWithoutACsrfTokenAreRefused() throws Exception {
        mvc.perform(post("/api/auth/admin/unlock").with(locked())
                        .contentType(MediaType.APPLICATION_JSON).content("{\"password\":\"x\"}"))
                .andExpect(status().isForbidden());
        verifyNoInteractions(access);
    }
}
