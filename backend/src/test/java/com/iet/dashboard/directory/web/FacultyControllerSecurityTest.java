package com.iet.dashboard.directory.web;

import com.iet.dashboard.directory.service.FacultyService;
import com.iet.dashboard.identity.config.AppSettings;
import com.iet.dashboard.identity.config.SecurityConfig;
import com.iet.dashboard.identity.service.UserService;
import com.iet.dashboard.tracking.service.TrackingService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.web.servlet.MockMvc;

import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;
import static org.mockito.Mockito.verifyNoInteractions;

/**
 * Skills come from the allocation sheet and are managed by the coordinator. Found by probing the
 * running app: any signed-in faculty member could overwrite any colleague's skills.
 */
@WebMvcTest(controllers = FacultyController.class)
@Import({SecurityConfig.class, AppSettings.class})
class FacultyControllerSecurityTest {

    @Autowired MockMvc mvc;
    @MockBean FacultyService facultyService;
    @MockBean UserService userService;
    @MockBean TrackingService trackingService;

    @Test
    @WithMockUser(username = "amit@iet.edu", roles = "FACULTY")
    void aFacultyMemberCannotChangeAnyonesSkills() throws Exception {
        mvc.perform(put("/api/faculty/3/skills-by-name").with(csrf())
                        .contentType(MediaType.APPLICATION_JSON).content("{\"skills\":[\"Hacked\"]}"))
                .andExpect(status().isForbidden());
        mvc.perform(put("/api/faculty/3/skills").with(csrf())
                        .contentType(MediaType.APPLICATION_JSON).content("{\"skillIds\":[1]}"))
                .andExpect(status().isForbidden());

        verifyNoInteractions(facultyService);
    }
}
