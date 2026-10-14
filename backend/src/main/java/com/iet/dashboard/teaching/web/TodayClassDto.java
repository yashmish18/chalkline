package com.iet.dashboard.teaching.web;

import java.util.List;

public record TodayClassDto(
        Long offeringId,
        Long courseId,
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
        List<UnitOptionDto> availableUnits,
        /** True when this class comes from the uploaded timetable; false = allocation without a slot. */
        boolean scheduled
) {
    public TodayClassDto(Long offeringId, Long courseId, String courseCode, String courseName, String courseDescription,
                         String ltp, String section, String subSection, String component, String timeSlot, String room,
                         int currentUnitNumber, Long nextTopicId, String nextTopic, String unitTitle, int targetWeek,
                         int totalPlanned, int completedSoFar, boolean isLogged, List<UnitOptionDto> availableUnits) {
        this(offeringId, courseId, courseCode, courseName, courseDescription, ltp, section, subSection, component, timeSlot,
                room, currentUnitNumber, nextTopicId, nextTopic, unitTitle, targetWeek, totalPlanned, completedSoFar,
                isLogged, availableUnits, true);
    }

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
                null,
                courseCode,
                courseName,
                "",
                "3-0-0",
                section,
                "ALL",
                component,
                timeSlot,
                "",
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
