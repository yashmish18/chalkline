package com.iet.dashboard.submissions.web;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;

public record ReviewRequest(
        @NotBlank(message = "Action is required")
        @Pattern(regexp = "^(ACCEPT|RETURN)$", message = "Action must be ACCEPT or RETURN")
        String action,

        String comment
) {}
