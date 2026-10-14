package com.iet.dashboard.identity.service;

import com.iet.dashboard.identity.domain.AppUser;
import com.iet.dashboard.identity.domain.Role;
import com.iet.dashboard.identity.repo.AppUserRepository;
import com.iet.dashboard.shared.error.ForbiddenException;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.test.context.ActiveProfiles;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/** The admin chooses an admin password at first sign-in; it exists only as a BCrypt hash. */
@DataJpaTest
@ActiveProfiles("test")
class AdminAccessServiceTest {

    private static final String CHOSEN_PHRASE = "Correct-Horse-Battery-9";
    private static final Instant T0 = Instant.parse("2026-10-14T05:00:00Z");

    @Autowired AppUserRepository users;

    private final BCryptPasswordEncoder encoder = new BCryptPasswordEncoder(4);
    private AppUser admin;
    private AppUser coordinator;

    @BeforeEach
    void setUp() {
        admin = new AppUser("s.taruna@jklu.edu.in", "Dr. S Taruna", Role.COORDINATOR);
        admin.setAdmin(true);
        admin = users.save(admin);
        coordinator = users.save(new AppUser("amit.sinhal@jklu.edu.in", "Dr. Amit Sinhal", Role.COORDINATOR));
        signedInAs(admin);
    }

    @AfterEach
    void signOut() {
        SecurityContextHolder.clearContext();
    }

    private AdminAccessService service(Instant now) {
        return new AdminAccessService(users, encoder, Clock.fixed(now, ZoneOffset.UTC));
    }

    private static void signedInAs(AppUser user) {
        SecurityContextHolder.getContext().setAuthentication(new UsernamePasswordAuthenticationToken(
                user.getEmail(), null, List.of(AdminAccessService.signInAuthority(user))));
    }

    @Test
    void theAdminChoosesThePasswordOnceAndOnlyAHashIsStored() {
        AppUser saved = service(T0).setInitialPassword(CHOSEN_PHRASE);

        assertThat(saved.getCoordinatorPasswordHash()).startsWith("$2a$").doesNotContain(CHOSEN_PHRASE);
        assertThat(encoder.matches(CHOSEN_PHRASE, saved.getCoordinatorPasswordHash())).isTrue();
        assertThatThrownBy(() -> service(T0).setInitialPassword("Another-Password-123"))
                .as("nobody can overwrite it from a signed-in session").isInstanceOf(ForbiddenException.class);
    }

    @Test
    void onlyTheAdminHasAnAdminPassword() {
        signedInAs(coordinator);
        assertThatThrownBy(() -> service(T0).setInitialPassword(CHOSEN_PHRASE)).isInstanceOf(ForbiddenException.class);
        assertThatThrownBy(() -> service(T0).unlock(CHOSEN_PHRASE)).isInstanceOf(ForbiddenException.class);
    }

    @Test
    void weakPasswordsAreRefusedAndNothingIsStored() {
        AdminAccessService service = service(T0);
        assertThatThrownBy(() -> service.setInitialPassword("short-1")).hasMessageContaining("at least 12");
        assertThatThrownBy(() -> service.setInitialPassword("            ")).hasMessageContaining("spaces");
        assertThatThrownBy(() -> service.setInitialPassword("my-s.taruna-pass")).hasMessageContaining("email");
        assertThatThrownBy(() -> service.setInitialPassword("x".repeat(73))).hasMessageContaining("at most 72");
        assertThatThrownBy(() -> service.setInitialPassword(null)).isInstanceOf(IllegalArgumentException.class);
        assertThat(users.findById(admin.getId()).orElseThrow().getCoordinatorPasswordHash()).isNull();
    }

    @Test
    void fiveWrongPasswordsLockAdminAccessForFifteenMinutes() {
        service(T0).setInitialPassword(CHOSEN_PHRASE);
        AdminAccessService now = service(T0);
        for (int i = 0; i < 4; i++) {
            assertThat(now.unlock("wrong-password-" + i)).isEqualTo(AdminAccessService.UnlockResult.WRONG_PASSWORD);
        }
        assertThat(now.unlock("wrong-again")).isEqualTo(AdminAccessService.UnlockResult.TEMPORARILY_LOCKED);
        assertThat(now.unlock(CHOSEN_PHRASE)).as("even the right password waits out the lock")
                .isEqualTo(AdminAccessService.UnlockResult.TEMPORARILY_LOCKED);
        assertThat(service(T0.plus(Duration.ofMinutes(14))).unlock(CHOSEN_PHRASE))
                .isEqualTo(AdminAccessService.UnlockResult.TEMPORARILY_LOCKED);
        assertThat(service(T0.plus(Duration.ofMinutes(16))).unlock(CHOSEN_PHRASE))
                .isEqualTo(AdminAccessService.UnlockResult.UNLOCKED);
        assertThat(users.findById(admin.getId()).orElseThrow().getCoordinatorFailedAttempts()).isZero();
    }

    @Test
    void unlockingBeforeAPasswordExistsIsRefused() {
        assertThatThrownBy(() -> service(T0).unlock(CHOSEN_PHRASE)).isInstanceOf(ForbiddenException.class);
    }

    @Test
    void onlyTheAdminStartsLockedCoordinatorsAndFacultyGoStraightIn() {
        assertThat(AdminAccessService.signInAuthority(admin).getAuthority()).isEqualTo(AdminAccessService.LOCKED_AUTHORITY);
        assertThat(AdminAccessService.signInAuthority(coordinator).getAuthority()).isEqualTo("ROLE_COORDINATOR");
        assertThat(AdminAccessService.signInAuthority(new AppUser("f@jklu.edu.in", "F", Role.FACULTY)).getAuthority())
                .isEqualTo("ROLE_FACULTY");
    }

    @Test
    void theAdminSessionIsToldWhetherItIsOpenLockedOrAwaitingItsPassword() {
        var locked = new UsernamePasswordAuthenticationToken(admin.getEmail(), null,
                List.of(new SimpleGrantedAuthority(AdminAccessService.LOCKED_AUTHORITY)));
        var open = new UsernamePasswordAuthenticationToken(admin.getEmail(), null,
                List.of(new SimpleGrantedAuthority("ROLE_COORDINATOR")));

        assertThat(AdminAccessService.accessOf(admin, locked)).isEqualTo(AdminAccessService.Access.SETUP_REQUIRED);
        admin.setCoordinatorPasswordHash("hash");
        assertThat(AdminAccessService.accessOf(admin, locked)).isEqualTo(AdminAccessService.Access.LOCKED);
        assertThat(AdminAccessService.accessOf(admin, open)).isEqualTo(AdminAccessService.Access.UNLOCKED);
        assertThat(AdminAccessService.accessOf(coordinator, open)).isNull();
    }

    @Test
    void withPreviewSignInTheAdminPasswordCannotBeClaimedFromTheBrowser() {
        var preview = new com.iet.dashboard.identity.config.AppSettings(false, false, "IET", "https://x", "jklu.edu.in", "", true);
        assertThatThrownBy(() -> new AdminAccessService(users, encoder, preview, Clock.fixed(T0, ZoneOffset.UTC))
                .setInitialPassword(CHOSEN_PHRASE))
                .isInstanceOf(ForbiddenException.class).hasMessageContaining("APP_ADMIN_INITIAL_PASSWORD");
        assertThat(users.findById(admin.getId()).orElseThrow().getCoordinatorPasswordHash()).isNull();
    }
}
