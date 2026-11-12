package com.iet.dashboard.academics.web;

import java.util.List;

/**
 * Result of parsing an uploaded course-description document. Returned as a **preview** —
 * nothing is persisted until the caller confirms, so a bad parse never overwrites a good
 * syllabus. The same shape is posted back to confirm (the coordinator or faculty member may
 * have edited unit titles or dropped noise before saving).
 */
public record ParsedSyllabusDto(
        String sourceFileName,
        int extractedCharacters,
        List<Unit> units,
        /** The course title as the document writes it ("Course Title and Code: X (CS -)" gives X), or null. */
        String documentTitle,
        /** The L-T-P the document states ("L-T-P: 3-0-2 (5 hours)" gives 3-0-2), or null. */
        String documentLtp,
        /** Every difference between the document and the allocation sheet's course, in words. */
        List<String> mismatches
) {
    public ParsedSyllabusDto(String sourceFileName, int extractedCharacters, List<Unit> units) {
        this(sourceFileName, extractedCharacters, units, null, null, List.of());
    }

    public ParsedSyllabusDto withMismatches(List<String> found) {
        return new ParsedSyllabusDto(sourceFileName, extractedCharacters, units, documentTitle, documentLtp, List.copyOf(found));
    }

    public record Unit(
            int unitNumber,
            String unitTitle,
            List<Topic> topics
    ) {
    }

    /** targetWeek is 0 unless a document states a week; nothing is spread or guessed. */
    public record Topic(
            String topicName,
            int targetWeek
    ) {
    }

    public int topicCount() {
        return units == null ? 0 : units.stream().mapToInt(unit -> unit.topics().size()).sum();
    }
}
