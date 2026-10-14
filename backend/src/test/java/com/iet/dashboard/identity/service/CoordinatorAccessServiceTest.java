package com.iet.dashboard.identity.service;

import com.iet.dashboard.identity.config.AppSettings;
import com.iet.dashboard.identity.domain.AppUser;
import com.iet.dashboard.identity.domain.CoordinatorInvite;
import com.iet.dashboard.identity.domain.Role;
import com.iet.dashboard.identity.repo.AppUserRepository;
import com.iet.dashboard.identity.repo.CoordinatorInviteRepository;
import com.iet.dashboard.notifications.email.EmailService;
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
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.contains;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;

/**
 * Becoming coordinator: invited by email, verified by opening the link as that person, and a
 * coordinator password that only ever exists as a BCrypt hash.
 */
@DataJpaTest
@ActiveProfiles("test")
class CoordinatorAccessServiceTest {

    private static final String CHOSEN_PHRASE = "Correct-Horse-Battery-9";
    private static final Instant T0 = Instant.parse("2026-10-14T05:00:00Z");

    @Autowired AppUserRepository users;
    @Autowired CoordinatorInviteRepository invites;

    private final BCryptPasswordEncoder encoder = new BCryptPasswordEncoder(4);
    private final EmailService email = mock(EmailService.class);
    private AppUser taruna;
    private AppUser amit;

    @BeforeEach
    void setUp() {
        taruna = users.save(new AppUser("s.taruna@jklu.edu.in", "Dr. S Taruna", Role.FACULTY));
        amit = users.save(new AppUser("amit.sinhal@jklu.edu.in", "Dr. Amit Sinhal", Role.FACULTY));
    }

    @AfterEach
    void signOut() {
        SecurityContextHolder.clearContext();
    }

    private CoordinatorAccessService service(Instant now, boolean devMode) {
        return new CoordinatorAccessService(users, invites, encoder, email,
                new AppSettings(devMode, false, "IET", "https://dash.jklu.edu.in/", "jklu.edu.in", ""),
                Clock.fixed(now, ZoneOffset.UTC));
    }

    private static void signedInAs(AppUser user, String authority) {
        SecurityContextHolder.getContext().setAuthentication(new UsernamePasswordAuthenticationToken(
                user.getEmail(), null, List.of(new SimpleGrantedAuthority(authority))));
    }

    /** The emailed token, read from the dev link (dev mode returns the link it emails). */
    private String inviteToken(AppUser user) {
        String link = service(T0, true).invite(user).devLink();
        assertThat(link).startsWith("https://dash.jklu.edu.in/?coordinator-setup=");
        return link.substring(link.indexOf('=') + 1);
    }

    @Test
    void anInviteStoresOnlyAHashOfTheTokenAndEmailsTheLinkOnceCommitted() {
        String token = inviteToken(taruna);

        CoordinatorInvite stored = invites.findAll().get(0);
        assertThat(stored.getTokenHash()).isNotEqualTo(token).hasSize(64)
                .isEqualTo(CoordinatorAccessService.sha256(token));
        assertThat(stored.getExpiresAt()).isEqualTo(T0.plus(Duration.ofHours(72)));
        assertThat(taruna.getRole()).as("an invite alone grants nothing").isEqualTo(Role.FACULTY);

        verifyNoInteractions(email);   // not before the transaction commits
        TransactionSynchronizationManager.getSynchronizations().forEach(TransactionSynchronization::afterCommit);
        verify(email).sendEmailAsync(eq("s.taruna@jklu.edu.in"), contains("coordinator"), contains(token));
    }

    @Test
    void openingTheLinkAsThatPersonMakesThemCoordinatorWithAHashedPassword() {
        String token = inviteToken(taruna);
        signedInAs(taruna, "ROLE_FACULTY");

        AppUser done = service(T0.plusSeconds(60), false).completeSetup(token, CHOSEN_PHRASE);

        assertThat(done.getRole()).isEqualTo(Role.COORDINATOR);
        assertThat(done.getCoordinatorPasswordHash()).startsWith("$2a$").doesNotContain(CHOSEN_PHRASE);
        assertThat(encoder.matches(CHOSEN_PHRASE, done.getCoordinatorPasswordHash())).isTrue();
        assertThat(invites.findAll().get(0).getUsedAt()).isNotNull();
    }

