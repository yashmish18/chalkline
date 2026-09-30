package com.iet.dashboard.directory.service;

import com.iet.dashboard.directory.domain.Faculty;
import com.iet.dashboard.directory.domain.Skill;
import com.iet.dashboard.directory.repo.FacultyRepository;
import com.iet.dashboard.directory.repo.SkillRepository;
import com.iet.dashboard.directory.web.CreateFacultyRequest;
import com.iet.dashboard.directory.web.FacultyDto;
import com.iet.dashboard.identity.domain.Role;
import com.iet.dashboard.identity.service.UserService;
import com.iet.dashboard.identity.web.UserDto;
import com.iet.dashboard.shared.error.ResourceNotFoundException;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.Collections;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class FacultyServiceTest {

    @Mock
    private FacultyRepository facultyRepository;

    @Mock
    private SkillRepository skillRepository;

    @Mock
    private UserService userService;

    @InjectMocks
    private FacultyServiceImpl facultyService;

    @Test
    void testGetAllFaculty_NoFilter_ReturnsAll() {
        Faculty faculty = new Faculty(2L, "Dr. Amit", "Assoc Prof", "PhD", "IT");
        faculty.setId(1L);
        faculty.setSkills(Collections.singleton(new Skill("Networks", "Systems")));

        when(facultyRepository.findAllWithSkills()).thenReturn(List.of(faculty));

        List<FacultyDto> result = facultyService.getAllFaculty(null);

        assertEquals(1, result.size());
        assertEquals("Dr. Amit", result.get(0).name());
        assertEquals(1, result.get(0).skills().size());
    }

    @Test
    void testGetAllFaculty_WithSkillFilter_ReturnsFiltered() {
        Faculty faculty = new Faculty(2L, "Dr. Amit", "Assoc Prof", "PhD", "IT");
        faculty.setId(1L);

        when(facultyRepository.findBySkillName("Networks")).thenReturn(List.of(faculty));

        List<FacultyDto> result = facultyService.getAllFaculty("Networks");

        assertEquals(1, result.size());
        assertEquals("Dr. Amit", result.get(0).name());
    }

    @Test
    void testGetFacultyById_Found() {
        Faculty faculty = new Faculty(2L, "Dr. Amit", "Assoc Prof", "PhD", "IT");
        faculty.setId(1L);

        when(facultyRepository.findByIdWithSkills(1L)).thenReturn(Optional.of(faculty));

        FacultyDto result = facultyService.getFacultyById(1L);

        assertNotNull(result);
        assertEquals(1L, result.id());
    }

    @Test
    void testGetFacultyById_NotFound_ThrowsException() {
        when(facultyRepository.findByIdWithSkills(99L)).thenReturn(Optional.empty());

        assertThrows(ResourceNotFoundException.class, () -> facultyService.getFacultyById(99L));
    }

    @Test
    void testCreateFaculty_Success() {
        CreateFacultyRequest request = new CreateFacultyRequest(
                "new@iet.edu", "Dr. New", "Assistant Professor", "PhD", "IT", Collections.emptyList()
        );

        when(userService.findByEmail("new@iet.edu")).thenReturn(Optional.empty());
        when(userService.createUser("new@iet.edu", "Dr. New", Role.FACULTY))
                .thenReturn(new UserDto(10L, "new@iet.edu", "Dr. New", "FACULTY"));

        Faculty savedFaculty = new Faculty(10L, "Dr. New", "Assistant Professor", "PhD", "IT");
        savedFaculty.setId(5L);
        when(facultyRepository.save(any(Faculty.class))).thenReturn(savedFaculty);

        FacultyDto result = facultyService.createFaculty(request);

        assertNotNull(result);
        assertEquals(5L, result.id());
        assertEquals("Dr. New", result.name());
    }

    @Test
    void testUpdateFacultySkillsByNames_NormalizesAndCreatesMissing() {
        Faculty faculty = new Faculty(2L, "Dr. Amit", "Assoc Prof", "PhD", "IT");
        faculty.setId(1L);

        when(facultyRepository.findByIdWithSkills(1L)).thenReturn(Optional.of(faculty));

        Skill existingSkill = new Skill("Machine Learning", "AI");
        when(skillRepository.findByNameIgnoreCase("Machine Learning")).thenReturn(Optional.of(existingSkill));

        Skill newSkill = new Skill("Rust Programming", "Technical");
        when(skillRepository.findByNameIgnoreCase("Rust Programming")).thenReturn(Optional.empty());
        when(skillRepository.save(any(Skill.class))).thenReturn(newSkill);

        when(facultyRepository.save(any(Faculty.class))).thenAnswer(invocation -> invocation.getArgument(0));

        FacultyDto result = facultyService.updateFacultySkillsByNames(1L, List.of(" Machine Learning ", "Rust Programming"));

        assertNotNull(result);
        assertEquals(2, result.skills().size());
        verify(skillRepository).save(any(Skill.class));
    }
}
