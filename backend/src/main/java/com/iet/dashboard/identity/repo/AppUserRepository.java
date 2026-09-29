package com.iet.dashboard.identity.repo;

import com.iet.dashboard.identity.domain.AppUser;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.Optional;

@Repository
public interface AppUserRepository extends JpaRepository<AppUser, Long> {

    Optional<AppUser> findByEmailIgnoreCase(String email);

    Optional<AppUser> findByEntraOid(String entraOid);

    boolean existsByEmailIgnoreCase(String email);
}
