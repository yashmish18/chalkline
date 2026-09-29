package com.iet.dashboard.tracking.service;

import com.iet.dashboard.tracking.web.AuditLogDto;
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

    byte[] generateSubmissionsExcelReport() throws IOException;

    void recordAudit(String action, String targetEntity, Long targetId, String details);

    List<AuditLogDto> getRecentAuditLogs(int limit);

    OverviewSummaryDto getOverviewSummary();

    ComplianceMatrixDto getComplianceMatrix();
}
