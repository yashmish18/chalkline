package com.iet.dashboard.academics.service;

import com.iet.dashboard.academics.web.AcademicTermDto;
import com.iet.dashboard.academics.web.CourseOfferingDto;
import com.iet.dashboard.academics.web.CourseProgressDto;
import com.iet.dashboard.academics.web.SyllabusTopicDto;
import com.iet.dashboard.academics.web.SyllabusTopicProgressDto;
import com.iet.dashboard.academics.web.TeachingAssignmentDto;
import com.iet.dashboard.teaching.domain.SessionStatus;
import com.iet.dashboard.teaching.repo.TeachingSessionRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

@Service
@Transactional(readOnly = true)
public class PacingCalculationServiceImpl implements PacingCalculationService {

    private final AcademicsService academicsService;
    private final TeachingSessionRepository sessionRepository;
    private java.time.Clock clock = java.time.Clock.system(java.time.ZoneId.of("Asia/Kolkata"));

    public PacingCalculationServiceImpl(AcademicsService academicsService, TeachingSessionRepository sessionRepository) {
        this.academicsService = academicsService;
        this.sessionRepository = sessionRepository;
    }

    void setClock(java.time.Clock clock) {
        this.clock = clock;
    }

    @Override
    public List<CourseProgressDto> getMyCoursesProgress(Long facultyId) {
        if (facultyId == null) {
            return List.of();
        }
        List<CourseProgressDto> result = new ArrayList<>();
        for (TeachingAssignmentDto assignment : academicsService.getTeachingAssignmentsForFaculty(facultyId)) {
            if (assignment.offering() == null) {
                continue;
            }
            CourseProgressDto base = calculateProgressForOffering(assignment.offering().id(), facultyId, false);
            result.add(new CourseProgressDto(
                    base.offeringId(), base.courseId(), base.courseCode(), base.courseName(), base.section(),
                    assignment.component(), base.credits(), base.termName(), base.currentWeek(), base.totalTopics(),
                    base.coveredTopics(), base.coveragePercentage(), base.expectedTopicsByCurrentWeek(), base.topicDelta(),
                    base.pacingStatus(), base.pacingLabel(), base.totalSessions(), base.heldSessions(),
                    base.conductRatePercentage(), base.topics(), base.ltp(),
                    assignment.subSection() != null ? assignment.subSection() : "ALL",
                    base.courseDescription()));
        }
        return result;
    }

    @Override
    public CourseProgressDto calculateProgressForOffering(Long offeringId, Long facultyId, boolean isCoordinator) {
        if (!isCoordinator && facultyId != null) {
            academicsService.assertFacultyAssignedToOffering(facultyId, offeringId);
        }
        CourseOfferingDto offering = academicsService.getOfferingById(offeringId);
        AcademicTermDto term = academicsService.getActiveTerm();
        return progress(offering, term, today(),
                academicsService.getLectureDaysByOffering().getOrDefault(offeringId, List.of()),
                academicsService.getSyllabusForCourse(offering.course().id()),
                sessionRepository.findCoveredTopicIdsForOffering(offeringId),
                sessionRepository.countTotalSessionsForOffering(offeringId),
                sessionRepository.countByOfferingIdAndStatus(offeringId, SessionStatus.HELD));
    }

    @Override
    public Map<Long, CourseProgressDto> calculateProgressForOfferings(List<CourseOfferingDto> offerings) {
        AcademicTermDto term = academicsService.getActiveTerm();
        Map<Long, List<SyllabusTopicDto>> syllabus = academicsService.getSyllabusByCourse();
        Map<Long, List<String>> lectureDays = academicsService.getLectureDaysByOffering();
        LocalDate today = today();

        Map<Long, Set<Long>> covered = new HashMap<>();
        for (Object[] pair : sessionRepository.findAllCoveredTopicPairs()) {
            covered.computeIfAbsent((Long) pair[0], k -> new HashSet<>()).add((Long) pair[1]);
        }
        Map<Long, long[]> counts = new HashMap<>(); // [total, held]
        for (Object[] row : sessionRepository.countAllByOfferingAndStatus()) {
            long[] c = counts.computeIfAbsent((Long) row[0], k -> new long[2]);
            long n = (Long) row[2];
            c[0] += n;
            if (row[1] == SessionStatus.HELD) {
                c[1] += n;
            }
        }

        Map<Long, CourseProgressDto> result = new HashMap<>();
        for (CourseOfferingDto offering : offerings) {
            long[] c = counts.getOrDefault(offering.id(), new long[2]);
            result.put(offering.id(), progress(offering, term, today,
                    lectureDays.getOrDefault(offering.id(), List.of()),
                    syllabus.getOrDefault(offering.course().id(), List.of()),
                    covered.getOrDefault(offering.id(), Set.of()), c[0], c[1]));
        }
        return result;
    }

