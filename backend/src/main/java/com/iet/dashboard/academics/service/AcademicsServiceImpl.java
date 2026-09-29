package com.iet.dashboard.academics.service;

import com.iet.dashboard.academics.domain.Course;
import com.iet.dashboard.academics.domain.CourseComponent;
import com.iet.dashboard.academics.domain.CourseOffering;
import com.iet.dashboard.academics.domain.TeachingAssignment;
import com.iet.dashboard.academics.repo.CourseOfferingRepository;
import com.iet.dashboard.academics.repo.CourseRepository;
import com.iet.dashboard.academics.repo.TeachingAssignmentRepository;
import com.iet.dashboard.academics.web.*;
import com.iet.dashboard.directory.service.FacultyService;
import com.iet.dashboard.directory.web.FacultyDto;
import com.iet.dashboard.shared.error.ResourceNotFoundException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

@Service
@Transactional
public class AcademicsServiceImpl implements AcademicsService {

    private final CourseRepository courseRepository;
    private final CourseOfferingRepository offeringRepository;
    private final TeachingAssignmentRepository assignmentRepository;
    private final FacultyService facultyService;

    public AcademicsServiceImpl(
            CourseRepository courseRepository,
            CourseOfferingRepository offeringRepository,
            TeachingAssignmentRepository assignmentRepository,
            FacultyService facultyService
    ) {
        this.courseRepository = courseRepository;
        this.offeringRepository = offeringRepository;
        this.assignmentRepository = assignmentRepository;
        this.facultyService = facultyService;
    }

    @Override
    @Transactional(readOnly = true)
    public List<CourseDto> getAllCourses() {
        return courseRepository.findAll().stream()
                .map(CourseDto::fromEntity)
                .collect(Collectors.toList());
    }

    @Override
    public CourseDto createCourse(CreateCourseRequest request) {
        if (courseRepository.existsByCodeIgnoreCase(request.code())) {
            throw new IllegalArgumentException("Course code already exists: " + request.code());
        }
        Course course = new Course(request.code(), request.name(), request.type());
        return CourseDto.fromEntity(courseRepository.save(course));
    }

    @Override
    @Transactional(readOnly = true)
    public List<CourseOfferingDto> getAllOfferings(Integer semester) {
        List<CourseOffering> offerings;
        if (semester != null && semester > 0) {
            offerings = offeringRepository.findBySemester(semester);
        } else {
            offerings = offeringRepository.findAllWithCourse();
        }
        return offerings.stream()
                .map(CourseOfferingDto::fromEntity)
                .collect(Collectors.toList());
    }

    @Override
    @Transactional(readOnly = true)
    public List<TeachingAssignmentDto> getAllTeachingAssignments() {
        List<TeachingAssignment> assignments = assignmentRepository.findAllWithCourseAndOffering();
        Map<Long, String> facultyNames = facultyService.getAllFaculty(null).stream()
                .collect(Collectors.toMap(FacultyDto::id, FacultyDto::name, (existing, replacement) -> existing));

        return assignments.stream()
                .map(a -> TeachingAssignmentDto.fromEntity(a, facultyNames.getOrDefault(a.getFacultyId(), "Faculty #" + a.getFacultyId())))
                .collect(Collectors.toList());
    }

    @Override
    @Transactional(readOnly = true)
    public List<TeachingAssignmentDto> getTeachingAssignmentsForFaculty(Long facultyId) {
        FacultyDto faculty = facultyService.getFacultyById(facultyId);
        List<TeachingAssignment> assignments = assignmentRepository.findByFacultyIdWithCourse(facultyId);

        return assignments.stream()
                .map(a -> TeachingAssignmentDto.fromEntity(a, faculty.name()))
                .collect(Collectors.toList());
    }

    @Override
    public TeachingAssignmentDto createTeachingAssignment(CreateTeachingAssignmentRequest request) {
        CourseOffering offering = offeringRepository.findById(request.offeringId())
                .orElseThrow(() -> new ResourceNotFoundException("Course offering not found: " + request.offeringId()));

        FacultyDto faculty = facultyService.getFacultyById(request.facultyId());

        CourseComponent component = CourseComponent.valueOf(request.component().toUpperCase());

        TeachingAssignment assignment = new TeachingAssignment(offering, faculty.id(), component);
        TeachingAssignment saved = assignmentRepository.save(assignment);

        return TeachingAssignmentDto.fromEntity(saved, faculty.name());
    }
}
