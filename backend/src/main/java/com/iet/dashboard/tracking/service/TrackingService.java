package com.iet.dashboard.tracking.service;

import com.iet.dashboard.tracking.web.ComplianceMatrixDto;
import com.iet.dashboard.tracking.web.DeadlineTrackingRowDto;
import com.iet.dashboard.tracking.web.DepartmentMetricsDto;
import com.iet.dashboard.tracking.web.FacultyComplianceRowDto;
import com.iet.dashboard.tracking.web.OverviewSummaryDto;

import java.io.IOException;
import java.util.List;

public interface TrackingService {

    DepartmentMetricsDto getDepartmentMetrics();

    List<DeadlineTrackingRowDto> getDeadlineTrackingSummary();

    List<FacultyComplianceRowDto> getFacultyComplianceSummary();

    /** Full coordinator-facing profile for one faculty member. */
    com.iet.dashboard.tracking.web.FacultyProfileDto getFacultyProfile(Long facultyId);

    byte[] generateSubmissionsExcelReport() throws IOException;

    OverviewSummaryDto getOverviewSummary();

    ComplianceMatrixDto getComplianceMatrix();

    com.iet.dashboard.tracking.web.IrregularityAuditDto getIrregularitiesAudit();
}
