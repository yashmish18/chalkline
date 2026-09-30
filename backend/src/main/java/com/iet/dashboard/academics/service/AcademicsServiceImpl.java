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
    private final com.iet.dashboard.academics.repo.AcademicTermRepository termRepository;
    private final com.iet.dashboard.academics.repo.SyllabusTopicRepository syllabusRepository;
    private final com.iet.dashboard.academics.repo.TimetableSlotRepository timetableSlotRepository;

    public AcademicsServiceImpl(
            CourseRepository courseRepository,
            CourseOfferingRepository offeringRepository,
            TeachingAssignmentRepository assignmentRepository,
            FacultyService facultyService,
            com.iet.dashboard.academics.repo.AcademicTermRepository termRepository,
            com.iet.dashboard.academics.repo.SyllabusTopicRepository syllabusRepository,
            com.iet.dashboard.academics.repo.TimetableSlotRepository timetableSlotRepository
    ) {
        this.courseRepository = courseRepository;
        this.offeringRepository = offeringRepository;
        this.assignmentRepository = assignmentRepository;
        this.facultyService = facultyService;
        this.termRepository = termRepository;
        this.syllabusRepository = syllabusRepository;
        this.timetableSlotRepository = timetableSlotRepository;
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
        String subSection = (request.subSection() != null && !request.subSection().isBlank())
                ? request.subSection().trim()
                : "ALL";

        TeachingAssignment assignment = new TeachingAssignment(offering, faculty.id(), component, subSection);
        TeachingAssignment saved = assignmentRepository.save(assignment);

        return TeachingAssignmentDto.fromEntity(saved, faculty.name());
    }

    @Override
    @Transactional(readOnly = true)
    public AcademicTermDto getActiveTerm() {
        return termRepository.findByActiveTrue()
                .map(AcademicTermDto::fromEntity)
                .orElse(null);
    }

    @Override
    @Transactional(readOnly = true)
    public List<AcademicTermDto> getAllTerms() {
        return termRepository.findAll().stream()
                .map(AcademicTermDto::fromEntity)
                .collect(Collectors.toList());
    }

    @Override
    public AcademicTermDto setActiveTerm(Long termId) {
        com.iet.dashboard.academics.domain.AcademicTerm target = termRepository.findById(termId)
                .orElseThrow(() -> new ResourceNotFoundException("Academic term not found: " + termId));

        termRepository.findAll().forEach(t -> {
            t.setActive(t.getId().equals(termId));
            termRepository.save(t);
        });

        return AcademicTermDto.fromEntity(target);
    }

    @Override
    @Transactional(readOnly = true)
    public List<SyllabusTopicDto> getSyllabusForCourse(Long courseId) {
        return syllabusRepository.findByCourseIdOrderByLectureOrderAsc(courseId).stream()
                .map(SyllabusTopicDto::fromEntity)
                .collect(Collectors.toList());
    }

    @Override
    @Transactional(readOnly = true)
    public CourseOfferingDto getOfferingById(Long offeringId) {
        CourseOffering offering = offeringRepository.findById(offeringId)
                .orElseThrow(() -> new ResourceNotFoundException("Course offering not found: " + offeringId));
        return CourseOfferingDto.fromEntity(offering);
    }

    @Override
    @Transactional(readOnly = true)
    public boolean isFacultyAssignedToOffering(Long facultyId, Long offeringId) {
        if (facultyId == null || offeringId == null) {
            return false;
        }
        return assignmentRepository.findByOfferingId(offeringId).stream()
                .anyMatch(a -> facultyId.equals(a.getFacultyId()));
    }

    @Override
    @Transactional(readOnly = true)
    public void assertFacultyAssignedToOffering(Long facultyId, Long offeringId) {
        if (!isFacultyAssignedToOffering(facultyId, offeringId)) {
            throw new com.iet.dashboard.shared.error.ForbiddenException(
                    "You do not have access to course offering #" + offeringId + " (not assigned to you)");
        }
    }

    @Override
    @Transactional(readOnly = true)
    public List<TimetableSlotDto> getTimetableForFaculty(Long facultyId) {
        if (facultyId == null) {
            return List.of();
        }
        return timetableSlotRepository.findByFacultyIdWithDetails(facultyId).stream()
                .map(TimetableSlotDto::fromEntity)
                .collect(Collectors.toList());
    }
}
