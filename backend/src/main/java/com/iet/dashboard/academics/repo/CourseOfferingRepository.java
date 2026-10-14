package com.iet.dashboard.academics.repo;

import com.iet.dashboard.academics.domain.CourseOffering;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface CourseOfferingRepository extends JpaRepository<CourseOffering, Long> {

    @Query("SELECT o FROM CourseOffering o JOIN FETCH o.course ORDER BY o.academicYear DESC, o.semester, o.course.code, o.section")
    List<CourseOffering> findAllWithCourse();

    @Query("SELECT o FROM CourseOffering o JOIN FETCH o.course WHERE o.semester = :semester ORDER BY o.course.code, o.section")
    List<CourseOffering> findBySemester(@Param("semester") int semester);

    java.util.Optional<CourseOffering> findFirstByCourseIdAndAcademicYearAndYearOfStudyAndSemesterAndSection(
            Long courseId, String academicYear, int yearOfStudy, int semester, String section);

    boolean existsByCourse_IdAndAcademicYearAndYearOfStudyAndSemesterAndSection(
            Long courseId, String academicYear, int yearOfStudy, int semester, String section);
}
