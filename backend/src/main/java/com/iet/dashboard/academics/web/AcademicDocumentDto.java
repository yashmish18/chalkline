package com.iet.dashboard.academics.web;

import com.iet.dashboard.academics.domain.AcademicDocument;
import com.iet.dashboard.academics.domain.AcademicDocumentCategory;

import java.time.Instant;

public record AcademicDocumentDto(
        Long id,
        AcademicDocumentCategory category,
        String title,
        String description,
        String fileName,
        String fileType,
        Long fileSize,
        String storageKey,
        Long termId,
        Long courseId,
        Long uploadedByFacultyId,
        String uploadedByName,
        Instant createdAt
) {
    public static AcademicDocumentDto fromEntity(AcademicDocument doc) {
        return new AcademicDocumentDto(
                doc.getId(),
                doc.getCategory(),
                doc.getTitle(),
                doc.getDescription(),
                doc.getFileName(),
                doc.getFileType(),
                doc.getFileSize(),
                doc.getStorageKey(),
                doc.getTermId(),
                doc.getCourseId(),
                doc.getUploadedByFacultyId(),
                doc.getUploadedByName(),
                doc.getCreatedAt()
        );
    }
}
