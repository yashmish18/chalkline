package com.iet.dashboard.directory.service;

import com.iet.dashboard.directory.domain.Faculty;
import com.iet.dashboard.directory.domain.Skill;
import com.iet.dashboard.directory.repo.FacultyRepository;
import com.iet.dashboard.directory.repo.SkillRepository;
import com.iet.dashboard.directory.web.CreateFacultyRequest;
import com.iet.dashboard.directory.web.FacultyDto;
import com.iet.dashboard.directory.web.SkillDto;
import com.iet.dashboard.identity.domain.Role;
import com.iet.dashboard.identity.service.UserService;
import com.iet.dashboard.identity.web.UserDto;
import com.iet.dashboard.shared.error.ResourceNotFoundException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;

@Service
@Transactional
public class FacultyServiceImpl implements FacultyService {

    private final FacultyRepository facultyRepository;
    private final SkillRepository skillRepository;
    private final com.iet.dashboard.directory.repo.DepartmentRepository departmentRepository;
    private final UserService userService;
    private final com.iet.dashboard.identity.repo.AppUserRepository appUserRepository;
    private final com.iet.dashboard.deadlines.repo.DeadlineRepository deadlineRepository;

    public FacultyServiceImpl(
            FacultyRepository facultyRepository,
            SkillRepository skillRepository,
            com.iet.dashboard.directory.repo.DepartmentRepository departmentRepository,
            UserService userService,
            com.iet.dashboard.identity.repo.AppUserRepository appUserRepository,
            com.iet.dashboard.deadlines.repo.DeadlineRepository deadlineRepository
    ) {
        this.facultyRepository = facultyRepository;
        this.skillRepository = skillRepository;
        this.departmentRepository = departmentRepository;
        this.userService = userService;
        this.appUserRepository = appUserRepository;
        this.deadlineRepository = deadlineRepository;
    }

    @Override
    @Transactional(readOnly = true)
    public List<FacultyDto> getAllFaculty(String skillFilter, Long departmentId) {
        List<Faculty> facultyList;
        if (skillFilter != null && !skillFilter.isBlank()) {
            facultyList = facultyRepository.findBySkillName(skillFilter.trim()).stream()
                    .filter(f -> departmentId == null || departmentId.equals(f.getDepartmentId()))
                    .collect(Collectors.toList());
        } else if (departmentId != null) {
            facultyList = facultyRepository.findByDepartmentIdWithSkills(departmentId);
        } else {
            facultyList = facultyRepository.findAllWithSkills();
        }
        Map<Long, String> emails = emailsFor(facultyList);
        return facultyList.stream()
                .map(f -> FacultyDto.fromEntity(f, emails.getOrDefault(f.getUserId(), "")))
                .collect(Collectors.toList());
    }

    @Override
    @Transactional(readOnly = true)
    public FacultyDto getFacultyById(Long id) {
        Faculty faculty = facultyRepository.findByIdWithSkills(id)
                .orElseThrow(() -> new ResourceNotFoundException("Faculty not found with id: " + id));
        return FacultyDto.fromEntity(faculty, emailOf(faculty.getUserId()));
    }

    @Override
    @Transactional(readOnly = true)
    public Optional<FacultyDto> getFacultyByUserId(Long userId) {
        return facultyRepository.findByUserId(userId)
                .map(f -> FacultyDto.fromEntity(f, emailOf(f.getUserId())));
    }

    /** userId → account email for a whole listing, in one query. */
    private Map<Long, String> emailsFor(List<Faculty> facultyList) {
        List<Long> userIds = facultyList.stream().map(Faculty::getUserId).distinct().toList();
        return userService.findByIds(userIds).stream()
                .collect(Collectors.toMap(UserDto::id, UserDto::email, (a, b) -> a));
    }

    private String emailOf(Long userId) {
        return userService.findById(userId).map(UserDto::email).orElse("");
    }

    @Override
    @Transactional(readOnly = true)
    public List<SkillDto> getAllSkills() {
        return skillRepository.findAll().stream().map(SkillDto::fromEntity).collect(Collectors.toList());
    }

    @Override
    @Transactional(readOnly = true)
    public List<com.iet.dashboard.directory.web.DepartmentDto> getAllDepartments() {
        return departmentRepository.findAll().stream()
                .map(com.iet.dashboard.directory.web.DepartmentDto::fromEntity)
                .collect(Collectors.toList());
    }

