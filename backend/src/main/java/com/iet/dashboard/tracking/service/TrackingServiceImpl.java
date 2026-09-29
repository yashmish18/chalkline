package com.iet.dashboard.tracking.service;

import com.iet.dashboard.deadlines.service.DeadlineService;
import com.iet.dashboard.deadlines.web.DeadlineAssignmentDto;
import com.iet.dashboard.deadlines.web.DeadlineDto;
import com.iet.dashboard.directory.service.FacultyService;
import com.iet.dashboard.directory.web.FacultyDto;
import com.iet.dashboard.identity.service.UserService;
import com.iet.dashboard.identity.web.UserDto;
import com.iet.dashboard.submissions.service.SubmissionService;
import com.iet.dashboard.submissions.web.SubmissionDto;
import com.iet.dashboard.tracking.domain.AuditLog;
import com.iet.dashboard.tracking.repo.AuditLogRepository;
import com.iet.dashboard.tracking.web.AuditLogDto;
import com.iet.dashboard.tracking.web.DeadlineTrackingRowDto;
import com.iet.dashboard.tracking.web.DepartmentMetricsDto;
import com.iet.dashboard.tracking.web.FacultyComplianceRowDto;
import org.apache.poi.ss.usermodel.*;
import org.apache.poi.xssf.usermodel.XSSFCellStyle;
import org.apache.poi.xssf.usermodel.XSSFColor;
import org.apache.poi.xssf.usermodel.XSSFSheet;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.time.Instant;
import java.util.*;

@Service
@Transactional
public class TrackingServiceImpl implements TrackingService {

    private static final Logger log = LoggerFactory.getLogger(TrackingServiceImpl.class);

    private final AuditLogRepository auditLogRepository;
    private final DeadlineService deadlineService;
    private final FacultyService facultyService;
    private final SubmissionService submissionService;
    private final UserService userService;

    public TrackingServiceImpl(
            AuditLogRepository auditLogRepository,
            DeadlineService deadlineService,
            FacultyService facultyService,
            SubmissionService submissionService,
            UserService userService
    ) {
        this.auditLogRepository = auditLogRepository;
        this.deadlineService = deadlineService;
        this.facultyService = facultyService;
        this.submissionService = submissionService;
        this.userService = userService;
    }

    @Override
    @Transactional(readOnly = true)
    public DepartmentMetricsDto getDepartmentMetrics() {
        List<DeadlineDto> deadlines = deadlineService.getAllDeadlines();
        List<FacultyDto> allFaculty = facultyService.getAllFaculty(null);

        long totalAssignments = 0;
        long totalSubmissions = 0;
        long onTimeSubmissions = 0;
        long overdueCount = 0;

        Map<Long, Integer> facultyTotalAssignments = new HashMap<>();
        Map<Long, Integer> facultyCompleted = new HashMap<>();

        for (FacultyDto f : allFaculty) {
            facultyTotalAssignments.put(f.id(), 0);
            facultyCompleted.put(f.id(), 0);
        }

        for (DeadlineDto deadline : deadlines) {
            List<DeadlineAssignmentDto> assignments = deadlineService.getAssignmentsForDeadline(deadline.id());
            for (DeadlineAssignmentDto assignment : assignments) {
                totalAssignments++;
                Long fId = assignment.facultyId();
                facultyTotalAssignments.put(fId, facultyTotalAssignments.getOrDefault(fId, 0) + 1);

                try {
                    SubmissionDto sub = submissionService.getSubmissionByAssignmentId(assignment.id());
                    String status = sub.status();
                    if ("SUBMITTED".equals(status) || "ACCEPTED".equals(status)) {
                        totalSubmissions++;
                        onTimeSubmissions++;
                        facultyCompleted.put(fId, facultyCompleted.getOrDefault(fId, 0) + 1);
                    } else if ("LATE".equals(status)) {
                        totalSubmissions++;
                        facultyCompleted.put(fId, facultyCompleted.getOrDefault(fId, 0) + 1);
                    } else if ("RETURNED".equals(status)) {
                        totalSubmissions++;
                    } else {
                        // PENDING
                        if (assignment.isOverdue()) {
                            overdueCount++;
                        }
                    }
                } catch (Exception e) {
                    if (assignment.isOverdue()) {
                        overdueCount++;
                    }
                }
            }
        }

        double submissionRate = totalAssignments > 0
                ? Math.round((double) totalSubmissions / totalAssignments * 1000.0) / 10.0
                : 100.0;

        double onTimeRate = totalSubmissions > 0
                ? Math.round((double) onTimeSubmissions / totalSubmissions * 1000.0) / 10.0
                : 100.0;

        long criticalFacultyCount = 0;
        for (FacultyDto f : allFaculty) {
            int assigned = facultyTotalAssignments.getOrDefault(f.id(), 0);
            if (assigned > 0) {
                int done = facultyCompleted.getOrDefault(f.id(), 0);
                double rate = (double) done / assigned * 100.0;
                if (rate < 60.0) {
                    criticalFacultyCount++;
                }
            }
        }

        return new DepartmentMetricsDto(
                totalAssignments,
                totalSubmissions,
                submissionRate,
                onTimeSubmissions,
                onTimeRate,
                overdueCount,
                criticalFacultyCount,
                allFaculty.size()
        );
    }

