package com.iet.dashboard.academics.service;

import com.iet.dashboard.academics.domain.CourseComponent;
import org.apache.pdfbox.Loader;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.pdmodel.PDPage;
import org.apache.pdfbox.text.PDFTextStripper;
import org.apache.pdfbox.text.TextPosition;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.time.DayOfWeek;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Reads a week-grid timetable PDF — days down the left, class times across the top — into the same
 * flat rows {@link com.iet.dashboard.shared.spreadsheet.SpreadsheetReader} produces, so the
 * importer keeps one code path for CSV, Excel and PDF.
 *
 * <p>Two layouts are covered. The department export spells a cell out
 * ("DBMS (CS1133) Lecture (L) Sec A – EB2-004"); the faculty-portal export repeats the course name
 * and names only the faculty for a lab ("Dr. S Taruna (P-A1) - LRC007"). A cell that carries no
 * course code is emitted with a blank code, and the importer resolves it against the faculty's
 * allocations. Layout is rebuilt from word coordinates, so nothing depends on whitespace alignment.
 */
final class TimetablePdfParser {

    private static final Pattern TIME_RANGE =
            Pattern.compile("(\\d{1,2}):(\\d{2})\\s*[-–—]\\s*(\\d{1,2}):(\\d{2})");
    private static final Pattern COURSE_CODE = Pattern.compile("\\(([A-Za-z]{2,5}\\d{3,4})\\)");
    private static final Pattern SECTION = Pattern.compile("(?i)\\b(?:sec|gp)\\s*-?\\s*([a-z])\\b");
    private static final Pattern PRACTICAL_BATCH = Pattern.compile("(?i)\\bP-([a-z]\\d+)\\b");
    private static final Pattern ROOM_TOKEN = Pattern.compile("[A-Za-z0-9]+(?:[-/][A-Za-z0-9]+)*");

    /** How far below the last day a stray line may sit before it is treated as a footnote/summary. */
    private static final float FOOTER_GAP = 0.6f;

    private TimetablePdfParser() {
    }

    /** Every row carries the same keys, because the importer detects columns from the first row. */
    private static final List<String> COLUMNS =
            List.of("day", "start", "end", "room", "coursecode", "section", "component", "batch");

    static List<Map<String, String>> parse(MultipartFile file) {
        if (file == null || file.isEmpty()) {
            throw new IllegalArgumentException("The uploaded file is empty.");
        }
        List<Word> words = readWords(file);
        List<Column> columns = columns(words);
        if (columns.size() < 2) {
            throw new IllegalArgumentException("No class-time header was found in the PDF. Upload the "
                    + "week-grid timetable, or use the CSV/Excel template.");
        }
        float gridTop = topOfGrid(words);
        List<DayRow> days = dayRows(words);
        if (days.isEmpty()) {
            throw new IllegalArgumentException("No day rows (Mon–Fri) were found in the PDF.");
        }
        float spacing = days.size() > 1
                ? (days.get(days.size() - 1).labelBottom() - days.get(0).labelBottom()) / (days.size() - 1)
                : 30;
        float gridBottom = days.get(days.size() - 1).labelBottom() + spacing * FOOTER_GAP;

        Map<Integer, List<Word>> cells = new LinkedHashMap<>();
        for (Word word : words) {
            int column = columnIndex(word, columns);
            if (column < 0) {
                continue;
            }
            int day = dayIndex(word, days, gridTop, gridBottom);
            if (day < 0) {
                continue;
            }
            cells.computeIfAbsent(day * columns.size() + column, key -> new ArrayList<>()).add(word);
        }

        List<Map<String, String>> rows = new ArrayList<>();
        for (int day = 0; day < days.size(); day++) {
            for (int column = 0; column < columns.size(); column++) {
                List<Word> cell = cells.get(day * columns.size() + column);
                if (cell != null) {
                    rows.addAll(cellRows(days.get(day), columns.get(column), cell));
                }
            }
        }
        if (rows.isEmpty()) {
            throw new IllegalArgumentException("No classes were found in the PDF timetable.");
        }
        return rows;
    }

