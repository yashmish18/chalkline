package com.iet.dashboard.academics.web;

import java.util.List;

public record AllocationImportResultDto(
        int rows,
        /** False when the sheet had errors: then nothing was saved and every count is 0. */
        boolean imported,
        int facultyCreated,
        int coursesCreated,
        int offeringsCreated,
        int assignmentsCreated,
        int assignmentsUpdated,
        /** Allocations of this semester that the sheet no longer lists, removed with their timetable slots. */
        int assignmentsRemoved,
        /** People whose dashboard role the sheet's Role column changed (Faculty <-> Coordinator). */
        int rolesChanged,
        List<Issue> issues
) {
    /** A row the importer could not use as written. Row numbers match the sheet (header = row 1). */
    public record Issue(int row, String severity, String message) {}
}
