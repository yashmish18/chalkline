package com.iet.dashboard.directory.service;

import com.iet.dashboard.directory.web.CreateFacultyRequest;
import com.iet.dashboard.directory.web.DepartmentDto;
import com.iet.dashboard.directory.web.FacultyDto;
import com.iet.dashboard.directory.web.SkillDto;

import java.util.List;
import java.util.Optional;

public interface FacultyService {

    List<FacultyDto> getAllFaculty(String skillFilter, Long departmentId);

    FacultyDto getFacultyById(Long id);

    Optional<FacultyDto> getFacultyByUserId(Long userId);

    List<SkillDto> getAllSkills();

    /** Reference data for department filters and pickers. */
    List<DepartmentDto> getAllDepartments();

    FacultyDto createFaculty(CreateFacultyRequest request);

    FacultyDto updateFacultySkills(Long facultyId, List<Long> skillIds);

    FacultyDto updateFacultySkillsByNames(Long facultyId, List<String> skillNames);

    void deleteFaculty(Long id);

    /**
     * Removes every faculty profile except the coordinator's own, returning how many were removed.
     * Coordinator accounts are retained so the operator does not lock themselves out.
     */
    int deleteAllFaculty();
}
