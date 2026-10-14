package com.iet.dashboard.tracking.service;

import com.iet.dashboard.academics.web.CourseDto;
import com.iet.dashboard.academics.web.CourseOfferingDto;
import com.iet.dashboard.academics.web.TeachingAssignmentDto;
import com.iet.dashboard.tracking.web.ReportDtos.Mismatch;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class ReportCalculatorTest {

    private static CourseOfferingDto section(long id, String ltp, int l, int t, int p, String sec, int students) {
        CourseDto course = new CourseDto(id, "CS" + id, "Course " + id, "THEORY", "CORE", 4, ltp, l, t, p, null);
        return new CourseOfferingDto(id, course, "2026-2027", 1, 1, sec, null, null, students);
    }

    private static TeachingAssignmentDto teach(long id, CourseOfferingDto o, String who, String component, String batch, int hours, int students) {
        return new TeachingAssignmentDto(id, o, id, who, component, batch, hours, students);
    }

    @Test
    void aPlannedComponentWithNobodyOnItIsAMissingOwner() {
        CourseOfferingDto o = section(1, "3-1-0", 3, 1, 0, "A", 60);
        List<Mismatch> m = ReportCalculator.mismatches(List.of(o), List.of(teach(1, o, "Dr. K", "LECTURE", "ALL", 3, 60)));

        assertThat(m).singleElement().satisfies(x -> {
            assertThat(x.type()).isEqualTo("MISSING_OWNER");
            assertThat(x.component()).isEqualTo("TUTORIAL");
            assertThat(x.plannedHours()).isEqualTo(1);
        });
    }

    @Test
    void fewerOrMoreHoursThanTheLtpPlanAreFlagged() {
        CourseOfferingDto o = section(2, "3-0-2", 3, 0, 2, "H", 63);
        List<Mismatch> m = ReportCalculator.mismatches(List.of(o), List.of(
                teach(1, o, "Dr. K", "LECTURE", "ALL", 4, 63),
                teach(2, o, "Dr. K", "PRACTICAL", "ALL", 1, 63)));

        assertThat(m).extracting(Mismatch::type).containsExactlyInAnyOrder("OVER_ALLOCATION", "HOURS_SHORT");
    }

    @Test
    void batchesThatDoNotCoverTheWholeSectionAreAGap() {
        CourseOfferingDto o = section(3, "3-0-2", 3, 0, 2, "B", 62);
        List<Mismatch> m = ReportCalculator.mismatches(List.of(o), List.of(
                teach(1, o, "Dr. A", "LECTURE", "ALL", 3, 62),
                teach(2, o, "Dr. A", "PRACTICAL", "B1", 2, 31)));

        assertThat(m).singleElement().satisfies(x -> {
            assertThat(x.type()).isEqualTo("BATCH_COVERAGE_GAP");
            assertThat(x.detail()).contains("31 of 62");
        });
    }

    @Test
    void aFullCourseAssignmentCoversEveryComponent() {
        CourseOfferingDto o = section(4, "3-1-2", 3, 1, 2, "C", 60);
        assertThat(ReportCalculator.mismatches(List.of(o), List.of(teach(1, o, "Dr. A", "ALL", "ALL", 6, 60)))).isEmpty();
    }

    @Test
    void remedialPlanIsRequiredWhenBehindByTwoOrWhenManyScoreBelowForty() {
        assertThat(ReportCalculator.remedialStatus(-1, null, null, List.of())).isEqualTo("NOT_REQUIRED");
        assertThat(ReportCalculator.remedialStatus(-2, null, null, List.of())).isEqualTo("REQUIRED");
        // 10 of 60 below 40% is 16.7% (> 15%)
        assertThat(ReportCalculator.remedialStatus(0, 10, 60, List.of())).isEqualTo("REQUIRED");
        assertThat(ReportCalculator.remedialStatus(-3, null, null, List.of("SUBMITTED"))).isEqualTo("FILED");
        assertThat(ReportCalculator.remedialStatus(-3, null, null, List.of("COMPLETED"))).isEqualTo("COMPLETED");
    }

    @Test
    void assessmentsDueSoFarFollowTheCalendar() {
        assertThat(ReportCalculator.assessmentsDueByNow(3, 8, 16)).isEqualTo(1);
        assertThat(ReportCalculator.assessmentsDueByNow(3, 16, 16)).isEqualTo(3);
        assertThat(ReportCalculator.assessmentsDueByNow(3, null, 16)).isZero();
    }

    @Test
    void missingAssessmentsAreNotFlaggedBeforeTheDepartmentRecordsAny() {
        assertThat(ReportCalculator.assessmentsBehind(0, 1, false)).isFalse();
        assertThat(ReportCalculator.assessmentsBehind(0, 1, true)).isTrue();
        assertThat(ReportCalculator.assessmentsBehind(1, 1, true)).isFalse();
    }
}
