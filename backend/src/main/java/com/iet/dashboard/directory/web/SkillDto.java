package com.iet.dashboard.directory.web;

import com.iet.dashboard.directory.domain.Skill;

public record SkillDto(
        Long id,
        String name,
        String category
) {
    public static SkillDto fromEntity(Skill skill) {
        return new SkillDto(skill.getId(), skill.getName(), skill.getCategory());
    }
}