    @Override
    @Transactional(readOnly = true)
    public List<DeadlineTrackingRowDto> getDeadlineTrackingSummary() {
        List<DeadlineDto> deadlines = deadlineService.getAllDeadlines();
        List<DeadlineTrackingRowDto> rows = new ArrayList<>();

        for (DeadlineDto deadline : deadlines) {
            List<DeadlineAssignmentDto> assignments = deadlineService.getAssignmentsForDeadline(deadline.id());
            int total = assignments.size();
            int submitted = 0;
            int accepted = 0;
            int overdue = 0;

            for (DeadlineAssignmentDto a : assignments) {
                try {
                    SubmissionDto sub = submissionService.getSubmissionByAssignmentId(a.id());
                    if ("ACCEPTED".equals(sub.status())) {
                        accepted++;
                        submitted++;
                    } else if ("SUBMITTED".equals(sub.status()) || "LATE".equals(sub.status()) || "RETURNED".equals(sub.status())) {
                        submitted++;
                    } else if (a.isOverdue()) {
                        overdue++;
                    }
                } catch (Exception e) {
                    if (a.isOverdue()) {
                        overdue++;
                    }
                }
            }

            double complianceRate = total > 0
                    ? Math.round((double) submitted / total * 1000.0) / 10.0
                    : 100.0;

            rows.add(new DeadlineTrackingRowDto(
                    deadline.id(),
                    deadline.title(),
                    deadline.type(),
                    deadline.dueAt(),
                    total,
                    submitted,
                    accepted,
                    overdue,
                    complianceRate
            ));
        }

        return rows;
    }

