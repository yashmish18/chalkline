package com.iet.dashboard.academics.service;

import com.iet.dashboard.academics.domain.AcademicTerm;
import com.iet.dashboard.academics.domain.Course;
import com.iet.dashboard.academics.domain.CourseComponent;
import com.iet.dashboard.academics.domain.CourseOffering;
import com.iet.dashboard.academics.domain.SubSections;
import com.iet.dashboard.academics.domain.TeachingAssignment;
import com.iet.dashboard.academics.repo.AcademicTermRepository;
import com.iet.dashboard.academics.repo.CourseOfferingRepository;
import com.iet.dashboard.academics.repo.CourseRepository;
import com.iet.dashboard.academics.repo.TeachingAssignmentRepository;
import com.iet.dashboard.academics.web.AllocationImportResultDto;
import com.iet.dashboard.academics.web.AllocationImportResultDto.Issue;
import com.iet.dashboard.directory.domain.Department;
import com.iet.dashboard.directory.domain.Faculty;
import com.iet.dashboard.directory.repo.DepartmentRepository;
import com.iet.dashboard.directory.repo.FacultyRepository;
import com.iet.dashboard.identity.domain.AppUser;
import com.iet.dashboard.identity.domain.Role;
import com.iet.dashboard.identity.config.AppSettings;
import com.iet.dashboard.identity.repo.AppUserRepository;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.beans.factory.annotation.Autowired;
import com.iet.dashboard.shared.spreadsheet.SpreadsheetReader;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

import java.nio.charset.StandardCharsets;
import java.text.Normalizer;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Set;
import java.util.Locale;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * The one importer. The sheet is the source of truth for the semester: each row is one teaching
 * assignment, and re-importing the same sheet updates rows in place instead of duplicating them.
 * A row that cannot be read is reported and skipped — nothing is guessed.
 */
@Service
@Transactional
public class AllocationImportServiceImpl implements AllocationImportService {

    private static final List<Map.Entry<String, List<String>>> HEADER_RULES = List.of(
            Map.entry("name", List.of("=name", "facultyname", "faculty", "teacher")),
            Map.entry("email", List.of("email", "emailid", "mail")),
            Map.entry("designation", List.of("designation", "rank", "=title")),
            Map.entry("department", List.of("department", "dept")),
            Map.entry("code", List.of("coursecode", "subjectcode", "=code")),
            Map.entry("course", List.of("coursename", "subjectname", "coursetitle", "=course")),
            Map.entry("ltp", List.of("ltp")),
            Map.entry("semester", List.of("semester", "=sem")),
            Map.entry("section", List.of("section", "=sec")),
            Map.entry("sectionstudents", List.of("sectionstudents", "sectionstrength", "=students", "=strength")),
            Map.entry("component", List.of("component", "=type")),
            Map.entry("batch", List.of("batch", "subsection")),
            Map.entry("batchstudents", List.of("batchstudents", "batchstrength")),
            Map.entry("hours", List.of("weeklyhours", "contacthours", "=hours")),
            Map.entry("pbl", List.of("pblhours", "=pbl")),
            // Dashboard access per person: "Coordinator" or "Faculty". Blank leaves the role as it is.
            Map.entry("role", List.of("=role", "access", "dashboardrole", "accessrole"))
    );

    private static final Pattern NUMBER = Pattern.compile("\\d+");
    private static final Pattern LTP = Pattern.compile("(\\d+)\\s*[-/ ]\\s*(\\d+)\\s*[-/ ]\\s*(\\d+)");
    private static final Pattern PARENTHETICAL = Pattern.compile("\\(([^)]*)\\)");
    private static final Pattern TITLE = Pattern.compile("\\b(?:Dr|Mr|Mrs|Ms|Prof)\\b\\.?", Pattern.CASE_INSENSITIVE);

    private final AppUserRepository userRepository;
    private final FacultyRepository facultyRepository;
    private final DepartmentRepository departmentRepository;
    private final CourseRepository courseRepository;
    private final CourseOfferingRepository offeringRepository;
    private final TeachingAssignmentRepository assignmentRepository;
    private final AcademicTermRepository termRepository;
    private final String emailDomain;

    @Autowired
    public AllocationImportServiceImpl(
            AppUserRepository userRepository,
            FacultyRepository facultyRepository,
            DepartmentRepository departmentRepository,
            CourseRepository courseRepository,
            CourseOfferingRepository offeringRepository,
            TeachingAssignmentRepository assignmentRepository,
            AcademicTermRepository termRepository,
            AppSettings settings
    ) {
        this.userRepository = userRepository;
        this.facultyRepository = facultyRepository;
        this.departmentRepository = departmentRepository;
        this.courseRepository = courseRepository;
        this.offeringRepository = offeringRepository;
        this.assignmentRepository = assignmentRepository;
        this.termRepository = termRepository;
        this.emailDomain = settings.emailDomain();
    }

