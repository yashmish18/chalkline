package com.iet.dashboard.academics.service;

import com.iet.dashboard.academics.domain.Course;
import com.iet.dashboard.academics.domain.CourseComponent;
import com.iet.dashboard.academics.domain.CourseOffering;
import com.iet.dashboard.academics.domain.SubSections;
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

import java.util.Comparator;
import java.util.HashMap;
import java.util.LinkedHashMap;
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
        String code = request.code().trim().toUpperCase();
        if (courseRepository.existsByCodeIgnoreCase(code)) {
            throw new IllegalArgumentException("Course code already exists: " + code);
        }

        int[] hours = request.ltpHours();
        int credits = (request.credits() != null && request.credits() > 0)
                ? request.credits()
                : defaultCreditsFor(hours);

        // Course nature is always THEORY — there is no "LAB" course type. A lab-only course is
        // simply a course whose LTP plan carries practical hours (e.g. 0-0-4); the practical
        // component is owned through PRACTICAL teaching assignments, not a course type.
        String nature = "THEORY";

        Course course = new Course(
                code,
                request.name().trim(),
                nature,
                credits,
                request.normalizedLtp(),
                hours[0],
                hours[1],
                hours[2],
                request.description()
        );
        course.setCategory(request.normalizedCategory());

        return CourseDto.fromEntity(courseRepository.save(course));
    }

    @Override
    public CourseOfferingDto createOffering(CreateOfferingRequest request) {
        Course course = courseRepository.findById(request.courseId())
                .orElseThrow(() -> new ResourceNotFoundException("Course not found: " + request.courseId()));

        String academicYear = request.academicYear().trim();
        String section = request.section().trim().toUpperCase();

        if (offeringRepository.existsByCourse_IdAndAcademicYearAndYearOfStudyAndSemesterAndSection(
                course.getId(), academicYear, request.yearOfStudy(), request.semester(), section)) {
            throw new IllegalArgumentException(String.format(
                    "%s is already offered to section %s in %s semester %d",
                    course.getCode(), section, academicYear, request.semester()));
        }

        com.iet.dashboard.academics.domain.AcademicTerm term = request.termId() != null
                ? termRepository.findById(request.termId())
                        .orElseThrow(() -> new ResourceNotFoundException("Academic term not found: " + request.termId()))
                : termRepository.findByActiveTrue().orElse(null);

        CourseOffering offering = new CourseOffering(
                course, academicYear, request.yearOfStudy(), request.semester(), section, term);

        return CourseOfferingDto.fromEntity(offeringRepository.save(offering));
    }

    @Override
    public List<CourseOfferingDto> createOfferingsBulk(CreateOfferingsBulkRequest request) {
        Course course = courseRepository.findById(request.courseId())
                .orElseThrow(() -> new ResourceNotFoundException("Course not found: " + request.courseId()));

        String academicYear = request.academicYear().trim();

        com.iet.dashboard.academics.domain.AcademicTerm term = request.termId() != null
                ? termRepository.findById(request.termId())
                        .orElseThrow(() -> new ResourceNotFoundException("Academic term not found: " + request.termId()))
                : termRepository.findByActiveTrue().orElse(null);

        List<CourseOfferingDto> created = new java.util.ArrayList<>();
        for (int i = 0; i < request.sectionCount(); i++) {
            String section = String.valueOf((char) ('A' + i));
            boolean exists = offeringRepository.existsByCourse_IdAndAcademicYearAndYearOfStudyAndSemesterAndSection(
                    course.getId(), academicYear, request.yearOfStudy(), request.semester(), section);
            if (exists) {
                continue; // Idempotent: re-opening an existing section set is a no-op for those sections
            }
            CourseOffering offering = new CourseOffering(course, academicYear, request.yearOfStudy(), request.semester(), section, term);
            created.add(CourseOfferingDto.fromEntity(offeringRepository.save(offering)));
        }

        if (created.isEmpty()) {
            throw new IllegalArgumentException(String.format(
                    "%s already has all %d requested sections open in %s semester %d",
                    course.getCode(), request.sectionCount(), academicYear, request.semester()));
        }

        return created;
    }

    /**
     * Default credits when the coordinator does not supply them: each lecture/tutorial hour is
     * one credit and a practical block counts one credit per two hours (a 2-hour lab = 1).
     */
    private static int defaultCreditsFor(int[] hours) {
        return Math.max(1, hours[0] + hours[1] + ((hours[2] + 1) / 2));
    }

    @Override
    @Transactional(readOnly = true)
    public List<CourseOfferingDto> getAllOfferings(Integer semester, Long departmentId) {
        List<CourseOffering> offerings;
        if (semester != null && semester > 0) {
            offerings = offeringRepository.findBySemester(semester);
        } else {
            offerings = offeringRepository.findAllWithCourse();
        }
        return offerings.stream()
                .filter(o -> departmentId == null
                        || (o.getCourse() != null && departmentId.equals(o.getCourse().getDepartmentId())))
                .map(CourseOfferingDto::fromEntity)
                .collect(Collectors.toList());
    }

    @Override
    @Transactional(readOnly = true)
    public List<TeachingAssignmentDto> getAllTeachingAssignments(Long departmentId) {
        List<TeachingAssignment> assignments = assignmentRepository.findAllWithCourseAndOffering();
        Map<Long, String> facultyNames = facultyService.getAllFaculty(null, null).stream()
                .collect(Collectors.toMap(FacultyDto::id, FacultyDto::name, (existing, replacement) -> existing));

        return assignments.stream()
                .filter(a -> {
                    if (departmentId == null) return true;
                    Course course = a.getOffering() != null ? a.getOffering().getCourse() : null;
                    return course != null && departmentId.equals(course.getDepartmentId());
                })
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

        CourseComponent component = CourseComponent.parseStrict(request.component());
        String subSection = SubSections.normalise(request.subSection(), offering.getSection());

        Course course = offering.getCourse();
        int plannedHours = plannedHoursForComponent(course, component);
        if (plannedHours <= 0) {
            throw new IllegalArgumentException(String.format(
                    "Course %s (%s) plans no %s hours, so a %s assignment cannot be created",
                    course.getCode(), course.getLtp(), component, component));
        }

        int contactHours = (request.contactHours() != null && request.contactHours() > 0)
                ? request.contactHours()
                : plannedHours;

        TeachingAssignment assignment = new TeachingAssignment(offering, faculty.id(), component, subSection, contactHours);
        TeachingAssignment saved = assignmentRepository.save(assignment);

        return TeachingAssignmentDto.fromEntity(saved, faculty.name());
    }

    @Override
    @Transactional(readOnly = true)
    public List<FacultyLtpLoadDto> getFacultyLtpLoad() {
        List<FacultyDto> allFaculty = facultyService.getAllFaculty(null, null);
        Map<Long, String> facultyNames = allFaculty.stream()
                .collect(Collectors.toMap(FacultyDto::id, FacultyDto::name, (existing, replacement) -> existing));
        Map<Long, Integer> facultyPbl = allFaculty.stream()
                .collect(Collectors.toMap(FacultyDto::id, FacultyDto::pblHours, (existing, replacement) -> existing));

        Map<Long, int[]> buckets = new LinkedHashMap<>();
        Map<Long, Integer> counts = new HashMap<>();

        for (TeachingAssignment assignment : assignmentRepository.findAllWithCourseAndOffering()) {
            int[] bucket = buckets.computeIfAbsent(assignment.getFacultyId(), key -> new int[3]);
            applyToBucket(bucket, assignment);
            counts.merge(assignment.getFacultyId(), 1, Integer::sum);
        }

        for (FacultyDto f : allFaculty) {
            if (f.pblHours() > 0 && !buckets.containsKey(f.id())) {
                buckets.put(f.id(), new int[3]);
            }
        }

        return buckets.entrySet().stream()
                .map(entry -> {
                    int[] bucket = entry.getValue();
                    int pbl = facultyPbl.getOrDefault(entry.getKey(), 0);
                    int total = bucket[0] + bucket[1] + bucket[2] + pbl;
                    return new FacultyLtpLoadDto(
                            entry.getKey(),
                            facultyNames.getOrDefault(entry.getKey(), "Faculty #" + entry.getKey()),
                            bucket[0],
                            bucket[1],
                            bucket[2],
                            pbl,
                            total,
                            counts.getOrDefault(entry.getKey(), 0));
                })
                .sorted(Comparator.comparingInt(FacultyLtpLoadDto::totalHours).reversed())
                .collect(Collectors.toList());
    }

    /**
     * Weekly hours the course plans for a component. A component the course does not plan
     * (e.g. THEORY on a {@code 0-0-4} lab course) returns 0 so the allocation is rejected.
     */
    private static int plannedHoursForComponent(Course course, CourseComponent component) {
        return switch (component) {
            case LECTURE -> course.getLectureHours();
            case TUTORIAL -> course.getTutorialHours();
            case PRACTICAL -> course.getPracticalHours();
            case THEORY -> course.getLectureHours() + course.getTutorialHours();
            case LAB -> course.getPracticalHours();
            case ALL -> course.getLectureHours() + course.getTutorialHours() + course.getPracticalHours();
        };
    }

    /**
     * Adds an assignment's share into the L-T-P load buckets (0 = lecture, 1 = tutorial,
     * 2 = practical). An {@code ALL} owner takes the whole course plan across all three
     * buckets; every other component lands in its own bucket.
     */
    private static void applyToBucket(int[] bucket, TeachingAssignment assignment) {
        Course course = assignment.getOffering().getCourse();
        switch (assignment.getComponent()) {
            case LECTURE, THEORY -> bucket[0] += assignment.getContactHours();
            case TUTORIAL -> bucket[1] += assignment.getContactHours();
            case PRACTICAL, LAB -> bucket[2] += assignment.getContactHours();
            case ALL -> {
                bucket[0] += course.getLectureHours();
                bucket[1] += course.getTutorialHours();
                bucket[2] += course.getPracticalHours();
            }
        }
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
    public AcademicTermDto updateActiveTerm(com.iet.dashboard.academics.web.UpdateTermRequest request) {
        if (!request.endDate().isAfter(request.startDate())) {
            throw new IllegalArgumentException("The semester must end after it starts.");
        }
        com.iet.dashboard.academics.domain.AcademicTerm term = termRepository.findByActiveTrue()
                .orElseGet(() -> new com.iet.dashboard.academics.domain.AcademicTerm(
                        request.name(), request.academicYear(), request.startDate(), request.endDate(), true));
        term.setName(request.name().trim());
        term.setAcademicYear(request.academicYear().trim());
        term.setStartDate(request.startDate());
        term.setEndDate(request.endDate());
        if (request.requiredAssessments() != null) {
            if (request.requiredAssessments() < 0 || request.requiredAssessments() > 10) {
                throw new IllegalArgumentException("Required assessments must be between 0 and 10.");
            }
            term.setRequiredAssessments(request.requiredAssessments());
        }
        return AcademicTermDto.fromEntity(termRepository.save(term));
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
    public Map<Long, List<SyllabusTopicDto>> getSyllabusByCourse() {
        return syllabusRepository.findAllOrdered().stream()
                .map(SyllabusTopicDto::fromEntity)
                .collect(Collectors.groupingBy(SyllabusTopicDto::courseId, LinkedHashMap::new, Collectors.toList()));
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

    @Override
    @Transactional
    public void deleteTeachingAssignment(Long assignmentId) {
        if (!assignmentRepository.existsById(assignmentId)) {
            throw new ResourceNotFoundException("Teaching assignment not found: " + assignmentId);
        }
        assignmentRepository.deleteById(assignmentId);
    }

    @Override
    @Transactional
    public void deleteTeachingAssignments(Integer semester) {
        if (semester != null) {
            assignmentRepository.deleteByOfferingSemester(semester);
        } else {
            assignmentRepository.deleteAll();
        }
    }

    @Override
    @Transactional
    public void deleteCourse(Long courseId) {
        if (!courseRepository.existsById(courseId)) {
            throw new ResourceNotFoundException("Course not found: " + courseId);
        }
        courseRepository.deleteById(courseId);
    }

    @Override
    @Transactional
    public void deleteTimetableSlot(Long slotId, Long ownerFacultyId) {
        var slot = timetableSlotRepository.findById(slotId)
                .orElseThrow(() -> new ResourceNotFoundException("Timetable slot not found: " + slotId));
        if (ownerFacultyId != null && !ownerFacultyId.equals(slot.getFacultyId())) {
            throw new com.iet.dashboard.shared.error.ForbiddenException("You can only remove your own timetable slots");
        }
        timetableSlotRepository.delete(slot);
    }
}
