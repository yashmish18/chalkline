package com.iet.dashboard.directory.service;

import com.iet.dashboard.academics.domain.*;
import com.iet.dashboard.academics.repo.*;
import com.iet.dashboard.directory.domain.Faculty;
import com.iet.dashboard.directory.domain.Skill;
import com.iet.dashboard.directory.repo.FacultyRepository;
import com.iet.dashboard.directory.repo.SkillRepository;
import com.iet.dashboard.directory.web.ImportResultDto;
import com.iet.dashboard.identity.domain.AppUser;
import com.iet.dashboard.identity.domain.Role;
import com.iet.dashboard.identity.repo.AppUserRepository;
import org.apache.poi.ss.usermodel.*;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

import java.io.BufferedReader;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.*;

@Service
@Transactional
public class FacultyImportServiceImpl implements FacultyImportService {

    private static final Logger log = LoggerFactory.getLogger(FacultyImportServiceImpl.class);

    private final AppUserRepository userRepository;
    private final FacultyRepository facultyRepository;
    private final SkillRepository skillRepository;
    private final CourseRepository courseRepository;
    private final CourseOfferingRepository offeringRepository;
    private final TeachingAssignmentRepository assignmentRepository;
    private final AcademicTermRepository termRepository;

    public FacultyImportServiceImpl(
            AppUserRepository userRepository,
            FacultyRepository facultyRepository,
            SkillRepository skillRepository,
            CourseRepository courseRepository,
            CourseOfferingRepository offeringRepository,
            TeachingAssignmentRepository assignmentRepository,
            AcademicTermRepository termRepository
    ) {
        this.userRepository = userRepository;
        this.facultyRepository = facultyRepository;
        this.skillRepository = skillRepository;
        this.courseRepository = courseRepository;
        this.offeringRepository = offeringRepository;
        this.assignmentRepository = assignmentRepository;
        this.termRepository = termRepository;
    }

