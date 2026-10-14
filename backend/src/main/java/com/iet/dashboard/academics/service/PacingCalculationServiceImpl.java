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

    public PacingCalculationServiceImpl(AcademicsService academicsService, TeachingSessionRepository sessionRepository) {
        this.academicsService = academicsService;
        this.sessionRepository = sessionRepository;
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
        return progress(offering, term,
                academicsService.getSyllabusForCourse(offering.course().id()),
                sessionRepository.findCoveredTopicIdsForOffering(offeringId),
                sessionRepository.countTotalSessionsForOffering(offeringId),
                sessionRepository.countByOfferingIdAndStatus(offeringId, SessionStatus.HELD));
    }

    @Override
    public Map<Long, CourseProgressDto> calculateProgressForOfferings(List<CourseOfferingDto> offerings) {
        AcademicTermDto term = academicsService.getActiveTerm();
        Map<Long, List<SyllabusTopicDto>> syllabus = academicsService.getSyllabusByCourse();

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
            result.put(offering.id(), progress(offering, term,
                    syllabus.getOrDefault(offering.course().id(), List.of()),
                    covered.getOrDefault(offering.id(), Set.of()), c[0], c[1]));
        }
        return result;
    }

    /** Teaching week of the active term, 1..16. Without an active term nothing is "due" yet. */
    static int currentWeek(AcademicTermDto term) {
        if (term == null || term.startDate() == null) {
            return 1;
        }
        long days = ChronoUnit.DAYS.between(term.startDate(), LocalDate.now());
        return Math.max(1, Math.min(16, (int) (days / 7) + 1));
    }

    private static CourseProgressDto progress(CourseOfferingDto offering, AcademicTermDto term, List<SyllabusTopicDto> topics,
                                              Set<Long> coveredIds, long totalSessions, long heldSessions) {
        int week = currentWeek(term);
        int total = topics.size();
        int covered = (int) topics.stream().filter(t -> coveredIds.contains(t.id())).count();
        int coverage = total > 0 ? (int) Math.round(covered * 100.0 / total) : 0;
        int expected = (int) topics.stream().filter(t -> t.targetWeek() <= week).count();
        int delta = covered - expected;

        String status;
        String label;
        if (topics.isEmpty()) {
            status = "ON_TRACK";
            label = "No syllabus configured";
        } else if (delta > 0) {
            status = "AHEAD";
            label = "Ahead by " + delta + " topic" + (delta > 1 ? "s" : "");
        } else if (delta == 0) {
            status = "ON_TRACK";
            label = "On Track (Week " + week + ")";
        } else {
            status = "BEHIND";
            label = "Behind by " + -delta + " topic" + (delta < -1 ? "s" : "");
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