    // ------------------------------------------------------------------ page text

    private static List<Word> readWords(MultipartFile file) {
        try (PDDocument document = Loader.loadPDF(file.getBytes())) {
            Collector collector = new Collector();
            collector.setSortByPosition(true);
            collector.getText(document);
            return collector.gridPage();
        } catch (IOException | RuntimeException e) {
            throw new IllegalArgumentException("The PDF could not be read: " + e.getMessage(), e);
        }
    }

    /** One text word with its box on the page; coordinates are top-left origin. */
    private record Word(String text, float x0, float y0, float x1, float y1) {
        float centerX() {
            return (x0 + x1) / 2;
        }

        float centerY() {
            return (y0 + y1) / 2;
        }
    }

    /**
     * Collects words with their positions, then keeps the one page that carries a time header.
     * Footnote and cover pages are dropped with it.
     */
    private static final class Collector extends PDFTextStripper {

        private final List<List<Word>> pages = new ArrayList<>();
        private final StringBuilder buffer = new StringBuilder();
        private List<Word> pageWords;
        private float x0;
        private float y0;
        private float x1;
        private float y1;
        private float lastX;
        private float lastY;

        Collector() throws IOException {
            super();
        }

        @Override
        protected void startPage(PDPage page) throws IOException {
            super.startPage(page);
            pageWords = new ArrayList<>();
            pages.add(pageWords);
            flush();
        }

        @Override
        protected void endPage(PDPage page) throws IOException {
            flush();
            super.endPage(page);
        }

        @Override
        protected void writeString(String string, List<TextPosition> positions) {
            for (TextPosition position : positions) {
                String value = position.getUnicode();
                if (value == null || value.isBlank()) {
                    flush();
                    continue;
                }
                boolean newLine = !buffer.isEmpty()
                        && Math.abs(position.getYDirAdj() - lastY) > position.getHeightDir();
                boolean newWord = newLine || (!buffer.isEmpty()
                        && position.getXDirAdj() - lastX > position.getWidthOfSpace() * 0.4f);
                if (newWord) {
                    flush();
                }
                if (buffer.isEmpty()) {
                    x0 = position.getXDirAdj();
                    y0 = position.getYDirAdj();
                    x1 = x0 + position.getWidthDirAdj();
                    y1 = y0 + position.getHeightDir();
                } else {
                    x0 = Math.min(x0, position.getXDirAdj());
                    y0 = Math.min(y0, position.getYDirAdj());
                    x1 = Math.max(x1, position.getXDirAdj() + position.getWidthDirAdj());
                    y1 = Math.max(y1, position.getYDirAdj() + position.getHeightDir());
                }
                buffer.append(value);
                lastX = position.getXDirAdj() + position.getWidthDirAdj();
                lastY = position.getYDirAdj();
            }
        }

        @Override
        protected void writeWordSeparator() {
            flush();
        }

        @Override
        protected void writeLineSeparator() {
            flush();
        }

        private void flush() {
            if (!buffer.isEmpty() && pageWords != null) {
                pageWords.add(new Word(buffer.toString(), x0, y0, x1, y1));
            }
            buffer.setLength(0);
            lastX = 0;
            lastY = 0;
        }

        List<Word> gridPage() {
            List<Word> best = List.of();
            int bestHeaders = 0;
            for (List<Word> page : pages) {
                int headers = (int) page.stream()
                        .filter(word -> TIME_RANGE.matcher(word.text()).find())
                        .count();
                if (headers > bestHeaders) {
                    bestHeaders = headers;
                    best = page;
                }
            }
            return best;
        }
    }

    // ------------------------------------------------------------------ grid geometry

    /** A class-time column: its minutes-from-midnight range and its horizontal band on the page. */
    private record Column(int startMinute, int endMinute, float left, float right) {
        float centerX() {
            return (left + right) / 2;
        }

        String start() {
            return "%02d:%02d".formatted(startMinute / 60, startMinute % 60);
        }

        String end() {
            return "%02d:%02d".formatted(endMinute / 60, endMinute % 60);
        }
    }

