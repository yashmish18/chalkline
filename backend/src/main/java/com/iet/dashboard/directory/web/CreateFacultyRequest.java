package com.iet.dashboard.directory.web;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import java.util.List;

public record CreateFacultyRequest(
        @NotBlank(message = "Email is required")
        @Email(message = "Invalid email format")
        String email,

        @NotBlank(message = "Name is required")
        String name,

        @NotBlank(message = "Designation is required")
        String designation,

        @NotBlank(message = "Qualifications are required")
        String qualifications,

        String department,

        List<Long> skillIds
) {
}
