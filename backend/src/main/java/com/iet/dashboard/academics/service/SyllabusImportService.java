package com.iet.dashboard.academics.service;

import com.iet.dashboard.academics.web.ParsedSyllabusDto;
import com.iet.dashboard.academics.web.SyllabusTopicDto;
import org.springframework.web.multipart.MultipartFile;

import java.util.List;

/**
 * Extracts units and topics from an uploaded course description so the daily-log topic
 * dropdown is populated without hand-typing a syllabus into the database.
 */
public interface SyllabusImportService {

    /** Extracts text (Apache Tika) and parses it. Nothing is persisted. */
    ParsedSyllabusDto parse(MultipartFile file);

    /** Parses already-extracted text — the seam used by tests and text uploads. */
    ParsedSyllabusDto parseText(String text, String sourceName);

    /** Replaces the course's syllabus with the (possibly edited) preview. */
    List<SyllabusTopicDto> apply(Long courseId, ParsedSyllabusDto parsed);
}
