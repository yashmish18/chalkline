package com.iet.dashboard.academics.service;

import com.iet.dashboard.academics.domain.CourseComponent;
import com.iet.dashboard.academics.domain.CourseOffering;
import com.iet.dashboard.academics.domain.SubSections;
import com.iet.dashboard.academics.domain.TeachingAssignment;
import com.iet.dashboard.academics.domain.TimetableSlot;
import com.iet.dashboard.academics.repo.CourseOfferingRepository;
import com.iet.dashboard.academics.repo.TeachingAssignmentRepository;
import com.iet.dashboard.academics.repo.TimetableSlotRepository;
import com.iet.dashboard.academics.web.AllocationImportResultDto.Issue;
import com.iet.dashboard.academics.web.TimetableImportResultDto;
import com.iet.dashboard.directory.domain.Faculty;
import com.iet.dashboard.directory.repo.FacultyRepository;
import com.iet.dashboard.identity.repo.AppUserRepository;
import com.iet.dashboard.shared.spreadsheet.SpreadsheetReader;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

import java.nio.charset.StandardCharsets;
import java.time.DayOfWeek;
import java.time.LocalTime;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Turns a timetable sheet into weekly class slots. A slot is only accepted when it matches an
 * allocation (same faculty, section, component and batch), so the Today screen can never show a
 * class someone was not given.
 */
@Service
@Transactional
public class TimetableImportServiceImpl implements TimetableImportService {

    private static final List<Map.Entry<String, List<String>>> HEADER_RULES = List.of(
            Map.entry("day", List.of("day", "dayofweek", "weekday")),
            Map.entry("start", List.of("start", "starttime", "from")),
            Map.entry("end", List.of("end", "endtime", "=to")),
            Map.entry("room", List.of("room", "venue", "location")),
            Map.entry("code", List.of("coursecode", "subjectcode", "=code")),
            Map.entry("semester", List.of("semester", "=sem")),
            Map.entry("section", List.of("section", "=sec")),
            Map.entry("component", List.of("component", "=type")),
            Map.entry("batch", List.of("batch", "subsection")),
            Map.entry("email", List.of("facultyemail", "email")),
            Map.entry("name", List.of("facultyname", "=faculty"))
    );

    private static final Pattern TIME = Pattern.compile("(\\d{1,2})(?:[:.](\\d{2}))?\\s*([AaPp][Mm])?");

    private final TimetableSlotRepository slotRepository;
    private final CourseOfferingRepository offeringRepository;
    private final TeachingAssignmentRepository assignmentRepository;
    private final FacultyRepository facultyRepository;
    private final AppUserRepository userRepository;

    public TimetableImportServiceImpl(
            TimetableSlotRepository slotRepository,
            CourseOfferingRepository offeringRepository,
            TeachingAssignmentRepository assignmentRepository,
            FacultyRepository facultyRepository,
            AppUserRepository userRepository
    ) {
        this.slotRepository = slotRepository;
        this.offeringRepository = offeringRepository;
        this.assignmentRepository = assignmentRepository;
        this.facultyRepository = facultyRepository;
        this.userRepository = userRepository;
    }

