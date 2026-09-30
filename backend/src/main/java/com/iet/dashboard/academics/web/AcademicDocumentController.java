package com.iet.dashboard.academics.web;

import com.iet.dashboard.academics.domain.AcademicDocumentCategory;
import com.iet.dashboard.academics.service.AcademicDocumentService;
import com.iet.dashboard.identity.service.UserService;
import com.iet.dashboard.identity.web.UserDto;
import org.springframework.core.io.Resource;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.util.List;

@RestController
@RequestMapping("/api/academic-documents")
public class AcademicDocumentController {

    private final AcademicDocumentService documentService;
    private final UserService userService;

    public AcademicDocumentController(AcademicDocumentService documentService, UserService userService) {
        this.documentService = documentService;
        this.userService = userService;
    }

    @PostMapping(value = "/upload", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    @PreAuthorize("hasAnyRole('COORDINATOR', 'FACULTY')")
    public ResponseEntity<AcademicDocumentDto> uploadDocument(
            @RequestParam("file") MultipartFile file,
            @RequestParam("category") AcademicDocumentCategory category,
            @RequestParam(value = "title", required = false) String title,
            @RequestParam(value = "description", required = false) String description,
            @RequestParam(value = "termId", required = false) Long termId,
            @RequestParam(value = "courseId", required = false) Long courseId
    ) {
        UserDto currentUser = userService.getCurrentUser();
        AcademicDocumentDto result = documentService.uploadDocument(
                file,
                category,
                title,
                description,
                termId,
                courseId,
                currentUser.id(),
                currentUser.name()
        );
        return ResponseEntity.status(HttpStatus.CREATED).body(result);
    }

    @GetMapping
    public ResponseEntity<List<AcademicDocumentDto>> getDocuments(
            @RequestParam(value = "category", required = false) AcademicDocumentCategory category,
            @RequestParam(value = "courseId", required = false) Long courseId,
            @RequestParam(value = "termId", required = false) Long termId
    ) {
        return ResponseEntity.ok(documentService.getDocuments(category, courseId, termId));
    }

    @GetMapping("/latest")
    public ResponseEntity<AcademicDocumentDto> getLatestDocument(
            @RequestParam("category") AcademicDocumentCategory category,
            @RequestParam(value = "termId", required = false) Long termId
    ) {
        AcademicDocumentDto doc = documentService.getLatestDocument(category, termId);
        if (doc == null) {
            return ResponseEntity.noContent().build();
        }
        return ResponseEntity.ok(doc);
    }

    @GetMapping("/course/{courseId}/latest")
    public ResponseEntity<AcademicDocumentDto> getLatestSyllabusForCourse(@PathVariable Long courseId) {
        AcademicDocumentDto doc = documentService.getLatestSyllabusForCourse(courseId);
        if (doc == null) {
            return ResponseEntity.noContent().build();
        }
        return ResponseEntity.ok(doc);
    }

    @GetMapping("/{id}/download")
    public ResponseEntity<Resource> downloadDocument(@PathVariable Long id) {
        AcademicDocumentDto doc = documentService.getDocumentById(id);
        Resource resource = documentService.downloadDocument(id);

        String encodedName = URLEncoder.encode(doc.fileName(), StandardCharsets.UTF_8).replace("+", "%20");

        return ResponseEntity.ok()
                .contentType(MediaType.parseMediaType(doc.fileType()))
                .header(HttpHeaders.CONTENT_DISPOSITION, "inline; filename=\"" + encodedName + "\"; filename*=UTF-8''" + encodedName)
                .contentLength(doc.fileSize())
                .body(resource);
    }

    @DeleteMapping("/{id}")
    @PreAuthorize("hasRole('COORDINATOR')")
    public ResponseEntity<Void> deleteDocument(@PathVariable Long id) {
        documentService.deleteDocument(id);
        return ResponseEntity.noContent().build();
    }
}
