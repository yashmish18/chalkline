package com.iet.dashboard.notifications.web;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

public record DirectReminderRequest(
        @NotNull(message = "Faculty ID is required")
        Long facultyId,

        @NotBlank(message = "Title is required")
        String title,

        @NotBlank(message = "Message is required")
        String message
) {}
