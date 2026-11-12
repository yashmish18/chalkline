package com.iet.dashboard.academics.service;

import com.iet.dashboard.academics.domain.CourseComponent;
import com.iet.dashboard.academics.domain.TeachingAssignment;
import com.iet.dashboard.academics.repo.AcademicTermRepository;
import com.iet.dashboard.academics.repo.CourseOfferingRepository;
import com.iet.dashboard.academics.repo.CourseRepository;
import com.iet.dashboard.academics.repo.TeachingAssignmentRepository;
import com.iet.dashboard.academics.web.AllocationImportResultDto;
import com.iet.dashboard.directory.domain.Faculty;
import com.iet.dashboard.directory.repo.DepartmentRepository;
import com.iet.dashboard.directory.repo.FacultyRepository;
import com.iet.dashboard.identity.domain.Role;
import com.iet.dashboard.identity.repo.AppUserRepository;
import org.apache.poi.ss.usermodel.Row;
import org.apache.poi.ss.usermodel.Sheet;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.context.ActiveProfiles;

import java.io.ByteArrayOutputStream;
import java.nio.charset.StandardCharsets;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/** The allocation sheet as it actually arrives: exported from Excel, hand-edited, pasted twice. */
@DataJpaTest
@ActiveProfiles("test")
class AllocationImportEdgeCaseTest {

    private static final String HEADER =
            "Faculty Name,Email,Course Code,Course Name,L-T-P,Semester,Section,Section Students,Component,Batch,Batch Students\n";

    @Autowired com.iet.dashboard.directory.repo.SkillRepository skillRepo;
    @Autowired com.iet.dashboard.academics.repo.TimetableSlotRepository slotRepo;
    @Autowired AppUserRepository users;
    @Autowired FacultyRepository faculty;
    @Autowired DepartmentRepository departments;
    @Autowired CourseRepository courses;
    @Autowired CourseOfferingRepository offerings;
    @Autowired TeachingAssignmentRepository assignments;
    @Autowired AcademicTermRepository terms;

    private AllocationImportService importer;

    @BeforeEach
    void setUp() {
        importer = new AllocationImportServiceImpl(users, faculty, departments, courses, offerings, assignments, terms, skillRepo, slotRepo);
    }

    private static MockMultipartFile csv(String body) {
        return new MockMultipartFile("file", "allocations.csv", "text/csv", body.getBytes(StandardCharsets.UTF_8));
    }

    @Test
    void blankLinesBetweenAndAfterRowsAreIgnored() {
        AllocationImportResultDto result = importer.importAllocations(csv(HEADER + """
                Dr. Amit Sinhal,amit@iet.edu,CS1139,Programming-I,3-0-2,1,C,64,Lecture,,

                   \t
                Dr. Surbhi Chhabra,surbhi@iet.edu,CS1139,Programming-I,3-0-2,1,C,64,Practical,C2,32


                """));

        assertThat(result.rows()).isEqualTo(2);
        assertThat(result.issues()).isEmpty();
        assertThat(result.assignmentsCreated()).isEqualTo(2);
    }

    /** Excel's "CSV UTF-8" puts a byte-order mark in front of the first header. */
    @Test
    void aByteOrderMarkDoesNotHideTheFirstColumn() {
        AllocationImportResultDto result = importer.importAllocations(csv("﻿" + HEADER
                + "Dr. Amit Sinhal,amit@iet.edu,CS1139,Programming-I,3-0-2,1,C,64,Lecture,,\n"));

        assertThat(result.issues()).isEmpty();
        assertThat(faculty.findAll()).extracting(Faculty::getName).containsExactly("Dr. Amit Sinhal");
    }

    @Test
    void aBlankLineAboveTheHeaderIsSkipped() {
        AllocationImportResultDto result = importer.importAllocations(csv("\n  \n" + HEADER
                + "Dr. Amit Sinhal,amit@iet.edu,CS1139,Programming-I,3-0-2,1,C,64,Lecture,,\n"));

        assertThat(result.issues()).isEmpty();
        assertThat(result.assignmentsCreated()).isEqualTo(1);
    }

