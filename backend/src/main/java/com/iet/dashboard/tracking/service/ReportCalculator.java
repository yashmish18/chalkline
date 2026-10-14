package com.iet.dashboard.tracking.service;

import com.iet.dashboard.academics.domain.CourseComponent;
import com.iet.dashboard.academics.web.CourseOfferingDto;
import com.iet.dashboard.academics.web.TeachingAssignmentDto;
import com.iet.dashboard.tracking.web.ReportDtos.Mismatch;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/** Pure rules behind the reports, kept free of I/O so each one can be tested on its own. */
final class ReportCalculator {

    /** A section is in trouble when it is this many topics behind its plan. */
    static final int REMEDIAL_TOPIC_LAG = 2;
    /** ...or when more than this share of students scored under 40% in the latest assessment. */
    static final double REMEDIAL_LOW_SCORE_SHARE = 0.15;

    private ReportCalculator() {
    }

    /**
     * Compares every section's allocation with its L-T-P plan, component by component.
     * A whole-section owner covers the component; batches must each carry the planned hours and,
     * together, every student of the section.
     */
    static List<Mismatch> mismatches(List<CourseOfferingDto> offerings, List<TeachingAssignmentDto> assignments) {
        Map<Long, List<TeachingAssignmentDto>> byOffering = new HashMap<>();
        for (TeachingAssignmentDto a : assignments) {
            if (a.offering() != null) {
                byOffering.computeIfAbsent(a.offering().id(), k -> new ArrayList<>()).add(a);
            }
        }
        List<Mismatch> result = new ArrayList<>();
        for (CourseOfferingDto o : offerings) {
            List<TeachingAssignmentDto> owners = byOffering.getOrDefault(o.id(), List.of());
            boolean ownsEverything = owners.stream().anyMatch(a -> canonical(a.component()) == CourseComponent.ALL);
            if (ownsEverything) {
                continue;
            }
            check(result, o, owners, CourseComponent.LECTURE, o.course().lectureHours());
            check(result, o, owners, CourseComponent.TUTORIAL, o.course().tutorialHours());
            check(result, o, owners, CourseComponent.PRACTICAL, o.course().practicalHours());
        }
        return result;
    }

    private static void check(List<Mismatch> out, CourseOfferingDto o, List<TeachingAssignmentDto> owners,
                              CourseComponent component, int planned) {
        List<TeachingAssignmentDto> mine = owners.stream().filter(a -> canonical(a.component()) == component).toList();
        String names = mine.stream().map(TeachingAssignmentDto::facultyName).distinct()
                .reduce((x, y) -> x + ", " + y).orElse("—");
        if (planned <= 0) {
            if (!mine.isEmpty()) {
                int hours = mine.stream().mapToInt(TeachingAssignmentDto::contactHours).sum();
                out.add(mismatch(o, component, 0, hours, "OVER_ALLOCATION",
                        "The L-T-P plan has no " + label(component) + ", but " + hours + "h a week are allocated.", names));
            }
            return;
        }
        if (mine.isEmpty()) {
            out.add(mismatch(o, component, planned, 0, "MISSING_OWNER",
                    "Plans " + planned + "h of " + label(component) + " a week; nobody is allocated.", "—"));
            return;
        }
        List<TeachingAssignmentDto> whole = mine.stream().filter(a -> "ALL".equals(batchOf(a))).toList();
        List<TeachingAssignmentDto> batches = mine.stream().filter(a -> !"ALL".equals(batchOf(a))).toList();

        if (!whole.isEmpty()) {
            int hours = whole.stream().mapToInt(TeachingAssignmentDto::contactHours).sum();
            if (hours < planned) {
                out.add(mismatch(o, component, planned, hours, "HOURS_SHORT",
                        hours + "h allocated against " + planned + "h planned.", names));
            } else if (hours > planned) {
                out.add(mismatch(o, component, planned, hours, "OVER_ALLOCATION",
                        hours + "h allocated against " + planned + "h planned.", names));
            }
            return;
        }
        for (TeachingAssignmentDto b : batches) {
            if (b.contactHours() != planned) {
                out.add(mismatch(o, component, planned, b.contactHours(),
                        b.contactHours() < planned ? "HOURS_SHORT" : "OVER_ALLOCATION",
                        "Batch " + batchOf(b) + ": " + b.contactHours() + "h against " + planned + "h planned.", b.facultyName()));
            }
        }
        int covered = batches.stream().mapToInt(TeachingAssignmentDto::studentCount).sum();
        if (o.studentCount() > 0 && covered > 0 && covered < o.studentCount()) {
            out.add(mismatch(o, component, planned, batches.stream().mapToInt(TeachingAssignmentDto::contactHours).max().orElse(0),
                    "BATCH_COVERAGE_GAP",
                    "Batches cover " + covered + " of " + o.studentCount() + " students; the rest have no " + label(component) + " owner.",
                    names));
        }
    }

    private static Mismatch mismatch(CourseOfferingDto o, CourseComponent component, int planned, int assigned,
                                     String type, String detail, String faculty) {
        return new Mismatch(o.id(), o.course().code(), o.course().name(), o.yearOfStudy(), o.semester(), o.section(),
                o.course().ltp(), component.name(), planned, assigned, type, detail, faculty);
    }

    /**
     * Whether a section needs a remedial plan and where that stands.
     *
     * @param planStatuses statuses of the plans already filed for the section
     */
    static String remedialStatus(int topicDelta, Integer below40, Integer assessed, List<String> planStatuses) {
        boolean lowScores = below40 != null && assessed != null && assessed > 0
                && (double) below40 / assessed > REMEDIAL_LOW_SCORE_SHARE;
        boolean required = topicDelta <= -REMEDIAL_TOPIC_LAG || lowScores;
        if (planStatuses.contains("SUBMITTED")) {
            return "FILED";
        }
        if (!planStatuses.isEmpty() && planStatuses.stream().allMatch("COMPLETED"::equals)) {
            return "COMPLETED";
        }
        return required ? "REQUIRED" : "NOT_REQUIRED";
    }

    /** Assessments a section should have held by this week, spreading the required number evenly. */
    static int assessmentsDueByNow(int required, Integer currentWeek, Integer totalWeeks) {
        if (currentWeek == null || totalWeeks == null || totalWeeks <= 0) {
            return 0;
        }
        return (int) Math.floor(required * Math.min(currentWeek, totalWeeks) / (double) totalWeeks);
    }

    /**
     * A section is behind on assessments only once the department records them at all: before the
     * first assessment is entered anywhere, "0 recorded" means "not in use yet", not "missed".
     */
    static boolean assessmentsBehind(int held, int dueByNow, boolean departmentRecordsAssessments) {
        return departmentRecordsAssessments && held < dueByNow;
    }

    static CourseComponent canonical(String component) {
        return CourseComponent.parse(component);
    }

    private static String batchOf(TeachingAssignmentDto a) {
        return a.subSection() == null || a.subSection().isBlank() ? "ALL" : a.subSection();
    }

    private static String label(CourseComponent c) {
        return switch (c) {
            case TUTORIAL -> "tutorial";
            case PRACTICAL, LAB -> "practical";
            default -> "lecture";
        };
    }
}
