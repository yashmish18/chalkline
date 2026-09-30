package com.iet.dashboard.academics.service;

import com.iet.dashboard.academics.web.AcademicTermDto;
import com.iet.dashboard.academics.web.CourseDto;
import com.iet.dashboard.academics.web.CourseOfferingDto;
import com.iet.dashboard.academics.web.CreateCourseRequest;
import com.iet.dashboard.academics.web.CreateTeachingAssignmentRequest;
import com.iet.dashboard.academics.web.SyllabusTopicDto;
import com.iet.dashboard.academics.web.TeachingAssignmentDto;

import java.util.List;

public interface AcademicsService {

    List<CourseDto> getAllCourses();

    CourseDto createCourse(CreateCourseRequest request);

    List<CourseOfferingDto> getAllOfferings(Integer semester);

    List<TeachingAssignmentDto> getAllTeachingAssignments();

    List<TeachingAssignmentDto> getTeachingAssignmentsForFaculty(Long facultyId);

    TeachingAssignmentDto createTeachingAssignment(CreateTeachingAssignmentRequest request);

    AcademicTermDto getActiveTerm();

    List<AcademicTermDto> getAllTerms();

    AcademicTermDto setActiveTerm(Long termId);

    List<SyllabusTopicDto> getSyllabusForCourse(Long courseId);

    CourseOfferingDto getOfferingById(Long offeringId);

    boolean isFacultyAssignedToOffering(Long facultyId, Long offeringId);

    void assertFacultyAssignedToOffering(Long facultyId, Long offeringId);

    List<com.iet.dashboard.academics.web.TimetableSlotDto> getTimetableForFaculty(Long facultyId);

    void deleteTeachingAssignment(Long assignmentId);

    void deleteCourse(Long courseId);
}
