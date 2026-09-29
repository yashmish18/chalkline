package com.iet.dashboard.submissions.repo;

import com.iet.dashboard.submissions.domain.Submission;
import com.iet.dashboard.submissions.domain.SubmissionStatus;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface SubmissionRepository extends JpaRepository<Submission, Long> {

    @Query("SELECT s FROM Submission s LEFT JOIN FETCH s.files WHERE s.deadlineAssignmentId = :assignmentId")
    Optional<Submission> findByDeadlineAssignmentId(@Param("assignmentId") Long assignmentId);

    @Query("SELECT s FROM Submission s LEFT JOIN FETCH s.files WHERE s.id = :id")
    Optional<Submission> findByIdWithFiles(@Param("id") Long id);

    List<Submission> findByStatus(SubmissionStatus status);
}