    @Test
    void extraWhitespaceAroundCellsAndInsideNamesIsTrimmed() {
        importer.importAllocations(csv(HEADER
                + "  Dr.   Amit Sinhal  ,  Amit@IET.edu  ,  cs1139 , Programming-I , 3 - 0 - 2 , 1 ,  c , 64 ,  Practical , c1 , 32 \n"));

        assertThat(users.findByEmailIgnoreCase("amit@iet.edu")).get()
                .satisfies(u -> assertThat(u.getEmail()).isEqualTo("amit@iet.edu"));
        assertThat(faculty.findAll()).extracting(Faculty::getName).containsExactly("Dr. Amit Sinhal");
        assertThat(courses.findByCodeIgnoreCase("CS1139")).get()
                .satisfies(c -> {
                    assertThat(c.getCode()).isEqualTo("CS1139");
                    assertThat(c.getLtp()).isEqualTo("3-0-2");
                });
        assertThat(offerings.findAll()).singleElement().satisfies(o -> assertThat(o.getSection()).isEqualTo("C"));
        assertThat(assignments.findAll()).singleElement().satisfies(a -> {
            assertThat(a.getSubSection()).isEqualTo("C1");
            assertThat(a.getStudentCount()).isEqualTo(32);
        });
    }

    @Test
    void componentsAreReadInAnySpellingAndCase() {
        AllocationImportResultDto result = importer.importAllocations(csv(HEADER + """
                Dr. A,a@iet.edu,CS1139,Programming-I,3-1-2,1,C,64,l,,
                Dr. A,a@iet.edu,CS1139,Programming-I,3-1-2,1,C,64,T,,
                Dr. A,a@iet.edu,CS1139,Programming-I,3-1-2,1,C,64,p,C1,32
                Dr. A,a@iet.edu,CS1139,Programming-I,3-1-2,1,C,64,Lab,C2,32
                Dr. A,a@iet.edu,CS1139,Programming-I,3-1-2,1,D,64,tutorial,,
                Dr. A,a@iet.edu,CS1139,Programming-I,3-1-2,1,D,64,PRACTICAL,D1,32
                Dr. A,a@iet.edu,CS1139,Programming-I,3-1-2,1,D,64,Theory,,
                """));

        assertThat(result.issues()).isEmpty();
        List<TeachingAssignment> all = assignments.findAll();
        assertThat(all).extracting(TeachingAssignment::getComponent).containsExactlyInAnyOrder(
                CourseComponent.LECTURE, CourseComponent.TUTORIAL, CourseComponent.PRACTICAL, CourseComponent.PRACTICAL,
                CourseComponent.TUTORIAL, CourseComponent.PRACTICAL, CourseComponent.LECTURE);
        assertThat(all).noneMatch(a -> a.getComponent() == CourseComponent.LAB || a.getComponent() == CourseComponent.THEORY);
    }

    @Test
    void theWholeCourseComponentIsRefusedBecauseEveryRowMustSayLectureTutorialOrPractical() {
        AllocationImportResultDto result = importer.importAllocations(csv(HEADER
                + "Dr. A,a@iet.edu,CS1139,Programming-I,3-1-2,1,E,64,all,,\n"));

        assertThat(result.imported()).isFalse();
        assertThat(result.issues()).anyMatch(i -> i.row() == 2 && i.message().contains("Lecture, Tutorial or Practical"));
    }

    /** "Batch C" on section C is the whole section; "Batch C1" is the C1 split. */
    @Test
    void batchesWrittenWithTheWordBatchAreNormalised() {
        importer.importAllocations(csv(HEADER + """
                Dr. A,a@iet.edu,CS1139,Programming-I,3-0-2,1,C,64,Practical,Batch C1,32
                Dr. A,a@iet.edu,CS1139,Programming-I,3-0-2,1,C,64,Practical,batch  c2,32
                Dr. B,b@iet.edu,CS1139,Programming-I,3-0-2,1,C,64,Practical,Batch C,64
                Dr. B,b@iet.edu,CS1139,Programming-I,3-0-2,1,D,64,Practical,2,32
                """));

        assertThat(assignments.findAll()).extracting(TeachingAssignment::getSubSection)
                .containsExactlyInAnyOrder("C1", "C2", "ALL", "D2");
        assertThat(assignments.findAll()).filteredOn(a -> a.getSubSection().equals("ALL")).singleElement()
                .extracting(TeachingAssignment::getStudentCount).as("a whole-section row has no batch strength").isNull();
    }

    @Test
    void aRowRepeatedInOneSheetCreatesOneAssignment() {
        String row = "Dr. Amit Sinhal,amit@iet.edu,CS1139,Programming-I,3-0-2,1,C,64,Practical,C1,32\n";
        AllocationImportResultDto result = importer.importAllocations(csv(HEADER + row + row));

        assertThat(result.issues()).singleElement().satisfies(i -> {
            assertThat(i.severity()).isEqualTo("WARN");
            assertThat(i.row()).isEqualTo(3);
        });
        assertThat(result.assignmentsCreated()).isEqualTo(1);
        assertThat(result.assignmentsUpdated()).isZero();
        assertThat(result.facultyCreated()).isEqualTo(1);
        assertThat(result.offeringsCreated()).isEqualTo(1);
        assertThat(assignments.findAll()).hasSize(1);
        assertThat(users.findAll()).hasSize(1);
    }

