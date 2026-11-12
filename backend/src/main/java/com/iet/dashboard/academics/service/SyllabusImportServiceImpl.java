package com.iet.dashboard.academics.service;

import com.iet.dashboard.academics.domain.Course;
import com.iet.dashboard.academics.domain.SyllabusTopic;
import com.iet.dashboard.academics.repo.CourseRepository;
import com.iet.dashboard.academics.repo.SyllabusTopicRepository;
import com.iet.dashboard.academics.web.ParsedSyllabusDto;
import com.iet.dashboard.academics.web.SyllabusTopicDto;
import com.iet.dashboard.shared.error.ResourceNotFoundException;
import com.iet.dashboard.shared.error.ValidationException;
import org.apache.tika.Tika;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;

@Service
@Transactional
public class SyllabusImportServiceImpl implements SyllabusImportService {

    private static final Logger log = LoggerFactory.getLogger(SyllabusImportServiceImpl.class);

    private static final long MAX_FILE_SIZE = 10 * 1024 * 1024; // 10MB is ample for a syllabus

    private static final Set<String> ALLOWED_MIME_TYPES = Set.of(
            "application/pdf",
            "application/msword",
            "application/vnd.openxmlformats-officedocument.wordprocessingml.document",
            "text/plain"
    );

    private final CourseRepository courseRepository;
    private final SyllabusTopicRepository syllabusTopicRepository;
    private final Tika tika = new Tika();

    public SyllabusImportServiceImpl(CourseRepository courseRepository,
                                     SyllabusTopicRepository syllabusTopicRepository) {
        this.courseRepository = courseRepository;
        this.syllabusTopicRepository = syllabusTopicRepository;
    }

    @Override
    @Transactional(readOnly = true)
    public ParsedSyllabusDto parse(MultipartFile file) {
        if (file == null || file.isEmpty()) {
            throw new ValidationException("Uploaded syllabus file cannot be empty");
        }
        if (file.getSize() > MAX_FILE_SIZE) {
            throw new ValidationException("Syllabus file exceeds the 10MB limit");
        }

        byte[] bytes;
        try {
            bytes = file.getBytes();
        } catch (IOException e) {
            throw new ValidationException("Failed to read the uploaded syllabus: " + e.getMessage());
        }

        String detectedMime;
        String text;
        try {
            detectedMime = tika.detect(new ByteArrayInputStream(bytes), file.getOriginalFilename()).toLowerCase();
            if (!ALLOWED_MIME_TYPES.contains(detectedMime)) {
                throw new ValidationException(
                        "Unsupported syllabus format: " + detectedMime + ". Upload a PDF, Word (.docx) or plain-text file.");
            }
            text = extractText(bytes, detectedMime);
        } catch (ValidationException e) {
            throw e;
        } catch (Exception e) {
            throw new ValidationException("Could not extract text from the syllabus: " + e.getMessage());
        }
        if (text.isBlank()) {
            throw new ValidationException("No text could be read from \"" + file.getOriginalFilename()
                    + "\". If it is a scanned PDF, upload the original Word or PDF file with selectable text.");
        }

        log.info("Parsed syllabus '{}' ({} chars, {})", file.getOriginalFilename(), text.length(), detectedMime);
        return SyllabusTextParser.parse(text, file.getOriginalFilename());
    }

    /**
     * The file's text. tika-core only detects types; it has no parsers, so its parseToString returned
     * "" for every syllabus. PDFBox and POI (.docx, and scratchpad for Word 97 .doc) do the reading.
     */
    private static String extractText(byte[] bytes, String mime) throws IOException {
        return switch (mime) {
            case "application/pdf" -> {
                try (var pdf = org.apache.pdfbox.Loader.loadPDF(bytes)) {
                    yield new org.apache.pdfbox.text.PDFTextStripper().getText(pdf);
                }
            }
            case "application/vnd.openxmlformats-officedocument.wordprocessingml.document" -> {
                try (var docx = new org.apache.poi.xwpf.usermodel.XWPFDocument(new ByteArrayInputStream(bytes));
                     var extractor = new org.apache.poi.xwpf.extractor.XWPFWordExtractor(docx)) {
                    yield extractor.getText();
                }
            }
            case "application/msword" -> {
                try (var doc = new org.apache.poi.hwpf.HWPFDocument(new ByteArrayInputStream(bytes));
                     var extractor = new org.apache.poi.hwpf.extractor.WordExtractor(doc)) {
                    yield String.join("\n", extractor.getParagraphText());
                }
            }
            default -> new String(bytes, java.nio.charset.StandardCharsets.UTF_8);
        };
    }

    @Override
    @Transactional(readOnly = true)
    public ParsedSyllabusDto parse(MultipartFile file, Long courseId) {
        ParsedSyllabusDto parsed = parse(file);
        Course course = courseRepository.findById(courseId)
                .orElseThrow(() -> new ResourceNotFoundException("Course not found: " + courseId));
        List<String> mismatches = new ArrayList<>();
        if (parsed.documentLtp() != null && course.getLtp() != null && !course.getLtp().isBlank()
                && !parsed.documentLtp().equals(squash(course.getLtp()))) {
            mismatches.add("The document's L-T-P is " + parsed.documentLtp() + ", but the allocation sheet has "
                    + course.getLtp() + " for " + course.getCode() + ". Correct whichever is wrong.");
        }
        if (parsed.documentTitle() != null && !squash(parsed.documentTitle()).equals(squash(course.getName()))) {
            mismatches.add("The document calls the course \"" + parsed.documentTitle() + "\", but the allocation sheet calls "
                    + course.getCode() + " \"" + course.getName() + "\".");
        }
        return parsed.withMismatches(mismatches);
    }

