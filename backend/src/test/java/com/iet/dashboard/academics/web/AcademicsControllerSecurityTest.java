package com.iet.dashboard.academics.web;

import com.iet.dashboard.academics.service.AcademicsService;
import com.iet.dashboard.academics.service.AllocationImportService;
import com.iet.dashboard.academics.service.PacingCalculationService;
import com.iet.dashboard.academics.service.SyllabusImportService;
import com.iet.dashboard.academics.service.TimetableImportService;
import com.iet.dashboard.directory.service.FacultyService;
import com.iet.dashboard.directory.web.FacultyDto;
import com.iet.dashboard.identity.config.AppSettings;
import com.iet.dashboard.identity.config.SecurityConfig;
import com.iet.dashboard.identity.service.UserService;
import com.iet.dashboard.identity.web.UserDto;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.RequestBuilder;

import java.util.List;
import java.util.Optional;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Who may call which academics endpoint, and whose timetable an upload lands on. The faculty id is
 * always taken from the signed-in account, never from anything the request carries.
 */
@WebMvcTest(controllers = AcademicsController.class)
@Import({SecurityConfig.class, AppSettings.class})
class AcademicsControllerSecurityTest {

    private static final long TARUNA_USER_ID = 7L;
    private static final long TARUNA_FACULTY_ID = 42L;

    @Autowired MockMvc mvc;
    @MockBean AcademicsService academicsService;
    @MockBean PacingCalculationService pacingService;
    @MockBean UserService userService;
    @MockBean FacultyService facultyService;
    @MockBean SyllabusImportService syllabusImportService;
    @MockBean AllocationImportService allocationImportService;
    @MockBean TimetableImportService timetableImportService;

    private static final MockMultipartFile SHEET =
            new MockMultipartFile("file", "tt.csv", "text/csv", "Day,Start\n".getBytes());

    private void signedInAsTaruna() {
        when(userService.getCurrentUser()).thenReturn(new UserDto(TARUNA_USER_ID, "taruna@iet.edu", "Taruna", "FACULTY"));
        when(facultyService.getFacultyByUserId(TARUNA_USER_ID)).thenReturn(Optional.of(
                new FacultyDto(TARUNA_FACULTY_ID, TARUNA_USER_ID, "Taruna", "Assistant Professor", "", "CSE", List.of())));
    }

    private void expectForbidden(RequestBuilder request) throws Exception {
        mvc.perform(request).andExpect(status().isForbidden());
    }

    @Test
    @WithMockUser(username = "taruna@iet.edu", roles = "FACULTY")
    void facultyCannotChangeCoursesOfferingsOrTerms() throws Exception {
        // Valid bodies, so the refusal comes from the role check and not from validation.
        expectForbidden(post("/api/courses").with(csrf()).contentType(MediaType.APPLICATION_JSON).content(
                "{\"code\":\"CS1\",\"name\":\"X\",\"type\":\"THEORY\"}"));
        expectForbidden(delete("/api/courses/1").with(csrf()));
        expectForbidden(post("/api/offerings/bulk").with(csrf()).contentType(MediaType.APPLICATION_JSON).content(
                "{\"courseId\":1,\"academicYear\":\"2026-27\",\"yearOfStudy\":1,\"semester\":1,\"sectionCount\":2}"));
        expectForbidden(put("/api/terms/active").with(csrf()).contentType(MediaType.APPLICATION_JSON).content(
                "{\"name\":\"Odd 2026\",\"academicYear\":\"2026-27\",\"startDate\":\"2026-07-15\",\"endDate\":\"2026-11-30\"}"));
        expectForbidden(post("/api/terms/3/activate").with(csrf()));

        verifyNoInteractions(academicsService);
    }

    @Test
    @WithMockUser(username = "taruna@iet.edu", roles = "FACULTY")
    void facultyCannotImportOrDeleteAllocations() throws Exception {
        expectForbidden(multipart("/api/allocations/import").file(SHEET).with(csrf()));
        expectForbidden(delete("/api/teaching-assignments").param("semester", "3").with(csrf()));
        expectForbidden(delete("/api/teaching-assignments/5").with(csrf()));

        verifyNoInteractions(allocationImportService, academicsService);
    }