    @Test
    void aLinkWorksOnlyOnceOnlyBeforeItExpiresAndOnlyForThePersonItWasSentTo() {
        String token = inviteToken(taruna);

        signedInAs(amit, "ROLE_FACULTY");
        assertThatThrownBy(() -> service(T0, false).completeSetup(token, CHOSEN_PHRASE))
                .isInstanceOf(ForbiddenException.class);
        assertThat(users.findById(amit.getId()).orElseThrow().getRole()).isEqualTo(Role.FACULTY);

        signedInAs(taruna, "ROLE_FACULTY");
        assertThatThrownBy(() -> service(T0.plus(Duration.ofHours(73)), false).completeSetup(token, CHOSEN_PHRASE))
                .isInstanceOf(IllegalArgumentException.class).hasMessageContaining("expired");

        service(T0, false).completeSetup(token, CHOSEN_PHRASE);
        assertThatThrownBy(() -> service(T0, false).completeSetup(token, CHOSEN_PHRASE))
                .isInstanceOf(IllegalArgumentException.class).hasMessageContaining("already used");
        assertThatThrownBy(() -> service(T0, false).completeSetup("made-up-token", CHOSEN_PHRASE))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void aNewInviteReplacesTheOldLink() {
        String first = inviteToken(taruna);
        String second = inviteToken(taruna);
        signedInAs(taruna, "ROLE_FACULTY");

        assertThatThrownBy(() -> service(T0, false).completeSetup(first, CHOSEN_PHRASE))
                .isInstanceOf(IllegalArgumentException.class);
        assertThat(service(T0, false).completeSetup(second, CHOSEN_PHRASE).getRole()).isEqualTo(Role.COORDINATOR);
    }

    @Test
    void weakPasswordsAreRefusedAndNothingChanges() {
        String token = inviteToken(taruna);
        signedInAs(taruna, "ROLE_FACULTY");
        CoordinatorAccessService service = service(T0, false);

        assertThatThrownBy(() -> service.completeSetup(token, "short-1")).hasMessageContaining("at least 12");
        assertThatThrownBy(() -> service.completeSetup(token, "            ")).hasMessageContaining("spaces");
        assertThatThrownBy(() -> service.completeSetup(token, "my-s.taruna-pass")).hasMessageContaining("email");
        assertThatThrownBy(() -> service.completeSetup(token, "x".repeat(73))).hasMessageContaining("at most 72");
        assertThatThrownBy(() -> service.completeSetup(token, null)).isInstanceOf(IllegalArgumentException.class);

        AppUser unchanged = users.findById(taruna.getId()).orElseThrow();
        assertThat(unchanged.getRole()).isEqualTo(Role.FACULTY);
        assertThat(unchanged.getCoordinatorPasswordHash()).isNull();
    }

    @Test
    void fiveWrongPasswordsLockCoordinatorAccessForFifteenMinutes() {
        String token = inviteToken(taruna);
        signedInAs(taruna, "ROLE_FACULTY");
        service(T0, false).completeSetup(token, CHOSEN_PHRASE);
        signedInAs(taruna, CoordinatorAccessService.LOCKED_AUTHORITY);

        CoordinatorAccessService now = service(T0, false);
        for (int i = 0; i < 4; i++) {
            assertThat(now.unlock("wrong-password-" + i)).isEqualTo(CoordinatorAccessService.UnlockResult.WRONG_PASSWORD);
        }
        assertThat(now.unlock("wrong-again")).isEqualTo(CoordinatorAccessService.UnlockResult.TEMPORARILY_LOCKED);
        assertThat(now.unlock(CHOSEN_PHRASE)).as("even the right password waits out the lock")
                .isEqualTo(CoordinatorAccessService.UnlockResult.TEMPORARILY_LOCKED);
        assertThat(service(T0.plus(Duration.ofMinutes(14)), false).unlock(CHOSEN_PHRASE))
                .isEqualTo(CoordinatorAccessService.UnlockResult.TEMPORARILY_LOCKED);

        assertThat(service(T0.plus(Duration.ofMinutes(16)), false).unlock(CHOSEN_PHRASE))
                .isEqualTo(CoordinatorAccessService.UnlockResult.UNLOCKED);
        assertThat(users.findById(taruna.getId()).orElseThrow().getCoordinatorFailedAttempts()).isZero();
    }

    @Test
    void someoneWithoutACoordinatorPasswordCannotUnlock() {
        signedInAs(amit, "ROLE_FACULTY");
        assertThatThrownBy(() -> service(T0, false).unlock(CHOSEN_PHRASE)).isInstanceOf(ForbiddenException.class);
    }

    @Test
    void noInviteForSomeoneAlreadySetUpOrSwitchedOff() {
        String token = inviteToken(taruna);
        signedInAs(taruna, "ROLE_FACULTY");
        service(T0, false).completeSetup(token, CHOSEN_PHRASE);
        amit.setActive(false);
        users.save(amit);

        assertThat(service(T0, false).invite(taruna).sent()).isFalse();
        assertThat(service(T0, false).invite(amit).sent()).isFalse();
    }

    @Test
    void resendingIsForPeopleOwedALinkAndAtMostOnceAMinute() {
        signedInAs(amit, "ROLE_FACULTY");
        assertThatThrownBy(() -> service(T0, false).resendForCurrentUser()).isInstanceOf(ForbiddenException.class);

        inviteToken(taruna);
        signedInAs(taruna, "ROLE_FACULTY");
        assertThatThrownBy(() -> service(Instant.now(), false).resendForCurrentUser())
                .hasMessageContaining("less than a minute");
        assertThat(service(Instant.now().plus(Duration.ofMinutes(2)), false).resendForCurrentUser().sent()).isTrue();
    }

    @Test
    void aCoordinatorSessionStartsLockedExceptForAPasswordlessDevAccount() {
        AppUser coordinator = new AppUser("hod@jklu.edu.in", "HoD", Role.COORDINATOR);
        assertThat(CoordinatorAccessService.signInAuthority(coordinator, false).getAuthority())
                .isEqualTo(CoordinatorAccessService.LOCKED_AUTHORITY);
        assertThat(CoordinatorAccessService.signInAuthority(coordinator, true).getAuthority())
                .isEqualTo("ROLE_COORDINATOR");
        coordinator.setCoordinatorPasswordHash(encoder.encode(CHOSEN_PHRASE));
        assertThat(CoordinatorAccessService.signInAuthority(coordinator, true).getAuthority())
                .as("once a password exists, dev asks for it too").isEqualTo(CoordinatorAccessService.LOCKED_AUTHORITY);
        assertThat(CoordinatorAccessService.signInAuthority(amit, false).getAuthority()).isEqualTo("ROLE_FACULTY");
    }

    @Test
    void theSessionIsToldWhetherCoordinatorAccessIsOpenLockedOrAwaitingSetup() {
        AppUser coordinator = new AppUser("hod@jklu.edu.in", "HoD", Role.COORDINATOR);
        var locked = new UsernamePasswordAuthenticationToken("hod@jklu.edu.in", null,
                List.of(new SimpleGrantedAuthority(CoordinatorAccessService.LOCKED_AUTHORITY)));
        var open = new UsernamePasswordAuthenticationToken("hod@jklu.edu.in", null,
                List.of(new SimpleGrantedAuthority("ROLE_COORDINATOR")));

        assertThat(CoordinatorAccessService.accessOf(coordinator, locked)).isEqualTo(CoordinatorAccessService.Access.SETUP_REQUIRED);
        coordinator.setCoordinatorPasswordHash("hash");
        assertThat(CoordinatorAccessService.accessOf(coordinator, locked)).isEqualTo(CoordinatorAccessService.Access.LOCKED);
        assertThat(CoordinatorAccessService.accessOf(coordinator, open)).isEqualTo(CoordinatorAccessService.Access.UNLOCKED);
        assertThat(CoordinatorAccessService.accessOf(amit, open)).isNull();
    }

    @Test
    void aCoordinatorWhoForgotThePasswordCanEmailThemselvesAResetLink() {
        String token = inviteToken(taruna);
        signedInAs(taruna, "ROLE_FACULTY");
        service(T0, false).completeSetup(token, CHOSEN_PHRASE);
        signedInAs(taruna, CoordinatorAccessService.LOCKED_AUTHORITY);

        String link = new CoordinatorAccessService(users, invites, encoder, email,
                new AppSettings(true, false, "IET", "https://dash.jklu.edu.in", "jklu.edu.in", ""), Clock.systemUTC())
                .resendForCurrentUser().devLink();
        service(Instant.now(), false).completeSetup(link.substring(link.indexOf('=') + 1), "A-Brand-New-Password-2");

        AppUser reset = users.findById(taruna.getId()).orElseThrow();
        assertThat(encoder.matches("A-Brand-New-Password-2", reset.getCoordinatorPasswordHash())).isTrue();
        assertThat(encoder.matches(CHOSEN_PHRASE, reset.getCoordinatorPasswordHash())).isFalse();
    }
}
