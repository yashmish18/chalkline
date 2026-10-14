package com.iet.dashboard.identity.service;

import com.iet.dashboard.identity.domain.Role;
import com.iet.dashboard.identity.web.UserDto;

import java.util.Collection;
import java.util.List;
import java.util.Optional;

public interface UserService {

    Optional<UserDto> findById(Long id);

    /** Resolves several users at once, so a directory listing does not query per row. */
    List<UserDto> findByIds(Collection<Long> ids);

    Optional<UserDto> findByEmail(String email);

    UserDto getCurrentUser();

    /** All active users holding the given role — used to fan out coordinator alerts. */
    List<UserDto> findUsersByRole(Role role);

    UserDto authenticateDev(String email);

    /**
     * Resolves an institutional (Microsoft Entra ID) account to an application user and
     * puts it in the security context. Until the Entra app registration is wired up this
     * resolves the seeded institutional account by email.
     */
    UserDto authenticateSso(String email);

    UserDto createUser(String email, String name, Role role);
}
