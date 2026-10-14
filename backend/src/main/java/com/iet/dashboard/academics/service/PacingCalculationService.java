package com.iet.dashboard.academics.service;

import com.iet.dashboard.academics.web.CourseOfferingDto;
import com.iet.dashboard.academics.web.CourseProgressDto;

import java.util.List;
import java.util.Map;

public interface PacingCalculationService {

    List<CourseProgressDto> getMyCoursesProgress(Long facultyId);

    CourseProgressDto calculateProgressForOffering(Long offeringId, Long facultyId, boolean isCoordinator);

    /** Progress for many sections with a fixed handful of queries, keyed by offering id. */
    Map<Long, CourseProgressDto> calculateProgressForOfferings(List<CourseOfferingDto> offerings);
}
