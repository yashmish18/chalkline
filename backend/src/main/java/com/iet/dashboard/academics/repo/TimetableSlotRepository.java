package com.iet.dashboard.academics.repo;

import com.iet.dashboard.academics.domain.TimetableSlot;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface TimetableSlotRepository extends JpaRepository<TimetableSlot, Long> {

    @Query("SELECT t FROM TimetableSlot t JOIN FETCH t.offering o JOIN FETCH o.course c WHERE t.facultyId = :facultyId ORDER BY t.dayOfWeek, t.startTime")
    List<TimetableSlot> findByFacultyIdWithDetails(@Param("facultyId") Long facultyId);

    void deleteByFacultyId(Long facultyId);
}
