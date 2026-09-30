package com.iet.dashboard.directory.service;

import com.iet.dashboard.academics.domain.AcademicTerm;
import com.iet.dashboard.academics.domain.Course;
import com.iet.dashboard.academics.domain.CourseOffering;
import com.iet.dashboard.academics.repo.*;
import com.iet.dashboard.directory.domain.Faculty;
import com.iet.dashboard.directory.domain.Skill;
import com.iet.dashboard.directory.repo.FacultyRepository;
import com.iet.dashboard.directory.repo.SkillRepository;
import com.iet.dashboard.directory.web.ImportResultDto;
import com.iet.dashboard.identity.domain.AppUser;
import com.iet.dashboard.identity.domain.Role;
import com.iet.dashboard.identity.repo.AppUserRepository;
import org.apache.poi.ss.usermodel.Row;
import org.apache.poi.ss.usermodel.Sheet;
import org.apache.poi.ss.usermodel.Workbook;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.mock.web.MockMultipartFile;

import java.io.ByteArrayOutputStream;
import java.nio.charset.StandardCharsets;
import java.time.LocalDate;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class FacultyImportServiceTest {

    @Mock
    private AppUserRepository userRepository;

    @Mock
    private FacultyRepository facultyRepository;

    @Mock
    private SkillRepository skillRepository;

    @Mock
    private CourseRepository courseRepository;

    @Mock
    private CourseOfferingRepository offeringRepository;

    @Mock
    private TeachingAssignmentRepository assignmentRepository;

    @Mock
    private AcademicTermRepository termRepository;

    private FacultyImportServiceImpl importService;

    @BeforeEach
    void setUp() {
        importService = new FacultyImportServiceImpl(
                userRepository,
                facultyRepository,
                skillRepository,
                courseRepository,
                offeringRepository,
                assignmentRepository,
                termRepository
        );
    }

    @Test
    void testImportFromCsv() {
        String csv = "Faculty Name,Email,Department,Designation,Qualifications,Skills,Course Code,Course Name,Section,Component,Sub Section\n" +
                "Dr. Neha Gupta,neha.gupta@iet.edu,Information Technology,Associate Professor,Ph.D.,\"Cloud, AI\",CS601,Cloud Systems,A,THEORY,ALL\n";

        MockMultipartFile file = new MockMultipartFile(
                "file", "faculties.csv", "text/csv", csv.getBytes(StandardCharsets.UTF_8)
        );

        when(termRepository.findByActiveTrue()).thenReturn(Optional.of(
                new AcademicTerm("Semester 1 (2026-27)", "2026-2027", LocalDate.now(), LocalDate.now().plusMonths(4), true)
        ));

        when(userRepository.findByEmailIgnoreCase("neha.gupta@iet.edu")).thenReturn(Optional.empty());
        AppUser savedUser = new AppUser("neha.gupta@iet.edu", "Dr. Neha Gupta", Role.FACULTY);
        savedUser.setId(10L);
        when(userRepository.save(any(AppUser.class))).thenReturn(savedUser);

        when(facultyRepository.findByUserId(10L)).thenReturn(Optional.empty());
        Faculty savedFaculty = new Faculty(10L, "Dr. Neha Gupta", "Associate Professor", "Ph.D.", "Information Technology");
        savedFaculty.setId(100L);
        when(facultyRepository.save(any(Faculty.class))).thenReturn(savedFaculty);

        when(skillRepository.findByNameIgnoreCase("Cloud")).thenReturn(Optional.of(new Skill("Cloud", "Technical")));
        when(skillRepository.findByNameIgnoreCase("AI")).thenReturn(Optional.of(new Skill("AI", "Technical")));

        Course course = new Course("CS601", "Cloud Systems", "THEORY");
        course.setId(200L);
        when(courseRepository.findByCodeIgnoreCase("CS601")).thenReturn(Optional.of(course));

        CourseOffering offering = new CourseOffering(course, "2026-2027", 3, 5, "A");
        offering.setId(300L);
        when(offeringRepository.findFirstByCourseIdAndSection(200L, "A")).thenReturn(Optional.of(offering));
        when(assignmentRepository.existsByOfferingIdAndFacultyIdAndComponentAndSubSection(any(), any(), any(), any())).thenReturn(false);

        ImportResultDto result = importService.importFacultyAndAllocations(file);

        assertThat(result.totalRows()).isEqualTo(1);
        assertThat(result.facultyCreated()).isEqualTo(1);
        assertThat(result.assignmentsCreated()).isEqualTo(1);
    }

    @Test
    void testImportFromExcel() throws Exception {
        Workbook wb = new XSSFWorkbook();
        Sheet sheet = wb.createSheet("Faculty");

        Row header = sheet.createRow(0);
        header.createCell(0).setCellValue("Faculty Name");
        header.createCell(1).setCellValue("Email");
        header.createCell(2).setCellValue("Department");
        header.createCell(3).setCellValue("Course Code");
        header.createCell(4).setCellValue("Section");

        Row r1 = sheet.createRow(1);
        r1.createCell(0).setCellValue("Prof. Suresh Verma");
        r1.createCell(1).setCellValue("suresh.verma@iet.edu");
        r1.createCell(2).setCellValue("Information Technology");
        r1.createCell(3).setCellValue("CS502");
        r1.createCell(4).setCellValue("B");

        ByteArrayOutputStream out = new ByteArrayOutputStream();
        wb.write(out);
        wb.close();

        MockMultipartFile file = new MockMultipartFile(
                "file", "faculties.xlsx", "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet", out.toByteArray()
        );

        when(termRepository.findByActiveTrue()).thenReturn(Optional.empty());
        when(userRepository.findByEmailIgnoreCase("suresh.verma@iet.edu")).thenReturn(Optional.empty());

        AppUser savedUser = new AppUser("suresh.verma@iet.edu", "Prof. Suresh Verma", Role.FACULTY);
        savedUser.setId(20L);
        when(userRepository.save(any(AppUser.class))).thenReturn(savedUser);

        Faculty savedFaculty = new Faculty(20L, "Prof. Suresh Verma", "Assistant Professor", "M.Tech / Ph.D.", "Information Technology");
        savedFaculty.setId(200L);
        when(facultyRepository.findByUserId(20L)).thenReturn(Optional.empty());
        when(facultyRepository.save(any(Faculty.class))).thenReturn(savedFaculty);

        Course course = new Course("CS502", "Database Systems", "THEORY");
        course.setId(502L);
        when(courseRepository.findByCodeIgnoreCase("CS502")).thenReturn(Optional.of(course));

        CourseOffering offering = new CourseOffering(course, "2026-2027", 3, 5, "B");
        offering.setId(602L);
        when(offeringRepository.findFirstByCourseIdAndSection(502L, "B")).thenReturn(Optional.of(offering));
        when(assignmentRepository.existsByOfferingIdAndFacultyIdAndComponentAndSubSection(any(), any(), any(), any())).thenReturn(false);

        ImportResultDto result = importService.importFacultyAndAllocations(file);

        assertThat(result.totalRows()).isEqualTo(1);
        assertThat(result.facultyCreated()).isEqualTo(1);
        assertThat(result.assignmentsCreated()).isEqualTo(1);
    }

    @Test
    void testGenerateTemplateCsv() {
        byte[] csv = importService.generateTemplateCsv();
        String content = new String(csv, StandardCharsets.UTF_8);
        assertThat(content).contains("Faculty Name,Email");
    }
}
