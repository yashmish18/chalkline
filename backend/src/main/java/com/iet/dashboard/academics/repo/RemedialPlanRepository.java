package com.iet.dashboard.academics.repo;

import com.iet.dashboard.academics.domain.RemedialPlan;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

import java.util.List;

public interface RemedialPlanRepository extends JpaRepository<RemedialPlan, Long> {

    List<RemedialPlan> findByOfferingIdOrderByCreatedAtDesc(Long offeringId);

    @Query("SELECT r FROM RemedialPlan r JOIN FETCH r.offering o JOIN FETCH o.course ORDER BY r.createdAt DESC")
    List<RemedialPlan> findAllWithOffering();
}
