package com.iet.dashboard.tracking.service;

import com.iet.dashboard.academics.domain.Assessment;
import com.iet.dashboard.academics.domain.CourseComponent;
import com.iet.dashboard.academics.domain.RemedialPlan;
import com.iet.dashboard.academics.domain.TimetableSlot;
import com.iet.dashboard.academics.repo.AssessmentRepository;
import com.iet.dashboard.academics.repo.RemedialPlanRepository;
import com.iet.dashboard.academics.repo.TimetableSlotRepository;
import com.iet.dashboard.academics.service.AcademicsService;
import com.iet.dashboard.academics.service.PacingCalculationService;
import com.iet.dashboard.academics.web.AcademicTermDto;
import com.iet.dashboard.academics.web.CourseOfferingDto;
import com.iet.dashboard.academics.web.CourseProgressDto;
import com.iet.dashboard.academics.web.FacultyLtpLoadDto;
import com.iet.dashboard.academics.web.TeachingAssignmentDto;
import com.iet.dashboard.directory.service.FacultyService;
import com.iet.dashboard.directory.web.FacultyDto;
import com.iet.dashboard.shared.error.ResourceNotFoundException;
import com.iet.dashboard.teaching.domain.SessionStatus;
import com.iet.dashboard.teaching.domain.TeachingSession;
import com.iet.dashboard.teaching.repo.TeachingSessionRepository;
import com.iet.dashboard.tracking.web.ComplianceMatrixDto;
import com.iet.dashboard.tracking.web.OverviewSummaryDto;
import com.iet.dashboard.tracking.web.ReportDtos.*;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.time.LocalDate;
import java.time.temporal.ChronoUnit;
import java.util.*;
import java.util.stream.Collectors;

/**
 * Builds every report from one bulk read of the semester. Rules live in {@link ReportCalculator};
 * this class only gathers data and arranges it.
 */
@Service
@Transactional(readOnly = true)
public class ReportServiceImpl implements ReportService {

    private final AcademicsService academicsService;
    private final PacingCalculationService pacingService;
    private final TrackingService trackingService;
    private final FacultyService facultyService;
    private final TeachingSessionRepository sessionRepository;
    private final TimetableSlotRepository slotRepository;
    private final AssessmentRepository assessmentRepository;
    private final RemedialPlanRepository remedialRepository;

    public ReportServiceImpl(AcademicsService academicsService, PacingCalculationService pacingService,
                             TrackingService trackingService, FacultyService facultyService,
                             TeachingSessionRepository sessionRepository, TimetableSlotRepository slotRepository,
                             AssessmentRepository assessmentRepository, RemedialPlanRepository remedialRepository) {
        this.academicsService = academicsService;
        this.pacingService = pacingService;
        this.trackingService = trackingService;
        this.facultyService = facultyService;
        this.sessionRepository = sessionRepository;
        this.slotRepository = slotRepository;
        this.assessmentRepository = assessmentRepository;
        this.remedialRepository = remedialRepository;
    }

    /** Everything one semester report needs, read once. */
    private final class Snapshot {
        final AcademicTermDto term = academicsService.getActiveTerm();
        final OverviewSummaryDto overview = trackingService.getOverviewSummary();
        final List<CourseOfferingDto> offerings = academicsService.getAllOfferings(null, null);
        final List<TeachingAssignmentDto> assignments = academicsService.getAllTeachingAssignments(null);
        final Map<Long, CourseProgressDto> progress = pacingService.calculateProgressForOfferings(offerings);
        final List<TeachingSession> sessions = sessionRepository.findAll();
        final List<TimetableSlot> slots = slotRepository.findAll();
        final List<Assessment> assessments = assessmentRepository.findAllWithOffering();
        final List<RemedialPlan> plans = remedialRepository.findAllWithOffering();
        final Map<Long, FacultyDto> faculty = facultyService.getAllFaculty(null, null).stream()
                .collect(Collectors.toMap(FacultyDto::id, f -> f, (a, b) -> a));
        final Map<Long, FacultyLtpLoadDto> loads = academicsService.getFacultyLtpLoad().stream()
                .collect(Collectors.toMap(FacultyLtpLoadDto::facultyId, l -> l, (a, b) -> a));
        final Map<Long, OverviewSummaryDto.FacultyPerformanceRow> performance = overview.facultyPerformance().stream()
                .collect(Collectors.toMap(OverviewSummaryDto.FacultyPerformanceRow::facultyId, r -> r, (a, b) -> a));
        final LocalDate today = LocalDate.now();
        final int required = term != null ? term.requiredAssessments() : 3;
        final Map<Long, List<TeachingAssignmentDto>> byOffering = assignments.stream()
                .filter(a -> a.offering() != null)
                .collect(Collectors.groupingBy(a -> a.offering().id()));
        final Map<Long, List<Assessment>> assessmentsByOffering = assessments.stream()
                .collect(Collectors.groupingBy(a -> a.getOffering().getId()));
        final Map<Long, List<RemedialPlan>> plansByOffering = plans.stream()
                .collect(Collectors.groupingBy(p -> p.getOffering().getId()));

