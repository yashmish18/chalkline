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

    @Query("SELECT st FROM SyllabusTopic st WHERE st.course.id = :courseId ORDER BY st.unitNumber ASC, st.lectureOrder ASC")
    List<SyllabusTopic> findByCourseIdGrouped(@Param("courseId") Long courseId);

    long countByCourseId(Long courseId);
}
