package com.iet.dashboard.academics.repo;

import com.iet.dashboard.academics.domain.AcademicDocument;
import com.iet.dashboard.academics.domain.AcademicDocumentCategory;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface AcademicDocumentRepository extends JpaRepository<AcademicDocument, Long> {

    List<AcademicDocument> findByCategoryOrderByCreatedAtDesc(AcademicDocumentCategory category);

    List<AcademicDocument> findByCourseIdOrderByCreatedAtDesc(Long courseId);

    List<AcademicDocument> findByTermIdOrderByCreatedAtDesc(Long termId);

    Optional<AcademicDocument> findFirstByCategoryOrderByCreatedAtDesc(AcademicDocumentCategory category);

    Optional<AcademicDocument> findFirstByCategoryAndTermIdOrderByCreatedAtDesc(AcademicDocumentCategory category, Long termId);

    Optional<AcademicDocument> findFirstByCourseIdOrderByCreatedAtDesc(Long courseId);

    Optional<AcademicDocument> findByStorageKey(String storageKey);
}