    private LocalDate today() {
        return LocalDate.now(clock);
    }

    /** Teaching week of the active term, 1 to the term's last week. Without dates, week 1. */
    static int currentWeek(AcademicTermDto term, LocalDate today) {
        if (term == null || term.startDate() == null) {
            return 1;
        }
        long days = ChronoUnit.DAYS.between(term.startDate(), today);
        int lastWeek = term.endDate() != null ? (int) (ChronoUnit.DAYS.between(term.startDate(), term.endDate()) / 7) + 1 : 16;
        return Math.max(1, Math.min(lastWeek, (int) (days / 7) + 1));
    }

    /** Dates in [from, to] whose weekday is one of the slots' days, counted once per slot; holidays don't count. */
    private static int lecturesBetween(List<String> lectureDays, LocalDate from, LocalDate to, Set<LocalDate> holidays) {
        int count = 0;
        for (LocalDate d = from; !d.isAfter(to); d = d.plusDays(1)) {
            if (holidays.contains(d)) {
                continue;
            }
            String day = d.getDayOfWeek().name();
            count += (int) lectureDays.stream().filter(day::equalsIgnoreCase).count();
        }
        return count;
    }

    /**
     * Pace from facts only: the syllabus as uploaded, the semester dates, and this section's
     * timetabled lectures. Expected topics = topics × (timetabled lectures already past ÷ all of
     * the term's timetabled lectures). One topic either way is rounding, not a judgement.
     */
    private static CourseProgressDto progress(CourseOfferingDto offering, AcademicTermDto term, LocalDate today,
                                              List<String> lectureDays, List<SyllabusTopicDto> topics,
                                              Set<Long> coveredIds, long totalSessions, long heldSessions) {
        int week = currentWeek(term, today);
        int total = topics.size();
        int covered = (int) topics.stream().filter(t -> coveredIds.contains(t.id())).count();
        int coverage = total > 0 ? (int) Math.round(covered * 100.0 / total) : 0;
        int expected = 0;
        int delta = 0;

        String status;
        String label;
        if (topics.isEmpty()) {
            status = "NO_SYLLABUS";
            label = "No syllabus uploaded";
        } else if (term == null || term.startDate() == null || term.endDate() == null) {
            status = "NOT_MEASURED";
            label = "Set the semester dates to measure pace";
        } else if (lectureDays.isEmpty()) {
            status = "NOT_MEASURED";
            label = "No lectures for this section in the timetable";
        } else if (today.isBefore(term.startDate())) {
            status = "NOT_MEASURED";
            label = "The semester starts on " + term.startDate();
        } else {
            int all = lecturesBetween(lectureDays, term.startDate(), term.endDate(), term.holidayDates());
            LocalDate lastPast = today.minusDays(1).isAfter(term.endDate()) ? term.endDate() : today.minusDays(1);
            int past = lecturesBetween(lectureDays, term.startDate(), lastPast, term.holidayDates());
            expected = all > 0 ? (int) Math.round(total * (double) past / all) : 0;
            delta = covered - expected;
            String facts = " (" + covered + " of " + total + " covered; " + past + " of " + all
                    + " timetabled lectures are past)";
            if (delta > 1) {
                status = "AHEAD";
                label = "Ahead by " + delta + " topics" + facts;
            } else if (delta < -1) {
                status = "BEHIND";
                label = "Behind by " + -delta + " topics" + facts;
            } else {
                status = "ON_TRACK";
                label = "On pace" + facts;
            }
        }

        List<SyllabusTopicProgressDto> topicProgress = topics.stream()
                .map(t -> SyllabusTopicProgressDto.fromTopic(t, coveredIds.contains(t.id())))
                .toList();

        return new CourseProgressDto(
                offering.id(),
                offering.course().id(),
                offering.course().code(),
                offering.course().name(),
                offering.section(),
                "LECTURE",
                offering.course().credits(),
                term != null ? term.name() : offering.termName(),
                week,
                total,
                covered,
                coverage,
                expected,
                delta,
                status,
                label,
                totalSessions,
                heldSessions,
                totalSessions > 0 ? (int) Math.round(heldSessions * 100.0 / totalSessions) : 0,
                topicProgress,
                offering.course().ltp() != null ? offering.course().ltp() : "",
                "ALL",
                offering.course().description());
    }
}
