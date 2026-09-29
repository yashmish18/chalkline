package com.iet.dashboard.identity.service;

import com.iet.dashboard.identity.domain.AppUser;
import com.iet.dashboard.identity.domain.Role;
import com.iet.dashboard.identity.repo.AppUserRepository;
import com.iet.dashboard.identity.web.UserDto;
import com.iet.dashboard.shared.error.ResourceNotFoundException;
import com.iet.dashboard.shared.error.UnauthorizedException;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.core.context.SecurityContextHolder;

import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class UserServiceTest {

    @Mock
    private AppUserRepository userRepository;

    @InjectMocks
    private UserServiceImpl userService;

    @AfterEach
    void tearDown() {
        SecurityContextHolder.clearContext();
    }

    @Test
    void testAuthenticateDev_Success() {
        AppUser user = new AppUser("coordinator@iet.edu", "Dr. Sharma", Role.COORDINATOR);
        user.setId(1L);

        when(userRepository.findByEmailIgnoreCase("coordinator@iet.edu")).thenReturn(Optional.of(user));

        UserDto result = userService.authenticateDev("coordinator@iet.edu");

        assertNotNull(result);
        assertEquals(1L, result.id());
        assertEquals("coordinator@iet.edu", result.email());
        assertEquals("COORDINATOR", result.role());
        assertNotNull(SecurityContextHolder.getContext().getAuthentication());
        assertEquals("coordinator@iet.edu", SecurityContextHolder.getContext().getAuthentication().getName());
    }

    @Test
    void testAuthenticateDev_UserNotFound_ThrowsException() {
        when(userRepository.findByEmailIgnoreCase("unknown@iet.edu")).thenReturn(Optional.empty());

        assertThrows(ResourceNotFoundException.class, () -> userService.authenticateDev("unknown@iet.edu"));
    }

    @Test
    void testGetCurrentUser_Unauthenticated_ThrowsUnauthorizedException() {
        SecurityContextHolder.clearContext();

        assertThrows(UnauthorizedException.class, () -> userService.getCurrentUser());
    }

    @Test
    void testGetCurrentUser_Authenticated_ReturnsUser() {
        AppUser user = new AppUser("faculty@iet.edu", "Prof. Verma", Role.FACULTY);
        user.setId(2L);

        when(userRepository.findByEmailIgnoreCase("faculty@iet.edu")).thenReturn(Optional.of(user));

        userService.authenticateDev("faculty@iet.edu");
        UserDto result = userService.getCurrentUser();

        assertNotNull(result);
        assertEquals("faculty@iet.edu", result.email());
        assertEquals("FACULTY", result.role());
    }

    @Test
    void testCreateUser_Success() {
        AppUser savedUser = new AppUser("new@iet.edu", "New Faculty", Role.FACULTY);
        savedUser.setId(10L);

        when(userRepository.existsByEmailIgnoreCase("new@iet.edu")).thenReturn(false);
        when(userRepository.save(any(AppUser.class))).thenReturn(savedUser);

        UserDto result = userService.createUser("new@iet.edu", "New Faculty", Role.FACULTY);

        assertNotNull(result);
        assertEquals(10L, result.id());
        assertEquals("new@iet.edu", result.email());
    }

    @Test
    void testCreateUser_AlreadyExists_ThrowsException() {
        when(userRepository.existsByEmailIgnoreCase("exists@iet.edu")).thenReturn(true);

        assertThrows(IllegalArgumentException.class, () -> userService.createUser("exists@iet.edu", "Exists", Role.FACULTY));
    }
}
