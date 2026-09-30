package com.iet.dashboard.teaching.web;

import java.util.List;

public record TodayClassDto(
        Long offeringId,
        String courseCode,
        String courseName,
        String courseDescription,
        String ltp,
        String section,
        String subSection,
        String component,
        String timeSlot,
        String room,
        int currentUnitNumber,
        Long nextTopicId,
        String nextTopic,
        String unitTitle,
        int targetWeek,
        int totalPlanned,
        int completedSoFar,
        boolean isLogged,
        List<UnitOptionDto> availableUnits
) {
    public TodayClassDto(
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
        this(
                offeringId,
                courseCode,
                courseName,
                "",
                "3-0-0",
                section,
                "ALL",
                component,
                timeSlot,
                "Room 304",
                1,
                nextTopicId,
                nextTopic,
                unitTitle,
                targetWeek,
                totalPlanned,
                completedSoFar,
                isLogged,
                List.of()
        );
    }
}
