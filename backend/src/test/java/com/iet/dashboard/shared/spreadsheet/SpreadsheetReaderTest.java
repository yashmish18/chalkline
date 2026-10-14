package com.iet.dashboard.shared.spreadsheet;

import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

/** The guards that keep a messy real sheet from being read as something it is not. */
class SpreadsheetReaderTest {

    private static final List<Map.Entry<String, List<String>>> RULES = List.of(
            Map.entry("CODE", List.of("coursecode", "subjectcode")),
            Map.entry("NAME", List.of("=name", "coursename", "subjectsname")),
            Map.entry("LECTURE_HOURS", List.of("lecturehours", "l")),
            Map.entry("PRACTICAL_HOURS", List.of("practicalhours", "p")));

    @Test
    void collapsesNonBreakingSpacesAndWhitespaceRuns() {
        assertThat(SpreadsheetReader.normalizeCell("\u00A0 \n Applied   Physics \t")).isEqualTo("Applied Physics");
        assertThat(SpreadsheetReader.normalizeCell("\u00A0")).isEmpty();
        assertThat(SpreadsheetReader.normalizeCell(null)).isEmpty();
        assertThat(SpreadsheetReader.normalizeHeader("Teaching  Semesters")).isEqualTo("teachingsemesters");
    }

    @Test
    void aSingleLetterAliasOnlyMatchesItsOwnColumn() {
        Map<String, String> detected = SpreadsheetReader.detectColumns(
                List.of("PBL", "Total Load", "L", "P"), RULES);

        // A literal L/P column still works; the letters inside "PBL"/"Total Load" no longer do.
        assertThat(detected)
                .containsEntry("l", "LECTURE_HOURS")
                .containsEntry("p", "PRACTICAL_HOURS")
                .hasSize(2);
    }

    @Test
    void theMostPreciseHeaderClaimsAColumn() {
        // "=name" is exact-only, so the personnel column is not silently taken as the course name.
        assertThat(SpreadsheetReader.detectColumns(List.of("Faculty Name", "Subjects Name"), RULES))
                .containsEntry("subjectsname", "NAME")
                .doesNotContainKey("facultyname");

        // Both headers are exact aliases of the same column, so the more specific one wins.
        assertThat(SpreadsheetReader.detectColumns(List.of("Subjects Name", "Name"), RULES))
                .containsExactly(Map.entry("subjectsname", "NAME"));
    }

    @Test
    void reportsHeadersNoRuleClaimed() {
        assertThat(SpreadsheetReader.unmappedHeaders(
                List.of("S. No.", "PBL", "Course Code"), Map.of("coursecode", "CODE")))
                .containsExactly("sno", "pbl");
        assertThat(SpreadsheetReader.headerOf(Map.of("coursecode", "CODE"), "CODE")).isEqualTo("coursecode");
        assertThat(SpreadsheetReader.headerOf(Map.of("coursecode", "CODE"), "NAME")).isNull();
    }
}