        int scheduled(Long facultyId, Long offeringId, String component, String batch) {
            if (term == null || term.startDate() == null || term.startDate().isAfter(today)) {
                return 0;
            }
            List<TimetableSlot> matching = slots.stream()
                    .filter(s -> (facultyId == null || facultyId.equals(s.getFacultyId()))
                            && offeringId.equals(s.getOffering().getId())
                            && (component == null || CourseComponent.parse(s.getComponent()).name().equals(component))
                            && (batch == null || batch.equals(s.getSubSection())))
                    .toList();
            return TrackingServiceImpl.scheduledBetween(matching, term.startDate(), term.endDate(), null, today);
        }

        long sessions(Long facultyId, Long offeringId, String component, String batch, SessionStatus status) {
            return sessions.stream()
                    .filter(s -> s.getStatus() == status
                            && offeringId.equals(s.getOffering().getId())
                            && (facultyId == null || facultyId.equals(s.getFacultyId()))
                            && (component == null || CourseComponent.parse(s.getSessionType()).name().equals(component))
                            && (batch == null || batch.equals(s.getSubSection())))
                    .count();
        }

        String remedialStatus(Long offeringId) {
            CourseProgressDto p = progress.get(offeringId);
            Assessment latest = latestAssessment(offeringId);
            return ReportCalculator.remedialStatus(
                    p != null && p.totalTopics() > 0 ? p.topicDelta() : 0,
                    latest != null ? latest.getBelow40Count() : null,
                    latest != null ? latest.getStudentsAssessed() : null,
                    plansByOffering.getOrDefault(offeringId, List.of()).stream().map(RemedialPlan::getStatus).toList());
        }

        Assessment latestAssessment(Long offeringId) {
            List<Assessment> list = assessmentsByOffering.getOrDefault(offeringId, List.of());
            return list.isEmpty() ? null : list.get(list.size() - 1);
        }

        int students(CourseOfferingDto o) {
            return o.studentCount();
        }
    }