    @Override
    @Transactional(readOnly = true)
    public List<FacultyComplianceRowDto> getFacultyComplianceSummary() {
        List<FacultyDto> facultyList = facultyService.getAllFaculty(null);
        List<DeadlineDto> deadlines = deadlineService.getAllDeadlines();

        Map<Long, List<DeadlineAssignmentDto>> facultyAssignments = new HashMap<>();
        for (FacultyDto f : facultyList) {
            facultyAssignments.put(f.id(), new ArrayList<>());
        }

        for (DeadlineDto d : deadlines) {
            List<DeadlineAssignmentDto> assignments = deadlineService.getAssignmentsForDeadline(d.id());
            for (DeadlineAssignmentDto a : assignments) {
                facultyAssignments.computeIfAbsent(a.facultyId(), k -> new ArrayList<>()).add(a);
            }
        }

        List<FacultyComplianceRowDto> rows = new ArrayList<>();
        for (FacultyDto f : facultyList) {
            List<DeadlineAssignmentDto> assignments = facultyAssignments.getOrDefault(f.id(), Collections.emptyList());
            int total = assignments.size();
            int submitted = 0;
            int accepted = 0;
            int pending = 0;

            for (DeadlineAssignmentDto a : assignments) {
                try {
                    SubmissionDto sub = submissionService.getSubmissionByAssignmentId(a.id());
                    if ("ACCEPTED".equals(sub.status())) {
                        accepted++;
                        submitted++;
                    } else if ("SUBMITTED".equals(sub.status()) || "LATE".equals(sub.status()) || "RETURNED".equals(sub.status())) {
                        submitted++;
                    } else {
                        pending++;
                    }
                } catch (Exception e) {
                    pending++;
                }
            }

            double complianceRate = total > 0
                    ? Math.round((double) submitted / total * 1000.0) / 10.0
                    : 100.0;

            String status = complianceRate < 60.0 ? "CRITICAL" : (complianceRate < 85.0 ? "WATCH" : "GOOD");

            rows.add(new FacultyComplianceRowDto(
                    f.id(),
                    f.name(),
                    f.designation(),
                    f.department(),
                    total,
                    submitted,
                    accepted,
                    pending,
                    complianceRate,
                    status
            ));
        }

        return rows;
    }

