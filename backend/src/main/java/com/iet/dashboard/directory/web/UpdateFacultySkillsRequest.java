package com.iet.dashboard.directory.web;

import jakarta.validation.constraints.NotNull;
import java.util.List;

public record UpdateFacultySkillsRequest(
        @NotNull(message = "Skill IDs list is required")
        List<Long> skillIds
) {
}
