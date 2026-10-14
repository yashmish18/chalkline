package com.iet.dashboard.directory.repo;

import com.iet.dashboard.directory.domain.Faculty;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface FacultyRepository extends JpaRepository<Faculty, Long> {

    Optional<Faculty> findByUserId(Long userId);

    @Query("SELECT DISTINCT f FROM Faculty f LEFT JOIN FETCH f.skills")
    List<Faculty> findAllWithSkills();

    @Query("SELECT DISTINCT f FROM Faculty f JOIN f.skills s WHERE LOWER(s.name) = LOWER(:skillName)")
    List<Faculty> findBySkillName(@Param("skillName") String skillName);

    @Query("SELECT f FROM Faculty f LEFT JOIN FETCH f.skills WHERE f.id = :id")
    Optional<Faculty> findByIdWithSkills(@Param("id") Long id);

    @Query("SELECT f FROM Faculty f LEFT JOIN FETCH f.skills WHERE f.department.id = :departmentId")
    List<Faculty> findByDepartmentIdWithSkills(@Param("departmentId") Long departmentId);
}