    /** A day row, anchored on the bottom of its day label. */
    private record DayRow(String day, float labelBottom) {
    }

    private static List<Column> columns(List<Word> words) {
        List<Word> headers = words.stream()
                .filter(word -> TIME_RANGE.matcher(word.text()).find())
                .sorted(Comparator.comparingDouble(Word::centerX))
                .toList();
        List<Column> columns = new ArrayList<>();
        for (Word header : headers) {
            Matcher range = TIME_RANGE.matcher(header.text());
            if (!range.find()) {
                continue;
            }
            Word suffix = meridiemWord(words, header);
            String meridiem = suffix == null ? "" : suffix.text().toLowerCase(Locale.ROOT);
            int start = minutes(Integer.parseInt(range.group(1)), Integer.parseInt(range.group(2)), meridiem);
            int end = minutes(Integer.parseInt(range.group(3)), Integer.parseInt(range.group(4)), meridiem);
            columns.add(new Column(start, end, header.x0(), suffix != null ? suffix.x1() : header.x1()));
        }
        return columns;
    }

    /** The "am"/"pm" sitting just to the right of a header, whose reading ("12:00 pm") the times need. */
    private static Word meridiemWord(List<Word> words, Word header) {
        return words.stream()
                .filter(word -> meridiemText(word.text()))
                .filter(word -> Math.abs(word.centerY() - header.centerY()) < 4)
                .filter(word -> word.centerX() > header.x1() && word.centerX() - header.x1() < 40)
                .min(Comparator.comparingDouble(Word::centerX))
                .orElse(null);
    }

    private static int minutes(int hour, int minute, String meridiem) {
        if ("pm".equals(meridiem) && hour < 12) {
            hour += 12;
        } else if ("am".equals(meridiem) && hour == 12) {
            hour = 0;
        }
        return hour * 60 + minute;
    }

    /** The line just below the time header; everything above it is the cover/title. */
    private static float topOfGrid(List<Word> words) {
        float bottom = 0;
        for (Word word : words) {
            if (TIME_RANGE.matcher(word.text()).find()) {
                bottom = Math.max(bottom, word.y1());
            }
        }
        return bottom + 4;
    }

    private static List<DayRow> dayRows(List<Word> words) {
        return words.stream()
                .filter(word -> weekday(word.text()) != null)
                .sorted(Comparator.comparingDouble(Word::centerY))
                .map(word -> new DayRow(weekday(word.text()), word.y1()))
                .distinct()
                .toList();
    }

    /**
     * The day whose label sits closest below the word. Day labels are laid out consistently with
     * their row, while a PDF exporter (Word in particular) can report a text run's own origin a
     * little high or low — so anchoring on the label, not a fixed band, keeps the row assignment.
     */
    private static int dayIndex(Word word, List<DayRow> days, float gridTop, float gridBottom) {
        if (weekday(word.text()) != null || meridiemText(word.text())) {
            return -1;
        }
        if (word.y0() < gridTop - 2 || word.y0() > gridBottom) {
            return -1;
        }
        int best = -1;
        float bestDistance = Float.MAX_VALUE;
        for (int i = 0; i < days.size(); i++) {
            float distance = Math.abs(word.y0() - days.get(i).labelBottom());
            if (distance < bestDistance) {
                bestDistance = distance;
                best = i;
            }
        }
        return best;
    }

    private static int columnIndex(Word word, List<Column> columns) {
        if (word.centerX() < columns.get(0).left() - 5) {
            return -1;
        }
        for (int i = 0; i < columns.size() - 1; i++) {
            if (word.centerX() < (columns.get(i).centerX() + columns.get(i + 1).centerX()) / 2) {
                return i;
            }
        }
        return columns.size() - 1;
    }

    private static String weekday(String text) {
        String value = text.trim().toUpperCase(Locale.ROOT);
        if (value.length() < 3 || value.length() > 9) {
            return null;
        }
        for (DayOfWeek day : DayOfWeek.values()) {
            if (day.name().startsWith(value)) {
                return day.name();
            }
        }
        return null;
    }

