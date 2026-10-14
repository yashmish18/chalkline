package com.iet.dashboard.academics.service;

import com.iet.dashboard.academics.web.AssessmentDto;
import com.iet.dashboard.academics.web.RecordAssessmentRequest;
import com.iet.dashboard.academics.web.RemedialPlanDto;
import com.iet.dashboard.academics.web.SubmitRemedialPlanRequest;

import java.util.List;

/**
 * Continuous assessments and remedial plans. Every method takes the acting faculty id from the
 * security context; null means the coordinator, who may act on any section.
 */
public interface AssessmentService {

    AssessmentDto recordAssessment(RecordAssessmentRequest request, Long actingFacultyId);

    List<AssessmentDto> getAssessments(Long offeringId, Long actingFacultyId);

    void deleteAssessment(Long assessmentId, Long actingFacultyId);

    RemedialPlanDto submitRemedialPlan(SubmitRemedialPlanRequest request, Long actingFacultyId);

    /** @param offeringId null for every plan (coordinator only) */
    List<RemedialPlanDto> getRemedialPlans(Long offeringId, Long actingFacultyId);

    RemedialPlanDto completeRemedialPlan(Long planId, Long actingFacultyId);
}
