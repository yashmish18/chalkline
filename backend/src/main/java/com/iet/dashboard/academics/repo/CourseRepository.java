package com.iet.dashboard.academics.repo;

import com.iet.dashboard.academics.domain.Course;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.Optional;

@Repository
public interface CourseRepository extends JpaRepository<Course, Long> {

    Optional<Course> findByCodeIgnoreCase(String code);

    boolean existsByCodeIgnoreCase(String code);
}
