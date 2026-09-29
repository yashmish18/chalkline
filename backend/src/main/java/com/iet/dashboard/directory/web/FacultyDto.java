package com.iet.dashboard.directory.web;

import com.iet.dashboard.directory.domain.Faculty;

import java.util.List;
import java.util.stream.Collectors;

public record FacultyDto(
        Long id,
        Long userId,
        String name,
        String designation,
        String qualifications,
        String department,
        List<SkillDto> skills
) {
    public static FacultyDto fromEntity(Faculty faculty) {
        List<SkillDto> skillDtos = faculty.getSkills().stream()
                .map(SkillDto::fromEntity)
                .collect(Collectors.toList());

        return new FacultyDto(
                faculty.getId(),
                faculty.getUserId(),
                faculty.getName(),
                faculty.getDesignation(),
                faculty.getQualifications(),
                faculty.getDepartment(),
                skillDtos
        );
    }
}