    /** With the default email domain; for tests. */
    public AllocationImportServiceImpl(
            AppUserRepository userRepository,
            FacultyRepository facultyRepository,
            DepartmentRepository departmentRepository,
            CourseRepository courseRepository,
            CourseOfferingRepository offeringRepository,
            TeachingAssignmentRepository assignmentRepository,
            AcademicTermRepository termRepository
    ) {
        this.userRepository = userRepository;
        this.facultyRepository = facultyRepository;
        this.departmentRepository = departmentRepository;
        this.courseRepository = courseRepository;
        this.offeringRepository = offeringRepository;
        this.assignmentRepository = assignmentRepository;
        this.termRepository = termRepository;
        this.emailDomain = new AppSettings(false, false).emailDomain();
    }

    @Override
    public AllocationImportResultDto importAllocations(MultipartFile file) {
        if (file == null || file.isEmpty()) {
            throw new IllegalArgumentException("The uploaded file is empty.");
        }
        List<Map<String, String>> rows = SpreadsheetReader.read(file);
        if (rows.isEmpty()) {
            throw new IllegalArgumentException("The sheet has no rows below the header.");
        }
        Map<String, String> columns = SpreadsheetReader.detectColumns(rows.get(0).keySet(), HEADER_RULES);
        for (String required : List.of("name", "code", "semester")) {
            if (!SpreadsheetReader.hasColumn(columns, required)) {
                throw new IllegalArgumentException("Missing column \"" + required
                        + "\". Download the template to see the expected headers.");
            }
        }

        AcademicTerm term = termRepository.findByActiveTrue().orElse(null);
        String academicYear = term != null ? term.getAcademicYear() : "";
        Counts counts = new Counts();
        List<Issue> issues = new ArrayList<>();
        Map<Long, Role> wantedRoles = new LinkedHashMap<>();
        Map<Long, Integer> roleRows = new LinkedHashMap<>();
        Set<String> derivedEmailWarned = new HashSet<>();

        for (int i = 0; i < rows.size(); i++) {
            int rowNumber = i + 2;
            Map<String, String> row = rows.get(i);
            String rawName = cell(row, columns, "name");
            if (rawName.isEmpty()) {
                issues.add(new Issue(rowNumber, "ERROR", "No faculty name; row skipped."));
                continue;
            }
            Integer semester = number(cell(row, columns, "semester"));
            if (semester == null || semester < 1 || semester > 8) {
                issues.add(new Issue(rowNumber, "ERROR", "Semester must be 1–8; row skipped."));
                continue;
            }
            String code = cell(row, columns, "code").toUpperCase(Locale.ROOT);
            if (code.isEmpty()) {
                issues.add(new Issue(rowNumber, "ERROR", "No course code; row skipped."));
                continue;
            }
            String rawComponent = cell(row, columns, "component");
            CourseComponent component;
            try {
                component = rawComponent.isEmpty() ? CourseComponent.LECTURE : CourseComponent.parseStrict(rawComponent);
            } catch (IllegalArgumentException e) {
                issues.add(new Issue(rowNumber, "ERROR", "Component \"" + rawComponent
                        + "\" is not Lecture, Tutorial or Practical; row skipped."));
                continue;
            }

            Faculty member = upsertFaculty(row, columns, rawName, counts);
            if (cell(row, columns, "email").isEmpty() && derivedEmailWarned.add(member.getName())) {
                String derived = userRepository.findById(member.getUserId()).map(AppUser::getEmail).orElse("");
                issues.add(new Issue(rowNumber, "WARN", "No email for " + member.getName() + "; using " + derived
                        + ". It must match their Microsoft email, or they can't sign in."));
            }
            String role = cell(row, columns, "role").toLowerCase(Locale.ROOT);
            if (role.contains("coord") || role.contains("admin")) {
                wantedRoles.put(member.getUserId(), Role.COORDINATOR);   // any Coordinator row wins
                roleRows.putIfAbsent(member.getUserId(), rowNumber);
            } else if (role.contains("faculty") || role.contains("teach")) {
                wantedRoles.putIfAbsent(member.getUserId(), Role.FACULTY);
                roleRows.putIfAbsent(member.getUserId(), rowNumber);
            } else if (!role.isEmpty()) {
                issues.add(new Issue(rowNumber, "WARN", "Role \"" + cell(row, columns, "role")
                        + "\" is not Faculty or Coordinator; treated as Faculty."));
            }
            Course course = upsertCourse(row, columns, code, counts);
            // A course belongs to the department the sheet names on its rows (first one wins).
            if (course.getDepartmentId() == null && member.getDepartmentId() != null) {
                course.setDepartmentId(member.getDepartmentId());
                courseRepository.save(course);
            }
            String section = cell(row, columns, "section").toUpperCase(Locale.ROOT);
            if (section.isEmpty()) {
                section = SubSections.WHOLE_SECTION;
            }
            CourseOffering offering = upsertOffering(row, columns, course, academicYear, semester, section, term, counts);

            String batch = SubSections.normalise(cell(row, columns, "batch"), section);
            Integer hours = number(cell(row, columns, "hours"));
            Integer batchStudents = SubSections.WHOLE_SECTION.equals(batch) ? null : number(cell(row, columns, "batchstudents"));

            TeachingAssignment assignment = assignmentRepository
                    .findByOfferingIdAndFacultyIdAndComponentAndSubSection(offering.getId(), member.getId(), component, batch)
                    .orElse(null);
            if (assignment == null) {
                assignment = new TeachingAssignment(offering, member.getId(), component, batch, 0);
                counts.assignmentsCreated++;
            } else {
                counts.assignmentsUpdated++;
            }
            assignment.setContactHours(hours != null && hours > 0 ? hours : plannedHours(course, component));
            assignment.setStudentCount(batchStudents);
            assignmentRepository.save(assignment);
        }

        assignRoles(wantedRoles, roleRows, counts, issues);

        return new AllocationImportResultDto(rows.size(), counts.facultyCreated, counts.coursesCreated,
                counts.offeringsCreated, counts.assignmentsCreated, counts.assignmentsUpdated,
                counts.rolesChanged, issues);
    }

