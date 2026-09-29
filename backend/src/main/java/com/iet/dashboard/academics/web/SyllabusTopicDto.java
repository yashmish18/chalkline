package com.iet.dashboard.academics.web;

import com.iet.dashboard.academics.domain.SyllabusTopic;

public record SyllabusTopicDto(
        Long id,
        Long courseId,
        int unitNumber,
        String unitTitle,
        String topicName,
        int targetWeek,
        int lectureOrder
) {
    public static SyllabusTopicDto fromEntity(SyllabusTopic topic) {
        return new SyllabusTopicDto(
                topic.getId(),
                topic.getCourse().getId(),
                topic.getUnitNumber(),
                topic.getUnitTitle(),
                topic.getTopicName(),
                topic.getTargetWeek(),
                topic.getLectureOrder()
        );
    }
}