    private static boolean meridiemText(String text) {
        return text.equalsIgnoreCase("am") || text.equalsIgnoreCase("pm");
    }

    // ------------------------------------------------------------------ cell -> rows

    private static List<Map<String, String>> cellRows(DayRow day, Column column, List<Word> cell) {
        String text = cell.stream()
                .sorted(Comparator.comparingDouble(Word::y0).thenComparingDouble(Word::x0))
                .map(Word::text)
                .reduce((left, right) -> left + " " + right)
                .orElse("");
        CourseComponent component = component(text);
        String section = section(text);
        String batch = component == CourseComponent.PRACTICAL || component == CourseComponent.TUTORIAL
                ? batch(text)
                : "";
        if (!batch.isEmpty() && section.isEmpty()) {
            section = batch.substring(0, 1);
        }

        List<Word> codes = distinctCodes(cell);
        if (codes.isEmpty()) {
            // Not a class cell (a break label, a stray letter): only emit when it names a component.
            if (component == null && batch.isEmpty()) {
                return List.of();
            }
            return List.of(row(day, column, "", room(cell, null), section, component, batch));
        }
        List<Map<String, String>> rows = new ArrayList<>();
        for (Word code : codes) {
            rows.add(row(day, column, codeString(code), room(cell, code), section, component, batch));
        }
        return rows;
    }

    /** A cell can hold more than one course (an overlapping entry); each becomes its own slot. */
    private static List<Word> distinctCodes(List<Word> cell) {
        List<Word> codes = new ArrayList<>();
        List<String> seen = new ArrayList<>();
        for (Word word : cell) {
            String code = codeString(word);
            if (!code.isEmpty() && !seen.contains(code)) {
                seen.add(code);
                codes.add(word);
            }
        }
        return codes;
    }

    private static String codeString(Word word) {
        Matcher matcher = COURSE_CODE.matcher(word.text());
        return matcher.find() ? matcher.group(1).toUpperCase(Locale.ROOT) : "";
    }

    private static CourseComponent component(String text) {
        String lower = text.toLowerCase(Locale.ROOT);
        boolean practical = lower.contains("practical") || lower.contains("(p-");
        boolean tutorial = lower.contains("tutorial") || lower.contains("(t-");
        boolean lecture = lower.contains("lecture") || lower.contains("(l)");
        if (practical && !lecture) {
            return CourseComponent.PRACTICAL;
        }
        if (tutorial && !lecture && !practical) {
            return CourseComponent.TUTORIAL;
        }
        return null;
    }

    private static String section(String text) {
        Matcher matcher = SECTION.matcher(text);
        return matcher.find() ? matcher.group(1).toUpperCase(Locale.ROOT) : "";
    }

    private static String batch(String text) {
        Matcher matcher = PRACTICAL_BATCH.matcher(text);
        return matcher.find() ? matcher.group(1).toUpperCase(Locale.ROOT) : "";
    }

    /** The room nearest the course code, so a cell with two entries pairs each code with its own. */
    private static String room(List<Word> cell, Word code) {
        return cell.stream()
                .filter(word -> isRoom(word.text()))
                .min(Comparator.comparingDouble(word ->
                        Math.abs(word.centerX() - (code != null ? code.centerX() : word.centerX()))))
                .map(Word::text)
                .orElse("");
    }

    private static boolean isRoom(String token) {
        if (!ROOM_TOKEN.matcher(token).matches()) {
            return false;
        }
        return token.chars().anyMatch(Character::isLetter) && token.chars().anyMatch(Character::isDigit);
    }

    private static Map<String, String> row(
            DayRow day, Column column, String code, String room,
            String section, CourseComponent component, String batch) {
        Map<String, String> row = new LinkedHashMap<>();
        for (String columnKey : COLUMNS) {
            row.put(columnKey, "");
        }
        row.put("day", day.day());
        row.put("start", column.start());
        row.put("end", column.end());
        row.put("room", room);
        row.put("coursecode", code);
        row.put("section", section);
        row.put("component", component == null ? "" : component.name());
        row.put("batch", batch);
        return row;
    }
}
