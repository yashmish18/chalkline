package com.iet.dashboard.directory.web;

import jakarta.validation.constraints.NotNull;
import java.util.List;

public record UpdateFacultySkillsByNameRequest(
        @NotNull(message = "Skills list is required")
        List<String> skills
) {
}
