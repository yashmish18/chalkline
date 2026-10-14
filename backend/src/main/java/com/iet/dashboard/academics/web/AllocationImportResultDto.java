package com.iet.dashboard.academics.web;

import java.util.List;

public record AllocationImportResultDto(
        int rows,
        int facultyCreated,
        int coursesCreated,
        int offeringsCreated,
        int assignmentsCreated,
        int assignmentsUpdated,
        /** People emailed a coordinator verification link because their row says Role = Coordinator. */
        int coordinatorInvites,
        List<Issue> issues
) {
    /** A row the importer could not use as written. Row numbers match the sheet (header = row 1). */
    public record Issue(int row, String severity, String message) {}
}
