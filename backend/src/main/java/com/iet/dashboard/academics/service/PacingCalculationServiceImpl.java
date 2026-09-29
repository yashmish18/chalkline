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
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

@Service
@Transactional(readOnly = true)
public class PacingCalculationServiceImpl implements PacingCalculationService {

    private final AcademicsService academicsService;
    private final TeachingSessionRepository sessionRepository;

    public PacingCalculationServiceImpl(
            AcademicsService academicsService,
            TeachingSessionRepository sessionRepository
    ) {
        this.academicsService = academicsService;
        this.sessionRepository = sessionRepository;
    }

    @Override
    public List<CourseProgressDto> getMyCoursesProgress(Long facultyId) {
        if (facultyId == null) {
            return List.of();
        }

        List<TeachingAssignmentDto> assignments = academicsService.getTeachingAssignmentsForFaculty(facultyId);
        List<CourseProgressDto> result = new ArrayList<>();

        for (TeachingAssignmentDto assignment : assignments) {
            if (assignment.offering() == null) {
                continue;
            }
            CourseProgressDto baseProgress = calculateProgressForOffering(assignment.offering().id(), facultyId, false);
            // Enrich with exact assignment component (THEORY or LAB)
            result.add(new CourseProgressDto(
                    baseProgress.offeringId(),
                    baseProgress.courseId(),
                    baseProgress.courseCode(),
                    baseProgress.courseName(),
                    baseProgress.section(),
                    assignment.component(),
                    baseProgress.credits(),
                    baseProgress.termName(),
                    baseProgress.currentWeek(),
                    baseProgress.totalTopics(),
                    baseProgress.coveredTopics(),
                    baseProgress.coveragePercentage(),
                    baseProgress.expectedTopicsByCurrentWeek(),
                    baseProgress.topicDelta(),
                    baseProgress.pacingStatus(),
                    baseProgress.pacingLabel(),
                    baseProgress.totalSessions(),
                    baseProgress.heldSessions(),
                    baseProgress.conductRatePercentage(),
                    baseProgress.topics()
            ));
        }

        return result;
    }

    @Override
    public CourseProgressDto calculateProgressForOffering(Long offeringId, Long facultyId, boolean isCoordinator) {
        if (!isCoordinator && facultyId != null) {
            academicsService.assertFacultyAssignedToOffering(facultyId, offeringId);
        }

        CourseOfferingDto offering = academicsService.getOfferingById(offeringId);
        AcademicTermDto activeTerm = academicsService.getActiveTerm();

        // Calculate academic term week (clamped to 1..16)
        int currentWeek = 11;
        if (activeTerm != null && activeTerm.startDate() != null) {
            long days = ChronoUnit.DAYS.between(activeTerm.startDate(), LocalDate.now());
            currentWeek = Math.max(1, Math.min(16, (int) (days / 7) + 1));
        }

        List<SyllabusTopicDto> allTopics = academicsService.getSyllabusForCourse(offering.course().id());
        Set<Long> coveredTopicIds = sessionRepository.findCoveredTopicIdsForOffering(offeringId);

        int totalTopics = allTopics.size();
        int coveredTopics = coveredTopicIds.size();
        int coveragePercentage = totalTopics > 0 ? (int) Math.round((coveredTopics * 100.0) / totalTopics) : 0;

        final int finalCurrentWeek = currentWeek;
        int expectedTopics = (int) allTopics.stream().filter(t -> t.targetWeek() <= finalCurrentWeek).count();

        int topicDelta = coveredTopics - expectedTopics;
        String pacingStatus;
        String pacingLabel;

        if (allTopics.isEmpty()) {
            pacingStatus = "ON_TRACK";
            pacingLabel = "No syllabus configured";
        } else if (topicDelta > 0) {
            pacingStatus = "AHEAD";
            pacingLabel = "Ahead by " + topicDelta + " topic" + (topicDelta > 1 ? "s" : "");
        } else if (topicDelta == 0) {
            pacingStatus = "ON_TRACK";
            pacingLabel = "On Track (Week " + currentWeek + ")";
        } else {
            pacingStatus = "BEHIND";
            int behindCount = Math.abs(topicDelta);
            pacingLabel = "Behind by " + behindCount + " topic" + (behindCount > 1 ? "s" : "");
        }

        long totalSessions = sessionRepository.countTotalSessionsForOffering(offeringId);
        long heldSessions = sessionRepository.countByOfferingIdAndStatus(offeringId, SessionStatus.HELD);
        int conductRatePercentage = totalSessions > 0
                ? (int) Math.round((heldSessions * 100.0) / totalSessions)
                : 100;

        List<SyllabusTopicProgressDto> topicProgressList = allTopics.stream()
                .map(t -> SyllabusTopicProgressDto.fromTopic(t, coveredTopicIds.contains(t.id())))
                .collect(Collectors.toList());

        return new CourseProgressDto(
                offering.id(),
                offering.course().id(),
                offering.course().code(),
                offering.course().name(),
                "Sec " + offering.section(),
                "THEORY",
                offering.course().credits(),
                activeTerm != null ? activeTerm.name() : offering.termName(),
                currentWeek,
                totalTopics,
                coveredTopics,
                coveragePercentage,
                expectedTopics,
                topicDelta,
                pacingStatus,
                pacingLabel,
                totalSessions,
                heldSessions,
                conductRatePercentage,
                topicProgressList
        );
    }
}
