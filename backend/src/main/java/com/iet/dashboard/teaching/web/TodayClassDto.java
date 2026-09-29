package com.iet.dashboard.teaching.web;

public record TodayClassDto(
        Long offeringId,
        String courseCode,
        String courseName,
        String section,
        String component,
        String timeSlot,
        Long nextTopicId,
        String nextTopic,
        String unitTitle,
        int targetWeek,
        int totalPlanned,
        int completedSoFar,
        boolean isLogged
) {
}
