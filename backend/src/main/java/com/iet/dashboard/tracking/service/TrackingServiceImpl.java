package com.iet.dashboard.tracking.service;

import com.iet.dashboard.academics.service.AcademicsService;
import com.iet.dashboard.academics.service.PacingCalculationService;
import com.iet.dashboard.academics.web.CourseOfferingDto;
import com.iet.dashboard.academics.web.CourseProgressDto;
import com.iet.dashboard.academics.web.TeachingAssignmentDto;
import com.iet.dashboard.deadlines.service.DeadlineService;
import com.iet.dashboard.deadlines.web.DeadlineAssignmentDto;
import com.iet.dashboard.deadlines.web.DeadlineDto;
import com.iet.dashboard.directory.service.FacultyService;
import com.iet.dashboard.directory.web.FacultyDto;
import com.iet.dashboard.identity.service.UserService;
import com.iet.dashboard.identity.web.UserDto;
import com.iet.dashboard.submissions.service.SubmissionService;
import com.iet.dashboard.submissions.web.SubmissionDto;
import com.iet.dashboard.tracking.web.ComplianceMatrixDto;
import com.iet.dashboard.tracking.web.DeadlineTrackingRowDto;
import com.iet.dashboard.tracking.web.DepartmentMetricsDto;
import com.iet.dashboard.tracking.web.FacultyComplianceRowDto;
import com.iet.dashboard.tracking.web.OverviewSummaryDto;
import org.apache.poi.ss.usermodel.*;
import org.apache.poi.xssf.usermodel.XSSFCellStyle;
import org.apache.poi.xssf.usermodel.XSSFColor;
import org.apache.poi.xssf.usermodel.XSSFSheet;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.time.Instant;
import java.util.*;

import com.iet.dashboard.academics.web.AcademicTermDto;
import com.iet.dashboard.teaching.domain.SessionStatus;
import com.iet.dashboard.teaching.domain.TeachingSession;
import com.iet.dashboard.teaching.repo.TeachingSessionRepository;
import com.iet.dashboard.academics.domain.TimetableSlot;
import com.iet.dashboard.academics.repo.TimetableSlotRepository;
import java.time.LocalDate;
import java.time.temporal.ChronoUnit;

@Service
@Transactional
public class TrackingServiceImpl implements TrackingService {

    private static final Logger log = LoggerFactory.getLogger(TrackingServiceImpl.class);

    private final DeadlineService deadlineService;
    private final FacultyService facultyService;
    private final SubmissionService submissionService;
    private final UserService userService;
    private final AcademicsService academicsService;
    private final PacingCalculationService pacingService;
    private final TeachingSessionRepository sessionRepository;
    private final TimetableSlotRepository timetableSlotRepository;

    public TrackingServiceImpl(
            DeadlineService deadlineService,
            FacultyService facultyService,
            SubmissionService submissionService,
            UserService userService,
            AcademicsService academicsService,
            PacingCalculationService pacingService,
            TeachingSessionRepository sessionRepository,
            TimetableSlotRepository timetableSlotRepository
    ) {
        this.deadlineService = deadlineService;
        this.facultyService = facultyService;
        this.submissionService = submissionService;
        this.userService = userService;
        this.academicsService = academicsService;
        this.pacingService = pacingService;
        this.sessionRepository = sessionRepository;
        this.timetableSlotRepository = timetableSlotRepository;
    }

    @Override
    @Transactional(readOnly = true)
    public DepartmentMetricsDto getDepartmentMetrics() {
        List<DeadlineDto> deadlines = deadlineService.getAllDeadlines();
        List<FacultyDto> allFaculty = facultyService.getAllFaculty(null, null);

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
            Map<Long, String> statuses = submissionStatuses(deadline.id());
            for (DeadlineAssignmentDto assignment : assignments) {
                totalAssignments++;
                Long fId = assignment.facultyId();
                facultyTotalAssignments.put(fId, facultyTotalAssignments.getOrDefault(fId, 0) + 1);

                String status = statuses.getOrDefault(assignment.id(), "PENDING");
                if ("SUBMITTED".equals(status) || "ACCEPTED".equals(status)) {
                    totalSubmissions++;
                    onTimeSubmissions++;
                    facultyCompleted.put(fId, facultyCompleted.getOrDefault(fId, 0) + 1);
                } else if ("LATE".equals(status)) {
                    totalSubmissions++;
                    facultyCompleted.put(fId, facultyCompleted.getOrDefault(fId, 0) + 1);
                } else if ("RETURNED".equals(status)) {
                    totalSubmissions++;
                } else if (assignment.isOverdue()) {
                    overdueCount++;
                }
            }
        }

        double submissionRate = totalAssignments > 0
                ? Math.round((double) totalSubmissions / totalAssignments * 1000.0) / 10.0
                : 0.0;