    @Test
    void anUnauthenticatedApiCallGetsA401JsonBody() throws Exception {
        mvc.perform(get("/api/courses"))
                .andExpect(status().isUnauthorized())
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_JSON))
                .andExpect(jsonPath("$.status").value(401))
                .andExpect(jsonPath("$.title").value("Unauthorized"));
        mvc.perform(multipart("/api/timetable/import").file(SHEET).with(csrf()))
                .andExpect(status().isUnauthorized());

        verifyNoInteractions(academicsService, timetableImportService);
    }

    @Test
    @WithMockUser(username = "taruna@iet.edu", roles = "FACULTY")
    void aWriteWithoutACsrfTokenIsRefused() throws Exception {
        signedInAsTaruna();

        mvc.perform(multipart("/api/timetable/import").file(SHEET)).andExpect(status().isForbidden());

        verifyNoInteractions(timetableImportService);
    }

    /** A facultyId smuggled into the request is ignored: the upload is scoped to the signed-in account. */
    @Test
    @WithMockUser(username = "taruna@iet.edu", roles = "FACULTY")
    void aFacultyTimetableUploadIsScopedToTheSignedInFaculty() throws Exception {
        signedInAsTaruna();
        when(timetableImportService.importTimetable(any(), eq(TARUNA_FACULTY_ID)))
                .thenReturn(new TimetableImportResultDto(1, 1, 1, List.of()));

        mvc.perform(multipart("/api/timetable/import").file(SHEET)
                        .param("facultyId", "99").param("onlyFacultyId", "99").with(csrf()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.slotsCreated").value(1));

        verify(timetableImportService).importTimetable(any(), eq(TARUNA_FACULTY_ID));
        verify(timetableImportService, never()).importTimetable(any(), eq(99L));
    }

    @Test
    @WithMockUser(username = "coordinator@iet.edu", roles = "COORDINATOR")
    void aCoordinatorTimetableUploadIsTheMasterTimetable() throws Exception {
        when(userService.getCurrentUser()).thenReturn(new UserDto(1L, "coordinator@iet.edu", "HoD", "COORDINATOR"));
        when(timetableImportService.importTimetable(any(), isNull()))
                .thenReturn(new TimetableImportResultDto(3, 3, 2, List.of()));

        mvc.perform(multipart("/api/timetable/import").file(SHEET).with(csrf()))
                .andExpect(status().isOk());

        verify(timetableImportService).importTimetable(any(), isNull());
    }

    @Test
    @WithMockUser(username = "ghost@iet.edu", roles = "FACULTY")
    void aFacultyAccountWithoutAProfileCannotUpload() throws Exception {
        when(userService.getCurrentUser()).thenReturn(new UserDto(8L, "ghost@iet.edu", "Ghost", "FACULTY"));
        when(facultyService.getFacultyByUserId(8L)).thenReturn(Optional.empty());

        mvc.perform(multipart("/api/timetable/import").file(SHEET).with(csrf()))
                .andExpect(status().isForbidden());

        verifyNoInteractions(timetableImportService);
    }

    @Test
    @WithMockUser(username = "taruna@iet.edu", roles = "FACULTY")
    void aFacultyCannotReadAnotherFacultysTimetableOrCourses() throws Exception {
        signedInAsTaruna();

        mvc.perform(get("/api/academics/timetable").param("facultyId", "99")).andExpect(status().isOk());
        mvc.perform(get("/api/academics/my-courses").param("facultyId", "99")).andExpect(status().isOk());

        verify(academicsService).getTimetableForFaculty(TARUNA_FACULTY_ID);
        verify(pacingService).getMyCoursesProgress(TARUNA_FACULTY_ID);
        verify(academicsService, never()).getTimetableForFaculty(99L);
    }

    @Test
    @WithMockUser(username = "taruna@iet.edu", roles = "FACULTY")
    void aFacultyDeletesOnlyTheirOwnTimetableSlots() throws Exception {
        signedInAsTaruna();

        mvc.perform(delete("/api/academics/timetable/5").with(csrf())).andExpect(status().isNoContent());

        verify(academicsService).deleteTimetableSlot(5L, TARUNA_FACULTY_ID);
    }
}
