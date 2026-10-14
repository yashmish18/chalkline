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
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/** A server with production settings: no dev sign-in, CSRF enforced, no bulk deletes. */
@WebMvcTest(controllers = {AuthController.class, DatabaseWipeController.class})
@Import({SecurityConfig.class, AppSettings.class})
@TestPropertySource(properties = {
        "app.auth.dev-mode=false",
        "app.auth.bypass-key=dev",
        "app.admin.destructive-actions=false",
        "app.security.csrf=true"
})
class ProductionSecurityTest {

    @Autowired MockMvc mvc;
    @MockBean UserService userService;
    @MockBean DatabaseWipeService wipeService;

    @Test
    void passwordlessDevSignInDoesNotExist() throws Exception {
        mvc.perform(post("/api/auth/dev-login").with(csrf())
                        .contentType(MediaType.APPLICATION_JSON).content("{\"email\":\"coordinator@iet.edu\"}"))
                .andExpect(status().isNotFound());
        verifyNoInteractions(userService);
    }

    @Test
    void theEmailTrustingMicrosoftShortcutAndBypassKeyDoNotWork() throws Exception {
        mvc.perform(post("/api/auth/microsoft").with(csrf())
                        .contentType(MediaType.APPLICATION_JSON).content("{\"bypassKey\":\"dev\"}"))
                .andExpect(status().isNotFound());
        verifyNoInteractions(userService);
    }

    @Test
    void theUiIsToldNotToOfferDevSignInOrBulkDeletes() throws Exception {
        mvc.perform(get("/api/auth/config"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.devLogin").value(false))
                .andExpect(jsonPath("$.destructiveActions").value(false));
    }

    @Test
    @WithMockUser(roles = "COORDINATOR")
    void writesWithoutACsrfTokenAreRefused() throws Exception {
        mvc.perform(post("/api/auth/logout")).andExpect(status().isForbidden());
    }

    @Test
    @WithMockUser(roles = "COORDINATOR")
    void deleteAllDataDoesNotExistOnAProductionServer() throws Exception {
        mvc.perform(delete("/api/admin/data").with(csrf())).andExpect(status().isNotFound());
        verifyNoInteractions(wipeService);
    }
}