        double onTimeRate = totalSubmissions > 0
                ? Math.round((double) onTimeSubmissions / totalSubmissions * 1000.0) / 10.0
                : 0.0;

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

            Map<Long, String> statuses = submissionStatuses(deadline.id());
            for (DeadlineAssignmentDto a : assignments) {
                String status = statuses.getOrDefault(a.id(), "PENDING");
                if ("ACCEPTED".equals(status)) {
                    accepted++;
                    submitted++;
                } else if ("SUBMITTED".equals(status) || "LATE".equals(status) || "RETURNED".equals(status)) {
                    submitted++;
                } else if (a.isOverdue()) {
                    overdue++;
                }
            }

            double complianceRate = total > 0
                    ? Math.round((double) submitted / total * 1000.0) / 10.0
                    : 0.0;

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
        List<FacultyDto> facultyList = facultyService.getAllFaculty(null, null);
        List<DeadlineDto> deadlines = deadlineService.getAllDeadlines();

        Map<Long, List<DeadlineAssignmentDto>> facultyAssignments = new HashMap<>();
        for (FacultyDto f : facultyList) {
            facultyAssignments.put(f.id(), new ArrayList<>());
        }

        Map<Long, String> statusByAssignment = new HashMap<>();
        for (DeadlineDto d : deadlines) {
            List<DeadlineAssignmentDto> assignments = deadlineService.getAssignmentsForDeadline(d.id());
            statusByAssignment.putAll(submissionStatuses(d.id()));
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
                String status = statusByAssignment.getOrDefault(a.id(), "PENDING");
                if ("ACCEPTED".equals(status)) {
                    accepted++;
                    submitted++;
                } else if ("SUBMITTED".equals(status) || "LATE".equals(status) || "RETURNED".equals(status)) {
                    submitted++;
                } else {
                    pending++;
                }
            }

            double complianceRate = total > 0
                    ? Math.round((double) submitted / total * 1000.0) / 10.0
                    : 0.0;

            String status = total == 0 ? "NOT_STARTED" : (complianceRate < 60.0 ? "CRITICAL" : (complianceRate < 85.0 ? "WATCH" : "GOOD"));

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
    @Transactional(readOnly = true)
    public com.iet.dashboard.tracking.web.FacultyProfileDto getFacultyProfile(Long facultyId) {
        FacultyDto faculty = facultyService.getFacultyById(facultyId);

        String email = faculty.userId() != null
                ? userService.findById(faculty.userId()).map(UserDto::email).orElse(null)
                : null;

        // Courses & sections they own, with the LTP component share on each assignment.
        List<com.iet.dashboard.tracking.web.FacultyProfileDto.CourseAssignment> courses =
                academicsService.getTeachingAssignmentsForFaculty(facultyId).stream()
                        .map(a -> new com.iet.dashboard.tracking.web.FacultyProfileDto.CourseAssignment(
                                a.id(),
                                a.offering().id(),
                                a.offering().course().code(),
                                a.offering().course().name(),
                                a.offering().course().category(),
                                a.offering().course().ltp(),
                                a.offering().course().type(),
                                a.offering().academicYear(),
                                a.offering().yearOfStudy(),
                                a.offering().semester(),
                                a.offering().section(),
                                a.component(),
                                a.subSection(),
                                a.contactHours()))
                        .toList();

        com.iet.dashboard.academics.web.FacultyLtpLoadDto load = academicsService.getFacultyLtpLoad().stream()
                .filter(row -> row.facultyId().equals(facultyId))
                .findFirst()
                .orElse(new com.iet.dashboard.academics.web.FacultyLtpLoadDto(
                        facultyId, faculty.name(), 0, 0, 0, 0, 0));

        FacultyComplianceRowDto compliance = getFacultyComplianceSummary().stream()
                .filter(row -> row.facultyId().equals(facultyId))
                .findFirst()
                .orElse(null);

        // Every deadline this faculty member was targeted for, newest due date first.
        List<com.iet.dashboard.tracking.web.FacultyProfileDto.SubmissionRow> submissions = new ArrayList<>();
        for (DeadlineDto deadline : deadlineService.getAllDeadlines()) {
            for (DeadlineAssignmentDto assignment : deadlineService.getAssignmentsForDeadline(deadline.id())) {
                if (!facultyId.equals(assignment.facultyId())) {
                    continue;
                }
                SubmissionDto submission;
                try {
                    submission = submissionService.getSubmissionByAssignmentId(assignment.id());
                } catch (Exception e) {
                    log.warn("Could not read submission for assignment {}: {}", assignment.id(), e.getMessage());
                    continue;
                }
                submissions.add(new com.iet.dashboard.tracking.web.FacultyProfileDto.SubmissionRow(
                        assignment.id(),
                        deadline.title(),
                        deadline.type(),
                        deadline.dueAt(),
                        submission.status(),
                        assignment.isOverdue(),
                        submission.totalVersions(),
                        submission.submittedAt()));
            }
        }
        submissions.sort(Comparator.comparing(
                com.iet.dashboard.tracking.web.FacultyProfileDto.SubmissionRow::dueAt,
                Comparator.nullsLast(Comparator.reverseOrder())));

        return new com.iet.dashboard.tracking.web.FacultyProfileDto(
                faculty.id(),
                faculty.name(),
                email,
                faculty.designation(),
                faculty.qualifications(),
                faculty.department(),
                faculty.skills(),
                courses,
                load,
                compliance,
                submissions);
    }

