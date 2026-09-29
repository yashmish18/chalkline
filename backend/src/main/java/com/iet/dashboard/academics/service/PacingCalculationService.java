package com.iet.dashboard.academics.service;

import com.iet.dashboard.academics.web.CourseProgressDto;

import java.util.List;

public interface PacingCalculationService {

    List<CourseProgressDto> getMyCoursesProgress(Long facultyId);

    CourseProgressDto calculateProgressForOffering(Long offeringId, Long facultyId, boolean isCoordinator);
}