    @Test
    void namesAreKeptExactlyAsWrittenAndTheSamePersonIsMatchedByEmail() {
        importer.importAllocations(csv(HEADER + """
                Prof. José D'Souza,Jose.DSouza@iet.edu,CS1139,Programming-I,3-0-2,1,C,64,Lecture,,
                Prof. José D'Souza,jose.dsouza@iet.edu,CS1139,Programming-I,3-0-2,1,D,64,Lecture,,
                """));

        assertThat(users.findAll()).singleElement().satisfies(u -> {
            assertThat(u.getEmail()).isEqualTo("jose.dsouza@iet.edu");
            assertThat(u.getName()).isEqualTo("Prof. José D'Souza");
            assertThat(u.getRole()).isEqualTo(Role.FACULTY);
        });
        assertThat(assignments.findAll()).hasSize(2);
    }

    @Test
    void aHeaderOnlySheetIsRefused() {
        assertThatThrownBy(() -> importer.importAllocations(csv(HEADER)))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("no rows");
        assertThatThrownBy(() -> importer.importAllocations(csv("")))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("empty");
    }

    @Test
    void aSheetWithoutARequiredColumnIsRefused() {
        assertThatThrownBy(() -> importer.importAllocations(csv("""
                Faculty Name,Course Code,Section
                Dr. A,CS1139,C
                """)))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("Missing column \"email\"");
    }

    @Test
    void rowsWithoutANameOrCodeOrWithASemesterOutOfRangeAreReportedAndNothingIsSaved() {
        AllocationImportResultDto result = importer.importAllocations(csv(HEADER + """
                ,a@iet.edu,CS1139,Programming-I,3-0-2,1,C,64,Lecture,,
                Dr. A,a@iet.edu,,Programming-I,3-0-2,1,C,64,Lecture,,
                Dr. A,a@iet.edu,CS1139,Programming-I,3-0-2,9,C,64,Lecture,,
                Dr. A,a@iet.edu,CS1139,Programming-I,3-0-2,0,C,64,Lecture,,
                Dr. A,a@iet.edu,CS1139,Programming-I,3-0-2,Sem 2,C,64,Lecture,,
                Dr. A,a@iet.edu,CS1139,Programming-I,3-0-2,2,C,64,Lecture,,
                """));

        assertThat(result.issues()).filteredOn(i -> i.row() > 0).extracting(AllocationImportResultDto.Issue::row)
                .containsExactly(2, 3, 4, 5, 6);
        assertThat(result.imported()).isFalse();
        assertThat(offerings.findAll()).isEmpty();
    }

    @Test
    void readsAnExcelWorkbook() throws Exception {
        byte[] xlsx;
        try (XSSFWorkbook wb = new XSSFWorkbook(); ByteArrayOutputStream out = new ByteArrayOutputStream()) {
            Sheet sheet = wb.createSheet("Load");
            String[][] cells = {
                    HEADER.trim().split(","),
                    {"Dr. Amit Sinhal", "amit@iet.edu", "CS1139", "Programming-I", "3-0-2", "1", "C", "64", "Practical", "C1", "32"},
                    {"", "", "", "", "", "", "", "", "", "", ""},
                    {"Dr. Surbhi Chhabra", "surbhi@iet.edu", "CS1139", "Programming-I", "3-0-2", "1", "C", "64", "Lecture", "", ""},
            };
            for (int r = 0; r < cells.length; r++) {
                Row row = sheet.createRow(r);
                for (int c = 0; c < cells[r].length; c++) {
                    if (r > 0 && (c == 5 || c == 7 || c == 10) && !cells[r][c].isEmpty()) {
                        row.createCell(c).setCellValue(Double.parseDouble(cells[r][c]));
                    } else {
                        row.createCell(c).setCellValue(cells[r][c]);
                    }
                }
            }
            wb.write(out);
            xlsx = out.toByteArray();
        }

        AllocationImportResultDto result = importer.importAllocations(new MockMultipartFile(
                "file", "Allocations.XLSX", "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet", xlsx));

        assertThat(result.rows()).as("the blank row is not a row").isEqualTo(2);
        assertThat(result.issues()).filteredOn(i -> !i.severity().equals("WARN")).isEmpty();
        assertThat(assignments.findAll()).filteredOn(a -> a.getSubSection().equals("C1")).singleElement()
                .extracting(TeachingAssignment::getStudentCount).isEqualTo(32);
        assertThat(users.findByEmailIgnoreCase("surbhi@iet.edu")).isPresent();
    }
}
