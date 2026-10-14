package com.iet.dashboard.deadlines.repo;

import com.iet.dashboard.deadlines.domain.Deadline;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface DeadlineRepository extends JpaRepository<Deadline, Long> {

    @Query("SELECT d FROM Deadline d ORDER BY d.dueAt ASC")
    List<Deadline> findAllOrderByDueAt();

    /** Deadlines keep a hard FK to their creator's account, so they must go before the account does. */
    void deleteByCreatedByIn(java.util.Collection<Long> createdBys);
}
