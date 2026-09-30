package com.iet.dashboard.academics.web;

import java.util.List;

public record CourseProgressDto(
        Long offeringId,
        Long courseId,
        String courseCode,
        String courseName,
        String section,
        String component,
        int credits,
        String termName,
        int currentWeek,
        int totalTopics,
        int coveredTopics,
        int coveragePercentage,
        int expectedTopicsByCurrentWeek,
        int topicDelta,
        String pacingStatus, // ON_TRACK, AHEAD, BEHIND
        String pacingLabel,
        long totalSessions,
        long heldSessions,
        int conductRatePercentage,
        List<SyllabusTopicProgressDto> topics,
        String ltp,
        String subSection,
        String courseDescription
) {
    public CourseProgressDto(
            Long offeringId,
            Long courseId,
            String courseCode,
            String courseName,
            String section,
            String component,
            int credits,
            String termName,
            int currentWeek,
            int totalTopics,
            int coveredTopics,
            int coveragePercentage,
            int expectedTopicsByCurrentWeek,
            int topicDelta,
            String pacingStatus,
            String pacingLabel,
            long totalSessions,
            long heldSessions,
            int conductRatePercentage,
            List<SyllabusTopicProgressDto> topics
    ) {
        this(offeringId, courseId, courseCode, courseName, section, component, credits, termName,
             currentWeek, totalTopics, coveredTopics, coveragePercentage, expectedTopicsByCurrentWeek,
             topicDelta, pacingStatus, pacingLabel, totalSessions, heldSessions, conductRatePercentage,
             topics, "3-0-0", "ALL", null);
    }
}
