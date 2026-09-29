package com.iet.dashboard.deadlines.repo;

import com.iet.dashboard.deadlines.domain.DeadlineAssignment;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface DeadlineAssignmentRepository extends JpaRepository<DeadlineAssignment, Long> {

    @Query("SELECT da FROM DeadlineAssignment da JOIN FETCH da.deadline d WHERE da.facultyId = :facultyId ORDER BY d.dueAt ASC")
    List<DeadlineAssignment> findByFacultyIdWithDeadline(@Param("facultyId") Long facultyId);

    @Query("SELECT da FROM DeadlineAssignment da JOIN FETCH da.deadline d WHERE da.deadline.id = :deadlineId")
    List<DeadlineAssignment> findByDeadlineId(@Param("deadlineId") Long deadlineId);

    Optional<DeadlineAssignment> findByDeadlineIdAndFacultyId(Long deadlineId, Long facultyId);

    @Query("SELECT da FROM DeadlineAssignment da JOIN FETCH da.deadline d WHERE da.id = :id AND da.facultyId = :facultyId")
    Optional<DeadlineAssignment> findByIdAndFacultyId(@Param("id") Long id, @Param("facultyId") Long facultyId);
}
