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
        List<SkillDto> skills,
        int pblHours,
        /** The account email the faculty signs in with; "" when it could not be resolved. */
        String email
) {
    public FacultyDto(Long id, Long userId, String name, String designation, String qualifications, String department, List<SkillDto> skills) {
        this(id, userId, name, designation, qualifications, department, skills, 0, "");
    }

    public FacultyDto(Long id, Long userId, String name, String designation, String qualifications, String department, List<SkillDto> skills, int pblHours) {
        this(id, userId, name, designation, qualifications, department, skills, pblHours, "");
    }

    public static FacultyDto fromEntity(Faculty faculty) {
        return fromEntity(faculty, "");
    }

    public static FacultyDto fromEntity(Faculty faculty, String email) {
        List<SkillDto> skillDtos = faculty.getSkills().stream()
                .map(SkillDto::fromEntity)
                .collect(Collectors.toList());

        return new FacultyDto(
                faculty.getId(),
                faculty.getUserId(),
                faculty.getName(),
                faculty.getDesignation(),
                faculty.getQualifications(),
                faculty.getDepartmentName(),
                skillDtos,
                faculty.getPblHours(),
                email != null ? email : ""
        );
    }
}