    @Override
    public SemesterReport semesterReport() {
        Snapshot s = new Snapshot();
        List<SectionPerformance> sections = sections(s, s.offerings);
        List<Mismatch> mismatches = ReportCalculator.mismatches(s.offerings, s.assignments);
        List<FacultyBreakdown> faculty = s.byOffering.values().stream().flatMap(List::stream)
                .map(TeachingAssignmentDto::facultyId).distinct()
                .map(id -> breakdown(s, id))
                .sorted(Comparator.comparing(FacultyBreakdown::name))
                .toList();
        List<CriticalCase> cases = criticalCases(s, sections, faculty);

        List<AssessmentProgress> assessmentProgress = new ArrayList<>();
        for (int i = 1; i <= s.required; i++) {
            final int n = i;
            int done = (int) s.offerings.stream()
                    .filter(o -> s.assessmentsByOffering.getOrDefault(o.id(), List.of()).size() >= n).count();
            OptionalDouble avg = s.offerings.stream()
                    .map(o -> s.assessmentsByOffering.getOrDefault(o.id(), List.of()))
                    .filter(list -> list.size() >= n && list.get(n - 1).getAveragePct() != null)
                    .mapToInt(list -> list.get(n - 1).getAveragePct()).average();
            assessmentProgress.add(new AssessmentProgress(n, done, s.offerings.size(),
                    avg.isPresent() ? (int) Math.round(avg.getAsDouble()) : null));
        }

        List<RemedialPlanLine> planLines = s.plans.stream().map(p -> new RemedialPlanLine(
                p.getId(), p.getOffering().getCourse().getCode(), p.getOffering().getSection(),
                Optional.ofNullable(s.faculty.get(p.getFacultyId())).map(FacultyDto::name).orElse("—"),
                p.getReason(), p.getAction(), p.getExtraSessions(), p.getTargetDate(), p.getStatus())).toList();

        DataSources sources = new DataSources(
                s.performance.size(), s.assignments.size(), s.offerings.size(), s.sessions.size(), s.slots.size(),
                s.offerings.stream().collect(Collectors.toMap(o -> o.course().id(), o -> s.progress.get(o.id()).totalTopics(), (a, b) -> a))
                        .values().stream().mapToInt(Integer::intValue).sum(),
                s.assessments.size(), s.plans.size());

        return new SemesterReport(meta(s), scorecard(s, sections, mismatches), cases, mismatches, sections, faculty,
                assessmentProgress, planLines, sources);
    }

    @Override
    public FacultyReport facultyReport(Long facultyId) {
        Snapshot s = new Snapshot();
        if (!s.faculty.containsKey(facultyId)) {
            throw new ResourceNotFoundException("Faculty not found: " + facultyId);
        }
        FacultyBreakdown summary = breakdown(s, facultyId);
        List<SectionPerformance> sections = sections(s, s.offerings);
        List<CriticalCase> cases = criticalCases(s, sections, List.of(summary)).stream()
                .filter(c -> facultyId.equals(c.facultyId())).toList();
        List<LogEntry> logs = s.sessions.stream()
                .filter(x -> facultyId.equals(x.getFacultyId()))
                .sorted(Comparator.comparing(TeachingSession::getSessionDate).reversed())
                .limit(120)
                .map(x -> new LogEntry(
                        x.getSessionDate(),
                        x.getOffering().getCourse().getCode(),
                        x.getOffering().getSection(),
                        x.getSubSection(),
                        CourseComponent.parse(x.getSessionType()).name(),
                        x.getStatus().name(),
                        x.getLecturesTaken(),
                        x.getTopicsCovered().stream().map(t -> t.getTopicName()).collect(Collectors.joining("; "))
                                + (x.getCustomTopicName() != null && !x.getCustomTopicName().isBlank()
                                ? (x.getTopicsCovered().isEmpty() ? "" : "; ") + x.getCustomTopicName() : ""),
                        x.getStatus() == SessionStatus.HELD ? x.getNote() : x.getReasonNotHeld()))
                .toList();
        return new FacultyReport(meta(s), summary, cases, logs);
    }

    @Override
    public CourseReport courseReport(String courseCode) {
        Snapshot s = new Snapshot();
        List<CourseOfferingDto> ofCourse = s.offerings.stream()
                .filter(o -> o.course().code().equalsIgnoreCase(courseCode)).toList();
        if (ofCourse.isEmpty()) {
            throw new ResourceNotFoundException("No sections for course " + courseCode);
        }
        var course = ofCourse.get(0).course();
        List<SectionPerformance> sections = sections(s, ofCourse);
        return new CourseReport(meta(s), course.code(), course.name(), course.ltp(),
                ofCourse.stream().mapToInt(CourseOfferingDto::studentCount).sum(), sections,
                ReportCalculator.mismatches(ofCourse, s.assignments));
    }

    private Meta meta(Snapshot s) {
        OverviewSummaryDto.TermInfo t = s.overview.term();
        return new Meta(t.name(), t.startDate(), t.endDate(), t.currentWeek(), t.totalWeeks(), s.required, Instant.now());
    }

