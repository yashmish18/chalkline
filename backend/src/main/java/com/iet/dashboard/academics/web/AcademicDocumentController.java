package com.iet.dashboard.academics.web;

import com.iet.dashboard.academics.domain.AcademicDocumentCategory;
import com.iet.dashboard.academics.service.AcademicDocumentService;
import com.iet.dashboard.academics.service.AcademicsService;
import com.iet.dashboard.directory.service.FacultyService;
import com.iet.dashboard.identity.service.UserService;
import com.iet.dashboard.identity.web.UserDto;
import com.iet.dashboard.shared.error.ForbiddenException;
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
    private final FacultyService facultyService;
    private final AcademicsService academicsService;

    public AcademicDocumentController(
            AcademicDocumentService documentService,
            UserService userService,
            FacultyService facultyService,
            AcademicsService academicsService
    ) {
        this.documentService = documentService;
        this.userService = userService;
        this.facultyService = facultyService;
        this.academicsService = academicsService;
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

        // Server-side ownership: faculty may only upload course documents for a course they
        // actually teach. Coordinators manage every course.
        if (courseId != null && "FACULTY".equals(currentUser.role())) {
            assertFacultyTeachesCourse(currentUser, courseId);
        }

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

    private void assertFacultyTeachesCourse(UserDto currentUser, Long courseId) {
        Long facultyId = facultyService.getFacultyByUserId(currentUser.id())
                .orElseThrow(() -> new ForbiddenException("Faculty profile not found for current user"))
                .id();
        boolean teachesCourse = academicsService.getTeachingAssignmentsForFaculty(facultyId).stream()
                .anyMatch(assignment -> assignment.offering() != null
                        && assignment.offering().course() != null
                        && assignment.offering().course().id().equals(courseId));
        if (!teachesCourse) {
            throw new ForbiddenException("You can only upload course documents for a course you teach");
        }
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
