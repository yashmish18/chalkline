package com.iet.dashboard.academics.service;

import com.iet.dashboard.academics.web.CourseDto;
import com.iet.dashboard.academics.web.CourseOfferingDto;
import com.iet.dashboard.academics.web.CreateCourseRequest;
import com.iet.dashboard.academics.web.CreateTeachingAssignmentRequest;
import com.iet.dashboard.academics.web.TeachingAssignmentDto;

import java.util.List;

public interface AcademicsService {

    List<CourseDto> getAllCourses();

    CourseDto createCourse(CreateCourseRequest request);

    List<CourseOfferingDto> getAllOfferings(Integer semester);

    List<TeachingAssignmentDto> getAllTeachingAssignments();

    List<TeachingAssignmentDto> getTeachingAssignmentsForFaculty(Long facultyId);

    TeachingAssignmentDto createTeachingAssignment(CreateTeachingAssignmentRequest request);
}
