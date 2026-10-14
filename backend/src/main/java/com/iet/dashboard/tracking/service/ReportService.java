package com.iet.dashboard.tracking.service;

import com.iet.dashboard.tracking.web.ReportDtos;

public interface ReportService {

    ReportDtos.SemesterReport semesterReport();

    ReportDtos.FacultyReport facultyReport(Long facultyId);

    ReportDtos.CourseReport courseReport(String courseCode);
}
