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
        List<Unit> units
) {
    public record Unit(
            int unitNumber,
            String unitTitle,
            List<Topic> topics
    ) {
    }

    public record Topic(
            String topicName,
            int targetWeek
    ) {
    }

    public int topicCount() {
        return units == null ? 0 : units.stream().mapToInt(unit -> unit.topics().size()).sum();
    }
}
