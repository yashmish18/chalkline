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
import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;

@Service
@Transactional
public class FacultyServiceImpl implements FacultyService {

    private final FacultyRepository facultyRepository;
    private final SkillRepository skillRepository;
    private final UserService userService;

    public FacultyServiceImpl(FacultyRepository facultyRepository, SkillRepository skillRepository, UserService userService) {
        this.facultyRepository = facultyRepository;
        this.skillRepository = skillRepository;
        this.userService = userService;
    }

    @Override
    @Transactional(readOnly = true)
    public List<FacultyDto> getAllFaculty(String skillFilter) {
        List<Faculty> facultyList;
        if (skillFilter != null && !skillFilter.isBlank()) {
            facultyList = facultyRepository.findBySkillName(skillFilter.trim());
        } else {
            facultyList = facultyRepository.findAllWithSkills();
        }
        return facultyList.stream().map(FacultyDto::fromEntity).collect(Collectors.toList());
    }

    @Override
    @Transactional(readOnly = true)
    public FacultyDto getFacultyById(Long id) {
        return facultyRepository.findByIdWithSkills(id)
                .map(FacultyDto::fromEntity)
                .orElseThrow(() -> new ResourceNotFoundException("Faculty not found with id: " + id));
    }

    @Override
    @Transactional(readOnly = true)
    public Optional<FacultyDto> getFacultyByUserId(Long userId) {
        return facultyRepository.findByUserId(userId).map(FacultyDto::fromEntity);
    }

    @Override
    @Transactional(readOnly = true)
    public List<SkillDto> getAllSkills() {
        return skillRepository.findAll().stream().map(SkillDto::fromEntity).collect(Collectors.toList());
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
                request.department()
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
}
