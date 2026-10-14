package com.iet.dashboard.tracking.web;

import com.iet.dashboard.tracking.service.TrackingService;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.io.IOException;
import java.time.LocalDate;
import java.util.List;

@RestController
@RequestMapping("/api")
@PreAuthorize("hasRole('COORDINATOR')")
public class TrackingController {

    private final TrackingService trackingService;

    public TrackingController(TrackingService trackingService) {
        this.trackingService = trackingService;
    }

    @GetMapping("/tracking/metrics")
    public ResponseEntity<DepartmentMetricsDto> getDepartmentMetrics() {
        return ResponseEntity.ok(trackingService.getDepartmentMetrics());
    }

    @GetMapping("/tracking/by-deadline")
    public ResponseEntity<List<DeadlineTrackingRowDto>> getDeadlineTracking() {
        return ResponseEntity.ok(trackingService.getDeadlineTrackingSummary());
    }

    @GetMapping("/tracking/by-faculty")
    public ResponseEntity<List<FacultyComplianceRowDto>> getFacultyCompliance() {
        return ResponseEntity.ok(trackingService.getFacultyComplianceSummary());
    }

    @GetMapping("/faculty/{facultyId}/profile")
    public ResponseEntity<FacultyProfileDto> getFacultyProfile(@PathVariable Long facultyId) {
        return ResponseEntity.ok(trackingService.getFacultyProfile(facultyId));
    }

    @GetMapping("/tracking/overview-summary")
    public ResponseEntity<OverviewSummaryDto> getOverviewSummary() {
        return ResponseEntity.ok(trackingService.getOverviewSummary());
    }

    @GetMapping("/tracking/compliance-matrix")
    public ResponseEntity<ComplianceMatrixDto> getComplianceMatrix() {
        return ResponseEntity.ok(trackingService.getComplianceMatrix());
    }

    @GetMapping("/tracking/irregularities-audit")
    public ResponseEntity<IrregularityAuditDto> getIrregularitiesAudit() {
        return ResponseEntity.ok(trackingService.getIrregularitiesAudit());
    }


    @GetMapping("/reports/submissions/export")
    public ResponseEntity<byte[]> exportSubmissionsReport() throws IOException {
        byte[] excelBytes = trackingService.generateSubmissionsExcelReport();
        String filename = "iet_submissions_report_" + LocalDate.now() + ".xlsx";

        return ResponseEntity.ok()
                .header(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=\"" + filename + "\"")
                .contentType(MediaType.parseMediaType("application/vnd.openxmlformats-officedocument.spreadsheetml.sheet"))
                .body(excelBytes);
    }
}