    @Override
    public TimetableImportResultDto importTimetable(MultipartFile file, Long onlyFacultyId) {
        if (file == null || file.isEmpty()) {
            throw new IllegalArgumentException("The uploaded file is empty.");
        }
        List<Map<String, String>> rows = isPdf(file) ? TimetablePdfParser.parse(file) : SpreadsheetReader.read(file);
        if (rows.isEmpty()) {
            throw new IllegalArgumentException("The sheet has no rows below the header.");
        }
        Map<String, String> columns = SpreadsheetReader.detectColumns(rows.get(0).keySet(), HEADER_RULES);
        for (String required : List.of("day", "start", "end", "code", "section")) {
            if (!SpreadsheetReader.hasColumn(columns, required)) {
                throw new IllegalArgumentException("Missing column \"" + required
                        + "\". Download the template to see the expected headers.");
            }
        }
        if (onlyFacultyId == null && !SpreadsheetReader.hasColumn(columns, "email") && !SpreadsheetReader.hasColumn(columns, "name")) {
            throw new IllegalArgumentException("A master timetable needs a Faculty Email (or Faculty Name) column.");
        }

        List<CourseOffering> offerings = offeringRepository.findAll();
        List<TeachingAssignment> assignments = assignmentRepository.findAllWithCourseAndOffering();
        List<Faculty> allFaculty = facultyRepository.findAll();

        List<TimetableSlot> accepted = new ArrayList<>();
        List<Issue> issues = new ArrayList<>();
        for (int i = 0; i < rows.size(); i++) {
            int rowNumber = i + 2;
            Map<String, String> row = rows.get(i);

            DayOfWeek day = day(cell(row, columns, "day"));
            LocalTime start = time(cell(row, columns, "start"));
            LocalTime end = time(cell(row, columns, "end"));
            if (day == null || start == null || end == null || !end.isAfter(start)) {
                issues.add(new Issue(rowNumber, "ERROR", "Day or time could not be read (use e.g. Mon, 09:00, 10:00)."));
                continue;
            }

            Long facultyId = onlyFacultyId != null ? onlyFacultyId : facultyFor(row, columns, allFaculty);
            if (facultyId == null) {
                issues.add(new Issue(rowNumber, "ERROR", "Faculty not found; check the email or name."));
                continue;
            }

            String section = cell(row, columns, "section").toUpperCase(Locale.ROOT);
            Integer semester = number(cell(row, columns, "semester"));
            CourseComponent component = CourseComponent.parse(cell(row, columns, "component"));
            String batch = SubSections.normalise(cell(row, columns, "batch"), section);

            // A PDF cell can name only the faculty, component and room. When it does, the course is
            // whatever single allocation of this faculty the cell can mean.
            String writtenCode = cell(row, columns, "code").toUpperCase(Locale.ROOT);
            String code = writtenCode.isEmpty()
                    ? inferCode(assignments, facultyId, section, component, batch)
                    : writtenCode;
            if (code == null) {
                issues.add(new Issue(rowNumber, "ERROR", "The row names no course code and no "
                        + "single allocation of this faculty matches it."));
                continue;
            }

            List<CourseOffering> candidates = offerings.stream()
                    .filter(o -> o.getCourse().getCode().equalsIgnoreCase(code)
                            && o.getSection().equalsIgnoreCase(section)
                            && (semester == null || o.getSemester() == semester))
                    .toList();
            if (candidates.size() != 1) {
                issues.add(new Issue(rowNumber, "ERROR", candidates.isEmpty()
                        ? code + " Sec " + section + " is not in the allocations."
                        : code + " Sec " + section + " runs in several semesters; add a Semester column."));
                continue;
            }
            CourseOffering offering = candidates.get(0);

            Long finalFacultyId = facultyId;
            boolean allocated = assignments.stream().anyMatch(a ->
                    a.getOffering().getId().equals(offering.getId())
                            && a.getFacultyId().equals(finalFacultyId)
                            && coversComponent(a, component)
                            && coversBatch(a, batch));
            if (!allocated) {
                issues.add(new Issue(rowNumber, "ERROR", "No allocation gives this faculty member the "
                        + component.name().toLowerCase(Locale.ROOT) + " of " + code + " Sec " + section
                        + (SubSections.WHOLE_SECTION.equals(batch) ? "" : " batch " + batch) + "."));
                continue;
            }

            // A row pasted twice would put the same class on someone's day twice.
            boolean repeated = accepted.stream().anyMatch(s -> s.getOffering().getId().equals(offering.getId())
                    && s.getFacultyId().equals(finalFacultyId) && s.getDayOfWeek().equals(day.name())
                    && s.getStartTime().equals(start) && s.getEndTime().equals(end)
                    && s.getComponent().equals(component.name()) && s.getSubSection().equals(batch));
            if (repeated) {
                issues.add(new Issue(rowNumber, "WARN", "Same class as an earlier row; skipped."));
                continue;
            }

            accepted.add(new TimetableSlot(offering, facultyId, day.name(), start, end,
                    cell(row, columns, "room"), component.name(), batch));
        }

        if (onlyFacultyId != null) {
            slotRepository.deleteByFacultyId(onlyFacultyId);
        } else {
            slotRepository.deleteAllInBatch();
        }
        slotRepository.saveAll(accepted);

        Set<Long> covered = new HashSet<>();
        accepted.forEach(s -> covered.add(s.getFacultyId()));
        return new TimetableImportResultDto(rows.size(), accepted.size(), covered.size(), issues);
    }

