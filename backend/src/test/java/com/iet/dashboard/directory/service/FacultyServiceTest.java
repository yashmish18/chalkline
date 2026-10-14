package com.iet.dashboard.directory.service;

import com.iet.dashboard.directory.domain.Department;
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

    @Mock
    private com.iet.dashboard.directory.repo.DepartmentRepository departmentRepository;

    @Mock
    private com.iet.dashboard.identity.repo.AppUserRepository appUserRepository;

    @Mock
    private com.iet.dashboard.deadlines.repo.DeadlineRepository deadlineRepository;

    @InjectMocks
    private FacultyServiceImpl facultyService;

    @Test
    void testGetAllFaculty_NoFilter_ReturnsAll() {
        Faculty faculty = new Faculty(2L, "Dr. Amit", "Assoc Prof", "PhD", new com.iet.dashboard.directory.domain.Department("IT", "Information Technology"));
        faculty.setId(1L);
        faculty.setSkills(Collections.singleton(new Skill("Networks", "Systems")));

        when(facultyRepository.findAllWithSkills()).thenReturn(List.of(faculty));

        List<FacultyDto> result = facultyService.getAllFaculty(null, null);

        assertEquals(1, result.size());
        assertEquals("Dr. Amit", result.get(0).name());
        assertEquals(1, result.get(0).skills().size());
    }

    @Test
    void testGetAllFaculty_WithSkillFilter_ReturnsFiltered() {
        Faculty faculty = new Faculty(2L, "Dr. Amit", "Assoc Prof", "PhD", new com.iet.dashboard.directory.domain.Department("IT", "Information Technology"));
        faculty.setId(1L);

        when(facultyRepository.findBySkillName("Networks")).thenReturn(List.of(faculty));

        List<FacultyDto> result = facultyService.getAllFaculty("Networks", null);

        assertEquals(1, result.size());
        assertEquals("Dr. Amit", result.get(0).name());
    }

    @Test
    void testGetFacultyById_Found() {
        Faculty faculty = new Faculty(2L, "Dr. Amit", "Assoc Prof", "PhD", new com.iet.dashboard.directory.domain.Department("IT", "Information Technology"));
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

        var it = new com.iet.dashboard.directory.domain.Department("IT", "Information Technology");
        when(departmentRepository.findByCodeIgnoreCase("IT")).thenReturn(Optional.of(it));
        Faculty savedFaculty = new Faculty(10L, "Dr. New", "Assistant Professor", "PhD", it);
        savedFaculty.setId(5L);
        when(facultyRepository.save(any(Faculty.class))).thenReturn(savedFaculty);

        FacultyDto result = facultyService.createFaculty(request);

        assertNotNull(result);
        assertEquals(5L, result.id());
        assertEquals("Dr. New", result.name());
    }

    @Test
    void testUpdateFacultySkillsByNames_NormalizesAndCreatesMissing() {
        Faculty faculty = new Faculty(2L, "Dr. Amit", "Assoc Prof", "PhD", new com.iet.dashboard.directory.domain.Department("IT", "Information Technology"));
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

    @Test
    void testDeleteFaculty_Success() {
        Faculty faculty = new Faculty(99L, "Dr. Amit", "Professor", "PhD",
                new Department("IT", "Information Technology"));
        faculty.setId(10L);
        when(facultyRepository.findById(10L)).thenReturn(Optional.of(faculty));
        when(userService.findUsersByRole(Role.COORDINATOR))
                .thenReturn(List.of(new UserDto(1L, "coordinator@iet.edu", "Coordinator", "COORDINATOR")));

        facultyService.deleteFaculty(10L);

        verify(facultyRepository).deleteById(10L);
        verify(deadlineRepository).deleteByCreatedByIn(List.of(99L));
        verify(appUserRepository).deleteById(99L);
    }

    @Test
    void testDeleteFaculty_CoordinatorProfile_Refused() {
        Faculty coordinator = new Faculty(1L, "Coordinator", "Coordinator", "",
                new Department("IT", "Information Technology"));
        coordinator.setId(1L);
        when(facultyRepository.findById(1L)).thenReturn(Optional.of(coordinator));
        when(userService.findUsersByRole(Role.COORDINATOR))
                .thenReturn(List.of(new UserDto(1L, "coordinator@iet.edu", "Coordinator", "COORDINATOR")));

        assertThrows(com.iet.dashboard.shared.error.ForbiddenException.class, () -> facultyService.deleteFaculty(1L));
        verify(facultyRepository, never()).deleteById(any());
        verify(appUserRepository, never()).deleteById(any());
    }

    @Test
    void testDeleteAllFaculty_KeepsCoordinatorProfile() {
        Department department = new Department("IT", "Information Technology");
        Faculty coordinator = new Faculty(1L, "Coordinator", "Coordinator", "", department);
        coordinator.setId(1L);
        Faculty first = new Faculty(2L, "Dr. Amit Sinhal", "Professor", "PhD", department);
        first.setId(2L);
        Faculty second = new Faculty(3L, "Dr. Surbhi Chhabra", "Professor", "PhD", department);
        second.setId(3L);

        when(userService.findUsersByRole(Role.COORDINATOR))
                .thenReturn(List.of(new UserDto(1L, "coordinator@iet.edu", "Coordinator", "COORDINATOR")));
        when(facultyRepository.findAll()).thenReturn(List.of(coordinator, first, second));

        int deleted = facultyService.deleteAllFaculty();

        assertEquals(2, deleted);
        @SuppressWarnings("unchecked")
        var captor = org.mockito.ArgumentCaptor.forClass(List.class);
        verify(facultyRepository).deleteAllInBatch(captor.capture());
        List<Faculty> removed = captor.getValue();
        assertEquals(2, removed.size());
        assertTrue(removed.stream().noneMatch(f -> f.getUserId().equals(1L)), "the coordinator's own profile must survive");
    }

    @Test
    void testDeleteAllFaculty_RemovesLinkedLogins_KeepsCoordinatorAccount() {
        when(userService.findUsersByRole(Role.COORDINATOR))
                .thenReturn(List.of(new UserDto(1L, "coordinator@iet.edu", "Coordinator", "COORDINATOR")));
        Department department = new Department("IT", "Information Technology");
        Faculty coordinator = new Faculty(1L, "Coordinator", "Coordinator", "", department);
        coordinator.setId(1L);
        Faculty staff = new Faculty(7L, "Dr. Amit Sinhal", "Professor", "PhD", department);
        staff.setId(2L);
        when(facultyRepository.findAll()).thenReturn(List.of(coordinator, staff));

        int deleted = facultyService.deleteAllFaculty();

        assertEquals(1, deleted);
        // The creator's deadlines would block the account delete (NO ACTION FK), so they go first.
        verify(deadlineRepository).deleteByCreatedByIn(List.of(7L));
        @SuppressWarnings("unchecked")
        var captor = org.mockito.ArgumentCaptor.forClass(List.class);
        verify(facultyRepository).deleteAllInBatch(captor.capture());
        List<Faculty> removed = captor.getValue();
        assertEquals(1, removed.size());
        assertEquals(7L, removed.get(0).getUserId());
        // The sign-in account is removed with the profile — but never the coordinator's.
        verify(appUserRepository).deleteAllById(List.of(7L));
    }

    @Test
    void testDeleteAllFaculty_NoDeleteWhenOnlyCoordinator() {
        when(userService.findUsersByRole(Role.COORDINATOR))
                .thenReturn(List.of(new UserDto(1L, "coordinator@iet.edu", "Coordinator", "COORDINATOR")));
        Faculty coordinator = new Faculty(1L, "Coordinator", "Coordinator", "",
                new Department("IT", "Information Technology"));
        coordinator.setId(1L);
        when(facultyRepository.findAll()).thenReturn(List.of(coordinator));

        assertEquals(0, facultyService.deleteAllFaculty());
        verify(facultyRepository, never()).deleteAllInBatch(any());
    }
}
