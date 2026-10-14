package com.iet.dashboard.directory.repo;

import com.iet.dashboard.directory.domain.Department;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface DepartmentRepository extends JpaRepository<Department, Long> {

    Optional<Department> findByCodeIgnoreCase(String code);

    Optional<Department> findByNameIgnoreCase(String name);

    boolean existsByCodeIgnoreCase(String code);
}
