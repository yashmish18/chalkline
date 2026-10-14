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
        int requiredAssessments
) {
    public AcademicTermDto(Long id, String name, String academicYear, LocalDate startDate, LocalDate endDate, boolean active) {
        this(id, name, academicYear, startDate, endDate, active, 3);
    }

    public static AcademicTermDto fromEntity(AcademicTerm term) {
        return new AcademicTermDto(
                term.getId(),
                term.getName(),
                term.getAcademicYear(),
                term.getStartDate(),
                term.getEndDate(),
                term.isActive(),
                term.getRequiredAssessments()
        );
    }
}
