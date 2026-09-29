package com.iet.dashboard.identity.service;

import com.iet.dashboard.identity.domain.Role;
import com.iet.dashboard.identity.web.UserDto;

import java.util.Optional;

public interface UserService {

    Optional<UserDto> findById(Long id);

    Optional<UserDto> findByEmail(String email);

    UserDto getCurrentUser();

    UserDto authenticateDev(String email);

    UserDto createUser(String email, String name, Role role);
}