    /**
     * The Role column decides who reaches the coordinator dashboard. Two people are never demoted by
     * a sheet: the admin, and whoever is uploading it (so nobody locks themselves out).
     */
    private void assignRoles(Map<Long, Role> wantedRoles, Map<Long, Integer> roleRows, Counts counts, List<Issue> issues) {
        var auth = SecurityContextHolder.getContext().getAuthentication();
        String uploader = auth == null ? "" : auth.getName();
        wantedRoles.forEach((userId, wanted) -> userRepository.findById(userId).ifPresent(user -> {
            if (user.getRole() == wanted) {
                return;
            }
            int row = roleRows.get(userId);
            if (wanted == Role.FACULTY && (user.isAdmin() || user.getEmail().equalsIgnoreCase(uploader))) {
                issues.add(new Issue(row, "INFO", user.getEmail() + " stays coordinator"
                        + (user.isAdmin() ? " (the admin)." : " (you can't remove your own access).")));
                return;
            }
            user.setRole(wanted);
            userRepository.save(user);
            counts.rolesChanged++;
            issues.add(new Issue(row, "INFO", user.getEmail() + " is now "
                    + (wanted == Role.COORDINATOR ? "a coordinator." : "faculty (coordinator access removed).")));
        }));
    }

    private Faculty upsertFaculty(Map<String, String> row, Map<String, String> columns, String rawName, Counts counts) {
        // "Dr. Kishtiz Verma (Visiting Faculty)": the note is a designation, not part of the name.
        String note = "";
        Matcher m = PARENTHETICAL.matcher(rawName);
        if (m.find()) {
            note = m.group(1).trim();
        }
        String name = PARENTHETICAL.matcher(rawName).replaceAll(" ").replaceAll("\\s+", " ").trim();
        String designation = cell(row, columns, "designation");
        if (designation.isEmpty() && note.toLowerCase(Locale.ROOT).contains("visit")) {
            designation = "Visiting Faculty";
        }

        String email = cell(row, columns, "email").toLowerCase(Locale.ROOT);
        if (email.isEmpty()) {
            email = emailSlug(name) + "@" + emailDomain;
        }
        AppUser user = userRepository.findByEmailIgnoreCase(email).orElse(null);
        if (user == null) {
            user = userRepository.save(new AppUser(email, name, Role.FACULTY));
        }

        Faculty member = facultyRepository.findByUserId(user.getId()).orElse(null);
        if (member == null) {
            member = new Faculty(user.getId(), name, designation.isEmpty() ? "Assistant Professor" : designation, "", null);
            counts.facultyCreated++;
        } else {
            if (!designation.isEmpty()) {
                member.setDesignation(designation);
            }
            // The sheet names people; an account created before it (e.g. the admin) takes that name.
            if (!name.equals(member.getName())) {
                member.setName(name);
            }
        }
        if (!name.equals(user.getName())) {
            user.setName(name);
            userRepository.save(user);
        }
        Department department = department(cell(row, columns, "department"));
        if (department != null) {
            member.setDepartment(department);
        }
        Integer pbl = number(cell(row, columns, "pbl"));
        if (pbl != null) {
            member.setPblHours(pbl);
        }
        return facultyRepository.save(member);
    }

