package com.iet.dashboard.identity.web;

import com.iet.dashboard.identity.config.SecurityConfig;
import com.iet.dashboard.identity.service.UserService;
import com.iet.dashboard.shared.error.UnauthorizedException;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.context.annotation.Import;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.web.servlet.MockMvc;

import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(controllers = UserController.class)
@Import(SecurityConfig.class)
class UserControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @MockBean
    private UserService userService;

    @Test
    void testGetMe_Unauthenticated_Returns401() throws Exception {
        mockMvc.perform(get("/api/me"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    @WithMockUser(username = "coordinator@iet.edu", roles = {"COORDINATOR"})
    void testGetMe_Authenticated_ReturnsUserDto() throws Exception {
        when(userService.getCurrentUser())
                .thenReturn(new UserDto(1L, "coordinator@iet.edu", "Dr. Sharma", "COORDINATOR"));

        mockMvc.perform(get("/api/me"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(1))
                .andExpect(jsonPath("$.email").value("coordinator@iet.edu"))
                .andExpect(jsonPath("$.role").value("COORDINATOR"));
    }

    @Test
    @WithMockUser(username = "faculty@iet.edu", roles = {"FACULTY"})
    void testGetMe_UserNotFound_Returns401() throws Exception {
        when(userService.getCurrentUser())
                .thenThrow(new UnauthorizedException("User profile not found or inactive"));

        mockMvc.perform(get("/api/me"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.title").value("Unauthorized"));
    }
}
