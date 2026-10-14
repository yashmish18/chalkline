package com.iet.dashboard.academics.web;

import com.iet.dashboard.academics.service.AcademicsService;
import com.iet.dashboard.academics.service.PacingCalculationService;
import com.iet.dashboard.directory.service.FacultyService;
import com.iet.dashboard.directory.web.FacultyDto;
import com.iet.dashboard.identity.service.UserService;
import com.iet.dashboard.identity.web.UserDto;
import com.iet.dashboard.shared.error.ForbiddenException;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api")
public class AcademicsController {

    private final AcademicsService academicsService;
    private final PacingCalculationService pacingService;
    private final UserService userService;
    private final FacultyService facultyService;
    private final com.iet.dashboard.academics.service.SyllabusImportService syllabusImportService;
    private final com.iet.dashboard.academics.service.AllocationImportService allocationImportService;
    private final com.iet.dashboard.academics.service.TimetableImportService timetableImportService;

    public AcademicsController(
            AcademicsService academicsService,
            PacingCalculationService pacingService,
            UserService userService,
            FacultyService facultyService,
            com.iet.dashboard.academics.service.SyllabusImportService syllabusImportService,
            com.iet.dashboard.academics.service.AllocationImportService allocationImportService,
            com.iet.dashboard.academics.service.TimetableImportService timetableImportService
    ) {
        this.academicsService = academicsService;
        this.pacingService = pacingService;
        this.userService = userService;
        this.facultyService = facultyService;
        this.syllabusImportService = syllabusImportService;
        this.allocationImportService = allocationImportService;
        this.timetableImportService = timetableImportService;
    }

    @GetMapping("/courses")
    public ResponseEntity<List<CourseDto>> getCourses() {
        return ResponseEntity.ok(academicsService.getAllCourses());
    }

    @PostMapping("/courses")
    @PreAuthorize("hasRole('COORDINATOR')")
    public ResponseEntity<CourseDto> createCourse(@Valid @RequestBody CreateCourseRequest request) {
        return ResponseEntity.status(HttpStatus.CREATED).body(academicsService.createCourse(request));
    }

    @DeleteMapping("/courses/{id}")
    @PreAuthorize("hasRole('COORDINATOR')")
    public ResponseEntity<Void> deleteCourse(@PathVariable Long id) {
        academicsService.deleteCourse(id);
        return ResponseEntity.noContent().build();
    }

    @GetMapping("/offerings")
    public ResponseEntity<List<CourseOfferingDto>> getOfferings(
            @RequestParam(required = false) Integer semester,
            @RequestParam(required = false) Long departmentId) {
        return ResponseEntity.ok(academicsService.getAllOfferings(semester, departmentId));
    }

    @PostMapping("/offerings")
    @PreAuthorize("hasRole('COORDINATOR')")
    public ResponseEntity<CourseOfferingDto> createOffering(@Valid @RequestBody CreateOfferingRequest request) {
        return ResponseEntity.status(HttpStatus.CREATED).body(academicsService.createOffering(request));
    }

    /** Opens several sections of one course at once (auto-lettered A, B, C…). */
    @PostMapping("/offerings/bulk")
    @PreAuthorize("hasRole('COORDINATOR')")
    public ResponseEntity<List<CourseOfferingDto>> createOfferingsBulk(@Valid @RequestBody CreateOfferingsBulkRequest request) {
        return ResponseEntity.status(HttpStatus.CREATED).body(academicsService.createOfferingsBulk(request));
    }