    @Override
    public FacultyDto createFaculty(CreateFacultyRequest request) {
        UserDto user = userService.findByEmail(request.email())
                .orElseGet(() -> userService.createUser(request.email(), request.name(), Role.FACULTY));

        Faculty faculty = new Faculty(
                user.id(),
                request.name(),
                request.designation(),
                request.qualifications(),
                resolveDepartment(request.department())
        );

        if (request.skillIds() != null && !request.skillIds().isEmpty()) {
            Set<Skill> skills = new HashSet<>(skillRepository.findAllById(request.skillIds()));
            faculty.setSkills(skills);
        }

        Faculty saved = facultyRepository.save(faculty);
        return FacultyDto.fromEntity(saved);
    }

    @Override
    public FacultyDto updateFacultySkills(Long facultyId, List<Long> skillIds) {
        Faculty faculty = facultyRepository.findByIdWithSkills(facultyId)
                .orElseThrow(() -> new ResourceNotFoundException("Faculty not found with id: " + facultyId));

        Set<Skill> skills = new HashSet<>(skillRepository.findAllById(skillIds));
        faculty.setSkills(skills);
        Faculty updated = facultyRepository.save(faculty);
        return FacultyDto.fromEntity(updated);
    }

    @Override
    public FacultyDto updateFacultySkillsByNames(Long facultyId, List<String> skillNames) {
        Faculty faculty = facultyRepository.findByIdWithSkills(facultyId)
                .orElseThrow(() -> new ResourceNotFoundException("Faculty not found with id: " + facultyId));

        Set<Skill> resolvedSkills = new HashSet<>();
        if (skillNames != null) {
            for (String rawName : skillNames) {
                if (rawName == null || rawName.trim().isBlank()) {
                    continue;
                }
                String trimmed = rawName.trim();
                Skill skill = skillRepository.findByNameIgnoreCase(trimmed)
                        .orElseGet(() -> skillRepository.save(new Skill(trimmed, "Technical")));
                resolvedSkills.add(skill);
            }
        }

        faculty.setSkills(resolvedSkills);
        Faculty updated = facultyRepository.save(faculty);
        return FacultyDto.fromEntity(updated);
    }

    /** Finds a department by code or name, creating it when the name is new. Blank means none. */
    private com.iet.dashboard.directory.domain.Department resolveDepartment(String codeOrName) {
        if (codeOrName == null || codeOrName.isBlank()) {
            return null;
        }
        String value = codeOrName.trim();
        return departmentRepository.findByCodeIgnoreCase(value)
                .or(() -> departmentRepository.findByNameIgnoreCase(value))
                .orElseGet(() -> departmentRepository.save(new com.iet.dashboard.directory.domain.Department(
                        value.replaceAll("[^A-Za-z]", "").toUpperCase(), value)));
    }

    @Override
    public void deleteFaculty(Long id) {
        Faculty faculty = facultyRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Faculty member not found: " + id));
        boolean isCoordinator = faculty.getUserId() != null
                && userService.findUsersByRole(Role.COORDINATOR).stream()
                        .anyMatch(u -> u.id().equals(faculty.getUserId()));
        if (isCoordinator) {
            throw new com.iet.dashboard.shared.error.ForbiddenException(
                    "The coordinator's own profile cannot be deleted.");
        }
        if (faculty.getUserId() != null) {
            // Deadlines hard-reference their creator's account — drop those before the account.
            deadlineRepository.deleteByCreatedByIn(List.of(faculty.getUserId()));
        }
        facultyRepository.deleteById(id);
        // The sign-in account goes too, so a deleted faculty member cannot sign in any more.
        if (faculty.getUserId() != null) {
            appUserRepository.deleteById(faculty.getUserId());
        }
    }

    @Override
    public int deleteAllFaculty() {
        Set<Long> coordinatorUserIds = userService.findUsersByRole(Role.COORDINATOR).stream()
                .map(UserDto::id)
                .collect(Collectors.toSet());
        List<Faculty> removable = facultyRepository.findAll().stream()
                .filter(f -> !coordinatorUserIds.contains(f.getUserId()))
                .collect(Collectors.toList());
        if (removable.isEmpty()) {
            return 0;
        }
        List<Long> removedUserIds = removable.stream()
                .map(Faculty::getUserId)
                .filter(java.util.Objects::nonNull)
                .distinct()
                .collect(Collectors.toList());
        // Deadlines keep a hard FK (NO ACTION) on their creator's account — clear those first.
        deadlineRepository.deleteByCreatedByIn(removedUserIds);
        facultyRepository.deleteAllInBatch(removable);
        // The accounts go too, so a deleted faculty member can no longer sign in.
        appUserRepository.deleteAllById(removedUserIds);
        return removable.size();
    }
}
