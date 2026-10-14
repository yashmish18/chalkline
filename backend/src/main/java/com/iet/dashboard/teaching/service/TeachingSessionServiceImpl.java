package com.iet.dashboard.teaching.service;

import com.iet.dashboard.academics.domain.CourseOffering;
import com.iet.dashboard.academics.domain.SyllabusTopic;
import com.iet.dashboard.academics.repo.CourseOfferingRepository;
import com.iet.dashboard.academics.repo.SyllabusTopicRepository;
import com.iet.dashboard.academics.service.AcademicsService;
import com.iet.dashboard.academics.web.AcademicTermDto;
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
import com.iet.dashboard.teaching.web.UnitOptionDto;
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

        // A class cannot have been held before the semester it belongs to even started.
        AcademicTermDto activeTerm = academicsService.getActiveTerm();
        if (activeTerm != null && activeTerm.startDate() != null
                && request.sessionDate().isBefore(activeTerm.startDate())) {
            throw new ValidationException(
                    "Classes cannot be logged before the semester starts (" + activeTerm.startDate() + ")");
        }

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
                request.unitNumber(),
                request.customTopicName(),
                com.iet.dashboard.academics.domain.SubSections.normalise(request.subSection(), offering.getSection()),
                request.lecturesTaken() != null ? request.lecturesTaken() : 1,
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

    /**
     * The classes a faculty member should log on a date. With an uploaded timetable this is exactly
     * that weekday's slots, in time order. Without one, every allocation is listed as unscheduled so
     * logging still works — but no time or room is invented.
     */
    @Override
    @Transactional(readOnly = true)
    public List<TodayClassDto> getTodayClassesForFaculty(Long facultyId, LocalDate date) {
        if (facultyId == null) {
            return List.of();
        }
        LocalDate targetDate = date != null ? date : LocalDate.now();
        String weekday = targetDate.getDayOfWeek().name();

        List<TeachingAssignmentDto> assignments = academicsService.getTeachingAssignmentsForFaculty(facultyId);
        List<TeachingSession> sessionsThatDay = sessionRepository.findByFacultyIdAndSessionDate(facultyId, targetDate);
        List<com.iet.dashboard.academics.web.TimetableSlotDto> timetable = academicsService.getTimetableForFaculty(facultyId);

        List<TodayClassDto> result = new ArrayList<>();
        if (timetable.isEmpty()) {
            for (TeachingAssignmentDto a : assignments) {
                if (a.offering() != null && a.offering().course() != null) {
                    result.add(todayClass(a.offering(), a.component(), a.subSection(), "", "", false, sessionsThatDay));
                }
            }
            return result;
        }

        for (var slot : timetable) {
            if (!weekday.equalsIgnoreCase(slot.dayOfWeek())) {
                continue;
            }
            assignments.stream()
                    .map(TeachingAssignmentDto::offering)
                    .filter(o -> o != null && o.id().equals(slot.offeringId()))
                    .findFirst()
                    .ifPresent(offering -> result.add(todayClass(offering, slot.component(), slot.subSection(),
                            slot.startTime() + " – " + slot.endTime(), slot.room(), true, sessionsThatDay)));
        }
        return result;
    }

    private TodayClassDto todayClass(CourseOfferingDto offering, String component, String subSection, String timeSlot,
                                     String room, boolean scheduled, List<TeachingSession> sessionsThatDay) {
        List<SyllabusTopicDto> allTopics = academicsService.getSyllabusForCourse(offering.course().id());
        Set<Long> coveredTopicIds = sessionRepository.findCoveredTopicIdsForOffering(offering.id());

        Map<Integer, List<SyllabusTopicDto>> topicsByUnit = allTopics.stream()
                .collect(Collectors.groupingBy(SyllabusTopicDto::unitNumber, LinkedHashMap::new, Collectors.toList()));
        List<UnitOptionDto> availableUnits = new ArrayList<>();
        topicsByUnit.forEach((unit, topics) -> availableUnits.add(new UnitOptionDto(unit, topics.get(0).unitTitle(), topics)));

        // Pre-select the next uncovered topic in syllabus order.
        SyllabusTopicDto next = allTopics.stream().filter(t -> !coveredTopicIds.contains(t.id())).findFirst().orElse(null);
        String batch = com.iet.dashboard.academics.domain.SubSections.normalise(subSection, offering.section());
        // A class is logged only when that exact component of that batch was logged: logging the
        // lecture must not also tick the tutorial that shares the section.
        com.iet.dashboard.academics.domain.CourseComponent ownComponent =
                com.iet.dashboard.academics.domain.CourseComponent.parse(component);
        boolean logged = sessionsThatDay.stream().anyMatch(s -> s.getOffering() != null
                && offering.id().equals(s.getOffering().getId())
                && ownComponent == com.iet.dashboard.academics.domain.CourseComponent.parse(s.getSessionType())
                && batch.equals(com.iet.dashboard.academics.domain.SubSections.normalise(s.getSubSection(), offering.section())));

        return new TodayClassDto(
                offering.id(),
                offering.course().id(),
                offering.course().code(),
                offering.course().name(),
                offering.course().description() != null ? offering.course().description() : "",
                offering.course().ltp() != null ? offering.course().ltp() : "",
                offering.section(),
                batch,
                component,
                timeSlot,
                room,
                next != null ? next.unitNumber() : 1,
                next != null ? next.id() : null,
                allTopics.isEmpty() ? "No syllabus loaded" : next != null ? next.topicName() : "All topics covered",
                next != null ? next.unitTitle() : "",
                next != null ? next.targetWeek() : 0,
                allTopics.size(),
                coveredTopicIds.size(),
                logged,
                availableUnits,
                scheduled
        );
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

    @Override
    public void deleteSession(Long sessionId, Long currentFacultyId, boolean isCoordinator) {
        TeachingSession session = sessionRepository.findById(sessionId)
                .orElseThrow(() -> new ResourceNotFoundException("Teaching session not found: " + sessionId));
        if (!isCoordinator && !session.getFacultyId().equals(currentFacultyId)) {
            throw new com.iet.dashboard.shared.error.ForbiddenException(
                    "You cannot delete a session recorded by another faculty member");
        }
        sessionRepository.delete(session);
    }
}