    private List<SectionPerformance> sections(Snapshot s, List<CourseOfferingDto> offerings) {
        return offerings.stream()
                .sorted(Comparator.comparingInt(CourseOfferingDto::yearOfStudy)
                        .thenComparing(o -> o.course().code()).thenComparing(CourseOfferingDto::section))
                .map(o -> {
                    List<TeachingAssignmentDto> owners = s.byOffering.getOrDefault(o.id(), List.of());
                    CourseProgressDto p = s.progress.get(o.id());
                    boolean paced = p != null && p.totalTopics() > 0;
                    Assessment latest = s.latestAssessment(o.id());
                    String components = owners.stream()
                            .map(a -> CourseComponent.parse(a.component()))
                            .distinct().sorted()
                            .map(c -> c == CourseComponent.ALL ? "Full course" : c.name().charAt(0) + c.name().substring(1).toLowerCase())
                            .collect(Collectors.joining(" + "));
                    return new SectionPerformance(
                            o.id(), o.course().code(), o.course().name(), o.yearOfStudy(), o.semester(), o.section(),
                            s.students(o), components.isEmpty() ? "—" : components,
                            owners.stream().map(TeachingAssignmentDto::facultyName).distinct().collect(Collectors.joining(", ")),
                            paced ? p.coveredTopics() : 0, paced ? p.totalTopics() : 0, paced ? p.coveragePercentage() : 0,
                            paced ? p.pacingStatus() : "NO_SYLLABUS", paced ? p.topicDelta() : 0,
                            s.sessions(null, o.id(), null, null, SessionStatus.HELD),
                            s.sessions(null, o.id(), null, null, SessionStatus.CANCELLED),
                            s.scheduled(null, o.id(), null, null),
                            s.assessmentsByOffering.getOrDefault(o.id(), List.of()).size(),
                            latest != null ? latest.getAveragePct() : null,
                            s.remedialStatus(o.id()));
                })
                .toList();
    }

    private FacultyBreakdown breakdown(Snapshot s, Long facultyId) {
        FacultyDto f = s.faculty.get(facultyId);
        FacultyLtpLoadDto load = s.loads.get(facultyId);
        OverviewSummaryDto.FacultyPerformanceRow perf = s.performance.get(facultyId);
        List<TeachingAssignmentDto> mine = s.assignments.stream()
                .filter(a -> facultyId.equals(a.facultyId()) && a.offering() != null).toList();

        List<TeachingLine> lines = mine.stream()
                .sorted(Comparator.comparingInt((TeachingAssignmentDto a) -> a.offering().yearOfStudy())
                        .thenComparing(a -> a.offering().course().code()).thenComparing(a -> a.offering().section()))
                .map(a -> {
                    String component = CourseComponent.parse(a.component()).name();
                    String batch = a.subSection() != null ? a.subSection() : "ALL";
                    String c = "ALL".equals(component) ? null : component;
                    CourseProgressDto p = s.progress.get(a.offering().id());
                    boolean paced = p != null && p.totalTopics() > 0;
                    return new TeachingLine(a.offering().id(), a.offering().yearOfStudy(), a.offering().course().code(),
                            a.offering().course().name(),
                            a.offering().section() + ("ALL".equals(batch) ? "" : " · " + batch),
                            component, a.contactHours(), a.studentCount(),
                            s.sessions(facultyId, a.offering().id(), c, "ALL".equals(batch) ? null : batch, SessionStatus.HELD),
                            s.scheduled(facultyId, a.offering().id(), c, "ALL".equals(batch) ? null : batch),
                            paced ? p.coveragePercentage() : 0, paced ? p.pacingStatus() : "NO_SYLLABUS",
                            s.assessmentsByOffering.getOrDefault(a.offering().id(), List.of()).size(),
                            s.remedialStatus(a.offering().id()));
                })
                .toList();

        // Students reached: per section, the most this person teaches there (whole section beats a batch).
        Map<Long, Integer> perSection = new HashMap<>();
        mine.forEach(a -> perSection.merge(a.offering().id(), a.studentCount(), Math::max));
        List<Integer> years = mine.stream().map(a -> a.offering().yearOfStudy()).distinct().sorted().toList();

        return new FacultyBreakdown(
                facultyId, f != null ? f.name() : "Faculty #" + facultyId,
                f != null ? f.designation() : "", f != null ? f.department() : "",
                load != null ? load.lectureHours() : 0, load != null ? load.tutorialHours() : 0,
                load != null ? load.practicalHours() : 0, load != null ? load.pblHours() : 0,
                load != null ? load.totalHours() : 0,
                perSection.values().stream().mapToInt(Integer::intValue).sum(), years,
                perf != null ? perf.sessionsHeld() : 0, perf != null ? perf.sessionsCancelled() : 0,
                perf != null ? perf.scheduledSoFar() : 0, perf != null ? perf.loggingRate() : null,
                perf != null ? perf.status() : "NO_DATA", lines);
    }

