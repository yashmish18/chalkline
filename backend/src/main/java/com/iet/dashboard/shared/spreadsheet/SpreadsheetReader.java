package com.iet.dashboard.shared.spreadsheet;

import org.apache.poi.ss.usermodel.Cell;
import org.apache.poi.ss.usermodel.DataFormatter;
import org.apache.poi.ss.usermodel.Row;
import org.apache.poi.ss.usermodel.Sheet;
import org.apache.poi.ss.usermodel.Workbook;
import org.apache.poi.ss.usermodel.WorkbookFactory;
import org.springframework.web.multipart.MultipartFile;

import java.io.BufferedReader;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Reads CSV and Excel uploads into header→value rows. Headers are normalised (lowercase,
 * alphanumeric only) so callers can apply their own fuzzy column detection; the first
 * non-empty row is always treated as the header. Column positions are preserved even when
 * the sheet has leading blank cells.
 */
public final class SpreadsheetReader {

    /**
     * Aliases shorter than this are only ever matched exactly. A one- or two-letter fragment
     * ("l", "t", "p") appears inside almost every unrelated header, which is how a "PBL"
     * column used to be detected as PRACTICAL_HOURS.
     */
    private static final int MIN_SUBSTRING_ALIAS_LENGTH = 3;

    /** Prefix marking an alias as exact-only, e.g. {@code "=name"} matches "name" but not "facultyname". */
    private static final String EXACT_ALIAS_PREFIX = "=";

    private SpreadsheetReader() {
    }

    /** Dispatches on the file extension; anything that is not .xlsx/.xls is read as CSV. */
    public static List<Map<String, String>> read(MultipartFile file) {
        if (file == null || file.isEmpty()) {
            throw new IllegalArgumentException("Uploaded file cannot be empty");
        }
        String name = file.getOriginalFilename() != null
                ? file.getOriginalFilename().toLowerCase()
                : "";
        try (InputStream in = file.getInputStream()) {
            if (name.endsWith(".xlsx") || name.endsWith(".xls")) {
                return parseExcel(in);
            }
            return parseCsv(in);
        } catch (Exception e) {
            throw new IllegalArgumentException("Failed to parse spreadsheet file: " + e.getMessage(), e);
        }
    }

    /**
     * Collapses non-breaking spaces and runs of whitespace into single spaces. Real sheets are
     * full of {@code \u00A0} ("blank" cells that survive {@code trim()}) and stray newlines; every
     * header and cell is put through this once so callers never see them.
     */
    public static String normalizeCell(String value) {
        if (value == null) {
            return "";
        }
        return value.replace('\u00A0', ' ').replaceAll("\\s+", " ").trim();
    }

    /** Lowercases and strips everything but letters and digits, for fuzzy header matching. */
    public static String normalizeHeader(String header) {
        return normalizeCell(header).toLowerCase().replaceAll("[^a-z0-9]", "");
    }

    /**
     * Automatic header detection: maps each spreadsheet header to a canonical column key.
     * Matching is exact-alias first, then substring (so "Credits (see UGC)" lands on
     * "credits"). A column is claimed by the header that matches it most precisely, so
     * "Subjects Name" is claimed as a course name while "Faculty Name" is not.
     */
    public static Map<String, String> detectColumns(
            Iterable<String> headers, List<Map.Entry<String, List<String>>> rules) {
        Map<String, Match> bestPerColumn = new HashMap<>();
        Map<String, String> headerPerColumn = new HashMap<>();
        for (String header : headers) {
            String normalized = normalizeHeader(header);
            if (normalized.isEmpty()) {
                continue;
            }
            Match match = matchRule(normalized, rules, true);
            if (match == null) {
                match = matchRule(normalized, rules, false);
            }
            if (match == null) {
                continue;
            }
            Match incumbent = bestPerColumn.get(match.column());
            if (incumbent == null || match.isBetterThan(incumbent)) {
                bestPerColumn.put(match.column(), match);
                headerPerColumn.put(match.column(), normalized);
            }
        }
        Map<String, String> mapping = new LinkedHashMap<>();
        headerPerColumn.forEach((column, header) -> mapping.put(header, column));
        return mapping;
    }

    /** A rule match, scored so the most precise header wins a column: exact beats substring, longer alias beats shorter. */
    private record Match(String column, boolean exact, int aliasLength) {
        boolean isBetterThan(Match other) {
            if (exact != other.exact) {
                return exact;
            }
            return aliasLength > other.aliasLength;
        }
    }

    /**
     * Matches one normalised header. An exact alias match wins outright and accepts any alias
     * length; otherwise the longest alias contained in the header wins, and only aliases of
     * {@value #MIN_SUBSTRING_ALIAS_LENGTH}+ characters are eligible. An alias prefixed with
     * {@code =} is exact-only, which is how generic words like {@code =name} stay precise.
     */
    private static Match matchRule(
            String normalized, List<Map.Entry<String, List<String>>> rules, boolean exactOnly) {
        Match best = null;
        for (Map.Entry<String, List<String>> rule : rules) {
            for (String rawAlias : rule.getValue()) {
                boolean exactOnlyAlias = rawAlias.startsWith(EXACT_ALIAS_PREFIX);
                String alias = exactOnlyAlias
                        ? rawAlias.substring(EXACT_ALIAS_PREFIX.length())
                        : rawAlias;
                boolean matches = exactOnly
                        ? normalized.equals(alias)
                        : !exactOnlyAlias
                                && alias.length() >= MIN_SUBSTRING_ALIAS_LENGTH
                                && normalized.contains(alias);
                if (!matches) {
                    continue;
                }
                Match candidate = new Match(rule.getKey(), exactOnly, alias.length());
                if (best == null || candidate.isBetterThan(best)) {
                    best = candidate;
                }
            }
        }
        return best;
    }

