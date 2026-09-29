package com.iet.dashboard.deadlines.service;

import com.iet.dashboard.deadlines.web.CreateDeadlineRequest;
import com.iet.dashboard.deadlines.web.DeadlineAssignmentDto;
import com.iet.dashboard.deadlines.web.DeadlineDto;

import java.util.List;

public interface DeadlineService {

    List<DeadlineDto> getAllDeadlines();

    DeadlineDto getDeadlineById(Long id);

    List<DeadlineAssignmentDto> getAssignmentsForDeadline(Long deadlineId);

    List<DeadlineAssignmentDto> getMyDeadlines();

    DeadlineDto createDeadline(CreateDeadlineRequest request);
}
