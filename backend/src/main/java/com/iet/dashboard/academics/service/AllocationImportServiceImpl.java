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
import com.iet.dashboard.academics.repo.TimetableSlotRepository;
import com.iet.dashboard.academics.web.AllocationImportResultDto;
import com.iet.dashboard.academics.web.AllocationImportResultDto.Issue;
import com.iet.dashboard.directory.domain.Department;
import com.iet.dashboard.directory.domain.Faculty;
import com.iet.dashboard.directory.domain.Skill;
import com.iet.dashboard.directory.repo.DepartmentRepository;
import com.iet.dashboard.directory.repo.FacultyRepository;
import com.iet.dashboard.directory.repo.SkillRepository;
import com.iet.dashboard.identity.domain.AppUser;
import com.iet.dashboard.identity.domain.Role;
import com.iet.dashboard.identity.repo.AppUserRepository;
import com.iet.dashboard.shared.spreadsheet.SpreadsheetReader;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.function.Function;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * The one importer, and the only way people and allocations enter the system. The sheet is the
 * source of truth for the semester:
 * <ul>
 *   <li>Nothing is invented. Every value stored is a value the sheet wrote; a missing required value
 *       is an error, never a default.</li>
 *   <li>The whole file is checked first. One error anywhere and nothing is saved, so a half-imported
 *       semester can't exist.</li>
 *   <li>A value repeated across rows (a course's name, a section's strength, a person's designation)
 *       must be identical on every row that gives it.</li>
 *   <li>Re-importing replaces the semester's allocations: rows that left the sheet are removed.</li>
 * </ul>
 */
@Service
@Transactional
public class AllocationImportServiceImpl implements AllocationImportService {

    private static final List<Map.Entry<String, List<String>>> HEADER_RULES = List.of(
            Map.entry("name", List.of("=name", "facultyname", "faculty", "teacher")),
            Map.entry("email", List.of("email", "emailid", "mail")),
            Map.entry("designation", List.of("designation", "rank", "=title")),
            Map.entry("department", List.of("department", "dept")),
            Map.entry("role", List.of("=role", "access", "dashboardrole", "accessrole")),
            Map.entry("skills", List.of("skills", "skill", "expertise")),
            Map.entry("pbl", List.of("pblhours", "=pbl")),
            Map.entry("code", List.of("coursecode", "subjectcode", "=code")),
            Map.entry("course", List.of("coursename", "subjectname", "coursetitle", "=course")),
            Map.entry("ltp", List.of("ltp")),
            Map.entry("semester", List.of("semester", "=sem")),
            Map.entry("section", List.of("section", "=sec")),
            Map.entry("sectionstudents", List.of("sectionstudents", "sectionstrength", "=students", "=strength")),
            Map.entry("component", List.of("component", "=type")),
            Map.entry("batch", List.of("batch", "subsection")),
            Map.entry("batchstudents", List.of("batchstudents", "batchstrength")),
            // Optional: when given it must equal what the L-T-P says, so a typo can't slip through.
            Map.entry("hours", List.of("weeklyhours", "contacthours", "=hours"))
    );

    private static final Pattern WHOLE_NUMBER = Pattern.compile("\\d+");
    private static final Pattern LTP = Pattern.compile("(\\d+)\\s*-\\s*(\\d+)\\s*-\\s*(\\d+)");
    private static final Pattern EMAIL = Pattern.compile("[^@\\s]+@[^@\\s]+\\.[^@\\s]+");
    private static final Pattern PARENTHETICAL = Pattern.compile("\\(([^)]*)\\)");

    private final AppUserRepository userRepository;
    private final FacultyRepository facultyRepository;
    private final DepartmentRepository departmentRepository;
    private final CourseRepository courseRepository;
    private final CourseOfferingRepository offeringRepository;
    private final TeachingAssignmentRepository assignmentRepository;
    private final AcademicTermRepository termRepository;
    private final SkillRepository skillRepository;
    private final TimetableSlotRepository slotRepository;

    public AllocationImportServiceImpl(
            AppUserRepository userRepository,
            FacultyRepository facultyRepository,
            DepartmentRepository departmentRepository,
            CourseRepository courseRepository,
            CourseOfferingRepository offeringRepository,
            TeachingAssignmentRepository assignmentRepository,
            AcademicTermRepository termRepository,
            SkillRepository skillRepository,
            TimetableSlotRepository slotRepository
    ) {
        this.userRepository = userRepository;
        this.facultyRepository = facultyRepository;
        this.departmentRepository = departmentRepository;
        this.courseRepository = courseRepository;
        this.offeringRepository = offeringRepository;
        this.assignmentRepository = assignmentRepository;
        this.termRepository = termRepository;
        this.skillRepository = skillRepository;
        this.slotRepository = slotRepository;
    }

    /** One person as the sheet describes them; blank optional fields stay null ("not given"). */
    private record Person(int row, String name, String email, String designation, String department,
                          Role role, List<String> skills, Integer pbl) {}

    /** One allocation row. */
    private record Allocation(int row, String email, String code, String courseName, int l, int t, int p,
                              int semester, String section, int sectionStudents,
                              CourseComponent component, String batch, Integer batchStudents) {
        int hours() {
            return switch (component) {
                case TUTORIAL -> t;
                case PRACTICAL -> p;
                default -> l;
            };
        }

        String sectionKey() {
            return code + "|" + semester + "|" + section;
        }

        String key() {
            return sectionKey() + "|" + email + "|" + component + "|" + batch;
        }
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
        for (String required : List.of("name", "email")) {
            if (!SpreadsheetReader.hasColumn(columns, required)) {
                throw new IllegalArgumentException("Missing column \"" + required
                        + "\". Download the template to see the expected headers.");
            }
        }

        List<Issue> issues = new ArrayList<>();
        Map<String, Person> people = new LinkedHashMap<>();
        Map<String, Allocation> allocations = new LinkedHashMap<>();
        Consistency same = new Consistency(issues);

        for (int i = 0; i < rows.size(); i++) {
            int row = i + 2;
            Map<String, String> cells = rows.get(i);
            Function<String, String> cell = column -> SpreadsheetReader.valueOf(cells, columns, column).trim();
            int errorsBefore = errorCount(issues);

            Person person = readPerson(row, cell, issues);
            if (person == null) {
                continue;
            }
            same.check(row, "name of " + person.email(), person.email(), person.name());
            same.check(row, "email of " + person.name(), person.name().toLowerCase(Locale.ROOT), person.email());
            same.check(row, "designation of " + person.email(), person.email() + "|d", person.designation());
            same.check(row, "department of " + person.email(), person.email() + "|dep", person.department());
            same.check(row, "role of " + person.email(), person.email() + "|r", person.role());
            same.check(row, "skills of " + person.email(), person.email() + "|s", person.skills());
            same.check(row, "PBL hours of " + person.email(), person.email() + "|p", person.pbl());
            people.merge(person.email(), person, AllocationImportServiceImpl::fillBlanks);

            Allocation allocation = readAllocation(row, person.email(), cell, issues);
            if (allocation == null || errorCount(issues) > errorsBefore) {
                continue;
            }
            same.check(row, "name of course " + allocation.code(), allocation.code() + "|n", allocation.courseName());
            same.check(row, "L-T-P of course " + allocation.code(), allocation.code() + "|ltp",
                    allocation.l() + "-" + allocation.t() + "-" + allocation.p());
            same.check(row, "Section Students of " + label(allocation), allocation.sectionKey(), allocation.sectionStudents());
            if (!SubSections.WHOLE_SECTION.equals(allocation.batch())) {
                same.check(row, "Batch Students of " + label(allocation) + " batch " + allocation.batch(),
                        allocation.sectionKey() + "|" + allocation.component() + "|" + allocation.batch(),
                        allocation.batchStudents());
            }
            if (allocations.putIfAbsent(allocation.key(), allocation) != null) {
                issues.add(new Issue(row, "WARN", "Same allocation as row " + allocations.get(allocation.key()).row()
                        + "; counted once."));
            }
        }
        checkBatchesFitTheirSection(allocations.values(), issues);

        if (errorCount(issues) > 0) {
            issues.add(0, new Issue(0, "ERROR", errorCount(issues) + " problem(s) found. Nothing was imported; "
                    + "fix the rows below and upload the sheet again."));
            return new AllocationImportResultDto(rows.size(), false, 0, 0, 0, 0, 0, 0, 0, issues);
        }
        return apply(rows.size(), people, allocations.values(), issues);
    }

    // ------------------------------------------------------------------ reading one row

    private static Person readPerson(int row, Function<String, String> cell, List<Issue> issues) {
        String rawName = cell.apply("name");
        String email = cell.apply("email").toLowerCase(Locale.ROOT);
        if (rawName.isEmpty()) {
            issues.add(new Issue(row, "ERROR", "Faculty Name is empty."));
        }
        if (email.isEmpty()) {
            issues.add(new Issue(row, "ERROR", "Email is empty. Enter the email they sign in with."));
        } else if (!EMAIL.matcher(email).matches()) {
            issues.add(new Issue(row, "ERROR", "Email \"" + email + "\" is not an email address."));
        }
        if (rawName.isEmpty() || email.isEmpty() || !EMAIL.matcher(email).matches()) {
            return null;
        }
        // "Dr. Kishtiz Verma (Visiting Faculty)": the note is a designation, not part of the name.
        Matcher note = PARENTHETICAL.matcher(rawName);
        String noted = note.find() ? note.group(1).trim() : "";
        String name = PARENTHETICAL.matcher(rawName).replaceAll(" ").replaceAll("\\s+", " ").trim();
        String designation = cell.apply("designation");
        if (designation.isEmpty() && !noted.isEmpty()) {
            designation = noted;
        }

        String rawRole = cell.apply("role").toLowerCase(Locale.ROOT);
        Role role = null;
        if (rawRole.contains("coord") || rawRole.contains("admin")) {
            role = Role.COORDINATOR;
        } else if (rawRole.contains("faculty") || rawRole.contains("teach")) {
            role = Role.FACULTY;
        } else if (!rawRole.isEmpty()) {
            issues.add(new Issue(row, "ERROR", "Role \"" + cell.apply("role") + "\" must be Faculty or Coordinator."));
        }

        List<String> skills = null;
        if (!cell.apply("skills").isEmpty()) {
            skills = new ArrayList<>(new LinkedHashSet<>(List.of(cell.apply("skills").split("\\s*[;|]\\s*")).stream()
                    .map(String::trim).filter(s -> !s.isEmpty()).toList()));
        }
        Integer pbl = wholeNumber(row, "PBL Hours", cell.apply("pbl"), issues);
        return new Person(row, name, email, blankToNull(designation), blankToNull(cell.apply("department")),
                role, skills, pbl);
    }

    /** Null for a faculty-only row (no course code) or a row with errors (already reported). */
    private static Allocation readAllocation(int row, String email, Function<String, String> cell, List<Issue> issues) {
        String code = cell.apply("code").toUpperCase(Locale.ROOT);
        if (code.isEmpty()) {
            for (String column : List.of("course", "ltp", "semester", "section", "component", "batch")) {
                if (!cell.apply(column).isEmpty()) {
                    issues.add(new Issue(row, "ERROR", "The row has course details but no Course Code."));
                    break;
                }
            }
            return null;
        }
        List<String> missing = new ArrayList<>();
        String courseName = require(cell, "course", "Course Name", missing);
        String rawLtp = require(cell, "ltp", "L-T-P", missing);
        String rawSemester = require(cell, "semester", "Semester", missing);
        String section = require(cell, "section", "Section", missing).toUpperCase(Locale.ROOT);
        String rawStudents = require(cell, "sectionstudents", "Section Students", missing);
        String rawComponent = require(cell, "component", "Component", missing);
        if (!missing.isEmpty()) {
            issues.add(new Issue(row, "ERROR", code + ": " + String.join(", ", missing) + " missing."));
            return null;
        }

        Matcher ltp = LTP.matcher(rawLtp);
        if (!ltp.matches()) {
            issues.add(new Issue(row, "ERROR", "L-T-P \"" + rawLtp + "\" must look like 3-0-2."));
        }
        Integer semester = wholeNumber(row, "Semester", rawSemester, issues);
        if (semester != null && (semester < 1 || semester > 8)) {
            issues.add(new Issue(row, "ERROR", "Semester must be 1–8, not " + semester + "."));
        }
        Integer sectionStudents = wholeNumber(row, "Section Students", rawStudents, issues);
        CourseComponent component = rawComponent.toUpperCase(Locale.ROOT).matches("L|T|P|LECTURE|THEORY|TUT|TUTORIAL|PRACTICAL|LAB")
                ? CourseComponent.parse(rawComponent) : null;
        if (component == null) {
            issues.add(new Issue(row, "ERROR", "Component \"" + rawComponent + "\" must be Lecture, Tutorial or Practical."));
            return null;
        }
        if (!ltp.matches() || semester == null || sectionStudents == null) {
            return null;
        }

        String rawBatch = cell.apply("batch");
        String batch = SubSections.normalise(rawBatch, section);
        if (component == CourseComponent.PRACTICAL && rawBatch.isEmpty()) {
            issues.add(new Issue(row, "ERROR", "Practical rows need a Batch (e.g. " + section
                    + "1), or ALL for the whole section."));
            return null;
        }
        if (component == CourseComponent.LECTURE && !SubSections.WHOLE_SECTION.equals(batch)) {
            issues.add(new Issue(row, "ERROR", "A lecture is for the whole section; leave Batch blank or ALL, not \""
                    + rawBatch + "\"."));
            return null;
        }
        Integer batchStudents = wholeNumber(row, "Batch Students", cell.apply("batchstudents"), issues);
        if (SubSections.WHOLE_SECTION.equals(batch)) {
            if (batchStudents != null && !batchStudents.equals(sectionStudents)) {
                issues.add(new Issue(row, "ERROR", "Batch Students (" + batchStudents + ") on a whole-section row must "
                        + "equal Section Students (" + sectionStudents + ") or be blank."));
            }
            batchStudents = null;
        } else if (batchStudents == null) {
            issues.add(new Issue(row, "ERROR", "Batch " + batch + " needs Batch Students."));
        }

        Allocation allocation = new Allocation(row, email, code, courseName,
                Integer.parseInt(ltp.group(1)), Integer.parseInt(ltp.group(2)), Integer.parseInt(ltp.group(3)),
                semester, section, sectionStudents, component, batch, batchStudents);
        if (allocation.hours() == 0) {
            issues.add(new Issue(row, "ERROR", code + " has L-T-P " + rawLtp + ", which gives "
                    + component.name().toLowerCase(Locale.ROOT) + " 0 hours a week."));
        }
        String rawHours = cell.apply("hours");
        if (!rawHours.isEmpty() && !rawHours.equals(String.valueOf(allocation.hours()))) {
            issues.add(new Issue(row, "ERROR", "Weekly Hours " + rawHours + " doesn't match L-T-P " + rawLtp
                    + " (" + component.name().toLowerCase(Locale.ROOT) + " = " + allocation.hours()
                    + "). Fix one, or leave Weekly Hours blank."));
        }
        return allocation;
    }

    private static void checkBatchesFitTheirSection(Iterable<Allocation> allocations, List<Issue> issues) {
        Map<String, Map<String, Allocation>> batches = new LinkedHashMap<>();
        Map<String, Integer> sectionStudents = new HashMap<>();
        for (Allocation a : allocations) {
            sectionStudents.putIfAbsent(a.sectionKey(), a.sectionStudents());
            if (a.batchStudents() != null) {
                batches.computeIfAbsent(a.sectionKey() + "|" + a.component(), k -> new LinkedHashMap<>())
                        .putIfAbsent(a.batch(), a);
            }
        }
        batches.values().forEach(group -> {
            Allocation first = group.values().iterator().next();
            int total = group.values().stream().mapToInt(Allocation::batchStudents).sum();
            int section = sectionStudents.get(first.sectionKey());
            if (total > section) {
                issues.add(new Issue(first.row(), "ERROR", label(first) + " " + first.component().name().toLowerCase(Locale.ROOT)
                        + " batches " + String.join(", ", group.keySet()) + " add up to " + total
                        + " students, more than the section's " + section + "."));
            }
        });
    }

    // ------------------------------------------------------------------ saving

    private AllocationImportResultDto apply(int rowCount, Map<String, Person> people,
                                            Iterable<Allocation> allocations, List<Issue> issues) {
        AcademicTerm term = termRepository.findByActiveTrue().orElse(null);
        String academicYear = term != null ? term.getAcademicYear() : "";
        Counts counts = new Counts();

        Map<String, Faculty> members = new HashMap<>();
        Map<Long, Role> wantedRoles = new LinkedHashMap<>();
        Map<Long, Integer> roleRows = new HashMap<>();
        for (Person person : people.values()) {
            Faculty member = upsertFaculty(person, counts);
            members.put(person.email(), member);
            if (person.role() != null) {
                wantedRoles.put(member.getUserId(), person.role());
                roleRows.put(member.getUserId(), person.row());
            }
        }

        Set<Long> kept = new HashSet<>();
        Map<String, Course> courses = new HashMap<>();
        for (Allocation a : allocations) {
            Faculty member = members.get(a.email());
            Course course = courses.computeIfAbsent(a.code(), code -> upsertCourse(a, member, counts));
            CourseOffering offering = upsertOffering(a, course, academicYear, term, counts);
            TeachingAssignment assignment = assignmentRepository
                    .findByOfferingIdAndFacultyIdAndComponentAndSubSection(offering.getId(), member.getId(), a.component(), a.batch())
                    .orElse(null);
            if (assignment == null) {
                assignment = new TeachingAssignment(offering, member.getId(), a.component(), a.batch(), 0);
                counts.assignmentsCreated++;
            } else {
                counts.assignmentsUpdated++;
            }
            assignment.setContactHours(a.hours());
            assignment.setStudentCount(a.batchStudents());
            kept.add(assignmentRepository.save(assignment).getId());
        }
        removeAllocationsNoLongerInTheSheet(academicYear, kept, counts);
        assignRoles(wantedRoles, roleRows, counts, issues);

        return new AllocationImportResultDto(rowCount, true, counts.facultyCreated, counts.coursesCreated,
                counts.offeringsCreated, counts.assignmentsCreated, counts.assignmentsUpdated,
                counts.assignmentsRemoved, counts.rolesChanged, issues);
    }

    /** The sheet is the whole semester: an allocation it no longer lists is gone, with its timetable slots. */
    private void removeAllocationsNoLongerInTheSheet(String academicYear, Set<Long> kept, Counts counts) {
        for (TeachingAssignment old : assignmentRepository.findAllWithCourseAndOffering()) {
            if (kept.contains(old.getId()) || !Objects.equals(old.getOffering().getAcademicYear(), academicYear)) {
                continue;
            }
            slotRepository.deleteByOfferingIdAndFacultyIdAndComponentAndSubSection(
                    old.getOffering().getId(), old.getFacultyId(), old.getComponent().name(), old.getSubSection());
            assignmentRepository.delete(old);
            counts.assignmentsRemoved++;
        }
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

    private Faculty upsertFaculty(Person person, Counts counts) {
        AppUser user = userRepository.findByEmailIgnoreCase(person.email())
                .orElseGet(() -> userRepository.save(new AppUser(person.email(), person.name(), Role.FACULTY)));
        if (!person.name().equals(user.getName())) {
            user.setName(person.name());
            userRepository.save(user);
        }
        Faculty member = facultyRepository.findByUserId(user.getId()).orElse(null);
        if (member == null) {
            member = new Faculty(user.getId(), person.name(), "", "", null);
            counts.facultyCreated++;
        }
        member.setName(person.name());
        if (person.designation() != null) {
            member.setDesignation(person.designation());
        }
        if (person.department() != null) {
            member.setDepartment(department(person.department()));
        }
        if (person.pbl() != null) {
            member.setPblHours(person.pbl());
        }
        if (person.skills() != null) {
            Set<Skill> skills = new LinkedHashSet<>();
            for (String name : person.skills()) {
                skills.add(skillRepository.findByNameIgnoreCase(name)
                        .orElseGet(() -> skillRepository.save(new Skill(name, "Technical"))));
            }
            member.setSkills(skills);
        }
        return facultyRepository.save(member);
    }

    private Course upsertCourse(Allocation a, Faculty member, Counts counts) {
        Course course = courseRepository.findByCodeIgnoreCase(a.code()).orElse(null);
        if (course == null) {
            course = new Course(a.code(), a.courseName(), "THEORY");
            counts.coursesCreated++;
        }
        course.setName(a.courseName());
        course.setLtp(a.l() + "-" + a.t() + "-" + a.p());
        course.setLectureHours(a.l());
        course.setTutorialHours(a.t());
        course.setPracticalHours(a.p());
        // A course belongs to the department of the first person the sheet lists for it.
        if (course.getDepartmentId() == null && member.getDepartmentId() != null) {
            course.setDepartmentId(member.getDepartmentId());
        }
        return courseRepository.save(course);
    }

    private CourseOffering upsertOffering(Allocation a, Course course, String academicYear, AcademicTerm term, Counts counts) {
        int yearOfStudy = (a.semester() + 1) / 2;
        CourseOffering offering = offeringRepository
                .findFirstByCourseIdAndAcademicYearAndYearOfStudyAndSemesterAndSection(
                        course.getId(), academicYear, yearOfStudy, a.semester(), a.section())
                .orElse(null);
        if (offering == null) {
            offering = new CourseOffering(course, academicYear, yearOfStudy, a.semester(), a.section(), term);
            counts.offeringsCreated++;
        }
        offering.setStudentCount(a.sectionStudents());
        return offeringRepository.save(offering);
    }

    private Department department(String codeOrName) {
        return departmentRepository.findByCodeIgnoreCase(codeOrName)
                .or(() -> departmentRepository.findByNameIgnoreCase(codeOrName))
                .orElseGet(() -> departmentRepository.save(new Department(
                        codeOrName.replaceAll("[^A-Za-z]", "").toUpperCase(Locale.ROOT), codeOrName)));
    }

    // ------------------------------------------------------------------ helpers

    /** Remembers the first value each key was given and reports any row that gives a different one. */
    private static final class Consistency {
        private final Map<String, Map.Entry<Integer, Object>> seen = new HashMap<>();
        private final List<Issue> issues;

        Consistency(List<Issue> issues) {
            this.issues = issues;
        }

        void check(int row, String what, String key, Object value) {
            if (value == null) {
                return;
            }
            Map.Entry<Integer, Object> first = seen.putIfAbsent(key, Map.entry(row, value));
            if (first != null && !first.getValue().equals(value)) {
                issues.add(new Issue(row, "ERROR", "The " + what + " is \"" + display(value) + "\" here but \""
                        + display(first.getValue()) + "\" on row " + first.getKey() + "."));
            }
        }

        private static String display(Object value) {
            return value instanceof List<?> list ? String.join("; ", list.stream().map(String::valueOf).toList())
                    : String.valueOf(value);
        }
    }

    /** A person's optional details may sit on any one of their rows. */
    private static Person fillBlanks(Person first, Person later) {
        return new Person(first.row(), first.name(), first.email(),
                first.designation() != null ? first.designation() : later.designation(),
                first.department() != null ? first.department() : later.department(),
                first.role() != null ? first.role() : later.role(),
                first.skills() != null ? first.skills() : later.skills(),
                first.pbl() != null ? first.pbl() : later.pbl());
    }

    private static String require(Function<String, String> cell, String column, String label, List<String> missing) {
        String value = cell.apply(column);
        if (value.isEmpty()) {
            missing.add(label);
        }
        return value;
    }

    /** A cell that must be a plain whole number: "64" yes, "6O" or "64 approx" no. Blank is null. */
    private static Integer wholeNumber(int row, String label, String raw, List<Issue> issues) {
        if (raw.isEmpty()) {
            return null;
        }
        if (!WHOLE_NUMBER.matcher(raw).matches()) {
            issues.add(new Issue(row, "ERROR", label + " \"" + raw + "\" must be a whole number."));
            return null;
        }
        return Integer.parseInt(raw);
    }

    private static String label(Allocation a) {
        return a.code() + " Sem " + a.semester() + " Sec " + a.section();
    }

    private static String blankToNull(String value) {
        return value.isEmpty() ? null : value;
    }

    private static int errorCount(List<Issue> issues) {
        return (int) issues.stream().filter(i -> i.severity().equals("ERROR")).count();
    }

    private static final class Counts {
        int facultyCreated;
        int coursesCreated;
        int offeringsCreated;
        int assignmentsCreated;
        int assignmentsUpdated;
        int assignmentsRemoved;
        int rolesChanged;
    }

    @Override
    public byte[] templateCsv() {
        String csv = """
                Faculty Name,Email,Designation,Department,Role,Skills,PBL Hours,Course Code,Course Name,L-T-P,Semester,Section,Section Students,Component,Batch,Batch Students
                Dr. S Taruna,s.taruna@jklu.edu.in,Professor,CSE,Coordinator,Databases; Data Mining,2,CS1133,Database Management Systems,3-0-4,3,A,60,Lecture,,
                Dr. S Taruna,s.taruna@jklu.edu.in,,,,,,CS1133,Database Management Systems,3-0-4,3,A,60,Practical,A1,30
                Dr. S Taruna,s.taruna@jklu.edu.in,,,,,,CS1133,Database Management Systems,3-0-4,3,A,60,Practical,A2,30
                Dr. Amit Sinhal,amit.sinhal@jklu.edu.in,Professor,CSE,Faculty,Programming,2,CS1139,Programming-I,3-0-2,1,C,64,Lecture,,
                Dr. New Joiner,new.joiner@jklu.edu.in,Assistant Professor,CSE,Faculty,Machine Learning,,,,,,,,,,
                """;
        return csv.getBytes(StandardCharsets.UTF_8);
    }
}
