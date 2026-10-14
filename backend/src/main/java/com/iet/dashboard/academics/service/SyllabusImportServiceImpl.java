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
            detectedMime = tika.detect(new ByteArrayInputStream(bytes), file.getOriginalFilename());
            if (!ALLOWED_MIME_TYPES.contains(detectedMime.toLowerCase())) {
                throw new ValidationException(
                        "Unsupported syllabus format: " + detectedMime + ". Upload a PDF, Word or plain-text file.");
            }
            text = tika.parseToString(new ByteArrayInputStream(bytes));
        } catch (ValidationException e) {
            throw e;
        } catch (Exception e) {
            throw new ValidationException("Could not extract text from the syllabus: " + e.getMessage());
        }

        log.info("Parsed syllabus '{}' ({} chars, {})", file.getOriginalFilename(), text.length(), detectedMime);
        return SyllabusTextParser.parse(text, file.getOriginalFilename());
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

        // Replace rather than append: re-importing a corrected document must not duplicate topics.
        syllabusTopicRepository.deleteByCourseId(courseId);

        List<SyllabusTopic> toSave = new ArrayList<>();
        int order = 1;
        for (ParsedSyllabusDto.Unit unit : parsed.units()) {
            if (unit.topics() == null) {
                continue;
            }
            for (ParsedSyllabusDto.Topic topic : unit.topics()) {
                if (topic.topicName() == null || topic.topicName().isBlank()) {
                    continue;
                }
                toSave.add(new SyllabusTopic(
                        course,
                        unit.unitNumber(),
                        unit.unitTitle() != null && !unit.unitTitle().isBlank()
                                ? unit.unitTitle()
                                : "Unit " + unit.unitNumber(),
                        topic.topicName().trim(),
                        topic.targetWeek() > 0 ? topic.targetWeek() : 1,
                        order++));
            }
        }

        if (toSave.isEmpty()) {
            throw new ValidationException("The parsed syllabus contains no topics to save");
        }

        syllabusTopicRepository.flush();
        return syllabusTopicRepository.saveAll(toSave).stream()
                .map(SyllabusTopicDto::fromEntity)
                .toList();
    }
}
