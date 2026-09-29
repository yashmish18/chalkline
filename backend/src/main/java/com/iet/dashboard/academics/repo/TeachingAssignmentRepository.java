package com.iet.dashboard.academics.repo;

import com.iet.dashboard.academics.domain.TeachingAssignment;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface TeachingAssignmentRepository extends JpaRepository<TeachingAssignment, Long> {

    @Query("SELECT ta FROM TeachingAssignment ta JOIN FETCH ta.offering o JOIN FETCH o.course c WHERE ta.facultyId = :facultyId ORDER BY o.semester, c.code")
    List<TeachingAssignment> findByFacultyIdWithCourse(@Param("facultyId") Long facultyId);

    @Query("SELECT ta FROM TeachingAssignment ta JOIN FETCH ta.offering o JOIN FETCH o.course c ORDER BY o.semester, c.code, ta.component")
    List<TeachingAssignment> findAllWithCourseAndOffering();

    @Query("SELECT ta FROM TeachingAssignment ta WHERE ta.offering.id = :offeringId")
    List<TeachingAssignment> findByOfferingId(@Param("offeringId") Long offeringId);
}
