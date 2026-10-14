package com.iet.dashboard.academics.web;

import java.util.List;

public record TimetableImportResultDto(
        int rows,
        int slotsCreated,
        int facultyCovered,
        List<AllocationImportResultDto.Issue> issues
) {}