    /** A PDF upload is read as a week grid; every other upload as a flat sheet. */
    private static boolean isPdf(MultipartFile file) {
        String name = file.getOriginalFilename();
        return name != null && name.toLowerCase(Locale.ROOT).endsWith(".pdf");
    }

    /**
     * The course a code-less row means: the single offering of this faculty that the row's section,
     * component and batch could belong to. Null when none or several match.
     */
    private static String inferCode(
            List<TeachingAssignment> assignments, Long facultyId,
            String section, CourseComponent component, String batch) {
        List<String> codes = assignments.stream()
                .filter(a -> a.getFacultyId().equals(facultyId))
                .filter(a -> section.isEmpty() || a.getOffering().getSection().equalsIgnoreCase(section))
                .filter(a -> coversComponent(a, component))
                .filter(a -> coversBatch(a, batch))
                .map(a -> a.getOffering().getCourse().getCode())
                .distinct()
                .toList();
        return codes.size() == 1 ? codes.get(0) : null;
    }

    private static boolean coversComponent(TeachingAssignment assignment, CourseComponent component) {
        return assignment.getComponent() == CourseComponent.ALL
                || CourseComponent.parse(assignment.getComponent().name()) == component;
    }

    private static boolean coversBatch(TeachingAssignment assignment, String batch) {
        return SubSections.WHOLE_SECTION.equals(assignment.getSubSection())
                || assignment.getSubSection().equals(batch);
    }

    private Long facultyFor(Map<String, String> row, Map<String, String> columns, List<Faculty> allFaculty) {
        String email = cell(row, columns, "email");
        if (!email.isEmpty()) {
            Optional<Long> userId = userRepository.findByEmailIgnoreCase(email).map(u -> u.getId());
            return userId.flatMap(id -> allFaculty.stream().filter(f -> f.getUserId().equals(id)).findFirst())
                    .map(Faculty::getId).orElse(null);
        }
        String name = cell(row, columns, "name");
        List<Faculty> byName = allFaculty.stream().filter(f -> f.getName().equalsIgnoreCase(name)).toList();
        return byName.size() == 1 ? byName.get(0).getId() : null;
    }

    /** "Mon", "tue", "Wednesday" → the day; anything shorter than three letters is unreadable. */
    static DayOfWeek day(String raw) {
        String value = raw.trim().toUpperCase(Locale.ROOT);
        if (value.length() < 3) {
            return null;
        }
        for (DayOfWeek d : DayOfWeek.values()) {
            if (d.name().startsWith(value)) {
                return d;
            }
        }
        return null;
    }

    /** "9:00", "09.30", "2:00 PM", "14:00" → LocalTime. */
    static LocalTime time(String raw) {
        Matcher m = TIME.matcher(raw.trim());
        if (!m.matches()) {
            return null;
        }
        int hour = Integer.parseInt(m.group(1));
        int minute = m.group(2) != null ? Integer.parseInt(m.group(2)) : 0;
        String meridiem = m.group(3);
        if (meridiem != null) {
            boolean pm = meridiem.equalsIgnoreCase("PM");
            if (hour == 12) {
                hour = pm ? 12 : 0;
            } else if (pm) {
                hour += 12;
            }
        }
        return hour < 24 && minute < 60 ? LocalTime.of(hour, minute) : null;
    }

    private static String cell(Map<String, String> row, Map<String, String> columns, String column) {
        return SpreadsheetReader.valueOf(row, columns, column).trim();
    }

    private static Integer number(String raw) {
        Matcher m = Pattern.compile("\\d+").matcher(raw);
        return m.find() ? Integer.parseInt(m.group()) : null;
    }

    @Override
    public byte[] templateCsv() {
        return """
                Day,Start,End,Room,Course Code,Semester,Section,Component,Batch,Faculty Email
                Mon,09:00,10:00,LT-1,CS1139,1,C,Lecture,,amit.sinhal@iet.edu
                Wed,14:00,16:00,Lab 3,CS1139,1,C,Practical,C1,amit.sinhal@iet.edu
                Wed,14:00,16:00,Lab 4,CS1139,1,C,Practical,C2,surbhi.chhabra@iet.edu
                """.getBytes(StandardCharsets.UTF_8);
    }
}
