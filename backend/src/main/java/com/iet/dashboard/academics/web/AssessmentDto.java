package com.iet.dashboard.academics.web;

import com.iet.dashboard.academics.domain.Assessment;

import java.time.LocalDate;

public record AssessmentDto(
        Long id,
        Long offeringId,
        String name,
        String kind,
        int weightPct,
        LocalDate heldOn,
        Integer studentsAssessed,
        Integer averagePct,
        Integer below40Count
) {
    public static AssessmentDto fromEntity(Assessment a) {
        return new AssessmentDto(a.getId(), a.getOffering().getId(), a.getName(), a.getKind(), a.getWeightPct(),
                a.getHeldOn(), a.getStudentsAssessed(), a.getAveragePct(), a.getBelow40Count());
    }
}
