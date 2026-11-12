package com.iet.dashboard.academics.repo;

import com.iet.dashboard.academics.domain.SyllabusTopic;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface SyllabusTopicRepository extends JpaRepository<SyllabusTopic, Long> {
    List<SyllabusTopic> findByCourseIdOrderByLectureOrderAsc(Long courseId);

    @Query("SELECT st FROM SyllabusTopic st ORDER BY st.course.id, st.lectureOrder")
    List<SyllabusTopic> findAllOrdered();

    void deleteByCourseId(Long courseId);

    /** Topics of this course that at least one logged class covered (their history must survive a re-upload). */
    @Query(value = "SELECT DISTINCT l.topic_id FROM session_topic_log l JOIN syllabus_topic t ON t.id = l.topic_id "
            + "WHERE t.course_id = :courseId", nativeQuery = true)
    java.util.Set<Long> findCoveredTopicIds(@org.springframework.data.repository.query.Param("courseId") Long courseId);
}
