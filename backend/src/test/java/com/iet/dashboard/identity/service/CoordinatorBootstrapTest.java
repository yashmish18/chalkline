package com.iet.dashboard.identity.service;

import com.iet.dashboard.directory.domain.Faculty;
import com.iet.dashboard.directory.repo.FacultyRepository;
import com.iet.dashboard.identity.domain.AppUser;
import com.iet.dashboard.identity.domain.Role;
import com.iet.dashboard.identity.repo.AppUserRepository;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.test.context.ActiveProfiles;

import static org.assertj.core.api.Assertions.assertThat;

@DataJpaTest
@ActiveProfiles("test")
class CoordinatorBootstrapTest {

    @Autowired AppUserRepository users;
    @Autowired FacultyRepository faculty;

    @Test
    void createsTheConfiguredCoordinatorWithAProfile() {
        new CoordinatorBootstrap(users, faculty, "HoD.Office@IET.edu", "Head of Department").run(null);

        AppUser coordinator = users.findByEmailIgnoreCase("hod.office@iet.edu").orElseThrow();
        assertThat(coordinator.getRole()).isEqualTo(Role.COORDINATOR);
        assertThat(faculty.findByUserId(coordinator.getId())).isPresent();
    }

    @Test
    void promotesAnExistingAccountInsteadOfDuplicatingIt() {
        users.save(new AppUser("amit.sinhal@iet.edu", "Dr. Amit Sinhal", Role.FACULTY));

        new CoordinatorBootstrap(users, faculty, "amit.sinhal@iet.edu", "Coordinator").run(null);
        new CoordinatorBootstrap(users, faculty, "amit.sinhal@iet.edu", "Coordinator").run(null);

        assertThat(users.findAll()).singleElement().extracting(AppUser::getRole).isEqualTo(Role.COORDINATOR);
        assertThat(faculty.findAll()).hasSize(1);
    }

    @Test
    void doesNothingWhenNoEmailIsConfigured() {
        new CoordinatorBootstrap(users, faculty, "", "Coordinator").run(null);
        assertThat(users.count()).isZero();
    }

    @Test
    void doesNothingForABlankOrMissingEmail() {
        new CoordinatorBootstrap(users, faculty, "   ", "Coordinator").run(null);
        new CoordinatorBootstrap(users, faculty, null, "Coordinator").run(null);

        assertThat(users.count()).isZero();
        assertThat(faculty.count()).isZero();
    }

    /** The allocation sheet already made the account and its profile; the bootstrap only promotes it. */
    @Test
    void promotesAFacultyMemberKeepingTheirExistingProfile() {
        AppUser amit = users.save(new AppUser("amit.sinhal@iet.edu", "Dr. Amit Sinhal", Role.FACULTY));
        faculty.save(new Faculty(amit.getId(), "Dr. Amit Sinhal", "Professor", "", null));

        new CoordinatorBootstrap(users, faculty, "  Amit.Sinhal@IET.edu ", "Coordinator").run(null);

        AppUser promoted = users.findByEmailIgnoreCase("amit.sinhal@iet.edu").orElseThrow();
        assertThat(promoted.getId()).isEqualTo(amit.getId());
        assertThat(promoted.getRole()).isEqualTo(Role.COORDINATOR);
        assertThat(promoted.getName()).as("the configured name does not overwrite theirs").isEqualTo("Dr. Amit Sinhal");
        assertThat(faculty.findAll()).singleElement().satisfies(f -> {
            assertThat(f.getUserId()).isEqualTo(amit.getId());
            assertThat(f.getDesignation()).isEqualTo("Professor");
        });
    }

    @Test
    void reactivatesADeactivatedCoordinator() {
        AppUser hod = new AppUser("hod@iet.edu", "HoD", Role.COORDINATOR);
        hod.setActive(false);
        users.save(hod);

        new CoordinatorBootstrap(users, faculty, "hod@iet.edu", "HoD").run(null);

        assertThat(users.findByEmailIgnoreCase("hod@iet.edu")).get().satisfies(u -> {
            assertThat(u.isActive()).isTrue();
            assertThat(u.getRole()).isEqualTo(Role.COORDINATOR);
        });
    }

    @Test
    void runningTwiceOnAFreshDatabaseCreatesOneAccountAndOneProfile() {
        new CoordinatorBootstrap(users, faculty, "hod@iet.edu", "HoD").run(null);
        new CoordinatorBootstrap(users, faculty, "HOD@iet.edu", "Someone Else").run(null);

        assertThat(users.findAll()).singleElement().satisfies(u -> {
            assertThat(u.getName()).isEqualTo("HoD");
            assertThat(u.getRole()).isEqualTo(Role.COORDINATOR);
        });
        assertThat(faculty.findAll()).singleElement()
                .satisfies(f -> assertThat(f.getDesignation()).isEqualTo("Coordinator"));
    }
}