    /**
     * Headers no rule claimed, in sheet order. Callers surface these so a column the coordinator
     * expected to be read is visibly ignored instead of silently dropped.
     */
    public static List<String> unmappedHeaders(Iterable<String> headers, Map<String, String> columnMap) {
        List<String> unmapped = new ArrayList<>();
        for (String header : headers) {
            String normalized = normalizeHeader(header);
            if (!normalized.isEmpty() && !columnMap.containsKey(normalized)) {
                unmapped.add(normalized);
            }
        }
        return unmapped;
    }

    /** Reads the value of a detected column from a row, or "" when absent. */
    public static String valueOf(Map<String, String> row, Map<String, String> columnMap, String column) {
        for (Map.Entry<String, String> entry : columnMap.entrySet()) {
            if (entry.getValue().equals(column)) {
                return row.getOrDefault(entry.getKey(), "");
            }
        }
        return "";
    }

    /** True when any header was mapped to the given canonical column. */
    public static boolean hasColumn(Map<String, String> columnMap, String column) {
        return columnMap.containsValue(column);
    }

    /** The normalised header mapped to a canonical column, or null when the sheet has none. */
    public static String headerOf(Map<String, String> columnMap, String column) {
        for (Map.Entry<String, String> entry : columnMap.entrySet()) {
            if (entry.getValue().equals(column)) {
                return entry.getKey();
            }
        }
        return null;
    }

    private static List<Map<String, String>> parseCsv(InputStream in) throws Exception {
        List<Map<String, String>> rows = new ArrayList<>();
        try (BufferedReader reader = new BufferedReader(new InputStreamReader(in, StandardCharsets.UTF_8))) {
            String headerLine = reader.readLine();
            while (headerLine != null && headerLine.trim().isEmpty()) {
                headerLine = reader.readLine();
            }
            if (headerLine == null) {
                return rows;
            }

            List<String> headers = parseCsvLine(headerLine);
            headers.replaceAll(SpreadsheetReader::normalizeHeader);

            String line;
            while ((line = reader.readLine()) != null) {
                if (line.trim().isEmpty()) {
                    continue;
                }
                List<String> values = parseCsvLine(line);
                Map<String, String> row = new HashMap<>();
                for (int i = 0; i < headers.size(); i++) {
                    row.put(headers.get(i), i < values.size() ? normalizeCell(values.get(i)) : "");
                }
                rows.add(row);
            }
        }
        return rows;
    }

    private static List<Map<String, String>> parseExcel(InputStream in) throws Exception {
        List<Map<String, String>> rows = new ArrayList<>();
        try (Workbook workbook = WorkbookFactory.create(in)) {
            Sheet sheet = workbook.getSheetAt(0);
            Row headerRow = sheet.getRow(0);
            if (headerRow == null) {
                return rows;
            }

            // Index-based iteration keeps header positions stable with blank leading cells.
            int columnCount = headerRow.getLastCellNum();
            List<String> headers = new ArrayList<>();
            for (int c = 0; c < columnCount; c++) {
                headers.add(normalizeHeader(getCellValueAsString(headerRow.getCell(c))));
            }

            DataFormatter formatter = new DataFormatter();
            for (int r = 1; r <= sheet.getLastRowNum(); r++) {
                Row row = sheet.getRow(r);
                if (row == null) {
                    continue;
                }
                Map<String, String> rowMap = new HashMap<>();
                boolean hasContent = false;
                for (int c = 0; c < headers.size(); c++) {
                    Cell cell = row.getCell(c);
                    String val = cell != null ? normalizeCell(formatter.formatCellValue(cell)) : "";
                    if (!val.isEmpty()) {
                        hasContent = true;
                    }
                    rowMap.put(headers.get(c), val);
                }
                if (hasContent) {
                    rows.add(rowMap);
                }
            }
        }
        return rows;
    }

    private static List<String> parseCsvLine(String line) {
        List<String> tokens = new ArrayList<>();
        StringBuilder sb = new StringBuilder();
        boolean inQuotes = false;

        for (int i = 0; i < line.length(); i++) {
            char c = line.charAt(i);
            if (c == '"') {
                inQuotes = !inQuotes;
            } else if (c == ',' && !inQuotes) {
                tokens.add(sb.toString().trim());
                sb.setLength(0);
            } else {
                sb.append(c);
            }
        }
        tokens.add(sb.toString().trim());
        return tokens;
    }

    private static String getCellValueAsString(Cell cell) {
        if (cell == null) {
            return "";
        }
        return normalizeCell(new DataFormatter().formatCellValue(cell));
    }
}
