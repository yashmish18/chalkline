package com.iet.dashboard.academics.service;

import com.iet.dashboard.academics.domain.Assessment;
import com.iet.dashboard.academics.domain.CourseOffering;
import com.iet.dashboard.academics.domain.RemedialPlan;
import com.iet.dashboard.academics.repo.AssessmentRepository;
import com.iet.dashboard.academics.repo.CourseOfferingRepository;
import com.iet.dashboard.academics.repo.RemedialPlanRepository;
import com.iet.dashboard.academics.repo.TeachingAssignmentRepository;
import com.iet.dashboard.academics.web.AssessmentDto;
import com.iet.dashboard.academics.web.RecordAssessmentRequest;
import com.iet.dashboard.academics.web.RemedialPlanDto;
import com.iet.dashboard.academics.web.SubmitRemedialPlanRequest;
import com.iet.dashboard.directory.service.FacultyService;
import com.iet.dashboard.directory.web.FacultyDto;
import com.iet.dashboard.shared.error.ForbiddenException;
import com.iet.dashboard.shared.error.ResourceNotFoundException;
import com.iet.dashboard.shared.error.ValidationException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

@Service
@Transactional
public class AssessmentServiceImpl implements AssessmentService {

    static final Set<String> KINDS = Set.of("MID_TERM", "QUIZ", "ASSIGNMENT", "LAB", "PROJECT", "OTHER");
    static final Set<String> REASONS = Set.of("BEHIND_SYLLABUS", "LOW_ASSESSMENT", "CANCELLED_CLASSES", "OTHER");

    private final AssessmentRepository assessmentRepository;
    private final RemedialPlanRepository remedialRepository;
    private final CourseOfferingRepository offeringRepository;
    private final TeachingAssignmentRepository assignmentRepository;
    private final FacultyService facultyService;

    public AssessmentServiceImpl(AssessmentRepository assessmentRepository, RemedialPlanRepository remedialRepository,
                                 CourseOfferingRepository offeringRepository, TeachingAssignmentRepository assignmentRepository,
                                 FacultyService facultyService) {
        this.assessmentRepository = assessmentRepository;
        this.remedialRepository = remedialRepository;
        this.offeringRepository = offeringRepository;
        this.assignmentRepository = assignmentRepository;
        this.facultyService = facultyService;
    }

    @Override
    public AssessmentDto recordAssessment(RecordAssessmentRequest r, Long actingFacultyId) {
        CourseOffering offering = offeringFor(r.offeringId(), actingFacultyId);
        String kind = r.kind().trim().toUpperCase();
        if (!KINDS.contains(kind)) {
            throw new ValidationException("Unknown assessment type: " + r.kind());
        }
        if (r.weightPct() < 0 || r.weightPct() > 100) {
            throw new ValidationException("Weight must be between 0 and 100%.");
        }
        if (r.averagePct() != null && (r.averagePct() < 0 || r.averagePct() > 100)) {
            throw new ValidationException("Average must be between 0 and 100%.");
        }
        if (r.below40Count() != null && r.studentsAssessed() != null && r.below40Count() > r.studentsAssessed()) {
            throw new ValidationException("More students below 40% than students assessed.");
        }
        Assessment saved = assessmentRepository.save(new Assessment(offering, r.name().trim(), kind, r.weightPct(),
                r.heldOn(), r.studentsAssessed(), r.averagePct(), r.below40Count(), actingFacultyId));
        return AssessmentDto.fromEntity(saved);
    }

    @Override
    @Transactional(readOnly = true)
    public List<AssessmentDto> getAssessments(Long offeringId, Long actingFacultyId) {
        offeringFor(offeringId, actingFacultyId);
        return assessmentRepository.findByOfferingIdOrderByHeldOnAsc(offeringId).stream().map(AssessmentDto::fromEntity).toList();
    }

    @Override
    public void deleteAssessment(Long assessmentId, Long actingFacultyId) {
        Assessment a = assessmentRepository.findById(assessmentId)
                .orElseThrow(() -> new ResourceNotFoundException("Assessment not found: " + assessmentId));
        offeringFor(a.getOffering().getId(), actingFacultyId);
        assessmentRepository.delete(a);
    }

    @Override
    public RemedialPlanDto submitRemedialPlan(SubmitRemedialPlanRequest r, Long actingFacultyId) {
        if (actingFacultyId == null) {
            throw new ValidationException("A remedial plan is filed by the faculty member who teaches the section.");
        }
        CourseOffering offering = offeringFor(r.offeringId(), actingFacultyId);
        String reason = r.reason().trim().toUpperCase();
        if (!REASONS.contains(reason)) {
            throw new ValidationException("Unknown reason: " + r.reason());
        }
        if (r.extraSessions() < 0) {
            throw new ValidationException("Extra sessions cannot be negative.");
        }
        RemedialPlan saved = remedialRepository.save(new RemedialPlan(offering, actingFacultyId, reason,
                r.laggingTopics(), r.action().trim(), r.extraSessions(), r.targetDate()));
        return RemedialPlanDto.fromEntity(saved);
    }

    @Override
    @Transactional(readOnly = true)
    public List<RemedialPlanDto> getRemedialPlans(Long offeringId, Long actingFacultyId) {
        if (offeringId == null) {
            if (actingFacultyId != null) {
                throw new ForbiddenException("Only the coordinator can list every remedial plan.");
            }
            Map<Long, String> names = facultyService.getAllFaculty(null, null).stream()
                    .collect(Collectors.toMap(FacultyDto::id, FacultyDto::name, (a, b) -> a));
            return remedialRepository.findAllWithOffering().stream()
                    .map(p -> RemedialPlanDto.fromEntity(p, names.getOrDefault(p.getFacultyId(), "Faculty #" + p.getFacultyId())))
                    .toList();
        }
        offeringFor(offeringId, actingFacultyId);
        return remedialRepository.findByOfferingIdOrderByCreatedAtDesc(offeringId).stream().map(RemedialPlanDto::fromEntity).toList();
    }

    @Override
    public RemedialPlanDto completeRemedialPlan(Long planId, Long actingFacultyId) {
        RemedialPlan plan = remedialRepository.findById(planId)
                .orElseThrow(() -> new ResourceNotFoundException("Remedial plan not found: " + planId));
        if (actingFacultyId != null && !actingFacultyId.equals(plan.getFacultyId())) {
            throw new ForbiddenException("Only the faculty member who filed this plan can complete it.");
        }
        plan.complete();
        return RemedialPlanDto.fromEntity(remedialRepository.save(plan));
    }

    /** The section, provided the acting faculty member teaches it (the coordinator always may). */
    private CourseOffering offeringFor(Long offeringId, Long actingFacultyId) {
        CourseOffering offering = offeringRepository.findById(offeringId)
                .orElseThrow(() -> new ResourceNotFoundException("Section not found: " + offeringId));
        if (actingFacultyId != null && !assignmentRepository.existsByOfferingIdAndFacultyId(offeringId, actingFacultyId)) {
            throw new ForbiddenException("You do not teach this section.");
        }
        return offering;
    }
}