    private Course upsertCourse(Map<String, String> row, Map<String, String> columns, String code, Counts counts) {
        Course course = courseRepository.findByCodeIgnoreCase(code).orElse(null);
        String name = cell(row, columns, "course");
        if (course == null) {
            course = new Course(code, name.isEmpty() ? code : name, "THEORY");
            counts.coursesCreated++;
        } else if (!name.isEmpty()) {
            course.setName(name);
        }
        Matcher ltp = LTP.matcher(cell(row, columns, "ltp"));
        if (ltp.find()) {
            int l = Integer.parseInt(ltp.group(1));
            int t = Integer.parseInt(ltp.group(2));
            int p = Integer.parseInt(ltp.group(3));
            course.setLtp(l + "-" + t + "-" + p);
            course.setLectureHours(l);
            course.setTutorialHours(t);
            course.setPracticalHours(p);
        }
        return courseRepository.save(course);
    }

    private CourseOffering upsertOffering(Map<String, String> row, Map<String, String> columns, Course course,
                                          String academicYear, int semester, String section, AcademicTerm term, Counts counts) {
        int yearOfStudy = (semester + 1) / 2;
        CourseOffering offering = offeringRepository
                .findFirstByCourseIdAndAcademicYearAndYearOfStudyAndSemesterAndSection(
                        course.getId(), academicYear, yearOfStudy, semester, section)
                .orElse(null);
        if (offering == null) {
            offering = new CourseOffering(course, academicYear, yearOfStudy, semester, section, term);
            counts.offeringsCreated++;
        }
        Integer students = number(cell(row, columns, "sectionstudents"));
        if (students != null && students > 0) {
            offering.setStudentCount(students);
        }
        return offeringRepository.save(offering);
    }

    private Department department(String codeOrName) {
        if (codeOrName.isEmpty()) {
            return null;
        }
        return departmentRepository.findByCodeIgnoreCase(codeOrName)
                .or(() -> departmentRepository.findByNameIgnoreCase(codeOrName))
                .orElseGet(() -> departmentRepository.save(new Department(
                        codeOrName.replaceAll("[^A-Za-z]", "").toUpperCase(Locale.ROOT), codeOrName)));
    }

    /** Weekly hours the course's L-T-P plans for a component, used when the sheet gives none. */
    private static int plannedHours(Course course, CourseComponent component) {
        return switch (component) {
            case TUTORIAL -> course.getTutorialHours();
            case PRACTICAL, LAB -> course.getPracticalHours();
            case ALL -> course.getLectureHours() + course.getTutorialHours() + course.getPracticalHours();
            case LECTURE, THEORY -> course.getLectureHours();
        };
    }

    /** "Dr. Amit Sinhal" → "amit.sinhal": the account name for a row without an email. */
    static String emailSlug(String name) {
        String base = TITLE.matcher(name).replaceAll(" ");
        base = Normalizer.normalize(base, Normalizer.Form.NFKD).replaceAll("\\p{M}", "");
        base = base.replaceAll("[^A-Za-z0-9]+", ".").replaceAll("(^\\.+|\\.+$)", "").toLowerCase(Locale.ROOT);
        return base.isEmpty() ? "faculty" : base;
    }

    private static String cell(Map<String, String> row, Map<String, String> columns, String column) {
        return SpreadsheetReader.valueOf(row, columns, column).trim();
    }

    private static Integer number(String raw) {
        Matcher m = NUMBER.matcher(raw);
        return m.find() ? Integer.parseInt(m.group()) : null;
    }

    private static final class Counts {
        int facultyCreated;
        int coursesCreated;
        int offeringsCreated;
        int assignmentsCreated;
        int assignmentsUpdated;
        int rolesChanged;
    }

    @Override
    public byte[] templateCsv() {
        String csv = """
                Faculty Name,Email,Designation,Department,Course Code,Course Name,L-T-P,Semester,Section,Section Students,Component,Batch,Batch Students,Weekly Hours,PBL Hours,Role
                Dr. S Taruna,s.taruna@jklu.edu.in,Professor,CSE,CS2105,Data Structures,3-1-2,3,B,60,Lecture,ALL,60,3,2,Coordinator
                Dr. Amit Sinhal,amit.sinhal@jklu.edu.in,Professor,CSE,CS1139,Programming-I,3-0-2,1,C,64,Lecture,ALL,64,3,2,Faculty
                Dr. Amit Sinhal,amit.sinhal@jklu.edu.in,Professor,CSE,CS1139,Programming-I,3-0-2,1,C,64,Practical,C1,32,2,2,Faculty
                Dr. Surbhi Chhabra,surbhi.chhabra@jklu.edu.in,Assistant Professor,CSE,CS1139,Programming-I,3-0-2,1,C,64,Practical,C2,32,2,2,Faculty
                """;
        return csv.getBytes(StandardCharsets.UTF_8);
    }
}
