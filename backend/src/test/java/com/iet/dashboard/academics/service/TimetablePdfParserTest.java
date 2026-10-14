package com.iet.dashboard.academics.service;

import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockMultipartFile;

import java.io.InputStream;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class TimetablePdfParserTest {

    /** The two real timetable layouts handed to us: the department grid and the faculty-portal grid. */
    @Test
    void readsTheDepartmentGridLayoutIntoSlots() throws Exception {
        List<Map<String, String>> rows = TimetablePdfParser.parse(pdf("department-grid.pdf"));

        assertThat(rows).containsExactly(
                slot("MONDAY", "15:00", "15:50", "LRC007", "CS1133", "A", "PRACTICAL", "A1"),
                slot("MONDAY", "16:00", "16:50", "LRC007", "CS1133", "A", "PRACTICAL", "A1"),
                slot("TUESDAY", "10:00", "10:50", "EB2-004", "CS1133", "A", "", ""),
                slot("TUESDAY", "15:00", "15:50", "EB2-206", "CS1133", "B", "", ""),
                slot("WEDNESDAY", "09:00", "09:50", "LRC007", "CS1133", "A", "PRACTICAL", "A2"),
                slot("WEDNESDAY", "10:00", "10:50", "LRC007", "CS1133", "A", "PRACTICAL", "A2"),
                slot("WEDNESDAY", "12:00", "12:50", "EB2-206", "CS1133", "B", "", ""),
                slot("WEDNESDAY", "15:00", "15:50", "EB2-004", "CS1133", "A", "", ""),
                slot("FRIDAY", "11:00", "11:50", "EB2-004", "CS1133", "A", "", ""),
                slot("FRIDAY", "15:00", "15:50", "EB2-206", "CS1133", "B", "", ""));
    }

    /**
     * The portal export repeats the course name inside each cell and drops the code from a lab, so
     * labs come out with a blank course for the importer to resolve. It also carries a stray
     * overlapping CS1142 entry on Wednesday afternoon, which stays a separate row.
     */
    @Test
    void readsThePortalGridLayoutIntoSlots() throws Exception {
        List<Map<String, String>> rows = TimetablePdfParser.parse(pdf("portal-grid.pdf"));

        assertThat(rows).containsExactly(
                slot("MONDAY", "15:00", "15:50", "LRC007", "", "A", "PRACTICAL", "A1"),
                slot("MONDAY", "16:00", "16:50", "LRC007", "", "A", "PRACTICAL", "A1"),
                slot("TUESDAY", "10:00", "10:50", "EB2-004", "CS1133", "A", "", ""),
                slot("TUESDAY", "15:00", "15:50", "EB2-206", "CS1133", "B", "", ""),
                slot("WEDNESDAY", "09:00", "09:50", "LRC007", "", "A", "PRACTICAL", "A2"),
                slot("WEDNESDAY", "10:00", "10:50", "LRC007", "", "A", "PRACTICAL", "A2"),
                slot("WEDNESDAY", "12:00", "12:50", "EB2-206", "CS1133", "B", "", ""),
                slot("WEDNESDAY", "15:00", "15:50", "EB2-004", "CS1133", "A", "", ""),
                slot("WEDNESDAY", "15:00", "15:50", "LRC007", "CS1142", "A", "", ""),
                slot("FRIDAY", "11:00", "11:50", "EB2-004", "CS1133", "A", "", ""),
                slot("FRIDAY", "15:00", "15:50", "EB2-206", "CS1133", "B", "", ""));
    }

    @Test
    void rejectsAnEmptyUpload() {
        assertThatThrownBy(() -> TimetablePdfParser.parse(
                new MockMultipartFile("file", "empty.pdf", "application/pdf", new byte[0])))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("empty");
    }

    /** A PDF that is not a week grid (here the workload-allocation sheet) is refused, not half-read. */
    @Test
    void refusesAPdfWithoutAClassTimeHeader() throws Exception {
        assertThatThrownBy(() -> TimetablePdfParser.parse(pdf("allocation-sheet.pdf")))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("No class-time header");
    }

    @Test
    void refusesAFileThatIsNotReallyAPdf() {
        assertThatThrownBy(() -> TimetablePdfParser.parse(new MockMultipartFile(
                "file", "broken.pdf", "application/pdf", "definitely not a pdf".getBytes())))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("could not be read");
    }

    /** The files handed to us in the project root parse exactly like the fixtures they were taken from. */
    @Test
    void readsTheUploadedTarunaFilesTheSameWayAsTheFixtures() throws Exception {
        assertThat(TimetablePdfParser.parse(pdf("taruna-department.pdf")))
                .isEqualTo(TimetablePdfParser.parse(pdf("department-grid.pdf")));
        assertThat(TimetablePdfParser.parse(pdf("taruna-portal.pdf")))
                .isEqualTo(TimetablePdfParser.parse(pdf("portal-grid.pdf")));
    }

    private static Map<String, String> slot(
            String day, String start, String end, String room,
            String code, String section, String component, String batch) {
        Map<String, String> row = new LinkedHashMap<>();
        row.put("day", day);
        row.put("start", start);
        row.put("end", end);
        row.put("room", room);
        row.put("coursecode", code);
        row.put("section", section);
        row.put("component", component);
        row.put("batch", batch);
        return row;
    }

    private static MockMultipartFile pdf(String name) throws Exception {
        try (InputStream in = TimetablePdfParserTest.class.getResourceAsStream("/timetable-pdf/" + name)) {
            return new MockMultipartFile("file", name, "application/pdf", in.readAllBytes());
        }
    }
}
