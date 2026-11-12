package com.iet.dashboard.academics.web;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

import java.time.LocalDate;

/** Semester settings the coordinator controls: what the term is called and when teaching runs. */
public record UpdateTermRequest(
        @NotBlank String name,
        @NotBlank String academicYear,
        @NotNull LocalDate startDate,
        @NotNull LocalDate endDate,
        /** Continuous assessments each section must hold; null keeps the current value. */
        Integer requiredAssessments,
        /** The semester's holidays; null keeps the current list, an empty list clears it. */
        java.util.List<AcademicTermDto.Holiday> holidays
) {
    public UpdateTermRequest(String name, String academicYear, LocalDate startDate, LocalDate endDate) {
        this(name, academicYear, startDate, endDate, null, null);
    }

    public UpdateTermRequest(String name, String academicYear, LocalDate startDate, LocalDate endDate, Integer requiredAssessments) {
        this(name, academicYear, startDate, endDate, requiredAssessments, null);
    }
}
