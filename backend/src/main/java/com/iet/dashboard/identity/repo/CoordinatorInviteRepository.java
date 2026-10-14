package com.iet.dashboard.identity.repo;

import com.iet.dashboard.identity.domain.CoordinatorInvite;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface CoordinatorInviteRepository extends JpaRepository<CoordinatorInvite, Long> {

    Optional<CoordinatorInvite> findByTokenHash(String tokenHash);

    List<CoordinatorInvite> findByUserIdAndUsedAtIsNull(Long userId);
}