    @Override
    public ImportResultDto importFacultyAndAllocations(MultipartFile file) {
        if (file == null || file.isEmpty()) {
            throw new IllegalArgumentException("Uploaded file cannot be empty");
        }

        String originalFilename = file.getOriginalFilename() != null ? file.getOriginalFilename().toLowerCase() : "";
        List<Map<String, String>> rows;

        try {
            if (originalFilename.endsWith(".xlsx") || originalFilename.endsWith(".xls")) {
                rows = parseExcel(file.getInputStream());
            } else {
                rows = parseCsv(file.getInputStream());
            }
        } catch (Exception e) {
            log.error("Failed to parse import file: {}", e.getMessage(), e);
            throw new IllegalArgumentException("Failed to parse spreadsheet file: " + e.getMessage());
        }

        int totalRows = rows.size();
        int facultyCreated = 0;
        int facultyUpdated = 0;
        int assignmentsCreated = 0;
        List<String> messages = new ArrayList<>();

        AcademicTerm activeTerm = termRepository.findByActiveTrue()
                .orElseGet(() -> termRepository.findAll().stream().findFirst().orElse(null));

        for (int i = 0; i < rows.size(); i++) {
            Map<String, String> row = rows.get(i);
            int rowNum = i + 2; // header is row 1
            String email = row.getOrDefault("email", "").trim().toLowerCase();
            String name = row.getOrDefault("name", "").trim();

            if (email.isEmpty()) {
                messages.add("Row " + rowNum + " skipped: missing email address.");
                continue;
            }
            if (name.isEmpty()) {
                name = email.split("@")[0].replace('.', ' ');
            }

            String department = row.getOrDefault("department", "Information Technology").trim();
            if (department.isEmpty()) department = "Information Technology";
            String designation = row.getOrDefault("designation", "Assistant Professor").trim();
            if (designation.isEmpty()) designation = "Assistant Professor";
            String qualifications = row.getOrDefault("qualifications", "M.Tech / Ph.D.").trim();
            if (qualifications.isEmpty()) qualifications = "M.Tech / Ph.D.";
            String skillsStr = row.getOrDefault("skills", "").trim();

            // 1. Find or create AppUser
            AppUser user = userRepository.findByEmailIgnoreCase(email).orElse(null);
            boolean isNewFaculty = false;
            if (user == null) {
                user = new AppUser(email, name, Role.FACULTY);
                user = userRepository.save(user);
                isNewFaculty = true;
            }

            // 2. Find or create Faculty
            Faculty faculty = facultyRepository.findByUserId(user.getId()).orElse(null);
            if (faculty == null) {
                faculty = new Faculty(user.getId(), name, designation, qualifications, department);
                faculty = facultyRepository.save(faculty);
                facultyCreated++;
            } else {
                faculty.setName(name);
                faculty.setDesignation(designation);
                faculty.setQualifications(qualifications);
                faculty.setDepartment(department);
                faculty = facultyRepository.save(faculty);
                if (!isNewFaculty) {
                    facultyUpdated++;
                }
            }

            // 3. Process Skills
            if (!skillsStr.isEmpty()) {
                String[] skillTokens = skillsStr.split("[,;]");
                Set<Skill> skillSet = new HashSet<>(faculty.getSkills());
                for (String token : skillTokens) {
                    String cleanSkill = token.trim();
                    if (!cleanSkill.isEmpty()) {
                        Skill sk = skillRepository.findByNameIgnoreCase(cleanSkill)
                                .orElseGet(() -> skillRepository.save(new Skill(cleanSkill, "Technical")));
                        skillSet.add(sk);
                    }
                }
                faculty.setSkills(skillSet);
                facultyRepository.save(faculty);
            }

            // 4. Process Course & Teaching Allocation (if course code provided)
            String courseCode = row.getOrDefault("coursecode", row.getOrDefault("course_code", "")).trim().toUpperCase();
            if (!courseCode.isEmpty()) {
                String courseName = row.getOrDefault("coursename", row.getOrDefault("course_name", courseCode + " Course")).trim();
                String rawSection = row.getOrDefault("section", "A").trim().toUpperCase();
                String cleanSection = rawSection.replaceAll("^(SEC|SECTION|DIV|DIVISION)\\s*", "").trim();
                if (cleanSection.isEmpty()) cleanSection = "A";

                String componentStr = row.getOrDefault("component", row.getOrDefault("type", "THEORY")).trim().toUpperCase();
                CourseComponent component = (componentStr.contains("LAB") || componentStr.contains("PRACTICAL"))
                        ? CourseComponent.LAB
                        : CourseComponent.THEORY;

                String subSection = row.getOrDefault("subsection", row.getOrDefault("sub_section", row.getOrDefault("batch", "ALL"))).trim().toUpperCase();
                if (subSection.isEmpty()) subSection = "ALL";

                // Find or create Course
                Course course = courseRepository.findByCodeIgnoreCase(courseCode).orElse(null);
                if (course == null) {
                    course = new Course(courseCode, courseName, component == CourseComponent.LAB ? "LAB" : "THEORY");
                    if (component == CourseComponent.LAB) {
                        course.setLtp("0-0-4");
                        course.setLectureHours(0);
                        course.setTutorialHours(0);
                        course.setPracticalHours(4);
                        course.setCredits(2);
                    } else {
                        course.setLtp("3-0-0");
                        course.setLectureHours(3);
                        course.setTutorialHours(0);
                        course.setPracticalHours(0);
                        course.setCredits(3);
                    }
                    course = courseRepository.save(course);
                }

                // Find or create CourseOffering for this section
                String academicYear = (activeTerm != null) ? activeTerm.getAcademicYear() : "2026-2027";
                CourseOffering offering = offeringRepository.findFirstByCourseIdAndSection(course.getId(), cleanSection).orElse(null);
                if (offering == null) {
                    offering = new CourseOffering(course, academicYear, 3, 5, cleanSection, activeTerm);
                    offering = offeringRepository.save(offering);
                }

                // Check and create TeachingAssignment
                boolean exists = assignmentRepository.existsByOfferingIdAndFacultyIdAndComponentAndSubSection(
                        offering.getId(), faculty.getId(), component, subSection
                );
                if (!exists) {
                    TeachingAssignment assignment = new TeachingAssignment(offering, faculty.getId(), component, subSection);
                    assignmentRepository.save(assignment);
                    assignmentsCreated++;
                }
            }
        }

        messages.add(String.format("Bulk import completed successfully: %d total rows processed, %d faculty created, %d faculty updated, %d course allocations assigned.",
                totalRows, facultyCreated, facultyUpdated, assignmentsCreated));

        return new ImportResultDto(totalRows, facultyCreated, facultyUpdated, assignmentsCreated, messages);
    }