    /** Same letters, whitespace runs collapsed: "3 - 0 - 2" and "3-0-2" agree, "Cloud Based" and "Cloud-Based" do not. */
    private static String squash(String value) {
        return value == null ? "" : value.trim().replaceAll("\\s*-\\s*", "-").replaceAll("\\s+", " ");
    }

    @Override
    @Transactional(readOnly = true)
    public ParsedSyllabusDto parseText(String text, String sourceName) {
        return SyllabusTextParser.parse(text, sourceName);
    }

    @Override
    public List<SyllabusTopicDto> apply(Long courseId, ParsedSyllabusDto parsed) {
        Course course = courseRepository.findById(courseId)
                .orElseThrow(() -> new ResourceNotFoundException("Course not found: " + courseId));

        if (parsed == null || parsed.units() == null || parsed.units().isEmpty() || parsed.topicCount() == 0) {
            throw new ValidationException("The parsed syllabus contains no topics to save");
        }

        // Reconcile rather than delete-and-insert: session_topic_log cascades on delete, so replacing
        // the rows would silently erase which topics every logged class covered.
        record Row(int unit, String unitTitle, String name, int week) { }
        List<Row> rows = new ArrayList<>();
        for (ParsedSyllabusDto.Unit unit : parsed.units()) {
            if (unit.topics() == null) {
                continue;
            }
            String unitTitle = unit.unitTitle() != null && !unit.unitTitle().isBlank() ? unit.unitTitle().trim() : "Unit " + unit.unitNumber();
            for (ParsedSyllabusDto.Topic topic : unit.topics()) {
                if (topic.topicName() != null && !topic.topicName().isBlank()) {
                    rows.add(new Row(unit.unitNumber(), unitTitle, topic.topicName().trim(), Math.max(0, topic.targetWeek())));
                }
            }
        }
        if (rows.isEmpty()) {
            throw new ValidationException("The parsed syllabus contains no topics to save");
        }

        List<SyllabusTopic> existing = syllabusTopicRepository.findByCourseIdOrderByLectureOrderAsc(courseId);
        java.util.Map<Integer, List<SyllabusTopic>> oldByUnit = new java.util.HashMap<>();
        existing.forEach(t -> oldByUnit.computeIfAbsent(t.getUnitNumber(), k -> new ArrayList<>()).add(t));
        java.util.Map<Integer, Long> newCountByUnit = rows.stream()
                .collect(java.util.stream.Collectors.groupingBy(Row::unit, java.util.stream.Collectors.counting()));
        java.util.Set<SyllabusTopic> unmatched = java.util.Collections.newSetFromMap(new java.util.IdentityHashMap<>());
        unmatched.addAll(existing);

        SyllabusTopic[] match = new SyllabusTopic[rows.size()];
        for (int i = 0; i < rows.size(); i++) {      // 1. same unit, same words
            Row row = rows.get(i);
            for (SyllabusTopic old : oldByUnit.getOrDefault(row.unit(), List.of())) {
                if (unmatched.contains(old) && old.getTopicName().trim().equalsIgnoreCase(row.name())) {
                    match[i] = old;
                    unmatched.remove(old);
                    break;
                }
            }
        }
        java.util.Map<Integer, Integer> placeInUnit = new java.util.HashMap<>();
        for (int i = 0; i < rows.size(); i++) {      // 2. a unit of unchanged size: same place = same topic, edited
            Row row = rows.get(i);
            int place = placeInUnit.merge(row.unit(), 1, Integer::sum) - 1;
            List<SyllabusTopic> olds = oldByUnit.getOrDefault(row.unit(), List.of());
            if (match[i] == null && olds.size() == newCountByUnit.get(row.unit()) && unmatched.contains(olds.get(place))) {
                match[i] = olds.get(place);
                unmatched.remove(olds.get(place));
            }
        }

        java.util.Set<Long> covered = syllabusTopicRepository.findCoveredTopicIds(courseId);
        List<String> wouldLose = unmatched.stream().filter(t -> covered.contains(t.getId()))
                .map(t -> "Unit " + t.getUnitNumber() + ": \"" + t.getTopicName() + "\"").toList();
        if (!wouldLose.isEmpty()) {
            throw new ValidationException("Classes are already logged against " + String.join(", ", wouldLose)
                    + ", which the new document no longer has. Add them back in the preview (or rename the new topic"
                    + " to match) and save again; nothing was changed.");
        }

        if (!unmatched.isEmpty()) {
            syllabusTopicRepository.deleteAll(new ArrayList<>(unmatched));
        }
        // lecture_order is unique per course: park the kept rows on negative numbers before renumbering.
        List<SyllabusTopic> kept = java.util.Arrays.stream(match).filter(java.util.Objects::nonNull).toList();
        kept.forEach(t -> t.setLectureOrder(-t.getLectureOrder()));
        if (!kept.isEmpty()) {
            syllabusTopicRepository.saveAll(kept);
            syllabusTopicRepository.flush();
        }

        List<SyllabusTopic> toSave = new ArrayList<>();
        for (int i = 0; i < rows.size(); i++) {
            Row row = rows.get(i);
            SyllabusTopic t = match[i] != null ? match[i]
                    : new SyllabusTopic(course, row.unit(), row.unitTitle(), row.name(), row.week(), i + 1);
            t.setUnitNumber(row.unit());
            t.setUnitTitle(row.unitTitle());
            t.setTopicName(row.name());
            t.setTargetWeek(row.week());
            t.setLectureOrder(i + 1);
            toSave.add(t);
        }

        syllabusTopicRepository.flush();
        return syllabusTopicRepository.saveAll(toSave).stream()
                .map(SyllabusTopicDto::fromEntity)
                .toList();
    }
}
