package com.iet.dashboard.notifications.service;

import com.iet.dashboard.academics.service.AcademicsService;
import com.iet.dashboard.academics.web.TeachingAssignmentDto;
import com.iet.dashboard.directory.service.FacultyService;
import com.iet.dashboard.directory.web.FacultyDto;
import com.iet.dashboard.identity.domain.Role;
import com.iet.dashboard.identity.service.UserService;
import com.iet.dashboard.notifications.domain.NotificationType;
import com.iet.dashboard.teaching.service.TeachingSessionService;
import com.iet.dashboard.teaching.web.PendingClassDto;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

import java.time.Clock;
import java.time.DayOfWeek;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * The coordinator never chases anyone. Every morning each faculty member is told exactly what of
 * theirs is missing — timetable, a course syllabus, classes left unlogged — and every Monday the
 * coordinators get one list of what is still missing after a week.
 */
@Service
public class FacultyNudgeService {

    private static final Logger log = LoggerFactory.getLogger(FacultyNudgeService.class);
    /** Classes unlogged for longer than this are reminded about (same window as a "late" log). */
    static final int REMIND_AFTER_DAYS = 2;
    /** Still missing after this long, the coordinator hears about it. */
    static final int ESCALATE_AFTER_DAYS = 7;

    private final FacultyService facultyService;
    private final AcademicsService academicsService;
    private final TeachingSessionService sessionService;
    private final NotificationService notificationService;
    private final UserService userService;
    private Clock clock = Clock.system(ZoneId.of("Asia/Kolkata"));

    public FacultyNudgeService(FacultyService facultyService, AcademicsService academicsService,
                               TeachingSessionService sessionService, NotificationService notificationService,
                               UserService userService) {
        this.facultyService = facultyService;
        this.academicsService = academicsService;
        this.sessionService = sessionService;
        this.notificationService = notificationService;
        this.userService = userService;
    }

    void setClock(Clock clock) {
        this.clock = clock;
    }

    /** @return how many reminders went out. */
    @Scheduled(cron = "${app.notifications.nudge-cron:0 30 8 * * ?}", zone = "Asia/Kolkata")
    public int remindFacultyOfMissingWork() {
        LocalDate today = LocalDate.now(clock);
        int sent = 0;
        Map<String, List<String>> overdue = new LinkedHashMap<>();

        for (FacultyDto faculty : facultyService.getAllFaculty(null, null)) {
            if (faculty.userId() == null) {
                continue;
            }
            try {
                List<TeachingAssignmentDto> assignments = academicsService.getTeachingAssignmentsForFaculty(faculty.id());
                if (assignments.isEmpty()) {
                    continue;
                }
                List<String> stale = new ArrayList<>();

                if (academicsService.getTimetableForFaculty(faculty.id()).isEmpty()) {
                    notify(faculty, "Upload your timetable",
                            "Your classes can't be listed or logged until your timetable PDF is uploaded.", "/timetable");
                    sent++;
                    stale.add("no timetable");
                }

                List<String> noSyllabus = assignments.stream()
                        .filter(a -> a.offering() != null && a.offering().course() != null)
                        .map(a -> a.offering().course())
                        .filter(c -> academicsService.getSyllabusForCourse(c.id()).isEmpty())
                        .map(c -> c.code())
                        .distinct()
                        .toList();
                if (!noSyllabus.isEmpty()) {
                    notify(faculty, "Add your course syllabus",
                            "No syllabus yet for " + String.join(", ", noSyllabus)
                                    + ". Upload it so classes can be logged against its topics.", "/my-courses");
                    sent++;
                    stale.add("no syllabus for " + String.join(", ", noSyllabus));
                }

                List<PendingClassDto> pending = sessionService.getPendingClasses(faculty.id(), null, today.minusDays(REMIND_AFTER_DAYS + 1L));
                if (!pending.isEmpty()) {
                    notify(faculty, pending.size() + " class" + (pending.size() == 1 ? "" : "es") + " not logged",
                            "The oldest is " + pending.get(0).courseCode() + " on " + pending.get(0).date()
                                    + ". Log each as held or not held.", "/today");
                    sent++;
                    long weekOld = pending.stream().filter(p -> p.date().isBefore(today.minusDays(ESCALATE_AFTER_DAYS))).count();
                    if (weekOld > 0) {
                        stale.add(weekOld + " class" + (weekOld == 1 ? "" : "es") + " unlogged for over a week");
                    }
                }
                if (!stale.isEmpty()) {
                    overdue.put(faculty.name(), stale);
                }
            } catch (RuntimeException e) {
                // One faculty member's bad data must not stop everyone else's reminders.
                log.warn("Could not check missing work for faculty {}: {}", faculty.id(), e.getMessage());
            }
        }

        if (today.getDayOfWeek() == DayOfWeek.MONDAY && !overdue.isEmpty()) {
            StringBuilder summary = new StringBuilder();
            overdue.forEach((name, items) -> summary.append(name).append(": ").append(String.join("; ", items)).append('\n'));
            for (var coordinator : userService.findUsersByRole(Role.COORDINATOR)) {
                notificationService.sendNotification(coordinator.id(), "Weekly: " + overdue.size() + " faculty still have missing work",
                        summary.toString().trim(), NotificationType.GENERAL, "/overview", true);
                sent++;
            }
        }
        log.info("Faculty reminder scan sent {} reminders", sent);
        return sent;
    }

    private void notify(FacultyDto faculty, String title, String message, String url) {
        notificationService.sendNotification(faculty.userId(), title, message, NotificationType.GENERAL, url, true);
    }
}
