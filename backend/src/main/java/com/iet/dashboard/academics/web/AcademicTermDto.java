package com.iet.dashboard.academics.web;

import com.iet.dashboard.academics.domain.AcademicTerm;
import java.time.LocalDate;

public record AcademicTermDto(
        Long id,
        String name,
        String academicYear,
        LocalDate startDate,
        LocalDate endDate,
        boolean active,
        int requiredAssessments,
        /** Days with no classes, in date order, as the coordinator entered them. */
        java.util.List<Holiday> holidays
) {
    public record Holiday(LocalDate date, String name) {
    }

    public AcademicTermDto {
        holidays = holidays == null ? java.util.List.of() : java.util.List.copyOf(holidays);
    }

    public AcademicTermDto(Long id, String name, String academicYear, LocalDate startDate, LocalDate endDate, boolean active) {
        this(id, name, academicYear, startDate, endDate, active, 3);
    }

    public AcademicTermDto(Long id, String name, String academicYear, LocalDate startDate, LocalDate endDate,
                           boolean active, int requiredAssessments) {
        this(id, name, academicYear, startDate, endDate, active, requiredAssessments, java.util.List.of());
    }

    public boolean isHoliday(LocalDate date) {
        return holidays.stream().anyMatch(h -> h.date().equals(date));
    }

    public java.util.Set<LocalDate> holidayDates() {
        return holidays.stream().map(Holiday::date).collect(java.util.stream.Collectors.toUnmodifiableSet());
    }

    public static AcademicTermDto fromEntity(AcademicTerm term) {
        return new AcademicTermDto(
                term.getId(),
                term.getName(),
                term.getAcademicYear(),
                term.getStartDate(),
                term.getEndDate(),
                term.isActive(),
                term.getRequiredAssessments(),
                term.getHolidays().stream().map(h -> new Holiday(h.getDate(), h.getName())).toList()
        );
    }
}