    @Override
    public byte[] generateSubmissionsExcelReport() throws IOException {
        log.info("Generating submissions Excel audit workbook...");
        List<DeadlineDto> deadlines = deadlineService.getAllDeadlines();
        Map<Long, FacultyDto> facultyMap = new HashMap<>();
        facultyService.getAllFaculty(null).forEach(f -> facultyMap.put(f.id(), f));

        try (XSSFWorkbook workbook = new XSSFWorkbook()) {
            XSSFSheet sheet = workbook.createSheet("Submissions Audit");

            // Fonts & Styles
            Font titleFont = workbook.createFont();
            titleFont.setFontName("Arial");
            titleFont.setFontHeightInPoints((short) 14);
            titleFont.setBold(true);
            titleFont.setColor(IndexedColors.DARK_BLUE.getIndex());

            CellStyle titleStyle = workbook.createCellStyle();
            titleStyle.setFont(titleFont);

            Font headerFont = workbook.createFont();
            headerFont.setFontName("Arial");
            headerFont.setFontHeightInPoints((short) 10);
            headerFont.setBold(true);
            headerFont.setColor(IndexedColors.WHITE.getIndex());

            XSSFCellStyle headerStyle = workbook.createCellStyle();
            headerStyle.setFont(headerFont);
            byte[] navyRgb = new byte[]{(byte) 30, (byte) 58, (byte) 95}; // Institutional Navy #1E3A5F
            headerStyle.setFillForegroundColor(new XSSFColor(navyRgb, null));
            headerStyle.setFillPattern(FillPatternType.SOLID_FOREGROUND);
            headerStyle.setAlignment(HorizontalAlignment.CENTER);
            headerStyle.setVerticalAlignment(VerticalAlignment.CENTER);
            headerStyle.setBorderBottom(BorderStyle.THIN);
            headerStyle.setBorderTop(BorderStyle.THIN);
            headerStyle.setBorderLeft(BorderStyle.THIN);
            headerStyle.setBorderRight(BorderStyle.THIN);

            CellStyle dataStyle = workbook.createCellStyle();
            dataStyle.setBorderBottom(BorderStyle.THIN);
            dataStyle.setBorderTop(BorderStyle.THIN);
            dataStyle.setBorderLeft(BorderStyle.THIN);
            dataStyle.setBorderRight(BorderStyle.THIN);
            dataStyle.setVerticalAlignment(VerticalAlignment.CENTER);

            // Title rows
            Row titleRow = sheet.createRow(0);
            Cell titleCell = titleRow.createCell(0);
            titleCell.setCellValue("INSTITUTE OF ENGINEERING & TECHNOLOGY (IET)");
            titleCell.setCellStyle(titleStyle);

            Row subTitleRow = sheet.createRow(1);
            subTitleRow.createCell(0).setCellValue("Department Coordinator Submissions & Compliance Audit Report — Generated: " + Instant.now());

            // Header row
            String[] headers = {
                    "#", "Faculty Name", "Designation", "Department", "Deadline Title",
                    "Type", "Due Date (UTC)", "Status", "Version", "Submitted At", "Reviewer Comment"
            };

            Row headerRow = sheet.createRow(3);
            headerRow.setHeightInPoints(24);
            for (int i = 0; i < headers.length; i++) {
                Cell cell = headerRow.createCell(i);
                cell.setCellValue(headers[i]);
                cell.setCellStyle(headerStyle);
            }

            int rowIdx = 4;
            int counter = 1;

            for (DeadlineDto deadline : deadlines) {
                List<DeadlineAssignmentDto> assignments = deadlineService.getAssignmentsForDeadline(deadline.id());
                for (DeadlineAssignmentDto assignment : assignments) {
                    FacultyDto faculty = facultyMap.get(assignment.facultyId());
                    SubmissionDto submission;
                    try {
                        submission = submissionService.getSubmissionByAssignmentId(assignment.id());
                    } catch (Exception e) {
                        submission = null;
                    }

                    Row row = sheet.createRow(rowIdx++);
                    row.createCell(0).setCellValue(counter++);
                    row.createCell(1).setCellValue(faculty != null ? faculty.name() : "Faculty #" + assignment.facultyId());
                    row.createCell(2).setCellValue(faculty != null ? faculty.designation() : "—");
                    row.createCell(3).setCellValue(faculty != null ? faculty.department() : "IET");
                    row.createCell(4).setCellValue(deadline.title());
                    row.createCell(5).setCellValue(deadline.type());
                    row.createCell(6).setCellValue(deadline.dueAt().toString());
                    row.createCell(7).setCellValue(submission != null ? submission.status() : "PENDING");
                    row.createCell(8).setCellValue(submission != null && submission.latestFile() != null ? "v" + submission.latestFile().version() : "—");
                    row.createCell(9).setCellValue(submission != null && submission.submittedAt() != null ? submission.submittedAt().toString() : "—");
                    row.createCell(10).setCellValue(submission != null && submission.reviewerComment() != null ? submission.reviewerComment() : "—");

                    for (int c = 0; c < headers.length; c++) {
                        row.getCell(c).setCellStyle(dataStyle);
                    }
                }
            }

            for (int i = 0; i < headers.length; i++) {
                sheet.autoSizeColumn(i);
            }

            recordAudit("EXPORT_REPORT", "REPORT", null, "Exported submissions compliance report with " + (counter - 1) + " rows");

            ByteArrayOutputStream bos = new ByteArrayOutputStream();
            workbook.write(bos);
            return bos.toByteArray();
        }
    }

    @Override
    public void recordAudit(String action, String targetEntity, Long targetId, String details) {
        Long userId = null;
        try {
            UserDto currentUser = userService.getCurrentUser();
            if (currentUser != null) {
                userId = currentUser.id();
            }
        } catch (Exception e) {
            // Unauthenticated or system action
        }

        AuditLog logEntry = new AuditLog(userId, action, targetEntity, targetId, details, "127.0.0.1");
        auditLogRepository.save(logEntry);
    }

    @Override
    @Transactional(readOnly = true)
    public List<AuditLogDto> getRecentAuditLogs(int limit) {
        int pageSize = Math.min(Math.max(limit, 1), 100);
        List<AuditLog> logs = auditLogRepository.findRecentLogs(PageRequest.of(0, pageSize));
        List<AuditLogDto> dtos = new ArrayList<>();

        for (AuditLog l : logs) {
            String userName = "System";
            if (l.getUserId() != null) {
                userName = userService.findById(l.getUserId())
                        .map(UserDto::name)
                        .orElse("User #" + l.getUserId());
            }
            dtos.add(AuditLogDto.fromEntity(l, userName));
        }

        return dtos;
    }
}
