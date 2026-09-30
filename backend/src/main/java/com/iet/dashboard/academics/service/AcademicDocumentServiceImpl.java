package com.iet.dashboard.academics.service;

import com.iet.dashboard.academics.domain.AcademicDocument;
import com.iet.dashboard.academics.domain.AcademicDocumentCategory;
import com.iet.dashboard.academics.repo.AcademicDocumentRepository;
import com.iet.dashboard.academics.web.AcademicDocumentDto;
import com.iet.dashboard.shared.error.ResourceNotFoundException;
import com.iet.dashboard.shared.error.ValidationException;
import com.iet.dashboard.submissions.storage.FileStorage;
import org.apache.tika.Tika;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.core.io.Resource;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

@Service
@Transactional
public class AcademicDocumentServiceImpl implements AcademicDocumentService {

    private static final Logger log = LoggerFactory.getLogger(AcademicDocumentServiceImpl.class);

    private static final Set<String> ALLOWED_MIME_TYPES = Set.of(
            "application/pdf",
            "application/msword",
            "application/vnd.openxmlformats-officedocument.wordprocessingml.document",
            "text/plain",
            "application/rtf",
            "text/rtf"
    );

    private static final long MAX_FILE_SIZE = 25 * 1024 * 1024; // 25 MB

    private final AcademicDocumentRepository documentRepository;
    private final FileStorage fileStorage;
    private final Tika tika = new Tika();

    public AcademicDocumentServiceImpl(AcademicDocumentRepository documentRepository, FileStorage fileStorage) {
        this.documentRepository = documentRepository;
        this.fileStorage = fileStorage;
    }

    @Override
    public AcademicDocumentDto uploadDocument(
            MultipartFile file,
            AcademicDocumentCategory category,
            String title,
            String description,
            Long termId,
            Long courseId,
            Long facultyId,
            String uploaderName
    ) {
        if (file == null || file.isEmpty()) {
            throw new ValidationException("Cannot upload an empty file");
        }

        if (file.getSize() > MAX_FILE_SIZE) {
            throw new ValidationException("File size exceeds 25MB limit: " + (file.getSize() / (1024 * 1024)) + "MB");
        }

        if (title == null || title.isBlank()) {
            title = file.getOriginalFilename() != null ? file.getOriginalFilename() : "Academic Document";
        }

        byte[] fileBytes;
        try {
            fileBytes = file.getBytes();
        } catch (IOException e) {
            throw new ValidationException("Failed to read file bytes: " + e.getMessage());
        }

        // Sniff real MIME bytes using Apache Tika
        String detectedMime;
        try {
            detectedMime = tika.detect(new ByteArrayInputStream(fileBytes), file.getOriginalFilename());
        } catch (IOException e) {
            throw new ValidationException("Failed to inspect file MIME type: " + e.getMessage());
        }

        if (!ALLOWED_MIME_TYPES.contains(detectedMime.toLowerCase())) {
            throw new ValidationException("Disallowed file format (" + detectedMime + "). Only PDF (.pdf) and Word documents (.doc, .docx) are allowed.");
        }

        // Store file with opaque UUID key
        String storageKey;
        try {
            storageKey = fileStorage.store(new ByteArrayInputStream(fileBytes), file.getOriginalFilename());
        } catch (IOException e) {
            log.error("Failed to store academic document file: {}", e.getMessage(), e);
            throw new RuntimeException("Storage failure while saving document: " + e.getMessage(), e);
        }

        String safeOriginalName = file.getOriginalFilename() != null ? file.getOriginalFilename() : "document";

        AcademicDocument document = new AcademicDocument(
                category,
                title.trim(),
                description != null ? description.trim() : null,
                safeOriginalName,
                detectedMime,
                file.getSize(),
                storageKey,
                termId,
                courseId,
                facultyId,
                uploaderName != null ? uploaderName : "Academic Coordinator"
        );

        AcademicDocument saved = documentRepository.save(document);
        log.info("Uploaded academic document: ID={}, Category={}, File={}, Size={}",
                saved.getId(), saved.getCategory(), saved.getFileName(), saved.getFileSize());

        return AcademicDocumentDto.fromEntity(saved);
    }

    @Override
    @Transactional(readOnly = true)
    public List<AcademicDocumentDto> getDocuments(AcademicDocumentCategory category, Long courseId, Long termId) {
        if (category != null) {
            return documentRepository.findByCategoryOrderByCreatedAtDesc(category).stream()
                    .map(AcademicDocumentDto::fromEntity)
                    .collect(Collectors.toList());
        }
        if (courseId != null) {
            return documentRepository.findByCourseIdOrderByCreatedAtDesc(courseId).stream()
                    .map(AcademicDocumentDto::fromEntity)
                    .collect(Collectors.toList());
        }
        if (termId != null) {
            return documentRepository.findByTermIdOrderByCreatedAtDesc(termId).stream()
                    .map(AcademicDocumentDto::fromEntity)
                    .collect(Collectors.toList());
        }
        return documentRepository.findAll().stream()
                .map(AcademicDocumentDto::fromEntity)
                .collect(Collectors.toList());
    }

    @Override
    @Transactional(readOnly = true)
    public AcademicDocumentDto getLatestDocument(AcademicDocumentCategory category, Long termId) {
        if (termId != null) {
            return documentRepository.findFirstByCategoryAndTermIdOrderByCreatedAtDesc(category, termId)
                    .map(AcademicDocumentDto::fromEntity)
                    .orElse(null);
        }
        return documentRepository.findFirstByCategoryOrderByCreatedAtDesc(category)
                .map(AcademicDocumentDto::fromEntity)
                .orElse(null);
    }

    @Override
    @Transactional(readOnly = true)
    public AcademicDocumentDto getLatestSyllabusForCourse(Long courseId) {
        return documentRepository.findFirstByCourseIdOrderByCreatedAtDesc(courseId)
                .map(AcademicDocumentDto::fromEntity)
                .orElse(null);
    }

    @Override
    @Transactional(readOnly = true)
    public AcademicDocumentDto getDocumentById(Long documentId) {
        AcademicDocument doc = documentRepository.findById(documentId)
                .orElseThrow(() -> new ResourceNotFoundException("Academic document not found: " + documentId));
        return AcademicDocumentDto.fromEntity(doc);
    }

    @Override
    @Transactional(readOnly = true)
    public Resource downloadDocument(Long documentId) {
        AcademicDocument doc = documentRepository.findById(documentId)
                .orElseThrow(() -> new ResourceNotFoundException("Academic document not found: " + documentId));
        try {
            return fileStorage.loadAsResource(doc.getStorageKey());
        } catch (IOException e) {
            log.error("Failed to load file resource for key {}: {}", doc.getStorageKey(), e.getMessage());
            throw new ResourceNotFoundException("Physical file not found for document: " + doc.getFileName());
        }
    }

    @Override
    public void deleteDocument(Long documentId) {
        AcademicDocument doc = documentRepository.findById(documentId)
                .orElseThrow(() -> new ResourceNotFoundException("Academic document not found: " + documentId));
        try {
            fileStorage.delete(doc.getStorageKey());
        } catch (IOException e) {
            log.warn("Could not delete physical file {}: {}", doc.getStorageKey(), e.getMessage());
        }
        documentRepository.delete(doc);
        log.info("Deleted academic document: ID={}, Title={}", doc.getId(), doc.getTitle());
    }
}
