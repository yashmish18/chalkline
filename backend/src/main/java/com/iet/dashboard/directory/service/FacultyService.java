package com.iet.dashboard.directory.service;

import com.iet.dashboard.directory.web.CreateFacultyRequest;
import com.iet.dashboard.directory.web.FacultyDto;
import com.iet.dashboard.directory.web.SkillDto;

import java.util.List;
import java.util.Optional;

public interface FacultyService {

    List<FacultyDto> getAllFaculty(String skillFilter);

    FacultyDto getFacultyById(Long id);

    Optional<FacultyDto> getFacultyByUserId(Long userId);

    List<SkillDto> getAllSkills();

    FacultyDto createFaculty(CreateFacultyRequest request);

    FacultyDto updateFacultySkills(Long facultyId, List<Long> skillIds);

    FacultyDto updateFacultySkillsByNames(Long facultyId, List<String> skillNames);
}
