package com.iet.dashboard.teaching.web;

import java.time.LocalDate;

/** A timetabled class that has passed with nothing logged for it: held, cancelled or swapped. */
public record PendingClassDto(
        LocalDate date,
        Long offeringId,
        String courseCode,
        String courseName,
        String section,
        String subSection,
        String component,
        String timeSlot,
        String room
) {}