    @Override
    public byte[] generateSubmissionsExcelReport() throws IOException {
        log.info("Generating submissions Excel audit workbook...");
        List<DeadlineDto> deadlines = deadlineService.getAllDeadlines();
        Map<Long, FacultyDto> facultyMap = new HashMap<>();
        facultyService.getAllFaculty(null, null).forEach(f -> facultyMap.put(f.id(), f));

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

            ByteArrayOutputStream bos = new ByteArrayOutputStream();
            workbook.write(bos);
            return bos.toByteArray();
        }
    }



    /** A faculty member with timetabled classes but no log for this many days is flagged. */
    static final int NOT_LOGGING_AFTER_DAYS = 7;

    @Override
    @Transactional(readOnly = true)
    public OverviewSummaryDto getOverviewSummary() {
        List<CourseOfferingDto> offerings = academicsService.getAllOfferings(null, null);
        List<TeachingAssignmentDto> assignments = academicsService.getAllTeachingAssignments(null);
        List<TeachingSession> allSessions = sessionRepository.findAll();
        List<TimetableSlot> slots = timetableSlotRepository.findAll();
        AcademicTermDto activeTerm = academicsService.getActiveTerm();
        Map<Long, CourseProgressDto> progressByOffering = pacingService.calculateProgressForOfferings(offerings);
        LocalDate today = LocalDate.now();
        OverviewSummaryDto.TermInfo term = termInfo(activeTerm, today);

        Map<Long, List<TeachingAssignmentDto>> assignmentsByOffering = new HashMap<>();
        for (TeachingAssignmentDto a : assignments) {
            if (a.offering() != null) {
                assignmentsByOffering.computeIfAbsent(a.offering().id(), k -> new ArrayList<>()).add(a);
            }
        }
        Map<Long, LocalDate> lastSessionByOffering = new HashMap<>();
        for (TeachingSession s : allSessions) {
            if (s.getOffering() != null && s.getSessionDate() != null) {
                lastSessionByOffering.merge(s.getOffering().getId(), s.getSessionDate(), (x, y) -> x.isAfter(y) ? x : y);
            }
        }

        List<OverviewSummaryDto.CourseCoverageRankingItem> rankingItems = new ArrayList<>();
        for (CourseOfferingDto offering : offerings) {
            List<TeachingAssignmentDto> owners = assignmentsByOffering.getOrDefault(offering.id(), List.of());
            String facultyNames = owners.stream().map(TeachingAssignmentDto::facultyName).distinct()
                    .reduce((x, y) -> x + ", " + y).orElse("Unassigned");
            CourseProgressDto progress = progressByOffering.get(offering.id());
            int expectedPct = progress.totalTopics() > 0
                    ? (int) Math.round((progress.expectedTopicsByCurrentWeek() * 100.0) / progress.totalTopics())
                    : 0;
            rankingItems.add(new OverviewSummaryDto.CourseCoverageRankingItem(
                    offering.id(), offering.course().code(), offering.course().name(), offering.semester(),
                    offering.section(), facultyNames, progress.coveredTopics(), progress.totalTopics(),
                    progress.coveragePercentage(), expectedPct, progress.pacingStatus(), progress.topicDelta(),
                    progress.totalTopics() > 0, lastSessionByOffering.get(offering.id()),
                    offering.yearOfStudy(), offering.studentCount()));
        }
        // Priority: furthest behind the plan first; sections without a syllabus cannot be paced and go last.
        rankingItems.sort(Comparator
                .comparing((OverviewSummaryDto.CourseCoverageRankingItem i) -> !i.syllabusConfigured())
                .thenComparingInt(OverviewSummaryDto.CourseCoverageRankingItem::topicDelta)
                .thenComparingInt(OverviewSummaryDto.CourseCoverageRankingItem::coveragePercentage)
                .thenComparing(OverviewSummaryDto.CourseCoverageRankingItem::courseCode));

        List<OverviewSummaryDto.CourseCoverageRankingItem> paced = rankingItems.stream()
                .filter(OverviewSummaryDto.CourseCoverageRankingItem::syllabusConfigured).toList();
        long coursesBehind = paced.stream().filter(i -> "BEHIND".equals(i.pacingStatus())).count();
        Integer avgCoverage = paced.isEmpty() ? null
                : (int) Math.round(paced.stream().mapToInt(OverviewSummaryDto.CourseCoverageRankingItem::coveragePercentage).average().orElse(0));
        long held = allSessions.stream().filter(s -> s.getStatus() == SessionStatus.HELD).count();
        long cancelled = allSessions.stream().filter(s -> s.getStatus() == SessionStatus.CANCELLED).count();
        Integer avgConduct = held + cancelled > 0 ? (int) Math.round(held * 100.0 / (held + cancelled)) : null;

        List<OverviewSummaryDto.FacultyPerformanceRow> facultyPerformance =
                facultyPerformance(assignments, allSessions, slots, rankingItems, activeTerm, today);
        int scheduled = facultyPerformance.stream().mapToInt(OverviewSummaryDto.FacultyPerformanceRow::scheduledSoFar).sum();
        long loggedAgainstSchedule = facultyPerformance.stream()
                .mapToLong(r -> r.scheduledSoFar() > 0 ? Math.min(r.sessionsHeld() + r.sessionsCancelled(), r.scheduledSoFar()) : 0).sum();
        Integer loggingRate = scheduled > 0 ? (int) Math.round(loggedAgainstSchedule * 100.0 / scheduled) : null;
        long totalStudents = offerings.stream().mapToLong(CourseOfferingDto::studentCount).sum();

        List<OverviewSummaryDto.NeedsAttentionItem> needsAttention = new ArrayList<>();
        for (OverviewSummaryDto.FacultyPerformanceRow row : facultyPerformance) {
            if ("NOT_LOGGING".equals(row.status())) {
                needsAttention.add(new OverviewSummaryDto.NeedsAttentionItem(
                        "not-logging-" + row.facultyId(), "NOT_LOGGING", "WARNING", row.facultyName(),
                        row.lastSessionDate() == null
                                ? "Not logging · none of " + row.scheduledSoFar() + " timetabled classes recorded"
                                : "Not logging · last class recorded " + row.daysSinceLastLog() + " days ago",
                        row.facultyId(), row.facultyName(), "Remind Faculty"));
            }
        }
        for (OverviewSummaryDto.CourseCoverageRankingItem item : paced) {
            if ("BEHIND".equals(item.pacingStatus())) {
                TeachingAssignmentDto owner = assignmentsByOffering.getOrDefault(item.offeringId(), List.of())
                        .stream().findFirst().orElse(null);
                needsAttention.add(new OverviewSummaryDto.NeedsAttentionItem(
                        "pacing-" + item.offeringId(), "COVERAGE_BEHIND",
                        item.topicDelta() <= -3 ? "CRITICAL" : "WARNING",
                        item.courseCode() + " · Sem " + item.semester() + " · Sec " + item.section(),
                        "Behind plan by " + -item.topicDelta() + " topics · " + item.facultyName(),
                        owner != null ? owner.facultyId() : null,
                        owner != null ? owner.facultyName() : item.facultyName(), "Remind Faculty"));
            }
        }
        Instant now = Instant.now();
        for (DeadlineDto d : deadlineService.getAllDeadlines()) {
            if (!d.dueAt().isBefore(now)) {
                continue;
            }
            Map<Long, String> statusByAssignment = submissionStatuses(d.id());
            for (DeadlineAssignmentDto da : deadlineService.getAssignmentsForDeadline(d.id())) {
                String status = statusByAssignment.getOrDefault(da.id(), "PENDING");
                if ("PENDING".equals(status) || "RETURNED".equals(status)) {
                    needsAttention.add(new OverviewSummaryDto.NeedsAttentionItem(
                            "overdue-" + da.id(), "OVERDUE_SUBMISSION", "CRITICAL", d.title() + " — Past Due",
                            da.facultyName() + " • " + ("RETURNED".equals(status) ? "Returned work not resubmitted" : "Not submitted"),
                            da.facultyId(), da.facultyName(), "Remind Faculty"));
                }
            }
        }
        for (CourseOfferingDto off : offerings) {
            if (!assignmentsByOffering.containsKey(off.id())) {
                needsAttention.add(new OverviewSummaryDto.NeedsAttentionItem(
                        "unassigned-" + off.id(), "UNASSIGNED_COURSE", "CRITICAL",
                        off.course().code() + " · Sem " + off.semester() + " · Sec " + off.section(),
                        "No faculty member allocated", null, "Unassigned", "Assign Faculty"));
            }
        }
        needsAttention.sort(Comparator.comparingInt(i -> severityRank(i.severity())));

        OverviewSummaryDto.OverviewKpis kpis = new OverviewSummaryDto.OverviewKpis(
                avgCoverage, avgConduct, coursesBehind, needsAttention.size(),
                rankingItems.size() - paced.size(),
                facultyPerformance.stream().filter(r -> "NOT_LOGGING".equals(r.status())).count(),
                loggingRate,
                facultyPerformance.stream().filter(r -> "NO_TIMETABLE".equals(r.status())).count(),
                totalStudents);

        return new OverviewSummaryDto(term, kpis, rankingItems, conductTrajectory(allSessions, activeTerm),
                needsAttention, facultyPerformance);
    }

    private static OverviewSummaryDto.TermInfo termInfo(AcademicTermDto term, LocalDate today) {
        if (term == null || term.startDate() == null) {
            return new OverviewSummaryDto.TermInfo(null, null, null, null, null, false);
        }
        int totalWeeks = term.endDate() != null
                ? (int) Math.max(1, (ChronoUnit.DAYS.between(term.startDate(), term.endDate()) + 6) / 7) : 16;
        boolean started = !term.startDate().isAfter(today);
        Integer week = started
                ? (int) Math.min(totalWeeks, ChronoUnit.DAYS.between(term.startDate(), today) / 7 + 1) : null;
        return new OverviewSummaryDto.TermInfo(term.name(), term.startDate(), term.endDate(), week, totalWeeks, started);
    }

    private static List<OverviewSummaryDto.WeeklyConductPoint> conductTrajectory(List<TeachingSession> sessions, AcademicTermDto term) {
        List<OverviewSummaryDto.WeeklyConductPoint> points = new ArrayList<>();
        LocalDate termStart = term != null ? term.startDate() : null;
        if (sessions.isEmpty() || termStart == null) {
            return points;
        }
        TreeMap<Integer, long[]> byWeek = new TreeMap<>();
        for (TeachingSession s : sessions) {
            if (s.getSessionDate() == null || s.getSessionDate().isBefore(termStart)) {
                continue;
            }
            int week = (int) (ChronoUnit.DAYS.between(termStart, s.getSessionDate()) / 7) + 1;
            long[] counts = byWeek.computeIfAbsent(week, k -> new long[2]);
            if (s.getStatus() == SessionStatus.HELD) {
                counts[0]++;
            } else if (s.getStatus() == SessionStatus.CANCELLED) {
                counts[1]++;
            }
        }
        int lastWeek = byWeek.isEmpty() ? 0 : byWeek.lastKey();
        for (int w = 1; w <= lastWeek; w++) {
            long[] c = byWeek.getOrDefault(w, new long[2]);
            long total = c[0] + c[1];
            points.add(new OverviewSummaryDto.WeeklyConductPoint(w, total > 0 ? (int) Math.round(c[0] * 100.0 / total) : 0, c[0], c[1]));
        }
        return points;
    }

    /**
     * Classes a timetable expects a faculty member to have logged by now: every slot occurrence
     * from the later of the semester start and the day the slot was uploaded, up to yesterday
     * (today's classes may not have happened yet) and never past the semester end.
     */
    static int scheduledBetween(List<TimetableSlot> facultySlots, LocalDate termStart, LocalDate termEnd, LocalDate from, LocalDate today) {
        LocalDate last = today.minusDays(1);
        if (termEnd != null && termEnd.isBefore(last)) {
            last = termEnd;
        }
        int count = 0;
        for (TimetableSlot slot : facultySlots) {
            LocalDate uploaded = slot.getCreatedAt().atZone(java.time.ZoneId.systemDefault()).toLocalDate();
            LocalDate first = java.util.stream.Stream.of(termStart, uploaded, from)
                    .filter(Objects::nonNull).max(Comparator.naturalOrder()).orElse(uploaded);
            java.time.DayOfWeek dow = java.time.DayOfWeek.valueOf(slot.getDayOfWeek());
            for (LocalDate d = first; !d.isAfter(last); d = d.plusDays(1)) {
                if (d.getDayOfWeek() == dow) {
                    count++;
                }
            }
        }
        return count;
    }

    private List<OverviewSummaryDto.FacultyPerformanceRow> facultyPerformance(
            List<TeachingAssignmentDto> assignments,
            List<TeachingSession> sessions,
            List<TimetableSlot> slots,
            List<OverviewSummaryDto.CourseCoverageRankingItem> ranking,
            AcademicTermDto term,
            LocalDate today
    ) {
        LocalDate termStart = term != null ? term.startDate() : null;
        LocalDate termEnd = term != null ? term.endDate() : null;
        boolean termRunning = termStart != null && !termStart.isAfter(today);

        Map<Long, com.iet.dashboard.academics.web.FacultyLtpLoadDto> loads = new HashMap<>();
        for (var load : academicsService.getFacultyLtpLoad()) {
            loads.put(load.facultyId(), load);
        }
        Map<Long, OverviewSummaryDto.CourseCoverageRankingItem> rankingByOffering = new HashMap<>();
        ranking.forEach(item -> rankingByOffering.put(item.offeringId(), item));
        Map<Long, List<TimetableSlot>> slotsByFaculty = new HashMap<>();
        slots.forEach(slot -> slotsByFaculty.computeIfAbsent(slot.getFacultyId(), k -> new ArrayList<>()).add(slot));

        Map<Long, String> names = new LinkedHashMap<>();
        Map<Long, Set<Long>> offeringsByFaculty = new HashMap<>();
        for (TeachingAssignmentDto a : assignments) {
            if (a.offering() != null) {
                names.putIfAbsent(a.facultyId(), a.facultyName());
                offeringsByFaculty.computeIfAbsent(a.facultyId(), k -> new HashSet<>()).add(a.offering().id());
            }
        }

        List<OverviewSummaryDto.FacultyPerformanceRow> rows = new ArrayList<>();
        for (Map.Entry<Long, String> entry : names.entrySet()) {
            Long facultyId = entry.getKey();
            List<TeachingSession> own = sessions.stream()
                    .filter(s -> facultyId.equals(s.getFacultyId()) && (termStart == null || !s.getSessionDate().isBefore(termStart)))
                    .toList();
            long heldCount = own.stream().filter(s -> s.getStatus() == SessionStatus.HELD).count();
            long cancelledCount = own.stream().filter(s -> s.getStatus() == SessionStatus.CANCELLED).count();
            LocalDate last = own.stream().map(TeachingSession::getSessionDate).filter(Objects::nonNull)
                    .max(Comparator.naturalOrder()).orElse(null);
            Integer daysSince = last != null ? (int) ChronoUnit.DAYS.between(last, today) : null;

            List<TimetableSlot> mySlots = slotsByFaculty.getOrDefault(facultyId, List.of());
            int scheduled = termRunning ? scheduledBetween(mySlots, termStart, termEnd, null, today) : 0;
            int scheduledLastWeek = termRunning
                    ? scheduledBetween(mySlots, termStart, termEnd, today.minusDays(NOT_LOGGING_AFTER_DAYS), today) : 0;
            Integer rate = scheduled > 0 ? (int) Math.round(Math.min(heldCount + cancelledCount, scheduled) * 100.0 / scheduled) : null;

            Set<Long> offeringIds = offeringsByFaculty.getOrDefault(facultyId, Set.of());
            List<OverviewSummaryDto.CourseCoverageRankingItem> paced = offeringIds.stream()
                    .map(rankingByOffering::get)
                    .filter(i -> i != null && i.syllabusConfigured())
                    .toList();
            int behind = (int) paced.stream().filter(i -> "BEHIND".equals(i.pacingStatus())).count();
            Integer avgCoverage = paced.isEmpty() ? null
                    : (int) Math.round(paced.stream().mapToInt(OverviewSummaryDto.CourseCoverageRankingItem::coveragePercentage).average().orElse(0));

            boolean silentLastWeek = daysSince == null || daysSince > NOT_LOGGING_AFTER_DAYS;
            String status;
            if (!termRunning) {
                status = "NOT_STARTED";
            } else if (mySlots.isEmpty()) {
                status = "NO_TIMETABLE";
            } else if (scheduledLastWeek > 0 && silentLastWeek) {
                status = "NOT_LOGGING";
            } else if (behind > 0) {
                status = "BEHIND";
            } else if (heldCount + cancelledCount > 0) {
                status = "ON_TRACK";
            } else {
                status = "NO_DATA";
            }

            var load = loads.get(facultyId);
            rows.add(new OverviewSummaryDto.FacultyPerformanceRow(
                    facultyId, entry.getValue(), offeringIds.size(),
                    load != null ? load.totalHours() : 0, load != null ? load.pblHours() : 0,
                    heldCount, cancelledCount, last, daysSince, behind, avgCoverage, scheduled, rate, status));
        }

        List<String> order = List.of("NOT_LOGGING", "BEHIND", "NO_TIMETABLE", "ON_TRACK", "NO_DATA", "NOT_STARTED");
        rows.sort(Comparator
                .comparingInt((OverviewSummaryDto.FacultyPerformanceRow r) -> order.indexOf(r.status()))
                .thenComparing(r -> r.loggingRate() == null ? Integer.MAX_VALUE : r.loggingRate())
                .thenComparing(OverviewSummaryDto.FacultyPerformanceRow::facultyName));
        return rows;
    }

    private static int severityRank(String severity) {
        return switch (severity) {
            case "CRITICAL" -> 0;
            case "WARNING" -> 1;
            default -> 2;
        };
    }

    /** Submission status per deadline assignment, read once per deadline instead of once per assignment. */
    private Map<Long, String> submissionStatuses(Long deadlineId) {
        return submissionService.getSubmissionStatusesForDeadline(deadlineId);
    }

    /**
     * A milestone is a deadline type. A section's cell looks at every deadline of that type and the
     * assignments that target the section (or its faculty, when the deadline was not section-specific):
     * done when all are submitted, overdue when any past-due one is missing, otherwise pending.
     */
    private static final List<String[]> MILESTONES = List.of(
            new String[]{"MID_SEM_QUESTION_BANK", "Question bank", "Mid-semester question paper and scheme"},
            new String[]{"MARKS_UPLOAD", "Marks uploaded", "Internal test and quiz marks"},
            new String[]{"COURSE_FILE", "Course file", "Lecture notes and course material"},
            new String[]{"REMEDIAL_PLAN", "Remedial plan", "Action plan for lagging sections"}
    );

    @Override
    @Transactional(readOnly = true)
    public ComplianceMatrixDto getComplianceMatrix() {
        List<ComplianceMatrixDto.CheckpointHeader> headers = new ArrayList<>();
        headers.add(new ComplianceMatrixDto.CheckpointHeader("SYLLABUS", "Syllabus", "60% of topics covered"));
        MILESTONES.forEach(m -> headers.add(new ComplianceMatrixDto.CheckpointHeader(m[0], m[1], m[2])));

        List<CourseOfferingDto> offerings = academicsService.getAllOfferings(null, null);
        List<TeachingAssignmentDto> assignments = academicsService.getAllTeachingAssignments(null);
        Map<Long, CourseProgressDto> progressByOffering = pacingService.calculateProgressForOfferings(offerings);
        Instant now = Instant.now();

        // Every deadline assignment of a milestone type, with its submission status, loaded once.
        Map<String, List<DeadlineDto>> deadlinesByType = new HashMap<>();
        Map<Long, List<DeadlineAssignmentDto>> assignmentsByDeadline = new HashMap<>();
        Map<Long, String> statusByAssignment = new HashMap<>();
        for (DeadlineDto d : deadlineService.getAllDeadlines()) {
            if (MILESTONES.stream().noneMatch(m -> m[0].equals(d.type()))) {
                continue;
            }
            deadlinesByType.computeIfAbsent(d.type(), k -> new ArrayList<>()).add(d);
            assignmentsByDeadline.put(d.id(), deadlineService.getAssignmentsForDeadline(d.id()));
            statusByAssignment.putAll(submissionStatuses(d.id()));
        }

        List<ComplianceMatrixDto.MatrixCourseRow> rows = new ArrayList<>();
        for (CourseOfferingDto offering : offerings) {
            List<TeachingAssignmentDto> owners = assignments.stream()
                    .filter(a -> a.offering() != null && a.offering().id().equals(offering.id())).toList();
            Set<Long> ownerIds = new HashSet<>();
            owners.forEach(a -> ownerIds.add(a.facultyId()));
            String facultyName = owners.stream().map(TeachingAssignmentDto::facultyName).distinct()
                    .reduce((x, y) -> x + ", " + y).orElse("Unassigned");

            List<ComplianceMatrixDto.CheckpointCell> cells = new ArrayList<>();
            CourseProgressDto progress = progressByOffering.get(offering.id());
            if (progress.totalTopics() == 0) {
                cells.add(new ComplianceMatrixDto.CheckpointCell("SYLLABUS", "PENDING", "No syllabus loaded", null));
            } else if (progress.coveragePercentage() >= 60) {
                cells.add(new ComplianceMatrixDto.CheckpointCell("SYLLABUS", "DONE", progress.coveragePercentage() + "% covered", null));
            } else if ("BEHIND".equals(progress.pacingStatus()) && progress.topicDelta() <= -2) {
                cells.add(new ComplianceMatrixDto.CheckpointCell("SYLLABUS", "OVERDUE", progress.coveragePercentage() + "% covered, behind plan", null));
            } else {
                cells.add(new ComplianceMatrixDto.CheckpointCell("SYLLABUS", "PENDING", progress.coveragePercentage() + "% covered", null));
            }

            for (String[] milestone : MILESTONES) {
                List<DeadlineAssignmentDto> targeted = new ArrayList<>();
                List<DeadlineDto> ofType = deadlinesByType.getOrDefault(milestone[0], List.of());
                Map<Long, DeadlineDto> deadlineOf = new HashMap<>();
                for (DeadlineDto d : ofType) {
                    for (DeadlineAssignmentDto da : assignmentsByDeadline.getOrDefault(d.id(), List.of())) {
                        boolean forThisSection = offering.id().equals(da.offeringId())
                                || (da.offeringId() == null && ownerIds.contains(da.facultyId()));
                        if (forThisSection) {
                            targeted.add(da);
                            deadlineOf.put(da.id(), d);
                        }
                    }
                }
                cells.add(milestoneCell(milestone[0], ofType.isEmpty(), targeted, deadlineOf, statusByAssignment, now));
            }

            rows.add(new ComplianceMatrixDto.MatrixCourseRow(offering.id(), offering.course().code(),
                    offering.course().name(), offering.section(), ownerIds.stream().findFirst().orElse(null), facultyName, cells));
        }
        return new ComplianceMatrixDto(headers, rows);
    }

    private static ComplianceMatrixDto.CheckpointCell milestoneCell(String key, boolean noDeadline,
                                                                   List<DeadlineAssignmentDto> targeted,
                                                                   Map<Long, DeadlineDto> deadlineOf,
                                                                   Map<Long, String> statusByAssignment, Instant now) {
        if (noDeadline) {
            return new ComplianceMatrixDto.CheckpointCell(key, "PENDING", "No deadline set", null);
        }
        if (targeted.isEmpty()) {
            return new ComplianceMatrixDto.CheckpointCell(key, "PENDING", "Not assigned to this section", null);
        }
        int done = 0;
        boolean overdue = false;
        for (DeadlineAssignmentDto da : targeted) {
            String status = statusByAssignment.getOrDefault(da.id(), "PENDING");
            if (status.equals("SUBMITTED") || status.equals("ACCEPTED") || status.equals("LATE")) {
                done++;
            } else if (deadlineOf.get(da.id()).dueAt().isBefore(now)) {
                overdue = true;
            }
        }
        String note = done + " of " + targeted.size() + " submitted";
        if (done == targeted.size()) {
            return new ComplianceMatrixDto.CheckpointCell(key, "DONE", note, null);
        }
        return new ComplianceMatrixDto.CheckpointCell(key, overdue ? "OVERDUE" : "PENDING", note, null);
    }

    @Override
    @Transactional(readOnly = true)
    public com.iet.dashboard.tracking.web.IrregularityAuditDto getIrregularitiesAudit() {
        List<CourseOfferingDto> offerings = academicsService.getAllOfferings(null, null);
        List<TeachingAssignmentDto> assignments = academicsService.getAllTeachingAssignments(null);
        Map<Long, List<com.iet.dashboard.academics.web.SyllabusTopicDto>> syllabus = academicsService.getSyllabusByCourse();
        List<com.iet.dashboard.tracking.web.IrregularityAuditDto.OfferingIrregularityItem> items = new ArrayList<>();
        int criticalCount = 0;
        int warningCount = 0;

        for (CourseOfferingDto offering : offerings) {
            List<TeachingAssignmentDto> owners = assignments.stream()
                    .filter(a -> a.offering() != null && a.offering().id().equals(offering.id())).toList();
            var course = offering.course();
            String ltp = course.ltp() != null ? course.ltp() : "—";
            String assigned = owners.isEmpty() ? "None"
                    : owners.stream().map(TeachingAssignmentDto::facultyName).distinct().reduce((a, b) -> a + ", " + b).orElse("None");

            if (owners.isEmpty()) {
                criticalCount++;
                items.add(new com.iet.dashboard.tracking.web.IrregularityAuditDto.OfferingIrregularityItem(
                        offering.id(), course.code(), course.name(), offering.section(), ltp, "MISSING_FACULTY", "CRITICAL",
                        "No faculty member is assigned to this section", "None"));
            } else {
                if (course.practicalHours() > 0 && owners.stream().noneMatch(a -> covers(a, "PRACTICAL"))) {
                    warningCount++;
                    items.add(new com.iet.dashboard.tracking.web.IrregularityAuditDto.OfferingIrregularityItem(
                            offering.id(), course.code(), course.name(), offering.section(), ltp, "UNASSIGNED_LAB", "WARNING",
                            "Plans " + course.practicalHours() + "h of practicals a week, but nobody owns them", assigned));
                }
                if (course.tutorialHours() > 0 && owners.stream().noneMatch(a -> covers(a, "TUTORIAL"))) {
                    warningCount++;
                    items.add(new com.iet.dashboard.tracking.web.IrregularityAuditDto.OfferingIrregularityItem(
                            offering.id(), course.code(), course.name(), offering.section(), ltp, "UNASSIGNED_TUTORIAL", "WARNING",
                            "Plans " + course.tutorialHours() + "h of tutorials a week, but nobody owns them", assigned));
                }
            }
            if (syllabus.getOrDefault(course.id(), List.of()).isEmpty()) {
                items.add(new com.iet.dashboard.tracking.web.IrregularityAuditDto.OfferingIrregularityItem(
                        offering.id(), course.code(), course.name(), offering.section(), ltp, "NO_SYLLABUS", "INFO",
                        "No syllabus loaded, so pacing can't be measured", assigned));
            }
        }
        items.sort(Comparator.comparingInt(item -> severityRank(item.severity())));
        return new com.iet.dashboard.tracking.web.IrregularityAuditDto(items.size(), criticalCount, warningCount, items);
    }

    private static boolean covers(TeachingAssignmentDto a, String component) {
        String c = com.iet.dashboard.academics.domain.CourseComponent.parse(a.component()).name();
        return c.equals(component) || c.equals("ALL");
    }
}