    /** The one import: teaching allocations, one row per (faculty, course, section, component, batch). */
    @PostMapping(value = "/allocations/import", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    @PreAuthorize("hasRole('COORDINATOR')")
    public ResponseEntity<AllocationImportResultDto> importAllocations(
            @RequestParam("file") org.springframework.web.multipart.MultipartFile file) {
        return ResponseEntity.ok(allocationImportService.importAllocations(file));
    }

    @GetMapping("/allocations/import-template")
    public ResponseEntity<byte[]> allocationImportTemplate() {
        return ResponseEntity.ok()
                .header(org.springframework.http.HttpHeaders.CONTENT_DISPOSITION,
                        "attachment; filename=\"allocations_template.csv\"")
                .contentType(MediaType.parseMediaType("text/csv"))
                .body(allocationImportService.templateCsv());
    }

    @GetMapping("/teaching-assignments")
    public ResponseEntity<List<TeachingAssignmentDto>> getAllAssignments(
            @RequestParam(required = false) Long departmentId) {
        return ResponseEntity.ok(academicsService.getAllTeachingAssignments(departmentId));
    }

    @GetMapping("/faculty/{facultyId}/teaching-assignments")
    public ResponseEntity<List<TeachingAssignmentDto>> getAssignmentsForFaculty(@PathVariable Long facultyId) {
        return ResponseEntity.ok(academicsService.getTeachingAssignmentsForFaculty(facultyId));
    }

    @GetMapping("/teaching-assignments/faculty-load")
    public ResponseEntity<List<FacultyLtpLoadDto>> getFacultyLtpLoad() {
        return ResponseEntity.ok(academicsService.getFacultyLtpLoad());
    }

    /**
     * Parses an uploaded course description into units/topics and returns a preview.
     * Faculty may only do this for a course they actually teach. Nothing is saved yet.
     */
    @PostMapping(value = "/courses/{courseId}/syllabus/preview", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public ResponseEntity<ParsedSyllabusDto> previewSyllabusImport(
            @PathVariable Long courseId,
            @RequestParam("file") org.springframework.web.multipart.MultipartFile file) {
        assertCanManageSyllabus(courseId);
        return ResponseEntity.ok(syllabusImportService.parse(file));
    }

    /** Saves the reviewed preview, replacing the course's existing syllabus topics. */
    @PostMapping("/courses/{courseId}/syllabus/confirm")
    public ResponseEntity<List<SyllabusTopicDto>> confirmSyllabusImport(
            @PathVariable Long courseId,
            @RequestBody ParsedSyllabusDto parsed) {
        assertCanManageSyllabus(courseId);
        return ResponseEntity.status(HttpStatus.CREATED).body(syllabusImportService.apply(courseId, parsed));
    }

    private void assertCanManageSyllabus(Long courseId) {
        UserDto currentUser = userService.getCurrentUser();
        if ("COORDINATOR".equals(currentUser.role())) {
            return;
        }

        FacultyDto faculty = facultyService.getFacultyByUserId(currentUser.id())
                .orElseThrow(() -> new ForbiddenException("Faculty profile not found for current user"));

        boolean teachesCourse = academicsService.getTeachingAssignmentsForFaculty(faculty.id()).stream()
                .anyMatch(assignment -> assignment.offering().course().id().equals(courseId));

        if (!teachesCourse) {
            throw new ForbiddenException("You can only import the syllabus for a course you teach");
        }
    }

    @PostMapping("/teaching-assignments")
    @PreAuthorize("hasRole('COORDINATOR')")
    public ResponseEntity<TeachingAssignmentDto> createAssignment(@Valid @RequestBody CreateTeachingAssignmentRequest request) {
        return ResponseEntity.status(HttpStatus.CREATED).body(academicsService.createTeachingAssignment(request));
    }

    @DeleteMapping("/teaching-assignments/{id}")
    @PreAuthorize("hasRole('COORDINATOR')")
    public ResponseEntity<Void> deleteAssignment(@PathVariable Long id) {
        academicsService.deleteTeachingAssignment(id);
        return ResponseEntity.noContent().build();
    }

    @DeleteMapping("/teaching-assignments")
    @PreAuthorize("hasRole('COORDINATOR')")
    public ResponseEntity<Void> deleteTeachingAssignments(@RequestParam(required = false) Integer semester) {
        academicsService.deleteTeachingAssignments(semester);
        return ResponseEntity.noContent().build();
    }


    @GetMapping("/terms")
    public ResponseEntity<List<AcademicTermDto>> getTerms() {
        return ResponseEntity.ok(academicsService.getAllTerms());
    }

    @GetMapping("/terms/active")
    public ResponseEntity<AcademicTermDto> getActiveTerm() {
        return ResponseEntity.ok(academicsService.getActiveTerm());
    }

    @PutMapping("/terms/active")
    @PreAuthorize("hasRole('COORDINATOR')")
    public ResponseEntity<AcademicTermDto> updateActiveTerm(@jakarta.validation.Valid @RequestBody UpdateTermRequest request) {
        return ResponseEntity.ok(academicsService.updateActiveTerm(request));
    }

    @PostMapping("/terms/{id}/activate")
    @PreAuthorize("hasRole('COORDINATOR')")
    public ResponseEntity<AcademicTermDto> activateTerm(@PathVariable Long id) {
        return ResponseEntity.ok(academicsService.setActiveTerm(id));
    }

    @GetMapping("/courses/{courseId}/syllabus")
    public ResponseEntity<List<SyllabusTopicDto>> getCourseSyllabus(@PathVariable Long courseId) {
        return ResponseEntity.ok(academicsService.getSyllabusForCourse(courseId));
    }

    @GetMapping("/academics/my-courses")
    @PreAuthorize("hasAnyRole('FACULTY', 'COORDINATOR')")
    public ResponseEntity<List<CourseProgressDto>> getMyCourses(
            @RequestParam(value = "facultyId", required = false) Long queryFacultyId
    ) {
        UserDto currentUser = userService.getCurrentUser();
        Long targetFacultyId;
        if ("COORDINATOR".equals(currentUser.role()) && queryFacultyId != null) {
            targetFacultyId = queryFacultyId;
        } else {
            targetFacultyId = resolveFacultyId(currentUser);
        }
        return ResponseEntity.ok(pacingService.getMyCoursesProgress(targetFacultyId));
    }


    @GetMapping("/academics/timetable")
    @PreAuthorize("hasAnyRole('FACULTY', 'COORDINATOR')")
    public ResponseEntity<List<TimetableSlotDto>> getTimetable(
            @RequestParam(value = "facultyId", required = false) Long queryFacultyId
    ) {
        UserDto currentUser = userService.getCurrentUser();
        Long targetFacultyId;
        if ("COORDINATOR".equals(currentUser.role()) && queryFacultyId != null) {
            targetFacultyId = queryFacultyId;
        } else {
            targetFacultyId = resolveFacultyId(currentUser);
        }
        return ResponseEntity.ok(academicsService.getTimetableForFaculty(targetFacultyId));
    }

    @DeleteMapping("/academics/timetable/{id}")
    @PreAuthorize("hasAnyRole('COORDINATOR', 'FACULTY')")
    public ResponseEntity<Void> deleteTimetableSlot(@PathVariable Long id) {
        UserDto currentUser = userService.getCurrentUser();
        Long ownerId = "COORDINATOR".equals(currentUser.role()) ? null : resolveFacultyId(currentUser);
        academicsService.deleteTimetableSlot(id, ownerId);
        return ResponseEntity.noContent().build();
    }

    /**
     * Timetable upload. The coordinator's sheet is the master timetable and replaces every slot;
     * a faculty member's sheet is theirs by definition and replaces only their own slots.
     */
    @PostMapping(value = "/timetable/import", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    @PreAuthorize("hasAnyRole('COORDINATOR', 'FACULTY')")
    public ResponseEntity<TimetableImportResultDto> importTimetable(
            @RequestParam("file") org.springframework.web.multipart.MultipartFile file) {
        UserDto currentUser = userService.getCurrentUser();
        Long onlyFacultyId = "COORDINATOR".equals(currentUser.role()) ? null : resolveFacultyId(currentUser);
        return ResponseEntity.ok(timetableImportService.importTimetable(file, onlyFacultyId));
    }

    @GetMapping("/timetable/import-template")
    public ResponseEntity<byte[]> timetableImportTemplate() {
        return ResponseEntity.ok()
                .header(org.springframework.http.HttpHeaders.CONTENT_DISPOSITION,
                        "attachment; filename=\"timetable_template.csv\"")
                .contentType(MediaType.parseMediaType("text/csv"))
                .body(timetableImportService.templateCsv());
    }

    private Long resolveFacultyId(UserDto currentUser) {
        return facultyService.getFacultyByUserId(currentUser.id())
                .map(FacultyDto::id)
                .orElseGet(() -> {
                    if ("COORDINATOR".equals(currentUser.role())) {
                        return 1L;
                    }
                    throw new ForbiddenException("Faculty profile not found for user: " + currentUser.email());
                });
    }
}
