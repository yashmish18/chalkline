package com.iet.dashboard.academics.service;

import com.iet.dashboard.academics.domain.Course;
import com.iet.dashboard.academics.domain.CourseComponent;
import com.iet.dashboard.academics.domain.CourseOffering;
import com.iet.dashboard.academics.domain.TeachingAssignment;
import com.iet.dashboard.academics.repo.CourseOfferingRepository;
import com.iet.dashboard.academics.repo.CourseRepository;
import com.iet.dashboard.academics.repo.TeachingAssignmentRepository;
import com.iet.dashboard.academics.web.CourseDto;
import com.iet.dashboard.academics.web.CreateCourseRequest;
import com.iet.dashboard.academics.web.CreateTeachingAssignmentRequest;
import com.iet.dashboard.academics.web.FacultyLtpLoadDto;
import com.iet.dashboard.academics.web.TeachingAssignmentDto;
import com.iet.dashboard.directory.service.FacultyService;
import com.iet.dashboard.directory.web.FacultyDto;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.Collections;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class AcademicsServiceTest {

    @Mock
    private CourseRepository courseRepository;

    @Mock
    private CourseOfferingRepository offeringRepository;

    @Mock
    private TeachingAssignmentRepository assignmentRepository;

    @Mock
    private FacultyService facultyService;

    @Mock
    private com.iet.dashboard.academics.repo.AcademicTermRepository termRepository;

    @Mock
    private com.iet.dashboard.academics.repo.SyllabusTopicRepository syllabusRepository;

    @Mock
    private com.iet.dashboard.academics.repo.TimetableSlotRepository timetableSlotRepository;

    @InjectMocks
    private AcademicsServiceImpl academicsService;

    @Test
    void testCreateCourse_Success() {
        CreateCourseRequest request = new CreateCourseRequest("CS601", "Cloud Computing", "THEORY");
        when(courseRepository.existsByCodeIgnoreCase("CS601")).thenReturn(false);

        Course saved = new Course("CS601", "Cloud Computing", "THEORY");
        saved.setId(10L);
        when(courseRepository.save(any(Course.class))).thenReturn(saved);

        CourseDto result = academicsService.createCourse(request);

        assertNotNull(result);
        assertEquals("CS601", result.code());
    }

    @Test
    void testCreateCourse_DuplicateCode_ThrowsException() {
        CreateCourseRequest request = new CreateCourseRequest("CS601", "Cloud Computing", "THEORY");
        when(courseRepository.existsByCodeIgnoreCase("CS601")).thenReturn(true);

        assertThrows(IllegalArgumentException.class, () -> academicsService.createCourse(request));
    }

    @Test
    void testCreateTeachingAssignment_Success() {
        CreateTeachingAssignmentRequest request = new CreateTeachingAssignmentRequest(1L, 2L, "THEORY");

        Course course = new Course("CS501", "Networks", "THEORY");
        CourseOffering offering = new CourseOffering(course, "2026-2027", 3, 5, "A");
        offering.setId(1L);

        FacultyDto faculty = new FacultyDto(2L, 2L, "Dr. Amit", "Assoc Prof", "PhD", "IT", Collections.emptyList());

        when(offeringRepository.findById(1L)).thenReturn(Optional.of(offering));
        when(facultyService.getFacultyById(2L)).thenReturn(faculty);

        TeachingAssignment saved = new TeachingAssignment(offering, 2L, CourseComponent.THEORY);
        saved.setId(100L);
        when(assignmentRepository.save(any(TeachingAssignment.class))).thenReturn(saved);

        TeachingAssignmentDto result = academicsService.createTeachingAssignment(request);

        assertNotNull(result);
        assertEquals(100L, result.id());
        assertEquals("Dr. Amit", result.facultyName());
        assertEquals("THEORY", result.component());
    }

    @Test
    void testGetActiveTerm() {
        com.iet.dashboard.academics.domain.AcademicTerm term = new com.iet.dashboard.academics.domain.AcademicTerm(
                "Odd 2026-27", "2026-2027", java.time.LocalDate.of(2026, 7, 15), java.time.LocalDate.of(2026, 12, 15), true
        );
        term.setId(1L);
        when(termRepository.findByActiveTrue()).thenReturn(Optional.of(term));

        com.iet.dashboard.academics.web.AcademicTermDto result = academicsService.getActiveTerm();
        assertNotNull(result);
        assertEquals("Odd 2026-27", result.name());
        assertTrue(result.active());
    }

    @Test
    void testGetSyllabusForCourse() {
        Course course = new Course("CS501", "Networks", "THEORY");
        course.setId(1L);
        com.iet.dashboard.academics.domain.SyllabusTopic topic = new com.iet.dashboard.academics.domain.SyllabusTopic(
                course, 1, "Unit 1", "OSI Model", 1, 1
        );
        topic.setId(10L);
        when(syllabusRepository.findByCourseIdOrderByLectureOrderAsc(1L)).thenReturn(List.of(topic));

        List<com.iet.dashboard.academics.web.SyllabusTopicDto> topics = academicsService.getSyllabusForCourse(1L);
        assertEquals(1, topics.size());
        assertEquals("OSI Model", topics.get(0).topicName());
    }

    @Test
    void testAssertFacultyAssignedToOffering_SuccessAndForbidden() {
        Course course = new Course("CS501", "Networks", "THEORY");
        CourseOffering offering = new CourseOffering(course, "2026-2027", 3, 5, "A");
        TeachingAssignment assignment = new TeachingAssignment(offering, 2L, CourseComponent.THEORY);

        when(assignmentRepository.findByOfferingId(1L)).thenReturn(List.of(assignment));

        // Faculty #2 is assigned -> should not throw
        assertDoesNotThrow(() -> academicsService.assertFacultyAssignedToOffering(2L, 1L));

        // Faculty #99 is NOT assigned -> throws ForbiddenException
        assertThrows(com.iet.dashboard.shared.error.ForbiddenException.class,
                () -> academicsService.assertFacultyAssignedToOffering(99L, 1L));
    }

    @Test
    void testGetTimetableForFaculty() {
        Course course = new Course("CS501", "Networks", "THEORY", 4, "3-0-2", 3, 0, 2, "Networking course");
        CourseOffering offering = new CourseOffering(course, "2026-2027", 3, 5, "A");
        offering.setId(1L);

        com.iet.dashboard.academics.domain.TimetableSlot slot = new com.iet.dashboard.academics.domain.TimetableSlot(
                offering, 2L, "MONDAY", java.time.LocalTime.of(10, 0), java.time.LocalTime.of(11, 0),
                "Room 304", "LECTURE", "ALL"
        );
        slot.setId(10L);

        when(timetableSlotRepository.findByFacultyIdWithDetails(2L)).thenReturn(List.of(slot));

        List<com.iet.dashboard.academics.web.TimetableSlotDto> result = academicsService.getTimetableForFaculty(2L);
        assertEquals(1, result.size());
        assertEquals("CS501", result.get(0).courseCode());
        assertEquals("3-0-2", result.get(0).ltp());
        assertEquals("MONDAY", result.get(0).dayOfWeek());
        assertEquals("Room 304", result.get(0).room());
    }

    @Test
    void testDeleteTeachingAssignment_Success() {
        when(assignmentRepository.existsById(50L)).thenReturn(true);
        academicsService.deleteTeachingAssignment(50L);
        verify(assignmentRepository).deleteById(50L);
    }

    @Test
    void testDeleteCourse_Success() {
        when(courseRepository.existsById(10L)).thenReturn(true);
        academicsService.deleteCourse(10L);
        verify(courseRepository).deleteById(10L);
    }

    @Test
    void testDeleteTeachingAssignments_BySemester() {
        academicsService.deleteTeachingAssignments(5);
        verify(assignmentRepository).deleteByOfferingSemester(5);
    }

    @Test
    void testDeleteTeachingAssignments_All() {
        academicsService.deleteTeachingAssignments(null);
        verify(assignmentRepository).deleteAll();
    }

    @Test
    void testDeleteTimetableSlot_Success() {
        com.iet.dashboard.academics.domain.TimetableSlot slot = new com.iet.dashboard.academics.domain.TimetableSlot(
                null, 7L, "MONDAY", java.time.LocalTime.of(9, 0), java.time.LocalTime.of(10, 0), "LT-1", "LECTURE", "ALL");
        when(timetableSlotRepository.findById(25L)).thenReturn(Optional.of(slot));
        academicsService.deleteTimetableSlot(25L, 7L);
        verify(timetableSlotRepository).delete(slot);
    }

    @Test
    void testDeleteTimetableSlot_RejectsAnotherFacultysSlot() {
        com.iet.dashboard.academics.domain.TimetableSlot slot = new com.iet.dashboard.academics.domain.TimetableSlot(
                null, 7L, "MONDAY", java.time.LocalTime.of(9, 0), java.time.LocalTime.of(10, 0), "LT-1", "LECTURE", "ALL");
        when(timetableSlotRepository.findById(25L)).thenReturn(Optional.of(slot));
        assertThrows(com.iet.dashboard.shared.error.ForbiddenException.class,
                () -> academicsService.deleteTimetableSlot(25L, 8L));
        verify(timetableSlotRepository, never()).delete(any());
    }

    // --- Course creation with an LTP plan ----------------------------------------

    @Test
    void testCreateCourse_DerivesHoursAndCreditsFromLtpPlan() {
        when(courseRepository.existsByCodeIgnoreCase("CS601")).thenReturn(false);
        when(courseRepository.save(any(Course.class))).thenAnswer(inv -> inv.getArgument(0));

        CourseDto result = academicsService.createCourse(
                new CreateCourseRequest("cs601", "Cloud Computing", "THEORY", null, "3-1-2", "Cloud fundamentals"));

        assertEquals("CS601", result.code());
        assertEquals("3-1-2", result.ltp());
        assertEquals(3, result.lectureHours());
        assertEquals(1, result.tutorialHours());
        assertEquals(2, result.practicalHours());
        assertEquals(5, result.credits());
        assertEquals("THEORY", result.type());
        assertEquals("CORE", result.category());
    }

    @Test
    void testCreateCourse_NormalizesCategoryAndKeepsTheoryNature() {
        when(courseRepository.existsByCodeIgnoreCase(anyString())).thenReturn(false);
        when(courseRepository.save(any(Course.class))).thenAnswer(inv -> inv.getArgument(0));

        // Pure practical course with an open-elective category — still THEORY-natured; the
        // practical load lives in the LTP plan, there is no "LAB" course type.
        CourseDto lab = academicsService.createCourse(
                new CreateCourseRequest("CS510", "Networking Lab", null, "open elective", null, "0-0-4", null));
        assertEquals("THEORY", lab.type());
        assertEquals("0-0-4", lab.ltp());
        assertEquals(4, lab.practicalHours());
        assertEquals("OPEN_ELECTIVE", lab.category());
        assertEquals(2, lab.credits());

        CourseDto elective = academicsService.createCourse(
                new CreateCourseRequest("CS511", "Cloud", null, "de", null, "3-0-0", null));
        assertEquals("DEPARTMENT_ELECTIVE", elective.category());

        assertThrows(IllegalArgumentException.class, () -> academicsService.createCourse(
                new CreateCourseRequest("CS512", "Nonsense", null, "compulsory-ish", null, "3-0-0", null)));
    }

    @Test
    void testCreateTeachingAssignment_AllComponentOwnsTheWholeCoursePlan() {
        Course course = new Course("CS501", "Networks", "THEORY", 4, "3-1-2", 3, 1, 2, null);
        CourseOffering offering = offeringFor(course, "A");
        FacultyDto faculty = new FacultyDto(2L, 2L, "Dr. Amit", "Assoc Prof", "PhD", "IT", Collections.emptyList());

        when(offeringRepository.findById(1L)).thenReturn(Optional.of(offering));
        when(facultyService.getFacultyById(2L)).thenReturn(faculty);
        when(assignmentRepository.save(any(TeachingAssignment.class))).thenAnswer(inv -> inv.getArgument(0));

        TeachingAssignmentDto all = academicsService.createTeachingAssignment(
                new CreateTeachingAssignmentRequest(1L, 2L, "ALL", "ALL", null));

        assertEquals(6, all.contactHours());
        assertEquals("ALL", all.component());
    }

    @Test
    void testCreateCourse_HonoursExplicitCreditsAndDefaultsLtp() {
        when(courseRepository.existsByCodeIgnoreCase("CS602")).thenReturn(false);
        when(courseRepository.save(any(Course.class))).thenAnswer(inv -> inv.getArgument(0));

        CourseDto result = academicsService.createCourse(
                new CreateCourseRequest("CS602", "Compilers", "THEORY", 4, null, null));

        assertEquals("3-0-0", result.ltp());
        assertEquals(4, result.credits());
    }

    @Test
    void testCreateCourse_RejectsMalformedLtp() {
        when(courseRepository.existsByCodeIgnoreCase("CS603")).thenReturn(false);

        assertThrows(IllegalArgumentException.class, () -> academicsService.createCourse(
                new CreateCourseRequest("CS603", "Bad LTP", "THEORY", null, "three-zero-zero", null)));
        assertThrows(IllegalArgumentException.class, () -> academicsService.createCourse(
                new CreateCourseRequest("CS603", "Bad LTP", "THEORY", null, "3-0", null)));
    }

    @Test
    void testCreateOffering_AttachesToActiveTermAndRejectsDuplicates() {
        Course course = new Course("CS601", "Cloud Computing", "THEORY", 5, "3-1-2", 3, 1, 2, null);
        course.setId(11L);

        when(courseRepository.findById(11L)).thenReturn(Optional.of(course));
        when(termRepository.findByActiveTrue()).thenReturn(Optional.empty());
        when(offeringRepository.existsByCourse_IdAndAcademicYearAndYearOfStudyAndSemesterAndSection(
                11L, "2026-2027", 3, 5, "A")).thenReturn(false);
        when(offeringRepository.save(any(CourseOffering.class))).thenAnswer(inv -> {
            CourseOffering saved = inv.getArgument(0);
            saved.setId(77L);
            return saved;
        });

        com.iet.dashboard.academics.web.CourseOfferingDto created = academicsService.createOffering(
                new com.iet.dashboard.academics.web.CreateOfferingRequest(11L, "2026-2027", 3, 5, "a", null));

        assertEquals(77L, created.id());
        assertEquals("A", created.section());
        assertEquals("CS601", created.course().code());

        when(offeringRepository.existsByCourse_IdAndAcademicYearAndYearOfStudyAndSemesterAndSection(
                11L, "2026-2027", 3, 5, "A")).thenReturn(true);
        assertThrows(IllegalArgumentException.class, () -> academicsService.createOffering(
                new com.iet.dashboard.academics.web.CreateOfferingRequest(11L, "2026-2027", 3, 5, "A", null)));
    }

    // --- LTP component ownership -------------------------------------------------

    private CourseOffering offeringFor(Course course, String section) {
        CourseOffering offering = new CourseOffering(course, "2026-2027", 3, 5, section);
        offering.setId(1L);
        return offering;
    }

    @Test
    void testCreateTeachingAssignment_DefaultsContactHoursFromCourseLtp() {
        Course course = new Course("CS501", "Networks", "THEORY", 4, "3-0-2", 3, 0, 2, "Networking");
        CourseOffering offering = offeringFor(course, "A");
        FacultyDto faculty = new FacultyDto(2L, 2L, "Dr. Amit", "Assoc Prof", "PhD", "IT", Collections.emptyList());

        when(offeringRepository.findById(1L)).thenReturn(Optional.of(offering));
        when(facultyService.getFacultyById(2L)).thenReturn(faculty);
        when(assignmentRepository.save(any(TeachingAssignment.class))).thenAnswer(inv -> inv.getArgument(0));

        // THEORY owns lectures + tutorials = 3 + 0
        TeachingAssignmentDto theory = academicsService.createTeachingAssignment(
                new CreateTeachingAssignmentRequest(1L, 2L, "THEORY", "ALL", null));
        assertEquals(3, theory.contactHours());

        // PRACTICAL owns the practical hours = 2
        TeachingAssignmentDto lab = academicsService.createTeachingAssignment(
                new CreateTeachingAssignmentRequest(1L, 2L, "PRACTICAL", "A1", null));
        assertEquals(2, lab.contactHours());
    }

    @Test
    void testCreateTeachingAssignment_HonoursExplicitContactHours() {
        Course course = new Course("CS501", "Networks", "THEORY", 4, "3-0-2", 3, 0, 2, "Networking");
        CourseOffering offering = offeringFor(course, "A");
        FacultyDto faculty = new FacultyDto(2L, 2L, "Dr. Amit", "Assoc Prof", "PhD", "IT", Collections.emptyList());

        when(offeringRepository.findById(1L)).thenReturn(Optional.of(offering));
        when(facultyService.getFacultyById(2L)).thenReturn(faculty);
        when(assignmentRepository.save(any(TeachingAssignment.class))).thenAnswer(inv -> inv.getArgument(0));

        TeachingAssignmentDto result = academicsService.createTeachingAssignment(
                new CreateTeachingAssignmentRequest(1L, 2L, "LECTURE", "ALL", 2));

        assertEquals(2, result.contactHours());
        assertEquals("LECTURE", result.component());
    }

    @Test
    void testCreateTeachingAssignment_RejectsComponentTheCourseDoesNotPlan() {
        // 0-0-4 practical-only course cannot own a THEORY component
        Course course = new Course("CS503", "Web Lab", "THEORY", 2, "0-0-4", 0, 0, 4, "Lab only");
        CourseOffering offering = offeringFor(course, "A");
        FacultyDto faculty = new FacultyDto(2L, 2L, "Dr. Amit", "Assoc Prof", "PhD", "IT", Collections.emptyList());

        when(offeringRepository.findById(1L)).thenReturn(Optional.of(offering));
        when(facultyService.getFacultyById(2L)).thenReturn(faculty);

        IllegalArgumentException ex = assertThrows(IllegalArgumentException.class, () ->
                academicsService.createTeachingAssignment(new CreateTeachingAssignmentRequest(1L, 2L, "THEORY", "ALL", null)));

        assertTrue(ex.getMessage().contains("0-0-4"));
        verify(assignmentRepository, never()).save(any(TeachingAssignment.class));
    }

    @Test
    void testCreateTeachingAssignment_RejectsUnknownComponent() {
        Course course = new Course("CS501", "Networks", "THEORY");
        CourseOffering offering = offeringFor(course, "A");
        FacultyDto faculty = new FacultyDto(2L, 2L, "Dr. Amit", "Assoc Prof", "PhD", "IT", Collections.emptyList());

        when(offeringRepository.findById(1L)).thenReturn(Optional.of(offering));
        when(facultyService.getFacultyById(2L)).thenReturn(faculty);

        assertThrows(IllegalArgumentException.class, () ->
                academicsService.createTeachingAssignment(new CreateTeachingAssignmentRequest(1L, 2L, "SEMINAR", "ALL", null)));
    }

    @Test
    void testCreateOfferingsBulk_CreatesAutoLetteredSectionsAndSkipsExisting() {
        Course course = new Course("CS101", "Programming Fundamentals", "THEORY", 4, "3-0-2", 3, 0, 2, null);
        course.setId(11L);

        when(courseRepository.findById(11L)).thenReturn(Optional.of(course));
        when(termRepository.findByActiveTrue()).thenReturn(Optional.empty());
        when(offeringRepository.save(any(CourseOffering.class))).thenAnswer(inv -> {
            CourseOffering saved = inv.getArgument(0);
            saved.setId(100L + saved.getSection().charAt(0));
            return saved;
        });
        when(offeringRepository.existsByCourse_IdAndAcademicYearAndYearOfStudyAndSemesterAndSection(
                eq(11L), eq("2026-2027"), eq(1), eq(1), anyString())).thenReturn(false);
        when(offeringRepository.existsByCourse_IdAndAcademicYearAndYearOfStudyAndSemesterAndSection(
                eq(11L), eq("2026-2027"), eq(1), eq(1), eq("A"))).thenReturn(true);

        var created = academicsService.createOfferingsBulk(
                new com.iet.dashboard.academics.web.CreateOfferingsBulkRequest(11L, "2026-2027", 1, 1, 3, null));

        // Section A already existed — only B and C are created, auto-lettered in order.
        assertEquals(2, created.size());
        assertEquals("B", created.get(0).section());
        assertEquals("C", created.get(1).section());
    }

    @Test
    void testCreateTeachingAssignment_SameFacultyMayOwnDifferentComponentsOfSameSection() {
        // One faculty member can own both the lecture and a practical batch of the same
        // course section — the unique key is (offering, faculty, component, sub-section).
        Course course = new Course("CS501", "Networks", "THEORY", 4, "3-0-2", 3, 0, 2, null);
        CourseOffering offering = offeringFor(course, "A");
        FacultyDto faculty = new FacultyDto(2L, 2L, "Dr. Amit", "Assoc Prof", "PhD", "IT", Collections.emptyList());

        when(offeringRepository.findById(1L)).thenReturn(Optional.of(offering));
        when(facultyService.getFacultyById(2L)).thenReturn(faculty);
        when(assignmentRepository.save(any(TeachingAssignment.class))).thenAnswer(inv -> inv.getArgument(0));

        TeachingAssignmentDto lecture = academicsService.createTeachingAssignment(
                new CreateTeachingAssignmentRequest(1L, 2L, "LECTURE", "ALL", null));
        TeachingAssignmentDto practical = academicsService.createTeachingAssignment(
                new CreateTeachingAssignmentRequest(1L, 2L, "PRACTICAL", "A1", null));

        assertEquals(3, lecture.contactHours());
        assertEquals(2, practical.contactHours());
        assertEquals(2, practical.facultyId());
        assertEquals(1L, practical.offering().id());
    }

    @Test
    void testCreateTeachingAssignment_DifferentFacultyMayOwnSameComponentOfSameSection() {
        // The lecture goes to one faculty member while the tutorial batches of the same
        // section go to another — a different owner per component is the normal case.
        Course course = new Course("CS502", "DBMS", "THEORY", 4, "3-1-0", 3, 1, 0, null);
        CourseOffering offering = offeringFor(course, "A");
        FacultyDto lecturer = new FacultyDto(2L, 2L, "Dr. Amit", "Assoc Prof", "PhD", "IT", Collections.emptyList());
        FacultyDto tutor = new FacultyDto(3L, 3L, "Prof. Priya", "Asst Prof", "PhD", "IT", Collections.emptyList());

        when(offeringRepository.findById(1L)).thenReturn(Optional.of(offering));
        when(facultyService.getFacultyById(2L)).thenReturn(lecturer);
        when(facultyService.getFacultyById(3L)).thenReturn(tutor);
        when(assignmentRepository.save(any(TeachingAssignment.class))).thenAnswer(inv -> inv.getArgument(0));

        TeachingAssignmentDto lecture = academicsService.createTeachingAssignment(
                new CreateTeachingAssignmentRequest(1L, 2L, "LECTURE", "ALL", null));
        TeachingAssignmentDto tutorialBatch = academicsService.createTeachingAssignment(
                new CreateTeachingAssignmentRequest(1L, 3L, "TUTORIAL", "A1", null));

        assertEquals(2L, lecture.facultyId());
        assertEquals(3L, tutorialBatch.facultyId());
        assertEquals("TUTORIAL", tutorialBatch.component());
        assertEquals("A1", tutorialBatch.subSection());
        assertEquals(1, tutorialBatch.contactHours());
    }

    @Test
    void testGetFacultyLtpLoad_ComputesBucketsFromContactHours() {
        Course networks = new Course("CS501", "Networks", "THEORY", 4, "3-0-2", 3, 0, 2, null);
        Course dbms = new Course("CS502", "DBMS", "THEORY", 4, "3-1-0", 3, 1, 0, null);

        TeachingAssignment lecture = new TeachingAssignment(offeringFor(networks, "A"), 2L, CourseComponent.LECTURE, "ALL", 3);
        TeachingAssignment lab = new TeachingAssignment(offeringFor(networks, "A"), 2L, CourseComponent.PRACTICAL, "A1", 2);
        TeachingAssignment tutorial = new TeachingAssignment(offeringFor(dbms, "A"), 3L, CourseComponent.TUTORIAL, "ALL", 1);

        when(assignmentRepository.findAllWithCourseAndOffering()).thenReturn(List.of(lecture, lab, tutorial));
        when(facultyService.getAllFaculty(null, null)).thenReturn(List.of(
                new FacultyDto(2L, 2L, "Dr. Amit", "Assoc Prof", "PhD", "IT", Collections.emptyList()),
                new FacultyDto(3L, 3L, "Prof. Priya", "Asst Prof", "PhD", "IT", Collections.emptyList())));

        List<FacultyLtpLoadDto> load = academicsService.getFacultyLtpLoad();

        assertEquals(2, load.size());
        // Sorted by total hours descending — Dr. Amit (3L + 2P = 5) first
        FacultyLtpLoadDto amit = load.get(0);
        assertEquals("Dr. Amit", amit.facultyName());
        assertEquals(3, amit.lectureHours());
        assertEquals(0, amit.tutorialHours());
        assertEquals(2, amit.practicalHours());
        assertEquals(5, amit.totalHours());
        assertEquals(2, amit.assignmentCount());

        FacultyLtpLoadDto priya = load.get(1);
        assertEquals(1, priya.tutorialHours());
        assertEquals(1, priya.totalHours());
    }

    @Test
    void testGetFacultyLtpLoad_SplitsAnAllComponentAcrossLtpBuckets() {
        Course networks = new Course("CS501", "Networks", "THEORY", 4, "3-1-2", 3, 1, 2, null);
        TeachingAssignment everything = new TeachingAssignment(
                offeringFor(networks, "A"), 2L, CourseComponent.ALL, "ALL", 6);

        when(assignmentRepository.findAllWithCourseAndOffering()).thenReturn(List.of(everything));
        when(facultyService.getAllFaculty(null, null)).thenReturn(List.of(
                new FacultyDto(2L, 2L, "Dr. Amit", "Assoc Prof", "PhD", "IT", Collections.emptyList())));

        FacultyLtpLoadDto load = academicsService.getFacultyLtpLoad().get(0);

        assertEquals(3, load.lectureHours());
        assertEquals(1, load.tutorialHours());
        assertEquals(2, load.practicalHours());
        assertEquals(6, load.totalHours());
    }
}
