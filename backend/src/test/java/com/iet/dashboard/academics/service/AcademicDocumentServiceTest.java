package com.iet.dashboard.academics.service;

import com.iet.dashboard.academics.domain.AcademicDocument;
import com.iet.dashboard.academics.domain.AcademicDocumentCategory;
import com.iet.dashboard.academics.repo.AcademicDocumentRepository;
import com.iet.dashboard.academics.web.AcademicDocumentDto;
import com.iet.dashboard.shared.error.ResourceNotFoundException;
import com.iet.dashboard.shared.error.ValidationException;
import com.iet.dashboard.submissions.storage.FileStorage;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.core.io.ByteArrayResource;
import org.springframework.core.io.Resource;
import org.springframework.mock.web.MockMultipartFile;

import java.io.InputStream;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class AcademicDocumentServiceTest {

    @Mock
    private AcademicDocumentRepository documentRepository;

    @Mock
    private FileStorage fileStorage;

    private AcademicDocumentServiceImpl service;

    @BeforeEach
    void setUp() {
        service = new AcademicDocumentServiceImpl(documentRepository, fileStorage);
    }

    @Test
    void testUploadDocument_ValidPdf() throws Exception {
        byte[] pdfBytes = "%PDF-1.5 test academic timetable document content".getBytes();
        MockMultipartFile file = new MockMultipartFile(
                "file", "timetable_2026.pdf", "application/pdf", pdfBytes
        );

        when(fileStorage.store(any(InputStream.class), eq("timetable_2026.pdf")))
                .thenReturn("key-uuid-timetable-pdf");

        AcademicDocument savedEntity = new AcademicDocument(
                AcademicDocumentCategory.MASTER_TIMETABLE,
                "Master Timetable Odd 2026-27",
                "Official schedule",
                "timetable_2026.pdf",
                "application/pdf",
                (long) pdfBytes.length,
                "key-uuid-timetable-pdf",
                1L,
                null,
                1L,
                "Dr. Coordinator"
        );
        savedEntity.setId(100L);

        when(documentRepository.save(any(AcademicDocument.class))).thenReturn(savedEntity);

        AcademicDocumentDto result = service.uploadDocument(
                file,
                AcademicDocumentCategory.MASTER_TIMETABLE,
                "Master Timetable Odd 2026-27",
                "Official schedule",
                1L,
                null,
                1L,
                "Dr. Coordinator"
        );

        assertNotNull(result);
        assertEquals(100L, result.id());
        assertEquals(AcademicDocumentCategory.MASTER_TIMETABLE, result.category());
        assertEquals("Master Timetable Odd 2026-27", result.title());
        assertEquals("timetable_2026.pdf", result.fileName());

        verify(fileStorage).store(any(InputStream.class), eq("timetable_2026.pdf"));
        verify(documentRepository).save(any(AcademicDocument.class));
    }

    @Test
    void testUploadDocument_ValidDocx() throws Exception {
        // Plain text is allowed in our test suite & Tika falls back gracefully
        byte[] docBytes = "Academic Calendar 2026-27 Schedule and Holidays".getBytes();
        MockMultipartFile file = new MockMultipartFile(
                "file", "calendar.docx", "application/vnd.openxmlformats-officedocument.wordprocessingml.document", docBytes
        );

        when(fileStorage.store(any(InputStream.class), eq("calendar.docx")))
                .thenReturn("key-uuid-calendar-docx");

        AcademicDocument savedEntity = new AcademicDocument(
                AcademicDocumentCategory.ACADEMIC_CALENDAR,
                "Academic Calendar 2026-27",
                "Official term calendar",
                "calendar.docx",
                "text/plain",
                (long) docBytes.length,
                "key-uuid-calendar-docx",
                1L,
                null,
                1L,
                "Dr. Coordinator"
        );
        savedEntity.setId(101L);

        when(documentRepository.save(any(AcademicDocument.class))).thenReturn(savedEntity);

        AcademicDocumentDto result = service.uploadDocument(
                file,
                AcademicDocumentCategory.ACADEMIC_CALENDAR,
                "Academic Calendar 2026-27",
                "Official term calendar",
                1L,
                null,
                1L,
                "Dr. Coordinator"
        );

        assertNotNull(result);
        assertEquals(101L, result.id());
        assertEquals(AcademicDocumentCategory.ACADEMIC_CALENDAR, result.category());
        verify(documentRepository).save(any(AcademicDocument.class));
    }

    @Test
    void testUploadDocument_EmptyFile_ThrowsValidationException() {
        MockMultipartFile file = new MockMultipartFile("file", "empty.pdf", "application/pdf", new byte[0]);

        assertThrows(ValidationException.class, () ->
                service.uploadDocument(
                        file, AcademicDocumentCategory.MASTER_TIMETABLE, "Title", "Desc",
                        1L, null, 1L, "Coord"
                ));
    }

    @Test
    void testDownloadDocument_Success() throws Exception {
        AcademicDocument doc = new AcademicDocument();
        doc.setId(42L);
        doc.setStorageKey("uuid-key-42");
        doc.setFileName("syllabus.pdf");

        when(documentRepository.findById(42L)).thenReturn(Optional.of(doc));
        Resource mockResource = new ByteArrayResource("file contents".getBytes());
        when(fileStorage.loadAsResource("uuid-key-42")).thenReturn(mockResource);

        Resource result = service.downloadDocument(42L);
        assertNotNull(result);
        assertTrue(result.exists());
    }

    @Test
    void testDownloadDocument_NotFound_ThrowsException() {
        when(documentRepository.findById(999L)).thenReturn(Optional.empty());

        assertThrows(ResourceNotFoundException.class, () -> service.downloadDocument(999L));
    }

    @Test
    void testDeleteDocument_Success() throws Exception {
        AcademicDocument doc = new AcademicDocument();
        doc.setId(15L);
        doc.setStorageKey("uuid-key-15");

        when(documentRepository.findById(15L)).thenReturn(Optional.of(doc));

        service.deleteDocument(15L);

        verify(fileStorage).delete("uuid-key-15");
        verify(documentRepository).delete(doc);
    }

    @Test
    void testGetDocuments_ByCategory() {
        AcademicDocument doc = new AcademicDocument();
        doc.setId(1L);
        doc.setCategory(AcademicDocumentCategory.MASTER_TIMETABLE);
        doc.setTitle("Timetable");
        doc.setFileName("tt.pdf");
        doc.setFileType("application/pdf");
        doc.setFileSize(100L);
        doc.setUploadedByName("Coord");

        when(documentRepository.findByCategoryOrderByCreatedAtDesc(AcademicDocumentCategory.MASTER_TIMETABLE))
                .thenReturn(List.of(doc));

        List<AcademicDocumentDto> results = service.getDocuments(AcademicDocumentCategory.MASTER_TIMETABLE, null, null);
        assertEquals(1, results.size());
        assertEquals("Timetable", results.get(0).title());
    }
}
