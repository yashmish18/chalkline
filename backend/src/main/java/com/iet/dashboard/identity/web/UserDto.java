package com.iet.dashboard.identity.web;

import com.iet.dashboard.identity.domain.AppUser;

public record UserDto(
        Long id,
        String email,
        String name,
        String role,
        /** UNLOCKED, LOCKED or SETUP_REQUIRED for a coordinator's own session; null otherwise. */
        String coordinatorAccess
) {
    public UserDto(Long id, String email, String name, String role) {
        this(id, email, name, role, null);
    }

    public static UserDto fromEntity(AppUser user) {
        return new UserDto(
                user.getId(),
                user.getEmail(),
                user.getName(),
                user.getRole().name()
        );
    }
}
