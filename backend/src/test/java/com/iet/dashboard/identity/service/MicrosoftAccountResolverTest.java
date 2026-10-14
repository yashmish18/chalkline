package com.iet.dashboard.identity.service;

import com.iet.dashboard.identity.domain.AppUser;
import com.iet.dashboard.identity.domain.Role;
import com.iet.dashboard.identity.repo.AppUserRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.Map;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.when;

/**
 * The rule that decides who a Microsoft sign-in is allowed to become. Everything here is about
 * refusing: an email the department does not know, or an account that has been switched off.
 */
@ExtendWith(MockitoExtension.class)
class MicrosoftAccountResolverTest {

    @Mock
    private AppUserRepository users;

    private MicrosoftAccountResolver resolver() {
        return new MicrosoftAccountResolver(users);
    }

    @Test
    void resolvesAnActiveAccountFromTheEntraPreferredUsername() {
        AppUser coordinator = new AppUser("coordinator@iet.edu", "Coordinator", Role.COORDINATOR);
        when(users.findByEmailIgnoreCase("coordinator@iet.edu")).thenReturn(Optional.of(coordinator));

        Optional<AppUser> found = resolver()
                .resolve(Map.of("preferred_username", "coordinator@iet.edu"), "Coordinator");

        assertThat(found).containsSame(coordinator);
    }

    @Test
    void prefersTheDedicatedEmailClaimAndIgnoresCase() {
        AppUser faculty = new AppUser("mayank.kundalwal@iet.edu", "Mayank", Role.FACULTY);
        when(users.findByEmailIgnoreCase("mayank.kundalwal@iet.edu")).thenReturn(Optional.of(faculty));

        Optional<AppUser> found = resolver().resolve(
                Map.of("email", "  Mayank.Kundalwal@IET.edu  ", "preferred_username", "someone.else@iet.edu"),
                "Mayank");

        assertThat(found).containsSame(faculty);
    }

    @Test
    void fallsBackToThePrincipalNameWhenTheTokenCarriesNoEmailClaim() {
        AppUser faculty = new AppUser("taruna@iet.edu", "Taruna", Role.FACULTY);
        when(users.findByEmailIgnoreCase("taruna@iet.edu")).thenReturn(Optional.of(faculty));

        assertThat(resolver().resolve(Map.of("sub", "abc-123"), "Taruna@IET.edu"))
                .containsSame(faculty);
    }

    @Test
    void refusesAnEmailTheDepartmentHasNoAccountFor() {
        when(users.findByEmailIgnoreCase("stranger@iet.edu")).thenReturn(Optional.empty());

        assertThat(resolver().resolve(Map.of("preferred_username", "stranger@iet.edu"), "Stranger"))
                .isEmpty();
    }

    @Test
    void refusesADeactivatedAccount() {
        AppUser former = new AppUser("former@iet.edu", "Former", Role.FACULTY);
        former.setActive(false);
        when(users.findByEmailIgnoreCase("former@iet.edu")).thenReturn(Optional.of(former));

        assertThat(resolver().resolve(Map.of("preferred_username", "former@iet.edu"), "Former"))
                .isEmpty();
    }

    @Test
    void refusesWhenThereIsNothingToMatchOn() {
        assertThat(resolver().resolve(Map.of(), "   ")).isEmpty();
        assertThat(resolver().resolve(Map.of("email", "  "), null)).isEmpty();
    }

    @Test
    void readsTheUpnWhenNeitherEmailNorPreferredUsernameIsPresent() {
        AppUser faculty = new AppUser("old.tenant@iet.edu", "Old Tenant", Role.FACULTY);
        when(users.findByEmailIgnoreCase("old.tenant@iet.edu")).thenReturn(Optional.of(faculty));

        assertThat(resolver().resolve(Map.of("upn", "Old.Tenant@IET.EDU"), "ignored-subject"))
                .containsSame(faculty);
    }

    @Test
    void aBlankEmailClaimFallsThroughToPreferredUsername() {
        AppUser faculty = new AppUser("taruna@iet.edu", "Taruna", Role.FACULTY);
        when(users.findByEmailIgnoreCase("taruna@iet.edu")).thenReturn(Optional.of(faculty));

        assertThat(resolver().resolve(Map.of("email", "   ", "preferred_username", "TARUNA@iet.edu"), null))
                .containsSame(faculty);
    }

    /**
     * A claim that is not an email (an object id, a bare login name) is skipped for the next claim,
     * the same way the sign-in success handler reads the token. Otherwise the two disagree and a
     * valid preferred_username is refused at the first step.
     */
    @Test
    void aClaimWithoutAnAtSignIsNotTakenAsTheEmail() {
        AppUser faculty = new AppUser("taruna@iet.edu", "Taruna", Role.FACULTY);
        when(users.findByEmailIgnoreCase("taruna@iet.edu")).thenReturn(Optional.of(faculty));

        assertThat(resolver().resolve(Map.of("email", "taruna", "preferred_username", "taruna@iet.edu"), null))
                .containsSame(faculty);
        assertThat(MicrosoftAccountResolver.emailFrom(Map.of("upn", "taruna"), null)).isEmpty();
    }

    @Test
    void aNonStringClaimIsIgnored() {
        assertThat(MicrosoftAccountResolver.emailFrom(Map.of("email", 42), null)).isEmpty();
        assertThat(MicrosoftAccountResolver.emailFrom(null, null)).isEmpty();
    }

    @Test
    void anInactiveAccountIsRefusedWhicheverClaimNamesIt() {
        AppUser former = new AppUser("former@iet.edu", "Former", Role.COORDINATOR);
        former.setActive(false);
        when(users.findByEmailIgnoreCase("former@iet.edu")).thenReturn(Optional.of(former));

        assertThat(resolver().resolve(Map.of("upn", "FORMER@iet.edu"), null)).isEmpty();
        assertThat(resolver().resolve(Map.of(), "former@iet.edu")).isEmpty();
    }

    @Test
    void anAccountOutsideTheInstitutionsDomainIsRefusedEvenIfItExistsHere() {
        var jkluOnly = new MicrosoftAccountResolver(users, new com.iet.dashboard.identity.config.AppSettings(
                false, false, "IET", "https://dash.example", "jklu.edu.in", "jklu.edu.in"));
        AppUser inside = new AppUser("s.taruna@jklu.edu.in", "Dr. S Taruna", Role.FACULTY);
        when(users.findByEmailIgnoreCase("s.taruna@jklu.edu.in")).thenReturn(Optional.of(inside));

        assertThat(jkluOnly.resolve(Map.of("email", "S.Taruna@JKLU.edu.in"), "x")).containsSame(inside);
        assertThat(jkluOnly.resolve(Map.of("email", "s.taruna@gmail.com"), "x")).isEmpty();
        assertThat(jkluOnly.resolve(Map.of("email", "s.taruna@jklu.edu.in.evil.com"), "x")).isEmpty();
    }
}