    private List<CriticalCase> criticalCases(Snapshot s, List<SectionPerformance> sections, List<FacultyBreakdown> faculty) {
        List<CriticalCase> cases = new ArrayList<>();
        Map<Long, SectionPerformance> sectionById = sections.stream().collect(Collectors.toMap(SectionPerformance::offeringId, x -> x));

        for (FacultyBreakdown f : faculty) {
            if ("NOT_LOGGING".equals(f.status())) {
                OverviewSummaryDto.FacultyPerformanceRow perf = s.performance.get(f.facultyId());
                cases.add(new CriticalCase(null, "CRITICAL", "Not logging",
                        f.lines().stream().map(l -> l.courseCode() + " " + l.section()).distinct().collect(Collectors.joining(", ")),
                        f.facultyId(), f.name(), f.studentsTaught(),
                        (f.scheduled() - Math.min(f.held() + f.cancelled(), f.scheduled())) + " of " + f.scheduled()
                                + " timetabled classes not logged"
                                + (perf != null && perf.daysSinceLastLog() != null ? "; last log " + perf.daysSinceLastLog() + " days ago" : "; nothing logged this term"),
                        "Log the missed classes; coordinator to follow up."));
            }
        }
        for (SectionPerformance x : sections) {
            Long owner = s.byOffering.getOrDefault(x.offeringId(), List.of()).stream()
                    .map(TeachingAssignmentDto::facultyId).findFirst().orElse(null);
            String entity = x.courseCode() + " · Sec " + x.section() + " · Year " + x.yearOfStudy();
            if ("BEHIND".equals(x.pacingStatus()) && x.topicDelta() <= -ReportCalculator.REMEDIAL_TOPIC_LAG) {
                boolean severe = x.topicDelta() <= -3;
                cases.add(new CriticalCase(null, severe ? "CRITICAL" : "WARNING", "Behind syllabus", entity, owner, x.faculty(),
                        x.students(), -x.topicDelta() + " topics behind plan (" + x.coveragePct() + "% covered)",
                        switch (x.remedialStatus()) {
                            case "FILED" -> "Remedial plan filed; check it is on schedule.";
                            case "COMPLETED" -> "Remedial plan completed; confirm recovery.";
                            default -> "File a remedial plan.";
                        }));
            }
            Assessment latest = s.latestAssessment(x.offeringId());
            if (latest != null && latest.getBelow40Count() != null && latest.getStudentsAssessed() != null
                    && latest.getStudentsAssessed() > 0
                    && (double) latest.getBelow40Count() / latest.getStudentsAssessed() > ReportCalculator.REMEDIAL_LOW_SCORE_SHARE) {
                cases.add(new CriticalCase(null, "WARNING", "Low assessment scores", entity, owner, x.faculty(),
                        latest.getBelow40Count(), latest.getBelow40Count() + " of " + latest.getStudentsAssessed()
                        + " students under 40% in " + latest.getName(),
                        "FILED".equals(x.remedialStatus()) ? "Remedial plan filed." : "File a remedial plan and re-assess."));
            }
            int due = ReportCalculator.assessmentsDueByNow(s.required, s.overview.term().currentWeek(), s.overview.term().totalWeeks());
            if (ReportCalculator.assessmentsBehind(x.assessmentsHeld(), due, !s.assessments.isEmpty()) && !"—".equals(x.components())) {
                cases.add(new CriticalCase(null, "WARNING", "Assessments behind", entity, owner, x.faculty(), x.students(),
                        x.assessmentsHeld() + " of " + due + " assessments due by now have been recorded",
                        "Hold or record the pending assessment."));
            }
            if (x.cancelled() >= 3) {
                cases.add(new CriticalCase(null, "WARNING", "Cancelled classes", entity, owner, x.faculty(), x.students(),
                        x.cancelled() + " classes cancelled this term", "Schedule make-up classes."));
            }
        }
        for (OverviewSummaryDto.NeedsAttentionItem item : s.overview.needsAttention()) {
            // Only people who teach this semester are held to its deadlines (not the coordinator's own profile).
            if ("OVERDUE_SUBMISSION".equals(item.type()) && item.facultyId() != null && s.performance.containsKey(item.facultyId())) {
                String subtitle = item.subtitle();
                cases.add(new CriticalCase(null, "CRITICAL", "Overdue submission", item.title().replace(" — Past Due", ""),
                        item.facultyId(), item.facultyName(), 0,
                        subtitle.contains("• ") ? subtitle.substring(subtitle.indexOf("• ") + 2) : subtitle,
                        "Submit today."));
            } else if ("UNASSIGNED_COURSE".equals(item.type())) {
                cases.add(new CriticalCase(null, "CRITICAL", "Unassigned section", item.title(), null, "—", 0,
                        item.subtitle(), "Allocate a faculty member."));
            }
        }

        List<String> categoryOrder = List.of("Not logging", "Overdue submission", "Unassigned section", "Behind syllabus",
                "Low assessment scores", "Assessments behind", "Cancelled classes");
        cases.sort(Comparator.comparing((CriticalCase c) -> "CRITICAL".equals(c.severity()) ? 0 : 1)
                .thenComparingInt(c -> categoryOrder.indexOf(c.category()))
                .thenComparing(c -> -c.studentsAffected())
                .thenComparing(CriticalCase::entity));
        int critical = 0;
        int warning = 0;
        List<CriticalCase> numbered = new ArrayList<>();
        for (CriticalCase c : cases) {
            String ref = "CRITICAL".equals(c.severity()) ? String.format("C-%02d", ++critical) : String.format("W-%02d", ++warning);
            numbered.add(new CriticalCase(ref, c.severity(), c.category(), c.entity(), c.facultyId(), c.facultyName(),
                    c.studentsAffected(), c.description(), c.requiredAction()));
        }
        return numbered;
    }

