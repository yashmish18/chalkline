package com.iet.dashboard.tracking.web;

import com.iet.dashboard.academics.service.AcademicsService;
import com.iet.dashboard.directory.service.FacultyService;
import com.iet.dashboard.directory.web.FacultyDto;
import com.iet.dashboard.identity.service.UserService;
import com.iet.dashboard.identity.web.UserDto;
import com.iet.dashboard.shared.error.ForbiddenException;
import com.iet.dashboard.tracking.service.ReportService;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

/**
 * Semester, faculty and course reports. A faculty member may read their own report, and the report
 * of a course they teach; everything else is for the coordinator.
 */
@RestController
@RequestMapping("/api/reports")
public class ReportController {

    private final ReportService reportService;
    private final UserService userService;
    private final FacultyService facultyService;
    private final AcademicsService academicsService;

    public ReportController(ReportService reportService, UserService userService, FacultyService facultyService,
                            AcademicsService academicsService) {
        this.reportService = reportService;
        this.userService = userService;
        this.facultyService = facultyService;
        this.academicsService = academicsService;
    }

    @GetMapping("/semester")
    @PreAuthorize("hasRole('COORDINATOR')")
    public ReportDtos.SemesterReport semester() {
        return reportService.semesterReport();
    }

    @GetMapping("/faculty/{facultyId}")
    @PreAuthorize("hasAnyRole('FACULTY', 'COORDINATOR')")
    public ReportDtos.FacultyReport faculty(@PathVariable Long facultyId) {
        Long self = selfFacultyId();
        if (self != null && !self.equals(facultyId)) {
            throw new ForbiddenException("You can only read your own report.");
        }
        return reportService.facultyReport(facultyId);
    }

    @GetMapping("/faculty/me")
    @PreAuthorize("hasRole('FACULTY')")
    public ReportDtos.FacultyReport myReport() {
        return reportService.facultyReport(selfFacultyId());
    }

    @GetMapping("/courses/{courseCode}")
    @PreAuthorize("hasAnyRole('FACULTY', 'COORDINATOR')")
    public ReportDtos.CourseReport course(@PathVariable String courseCode) {
        Long self = selfFacultyId();
        if (self != null && academicsService.getTeachingAssignmentsForFaculty(self).stream()
                .noneMatch(a -> a.offering() != null && a.offering().course().code().equalsIgnoreCase(courseCode))) {
            throw new ForbiddenException("You can only read reports for courses you teach.");
        }
        return reportService.courseReport(courseCode);
    }

    /** The caller's faculty id, or null for the coordinator. Always from the session, never the request. */
    private Long selfFacultyId() {
        UserDto user = userService.getCurrentUser();
        if ("COORDINATOR".equals(user.role())) {
            return null;
        }
        return facultyService.getFacultyByUserId(user.id()).map(FacultyDto::id)
                .orElseThrow(() -> new ForbiddenException("No faculty profile for " + user.email()));
    }
}