    @Override
    public byte[] generateTemplateCsv() {
        String csv = "Faculty Name,Email,Department,Designation,Qualifications,Skills,Course Code,Course Name,Section,Component,Sub Section\n" +
                "Dr. Priya Sharma,priya.sharma@iet.edu,Information Technology,Associate Professor,Ph.D. in Computer Science,\"Cloud Computing, Distributed Systems\",CS505,Cloud Computing,A,THEORY,ALL\n" +
                "Prof. Rahul Mehta,rahul.mehta@iet.edu,Information Technology,Assistant Professor,M.Tech in Data Engineering,\"Big Data, SQL, Spark\",CS506,Data Engineering Lab,A,LAB,A1\n" +
                "Prof. Ananya Sen,ananya.sen@iet.edu,Information Technology,Assistant Professor,M.Tech in Software Systems,\"Web Architecture, React\",CS503,Web Technologies Lab,B,LAB,B1\n";
        return csv.getBytes(StandardCharsets.UTF_8);
    }

    private List<Map<String, String>> parseCsv(InputStream in) throws Exception {
        List<Map<String, String>> rows = new ArrayList<>();
        try (BufferedReader reader = new BufferedReader(new InputStreamReader(in, StandardCharsets.UTF_8))) {
            String headerLine = reader.readLine();
            if (headerLine == null) return rows;

            List<String> headers = parseCsvLine(headerLine);
            for (int i = 0; i < headers.size(); i++) {
                headers.set(i, normalizeHeader(headers.get(i)));
            }

            String line;
            while ((line = reader.readLine()) != null) {
                if (line.trim().isEmpty()) continue;
                List<String> values = parseCsvLine(line);
                Map<String, String> row = new HashMap<>();
                for (int i = 0; i < headers.size(); i++) {
                    String val = (i < values.size()) ? values.get(i).trim() : "";
                    row.put(headers.get(i), val);
                }
                rows.add(row);
            }
        }
        return rows;
    }

    private List<Map<String, String>> parseExcel(InputStream in) throws Exception {
        List<Map<String, String>> rows = new ArrayList<>();
        Workbook workbook = WorkbookFactory.create(in);
        Sheet sheet = workbook.getSheetAt(0);

        Row headerRow = sheet.getRow(0);
        if (headerRow == null) return rows;

        List<String> headers = new ArrayList<>();
        for (Cell cell : headerRow) {
            headers.add(normalizeHeader(getCellValueAsString(cell)));
        }

        DataFormatter formatter = new DataFormatter();
        for (int r = 1; r <= sheet.getLastRowNum(); r++) {
            Row row = sheet.getRow(r);
            if (row == null) continue;

            Map<String, String> rowMap = new HashMap<>();
            boolean hasContent = false;
            for (int c = 0; c < headers.size(); c++) {
                Cell cell = row.getCell(c);
                String val = (cell != null) ? formatter.formatCellValue(cell).trim() : "";
                if (!val.isEmpty()) hasContent = true;
                rowMap.put(headers.get(c), val);
            }
            if (hasContent) {
                rows.add(rowMap);
            }
        }
        return rows;
    }

    private String normalizeHeader(String header) {
        if (header == null) return "";
        return header.toLowerCase().replaceAll("[^a-z0-9]", "");
    }

    private List<String> parseCsvLine(String line) {
        List<String> tokens = new ArrayList<>();
        StringBuilder sb = new StringBuilder();
        boolean inQuotes = false;

        for (int i = 0; i < line.length(); i++) {
            char c = line.charAt(i);
            if (c == '\"') {
                inQuotes = !inQuotes;
            } else if (c == ',' && !inQuotes) {
                tokens.add(sb.toString().trim());
                sb.setLength(0);
            } else {
                sb.append(c);
            }
        }
        tokens.add(sb.toString().trim());
        return tokens;
    }

    private String getCellValueAsString(Cell cell) {
        if (cell == null) return "";
        DataFormatter formatter = new DataFormatter();
        return formatter.formatCellValue(cell).trim();
    }
}
