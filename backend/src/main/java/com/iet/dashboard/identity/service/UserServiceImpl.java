package com.iet.dashboard.identity.service;

import com.iet.dashboard.identity.config.AppSettings;

import com.iet.dashboard.identity.domain.AppUser;
import com.iet.dashboard.identity.domain.Role;
import com.iet.dashboard.identity.repo.AppUserRepository;
import com.iet.dashboard.identity.web.UserDto;
import com.iet.dashboard.shared.error.ResourceNotFoundException;
import com.iet.dashboard.shared.error.UnauthorizedException;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Collections;
import java.util.List;
import java.util.Optional;

@Service
@Transactional
public class UserServiceImpl implements UserService {

    private final AppUserRepository userRepository;
    private final AppSettings settings;

    public UserServiceImpl(AppUserRepository userRepository, AppSettings settings) {
        this.userRepository = userRepository;
        this.settings = settings;
    }

    @Override
    @Transactional(readOnly = true)
    public Optional<UserDto> findById(Long id) {
        return userRepository.findById(id).map(UserDto::fromEntity);
    }

    @Override
    @Transactional(readOnly = true)
    public List<UserDto> findByIds(java.util.Collection<Long> ids) {
        if (ids == null || ids.isEmpty()) {
            return List.of();
        }
        return userRepository.findAllById(ids).stream().map(UserDto::fromEntity).toList();
    }

    @Override
    @Transactional(readOnly = true)
    public Optional<UserDto> findByEmail(String email) {
        return userRepository.findByEmailIgnoreCase(email).map(UserDto::fromEntity);
    }

    @Override
    @Transactional(readOnly = true)
    public UserDto getCurrentUser() {
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        if (auth == null || !auth.isAuthenticated() || "anonymousUser".equals(auth.getPrincipal())) {
            throw new UnauthorizedException("User is not authenticated");
        }

        String email = auth.getName();
        return userRepository.findByEmailIgnoreCase(email)
                .filter(AppUser::isActive)
                .map(user -> {
                    var access = CoordinatorAccessService.accessOf(user, auth);
                    return new UserDto(user.getId(), user.getEmail(), user.getName(), user.getRole().name(),
                            access == null ? null : access.name());
                })
                .orElseThrow(() -> new UnauthorizedException("User profile not found or inactive: " + email));
    }

    @Override
    @Transactional(readOnly = true)
    public List<UserDto> findUsersByRole(Role role) {
        return userRepository.findByRole(role).stream()
                .filter(AppUser::isActive)
                .map(UserDto::fromEntity)
                .collect(java.util.stream.Collectors.toList());
    }

    @Override
    public UserDto authenticateDev(String email) {
        return authenticateSso(email);
    }

    @Override
    public UserDto authenticateSso(String email) {
        AppUser user = userRepository.findByEmailIgnoreCase(email)
                .filter(AppUser::isActive)
                .orElseThrow(() -> new ResourceNotFoundException("Active user not found for email: " + email));

        // A coordinator's session starts locked until the coordinator password is entered.
        var authority = CoordinatorAccessService.signInAuthority(user, settings != null && settings.devMode());
        var authToken = new UsernamePasswordAuthenticationToken(user.getEmail(), null, Collections.singletonList(authority));
        SecurityContextHolder.getContext().setAuthentication(authToken);

        return UserDto.fromEntity(user);
    }

    @Override
    public UserDto createUser(String email, String name, Role role) {
        if (userRepository.existsByEmailIgnoreCase(email)) {
            throw new IllegalArgumentException("User with email already exists: " + email);
        }
        AppUser user = new AppUser(email, name, role);
        AppUser saved = userRepository.save(user);
        return UserDto.fromEntity(saved);
    }
}
