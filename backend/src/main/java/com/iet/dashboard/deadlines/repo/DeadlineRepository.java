package com.iet.dashboard.deadlines.repo;

import com.iet.dashboard.deadlines.domain.Deadline;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.stereotype.Repository;

import java.time.Instant;
import java.util.List;

@Repository
public interface DeadlineRepository extends JpaRepository<Deadline, Long> {

    @Query("SELECT d FROM Deadline d ORDER BY d.dueAt ASC")
    List<Deadline> findAllOrderByDueAt();

    @Query("SELECT d FROM Deadline d WHERE d.dueAt < :now ORDER BY d.dueAt ASC")
    List<Deadline> findOverdueDeadlines(Instant now);
}