    private List<ScoreLine> scorecard(Snapshot s, List<SectionPerformance> sections, List<Mismatch> mismatches) {
        List<ScoreLine> lines = new ArrayList<>();
        OverviewSummaryDto.OverviewKpis k = s.overview.kpis();

        long withStrength = s.offerings.stream().filter(o -> o.studentCount() > 0).count();
        // Each offering (one course + one section) carries its own section strength, so adding them up
        // makes student-seats, not people: the same student is counted once per course they take.
        lines.add(new ScoreLine("Section strengths", "Every section's strength recorded",
                withStrength == 0 ? "—" : withStrength + " of " + s.offerings.size() + " sections",
                withStrength == s.offerings.size() && !s.offerings.isEmpty() ? "OK" : withStrength == 0 ? "NO_DATA" : "WATCH",
                withStrength == 0
                        ? "No section strength recorded yet; add 'Section Students' to the allocation sheet and re-import"
                        : k.totalStudents() + " student-seats across sections — a student taking five courses is counted five times"));

        long gaps = mismatches.stream().filter(m -> m.type().equals("MISSING_OWNER") || m.type().equals("BATCH_COVERAGE_GAP")).count();
        lines.add(new ScoreLine("Sections running", "Every planned component allocated",
                s.offerings.size() + " sections", gaps == 0 && mismatches.isEmpty() ? "OK" : gaps > 0 ? "CRITICAL" : "WATCH",
                mismatches.size() + " allocation mismatches (" + gaps + " without an owner)"));

        List<SectionPerformance> paced = sections.stream().filter(x -> !"NO_SYLLABUS".equals(x.pacingStatus())).toList();
        long onPlan = paced.stream().filter(x -> !"BEHIND".equals(x.pacingStatus())).count();
        Integer onPlanPct = paced.isEmpty() ? null : (int) Math.round(onPlan * 100.0 / paced.size());
        lines.add(new ScoreLine("Syllabus pacing", "≥ 90% of sections on plan",
                onPlanPct == null ? "—" : onPlanPct + "% on plan",
                onPlanPct == null ? "NO_DATA" : onPlanPct >= 90 ? "OK" : onPlanPct >= 75 ? "WATCH" : "CRITICAL",
                paced.size() + " of " + sections.size() + " sections have a syllabus; " + (paced.size() - onPlan) + " behind"));

        Integer rate = k.loggingRatePercentage();
        lines.add(new ScoreLine("Class logging", "100% of timetabled classes logged",
                rate == null ? "—" : rate + "% logged",
                rate == null ? "NO_DATA" : rate >= 95 ? "OK" : rate >= 80 ? "WATCH" : "CRITICAL",
                k.facultyNotLogging() + " faculty not logging; " + k.facultyWithoutTimetable() + " without a timetable"));

        double avgHeld = s.offerings.isEmpty() ? 0
                : s.offerings.stream().mapToInt(o -> s.assessmentsByOffering.getOrDefault(o.id(), List.of()).size()).average().orElse(0);
        int due = ReportCalculator.assessmentsDueByNow(s.required, s.overview.term().currentWeek(), s.overview.term().totalWeeks());
        long onSchedule = s.offerings.stream().filter(o -> s.assessmentsByOffering.getOrDefault(o.id(), List.of()).size() >= due).count();
        lines.add(new ScoreLine("Continuous assessment", s.required + " per section",
                s.assessments.isEmpty() ? "—" : String.format("%.1f / %d held", avgHeld, s.required),
                s.assessments.isEmpty() ? "NO_DATA" : onSchedule == s.offerings.size() ? "OK" : "WATCH",
                s.assessments.isEmpty() ? "No assessment recorded yet; tracked once faculty start recording them"
                        : due + " due by this week; " + onSchedule + " of " + s.offerings.size() + " sections on schedule"));

        long needed = sections.stream().filter(x -> !"NOT_REQUIRED".equals(x.remedialStatus())).count();
        long filed = sections.stream().filter(x -> "FILED".equals(x.remedialStatus()) || "COMPLETED".equals(x.remedialStatus())).count();
        lines.add(new ScoreLine("Remedial plans", "Filed for every section that needs one",
                needed == 0 ? "None needed" : filed + " / " + needed + " filed",
                needed == 0 ? "OK" : filed == needed ? "OK" : "CRITICAL",
                "Needed when ≥ 2 topics behind or > 15% score under 40%"));

        ComplianceMatrixDto matrix = trackingService.getComplianceMatrix();
        int courseFileColumn = -1;
        for (int i = 0; i < matrix.checkpoints().size(); i++) {
            if ("COURSE_FILE".equals(matrix.checkpoints().get(i).key())) {
                courseFileColumn = i;
            }
        }
        if (courseFileColumn >= 0) {
            final int col = courseFileColumn;
            // A course file is filed by a faculty member, not by each of their sections: the matrix
            // repeats one submission across every section its owner teaches, so count people, not rows.
            Map<Long, Boolean> byFaculty = new LinkedHashMap<>();
            boolean anyDeadline = false;
            for (var row : matrix.rows()) {
                var cell = row.cells().get(col);
                if (!"No deadline set".equals(cell.note())) {
                    anyDeadline = true;
                }
                if (row.facultyId() != null) {
                    byFaculty.merge(row.facultyId(), "DONE".equals(cell.status()), (a, b) -> a || b);
                }
            }
            long doneFaculty = byFaculty.values().stream().filter(Boolean.TRUE::equals).count();
            lines.add(new ScoreLine("Course files", "Submitted by every faculty member",
                    anyDeadline ? doneFaculty + " / " + byFaculty.size() + " faculty" : "—",
                    !anyDeadline ? "NO_DATA" : doneFaculty == byFaculty.size() ? "OK" : "WATCH",
                    anyDeadline ? "From the COURSE_FILE deadline" : "No course-file deadline created yet"));
        }
        return lines;
    }
}
