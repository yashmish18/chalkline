package com.iet.dashboard.academics.repo;

import com.iet.dashboard.academics.domain.Assessment;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

import java.util.List;

public interface AssessmentRepository extends JpaRepository<Assessment, Long> {

    List<Assessment> findByOfferingIdOrderByHeldOnAsc(Long offeringId);

    @Query("SELECT a FROM Assessment a JOIN FETCH a.offering ORDER BY a.heldOn")
    List<Assessment> findAllWithOffering();
}
