package com.iet.dashboard.identity.web;

import com.iet.dashboard.identity.domain.AppUser;

public record UserDto(
        Long id,
        String email,
        String name,
        String role
) {
    public static UserDto fromEntity(AppUser user) {
        return new UserDto(
                user.getId(),
                user.getEmail(),
                user.getName(),
                user.getRole().name()
        );
    }
}
