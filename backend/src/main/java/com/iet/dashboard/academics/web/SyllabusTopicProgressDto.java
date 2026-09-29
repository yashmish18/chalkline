package com.iet.dashboard.academics.web;

public record SyllabusTopicProgressDto(
        Long id,
        Long courseId,
        int unitNumber,
        String unitTitle,
        String topicName,
        int targetWeek,
        int lectureOrder,
        boolean isCovered
) {
    public static SyllabusTopicProgressDto fromTopic(SyllabusTopicDto topic, boolean isCovered) {
        return new SyllabusTopicProgressDto(
                topic.id(),
                topic.courseId(),
                topic.unitNumber(),
                topic.unitTitle(),
                topic.topicName(),
                topic.targetWeek(),
                topic.lectureOrder(),
                isCovered
        );
    }
}
