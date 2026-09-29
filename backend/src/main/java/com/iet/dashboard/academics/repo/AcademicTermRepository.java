package com.iet.dashboard.academics.repo;

import com.iet.dashboard.academics.domain.AcademicTerm;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.Optional;

@Repository
public interface AcademicTermRepository extends JpaRepository<AcademicTerm, Long> {
    Optional<AcademicTerm> findByActiveTrue();
    Optional<AcademicTerm> findByName(String name);
}
