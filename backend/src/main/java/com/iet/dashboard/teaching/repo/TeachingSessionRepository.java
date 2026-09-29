package com.iet.dashboard.teaching.repo;

import com.iet.dashboard.teaching.domain.SessionStatus;
import com.iet.dashboard.teaching.domain.TeachingSession;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.LocalDate;
import java.util.List;
import java.util.Set;

@Repository
public interface TeachingSessionRepository extends JpaRepository<TeachingSession, Long> {

    @Query("SELECT s FROM TeachingSession s LEFT JOIN FETCH s.topicsCovered WHERE s.offering.id = :offeringId ORDER BY s.sessionDate DESC")
    List<TeachingSession> findByOfferingIdWithTopics(@Param("offeringId") Long offeringId);

    List<TeachingSession> findByFacultyIdAndSessionDate(Long facultyId, LocalDate sessionDate);

    @Query("SELECT DISTINCT t.id FROM TeachingSession s JOIN s.topicsCovered t WHERE s.offering.id = :offeringId AND s.status = 'HELD'")
    Set<Long> findCoveredTopicIdsForOffering(@Param("offeringId") Long offeringId);

    @Query("SELECT COUNT(s) FROM TeachingSession s WHERE s.offering.id = :offeringId AND s.status = :status")
    long countByOfferingIdAndStatus(@Param("offeringId") Long offeringId, @Param("status") SessionStatus status);

    @Query("SELECT COUNT(s) FROM TeachingSession s WHERE s.offering.id = :offeringId")
    long countTotalSessionsForOffering(@Param("offeringId") Long offeringId);
}
