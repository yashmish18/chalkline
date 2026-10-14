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
}
