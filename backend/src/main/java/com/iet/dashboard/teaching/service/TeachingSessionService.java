package com.iet.dashboard.teaching.service;

import com.iet.dashboard.teaching.web.LogSessionRequest;
import com.iet.dashboard.teaching.web.TeachingSessionDto;
import com.iet.dashboard.teaching.web.TodayClassDto;

import java.time.LocalDate;
import java.util.List;
import java.util.Set;

public interface TeachingSessionService {

    TeachingSessionDto logSession(LogSessionRequest request, Long currentFacultyId);

    List<TodayClassDto> getTodayClassesForFaculty(Long facultyId, LocalDate date);

    List<TeachingSessionDto> getSessionsForOffering(Long offeringId, Long currentFacultyId, boolean isCoordinator);

    Set<Long> getCoveredTopicIdsForOffering(Long offeringId);
}
