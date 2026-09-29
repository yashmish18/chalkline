package com.iet.dashboard.teaching.web;

import com.iet.dashboard.academics.web.SyllabusTopicDto;
import com.iet.dashboard.teaching.domain.SessionStatus;
import com.iet.dashboard.teaching.domain.TeachingSession;

import java.time.Instant;
import java.time.LocalDate;
import java.util.Comparator;
import java.util.List;
import java.util.stream.Collectors;

public record TeachingSessionDto(
        Long id,
        Long offeringId,
        String courseCode,
        String courseName,
        String section,
        Long facultyId,
        LocalDate sessionDate,
        String sessionType,
        SessionStatus status,
        String note,
        String reasonNotHeld,
        List<SyllabusTopicDto> topicsCovered,
        Instant createdAt
) {
    public static TeachingSessionDto fromEntity(TeachingSession session) {
        List<SyllabusTopicDto> topics = session.getTopicsCovered() != null
                ? session.getTopicsCovered().stream()
                        .map(SyllabusTopicDto::fromEntity)
                        .sorted(Comparator.comparingInt(SyllabusTopicDto::lectureOrder))
                        .collect(Collectors.toList())
                : List.of();

        String courseCode = null;
        String courseName = null;
        String section = null;

        if (session.getOffering() != null) {
            section = session.getOffering().getSection();
            if (session.getOffering().getCourse() != null) {
                courseCode = session.getOffering().getCourse().getCode();
                courseName = session.getOffering().getCourse().getName();
            }
        }

        return new TeachingSessionDto(
                session.getId(),
                session.getOffering() != null ? session.getOffering().getId() : null,
                courseCode,
                courseName,
                section,
                session.getFacultyId(),
                session.getSessionDate(),
                session.getSessionType(),
                session.getStatus(),
                session.getNote(),
                session.getReasonNotHeld(),
                topics,
                session.getCreatedAt()
        );
    }
}
