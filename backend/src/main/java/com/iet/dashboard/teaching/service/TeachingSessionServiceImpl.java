package com.iet.dashboard.teaching.service;

import com.iet.dashboard.academics.domain.CourseOffering;
import com.iet.dashboard.academics.domain.SyllabusTopic;
import com.iet.dashboard.academics.repo.CourseOfferingRepository;
import com.iet.dashboard.academics.repo.SyllabusTopicRepository;
import com.iet.dashboard.academics.service.AcademicsService;
import com.iet.dashboard.academics.web.CourseOfferingDto;
import com.iet.dashboard.academics.web.SyllabusTopicDto;
import com.iet.dashboard.academics.web.TeachingAssignmentDto;
import com.iet.dashboard.shared.error.ResourceNotFoundException;
import com.iet.dashboard.shared.error.ValidationException;
import com.iet.dashboard.teaching.domain.SessionStatus;
import com.iet.dashboard.teaching.domain.TeachingSession;
import com.iet.dashboard.teaching.repo.TeachingSessionRepository;
import com.iet.dashboard.teaching.web.LogSessionRequest;
import com.iet.dashboard.teaching.web.TeachingSessionDto;
import com.iet.dashboard.teaching.web.TodayClassDto;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.util.*;
import java.util.stream.Collectors;

@Service
@Transactional
public class TeachingSessionServiceImpl implements TeachingSessionService {

    private final TeachingSessionRepository sessionRepository;
    private final CourseOfferingRepository offeringRepository;
    private final SyllabusTopicRepository topicRepository;
    private final AcademicsService academicsService;

    public TeachingSessionServiceImpl(
            TeachingSessionRepository sessionRepository,
            CourseOfferingRepository offeringRepository,
            SyllabusTopicRepository topicRepository,
            AcademicsService academicsService
    ) {
        this.sessionRepository = sessionRepository;
        this.offeringRepository = offeringRepository;
        this.topicRepository = topicRepository;
        this.academicsService = academicsService;
    }

    @Override
    public TeachingSessionDto logSession(LogSessionRequest request, Long currentFacultyId) {
        if (request.offeringId() == null) {
            throw new ValidationException("Course offering ID is required");
        }
        if (request.sessionDate() == null) {
            throw new ValidationException("Session date is required");
        }
        if (request.status() == null) {
            throw new ValidationException("Session status is required");
        }

        // Enforce IDOR protection: faculty must be assigned to this offering
        academicsService.assertFacultyAssignedToOffering(currentFacultyId, request.offeringId());

        if (request.status() != SessionStatus.HELD && (request.reasonNotHeld() == null || request.reasonNotHeld().isBlank())) {
            throw new ValidationException("Reason is required when class is cancelled or swapped");
        }

        CourseOffering offering = offeringRepository.findById(request.offeringId())
                .orElseThrow(() -> new ResourceNotFoundException("Course offering not found: " + request.offeringId()));

        TeachingSession session = new TeachingSession(
                offering,
                currentFacultyId,
                request.sessionDate(),
                request.sessionType() != null ? request.sessionType() : "LECTURE",
                request.status(),
                request.note(),
                request.reasonNotHeld()
        );

        if (request.status() == SessionStatus.HELD && request.topicIds() != null && !request.topicIds().isEmpty()) {
            List<SyllabusTopic> topics = topicRepository.findAllById(request.topicIds());
            for (SyllabusTopic topic : topics) {
                session.addTopic(topic);
            }
        }

        TeachingSession saved = sessionRepository.save(session);
        return TeachingSessionDto.fromEntity(saved);
    }

    @Override
    @Transactional(readOnly = true)
    public List<TodayClassDto> getTodayClassesForFaculty(Long facultyId, LocalDate date) {
        if (facultyId == null) {
            return List.of();
        }
        LocalDate targetDate = date != null ? date : LocalDate.now();

        List<TeachingAssignmentDto> assignments = academicsService.getTeachingAssignmentsForFaculty(facultyId);
        List<TeachingSession> todaySessions = sessionRepository.findByFacultyIdAndSessionDate(facultyId, targetDate);

        List<TodayClassDto> result = new ArrayList<>();
        for (TeachingAssignmentDto assignment : assignments) {
            CourseOfferingDto offering = assignment.offering();
            if (offering == null || offering.course() == null) {
                continue;
            }

            List<SyllabusTopicDto> allTopics = academicsService.getSyllabusForCourse(offering.course().id());
            Set<Long> coveredTopicIds = sessionRepository.findCoveredTopicIdsForOffering(offering.id());

            // Pre-select the next uncovered topic in sequential syllabus order
            Long nextTopicId = null;
            String nextTopicName = "All syllabus topics completed";
            String unitTitle = "Course Complete";
            int targetWeek = 14;

            for (SyllabusTopicDto topic : allTopics) {
                if (!coveredTopicIds.contains(topic.id())) {
                    nextTopicId = topic.id();
                    nextTopicName = topic.topicName();
                    unitTitle = topic.unitTitle();
                    targetWeek = topic.targetWeek();
                    break;
                }
            }

            if (allTopics.isEmpty()) {
                nextTopicName = "No syllabus topics configured";
                unitTitle = "Unit 1";
                targetWeek = 1;
            }

            String timeSlot = (offering.id() % 2 == 1)
                    ? "10:00 AM – 11:00 AM (Room 304)"
                    : "02:00 PM – 03:00 PM (Room 201)";

            boolean isLogged = todaySessions.stream()
                    .anyMatch(s -> s.getOffering() != null && offering.id().equals(s.getOffering().getId()));

            result.add(new TodayClassDto(
                    offering.id(),
                    offering.course().code(),
                    offering.course().name(),
                    "Sec " + offering.section(),
                    assignment.component(),
                    timeSlot,
                    nextTopicId,
                    nextTopicName,
                    unitTitle,
                    targetWeek,
                    allTopics.size(),
                    coveredTopicIds.size(),
                    isLogged
            ));
        }

        return result;
    }

    @Override
    @Transactional(readOnly = true)
    public List<TeachingSessionDto> getSessionsForOffering(Long offeringId, Long currentFacultyId, boolean isCoordinator) {
        if (!isCoordinator) {
            academicsService.assertFacultyAssignedToOffering(currentFacultyId, offeringId);
        }

        return sessionRepository.findByOfferingIdWithTopics(offeringId).stream()
                .map(TeachingSessionDto::fromEntity)
                .collect(Collectors.toList());
    }

    @Override
    @Transactional(readOnly = true)
    public Set<Long> getCoveredTopicIdsForOffering(Long offeringId) {
        return sessionRepository.findCoveredTopicIdsForOffering(offeringId);
    }
}
