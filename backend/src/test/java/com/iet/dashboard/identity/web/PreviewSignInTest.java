package com.iet.dashboard.identity.web;

import com.iet.dashboard.identity.config.AppSettings;
import com.iet.dashboard.identity.config.SecurityConfig;
import com.iet.dashboard.identity.service.UserService;
import com.iet.dashboard.shared.admin.DatabaseWipeController;
import com.iet.dashboard.shared.admin.DatabaseWipeService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.web.servlet.MockMvc;

import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/** A production server with the temporary preview sign-in on: email sign-in works, nothing else loosens. */
@WebMvcTest(controllers = {AuthController.class, DatabaseWipeController.class})
@Import({SecurityConfig.class, AppSettings.class})
@TestPropertySource(properties = {
        "app.auth.dev-mode=false",
        "app.auth.preview-sign-in=true",
        "app.admin.destructive-actions=false",
        "app.security.csrf=true"
})
class PreviewSignInTest {

    @Autowired MockMvc mvc;
    @MockBean UserService userService;
    @MockBean DatabaseWipeService wipeService;

    @Test
    void theUiIsOfferedEmailSignInButNotDevToolsOrBulkDeletes() throws Exception {
        mvc.perform(get("/api/auth/config"))
                .andExpect(jsonPath("$.emailSignIn").value(true))
                .andExpect(jsonPath("$.devLogin").value(false))
                .andExpect(jsonPath("$.destructiveActions").value(false));
    }

    @Test
    void signingInByEmailWorks() throws Exception {
        when(userService.authenticateDev("amit.sinhal@jklu.edu.in"))
                .thenReturn(new UserDto(2L, "amit.sinhal@jklu.edu.in", "Dr. Amit Sinhal", "FACULTY"));
        mvc.perform(post("/api/auth/dev-login").with(csrf())
                        .contentType(MediaType.APPLICATION_JSON).content("{\"email\":\"amit.sinhal@jklu.edu.in\"}"))
                .andExpect(status().isOk());
    }

    @Test
    void theEmailTrustingMicrosoftShortcutStillDoesNotExist() throws Exception {
        mvc.perform(post("/api/auth/microsoft").with(csrf())
                        .contentType(MediaType.APPLICATION_JSON).content("{\"bypassKey\":\"dev\"}"))
                .andExpect(status().isNotFound());
    }

    @Test
    @WithMockUser(roles = "COORDINATOR")
    void deleteAllDataStillDoesNotExist() throws Exception {
        mvc.perform(delete("/api/admin/data").with(csrf())).andExpect(status().isNotFound());
        verifyNoInteractions(wipeService);
    }
}
