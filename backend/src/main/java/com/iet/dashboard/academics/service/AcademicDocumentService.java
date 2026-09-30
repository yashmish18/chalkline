package com.iet.dashboard.academics.service;

import com.iet.dashboard.academics.domain.AcademicDocumentCategory;
import com.iet.dashboard.academics.web.AcademicDocumentDto;
import org.springframework.core.io.Resource;
import org.springframework.web.multipart.MultipartFile;

import java.util.List;

public interface AcademicDocumentService {

    AcademicDocumentDto uploadDocument(
            MultipartFile file,
            AcademicDocumentCategory category,
            String title,
            String description,
            Long termId,
            Long courseId,
            Long facultyId,
            String uploaderName
    );

    List<AcademicDocumentDto> getDocuments(AcademicDocumentCategory category, Long courseId, Long termId);

    AcademicDocumentDto getLatestDocument(AcademicDocumentCategory category, Long termId);

    AcademicDocumentDto getLatestSyllabusForCourse(Long courseId);

    AcademicDocumentDto getDocumentById(Long documentId);

    Resource downloadDocument(Long documentId);

    void deleteDocument(Long documentId);
}
