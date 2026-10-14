package com.iet.dashboard.academics.service;

import com.iet.dashboard.academics.web.AcademicTermDto;
import com.iet.dashboard.academics.web.CourseDto;
import com.iet.dashboard.academics.web.CourseOfferingDto;
import com.iet.dashboard.academics.web.CreateCourseRequest;
import com.iet.dashboard.academics.web.CreateOfferingRequest;
import com.iet.dashboard.academics.web.CreateOfferingsBulkRequest;
import com.iet.dashboard.academics.web.CreateTeachingAssignmentRequest;
import com.iet.dashboard.academics.web.SyllabusTopicDto;
import com.iet.dashboard.academics.web.TeachingAssignmentDto;

import java.util.List;

public interface AcademicsService {

    List<CourseDto> getAllCourses();

    CourseDto createCourse(CreateCourseRequest request);

    List<CourseOfferingDto> getAllOfferings(Integer semester, Long departmentId);

    CourseOfferingDto createOffering(CreateOfferingRequest request);

    /**
     * Opens several sections of one course at once (auto-lettered A, B, C…). Sections that
     * already exist are skipped; the response lists everything open after the call.
     */
    List<CourseOfferingDto> createOfferingsBulk(CreateOfferingsBulkRequest request);

    List<TeachingAssignmentDto> getAllTeachingAssignments(Long departmentId);

    List<TeachingAssignmentDto> getTeachingAssignmentsForFaculty(Long facultyId);

    TeachingAssignmentDto createTeachingAssignment(CreateTeachingAssignmentRequest request);

    /** Computed weekly LTP load per faculty, derived from assignment contact hours. */
    List<com.iet.dashboard.academics.web.FacultyLtpLoadDto> getFacultyLtpLoad();

    AcademicTermDto getActiveTerm();

    List<AcademicTermDto> getAllTerms();

    AcademicTermDto setActiveTerm(Long termId);

    /** Updates the active term's name and dates, creating the term when none exists yet. */
    AcademicTermDto updateActiveTerm(com.iet.dashboard.academics.web.UpdateTermRequest request);

    List<SyllabusTopicDto> getSyllabusForCourse(Long courseId);

    /** Every course's syllabus in lecture order, keyed by course id — one query. */
    java.util.Map<Long, List<SyllabusTopicDto>> getSyllabusByCourse();

    CourseOfferingDto getOfferingById(Long offeringId);

    boolean isFacultyAssignedToOffering(Long facultyId, Long offeringId);

    void assertFacultyAssignedToOffering(Long facultyId, Long offeringId);

    List<com.iet.dashboard.academics.web.TimetableSlotDto> getTimetableForFaculty(Long facultyId);

    void deleteTeachingAssignment(Long assignmentId);
    
    void deleteTeachingAssignments(Integer semester);


    void deleteCourse(Long courseId);

    /** @param ownerFacultyId null for the coordinator; otherwise the slot must belong to this faculty member */
    void deleteTimetableSlot(Long slotId, Long ownerFacultyId);
}
