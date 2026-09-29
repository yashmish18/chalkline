package com.iet.dashboard.tracking.web;

import java.util.List;

public record ComplianceMatrixDto(
        List<CheckpointHeader> checkpoints,
        List<MatrixCourseRow> rows
) {
    public record CheckpointHeader(
            String key,
            String name,
            String description
    ) {}

    public record MatrixCourseRow(
            Long offeringId,
            String courseCode,
            String courseName,
            String section,
            Long facultyId,
            String facultyName,
            List<CheckpointCell> cells
    ) {}

    public record CheckpointCell(
            String checkpointKey,
            String status, // DONE, PENDING, OVERDUE
            String note,
            String completedDate
    ) {}
}
