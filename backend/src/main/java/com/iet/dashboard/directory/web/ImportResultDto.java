package com.iet.dashboard.directory.web;

import java.util.List;

public record ImportResultDto(
        int totalRows,
        int facultyCreated,
        int facultyUpdated,
        int assignmentsCreated,
        List<String> messages
) {}
