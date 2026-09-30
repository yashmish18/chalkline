package com.iet.dashboard.academics.web;

import com.iet.dashboard.academics.domain.TimetableSlot;

public record TimetableSlotDto(
        Long id,
        Long offeringId,
        String courseCode,
        String courseName,
        String courseDescription,
        String ltp,
        String section,
        String subSection,
        String component,
        String dayOfWeek,
        String startTime,
        String endTime,
        String room,
        Long facultyId
) {
    public static TimetableSlotDto fromEntity(TimetableSlot slot) {
        String code = slot.getOffering() != null && slot.getOffering().getCourse() != null
                ? slot.getOffering().getCourse().getCode() : "";
        String name = slot.getOffering() != null && slot.getOffering().getCourse() != null
                ? slot.getOffering().getCourse().getName() : "";
        String desc = slot.getOffering() != null && slot.getOffering().getCourse() != null
                ? slot.getOffering().getCourse().getDescription() : "";
        String ltp = slot.getOffering() != null && slot.getOffering().getCourse() != null
                ? slot.getOffering().getCourse().getLtp() : "3-0-0";
        String section = slot.getOffering() != null ? slot.getOffering().getSection() : "A";

        return new TimetableSlotDto(
                slot.getId(),
                slot.getOffering() != null ? slot.getOffering().getId() : null,
                code,
                name,
                desc,
                ltp,
                section,
                slot.getSubSection(),
                slot.getComponent(),
                slot.getDayOfWeek(),
                slot.getStartTime().toString(),
                slot.getEndTime().toString(),
                slot.getRoom(),
                slot.getFacultyId()
        );
    }
}
