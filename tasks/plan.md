# Implementation Plan: Faculty Dashboard Modular Monolith

**Scope:** Department Coordinator & Faculty Portal, IET  
**Architecture:** Spring Boot 3 (Java 21) + PostgreSQL 16 + React (TypeScript + Vite + Tailwind CSS)  
**UX Direction:** Swiss Modernism 2.0 (Institutional Navy, crisp data cards, accessible AA contrast, Lucide icons)

---

## Architecture & Dependency Map

```
Identity & Auth (AppUser, Roles, /api/me)
    │
    ├── Directory & Academics (Faculty, Skills, Courses, Offerings, Teaching Allocations)
    │       │
    │       └── Deadlines & Targeting (Deadlines, Deadline Assignments)
    │               │
    │               ├── Submissions & File Storage (Submissions, Files, LocalStorage)
    │               │       │
    │               │       ├── Notifications (In-App Center, Unread Polling, Email Dispatch)
    │               │       │
    │               │       └── Tracking & Analytics (Metrics, Overdue Roster, Excel Export)
    │               │
    │               └── Audit Trail (Coordinator & Faculty Actions)
```

---

## Vertical Slices

### Slice 0: Workspace Scaffolding & Foundational Setup
* Maven/Gradle project structure with Spring Boot 3, Java 21, Spring Data JPA, Spring Security, Flyway, PostgreSQL driver.
* Vite + React + TypeScript + Tailwind CSS frontend app with design tokens from `DESIGN.md`.
* Docker Compose configuration for PostgreSQL 16 and Mailpit (local email testing).

### Slice 1: Identity, Authentication & Role Resolution
* Schema: `app_user` with roles `COORDINATOR`, `FACULTY`.
* BFF session authentication filter & `/api/me` endpoint.
* Role-based route guard and session context on frontend.

### Slice 2: Faculty Directory & Academic Allocations
* Schema: `faculty`, `skill`, `faculty_skill`, `course`, `course_offering`, `teaching_assignment`.
* Coordinator views: Manage faculty list, assign structured skill tags, manage courses and section offerings.
* Coordinator course allocation screen: assign faculty to theory/lab sections.

### Slice 3: Deadlines & Dynamic Targeting
* Schema: `deadline`, `deadline_assignment`.
* Coordinator deadline creation form with targeting rules (all faculty, selected faculty, or by semester/course offering).
* Automatic generation of `deadline_assignment` and empty `submission` slots.

### Slice 4: File Submissions & Versioned Storage
* Schema: `submission`, `submission_file`.
* `FileStorage` interface with `LocalDiskStorage` implementation.
* Faculty submission upload with MIME validation (Apache Tika), SHA-256 calculation, and status progression (`Submitted`, `Late`).
* Coordinator submission review: download file, Accept or Return with reason; Faculty resubmission flow.

### Slice 5: Notifications Center (In-App & Email)
* Schema: `notification` (id, user_id, title, message, type, target_url, read_at, created_at).
* `NotificationService` handling in-app event creation and asynchronous email dispatch.
* Frontend Notification Center: Top nav bell with unread badge, polling `GET /api/notifications/unread-count`, dropdown list, mark-as-read, and deep linking.
* Automated reminder scheduler for upcoming (5 days, 1 day) and overdue deadlines.

### Slice 6: Tracking Dashboard & Excel Reporting
* Read models & aggregate queries: submission rate, on-time rate, overdue count, faculty at Critical (< 60%).
* Department drill-down tracking table (by deadline and by faculty).
* Excel export via Apache POI (`/api/reports/submissions/export`).

### Slice 7: End-to-End Verification & UX Polish (UI-UX Pro Max)
* Apply Swiss Modernism 2.0 aesthetics across all screens.
* Empty states, skeleton loaders, error boundaries, WCAG AA compliance, and keyboard navigation.
* End-to-end user journey verification (Coordinator creates deadline -> Faculty receives notification and uploads -> Coordinator reviews and accepts).

---

## Phase 2: Faculty-Driven Academic Tracker & Compliance Engine

### Slice 8: Academic Core Refactor & Persistent Sidebar Navigation
* Flyway migration `V7__terms_and_syllabus.sql`: `academic_term` and `syllabus_topic`.
* Academics module update: `Term`, `SyllabusTopic` entities, repos, service, and DTOs with IDOR protection.
* Frontend: Collapsible persistent `Sidebar.tsx` with role-aware navigation (Coordinator vs Faculty), replacing top navbar.

### Slice 9: Faculty Daily Session Logging & "Today" Screen
* Flyway migration `V8__teaching_sessions.sql`: `teaching_session` and `session_topic_log`.
* `TeachingSessionService`: Faculty logging of classes held/cancelled/swapped with covered syllabus topics; verified by caller `facultyId`.
* Frontend: `TodayView.tsx` with 20-second quick-log form pre-selecting next uncovered syllabus topic.

### Slice 10: Syllabus as Data, Progress Pacing & "My Courses" View
* Backend calculation engine: Query-derived syllabus coverage %, weekly conduct rate %, and pacing status (ahead/on track/behind by N topics).
* Frontend: `MyCoursesView.tsx` for faculty showing per-offering syllabus unit breakdown, lecture checkmarks, and pacing indicators.

### Slice 11: Sparse Coordinator Overview Dashboard & Compliance Matrix
* Backend query projections: Worst-first coverage ranking, weekly conduct trajectory, milestone compliance matrix.
* Frontend: Sparse `CoordinatorOverviewView.tsx` with 4 KPI numbers in 1 row, Worst-First Bar Chart, Conduct Line Chart, Compliance Checkpoint Matrix, and an actionable "Needs Attention" list with instant 1-click reminders.

### Slice 12: Skills Tag Input & Final E2E Verification
* Normalized skill table (`skill.normalized_name`) with case-insensitive search and dynamic insertion.
* Frontend: `TagInput.tsx` chip component with autosuggest.
* End-to-end integration tests, ArchUnit verification, and floor guard audit.

## Phase 3: Truthful Data, Labs, Lecture-Numbered Syllabus & Logging (2026-10-29)

Root causes found before planning:
- `JKLU_allocations_odd_2026.csv` holds values the source workbook does not have. `Course Workload Distributions.xlsx` has no emails, L-T-P, student counts or batches; those were guessed. Taruna's sheet row says Practical batch **A3**, but her timetable PDF says **A1/A2**, so the importer rejects both labs. That is why only theory classes show.
- The importer fills gaps silently: an email from the name, "Assistant Professor", weekly hours from L-T-P, course name = code, component = Lecture. A section's student count is whatever its last row says. In the sample sheet, 56 sections have conflicting counts.
- Syllabus and timetable imports save topics and slots but never save the uploaded file as an `AcademicDocument`. So the file list says "no timetable/syllabus uploaded".
- Logging sends `LAB` while allocations and slots say `PRACTICAL` (practical→lab sync issue).
- LTP load skips faculty who have no course and no PBL, and it only sums per faculty, not per section.
- The syllabus parser invents a target week per topic (`TERM_WEEKS = 11`) instead of using the lecture numbers in the document.

### Slice 16: One template, no invented values, all-or-nothing import
- [x] Real emails, L-T-P, student counts and batches are entered by the coordinator in the template (decided 2026-10-29); the template's example rows show where each goes.
- [x] Template: Faculty Name, Email, Designation, Department, Role, Skills, Course Code, Course Name, L-T-P, Semester, Section, Section Students, Component, Batch, Batch Students, PBL Hours. Weekly Hours is optional; if given it must equal the L-T-P hours.
- [x] A row with no Course Code = a faculty-only row (a person with no course).
- [x] Skills column: `;`-separated, saved through the existing skill table.
- [x] Remove all fallbacks. A missing email, course name, L-T-P (new course), Section Students, Batch (practical) or Batch Students (on a batch row) is an ERROR.
- [x] Consistency checks are ERRORs: one course code with two names or two L-T-Ps, one section with two student counts, batch students summing past section students, Batch Students on a lecture row.
- [x] Validate the whole file first. Any ERROR → save nothing and list every problem with row and column.
- [x] Round-trip check (sheet → stored values, letter for letter) is in `scripts/simulate_faculty.py`; no export endpoint needed.
- [x] Re-importing replaces the semester: allocations the sheet no longer lists are removed, with their timetable slots.

### Slice 17: LTP shown section-wise
- [x] Load = per assignment: L hours for Lecture, T for Tutorial, P for Practical (per batch); + PBL once per faculty.
- [x] Dashboard table per faculty → rows per course/section/component/batch with students and hours; faculty with zero load still listed.

### Slice 18: Timetable: labs, master upload, file shown
- [x] Merge back-to-back cells of the same class into one slot (2-hour lab = one slot).
- [x] Import result lists every rejected cell and why (e.g. "PDF says P-A1, sheet gives Taruna A3"), shown in the UI and not hidden.
- [x] "Master timetable" = each faculty uploads their own timetable PDF (decided 2026-10-29); no coordinator-wide master upload work.
- [x] Save every timetable and syllabus upload as an `AcademicDocument` (reuse `AcademicDocumentService`) so the file list shows it.

### Slice 19: Syllabus by lecture number (blocked: waiting for the syllabus document)
- [ ] Migration: `syllabus_topic.lecture_from`, `lecture_to`. Parse "Lecture 15, 16, 17" / "L15–17" exactly as written; drop the invented target week.
- [ ] Pacing = lectures held vs lecture number reached.

### Slice 20: Class logging
- [x] One component vocabulary: `LECTURE | TUTORIAL | PRACTICAL` end to end; drop `LAB`/`THEORY` aliases.
- [x] Lecture-number dropdown (defaults to the next number; unique per section/batch). Mapping a number to its syllabus topic waits on Slice 19.
- [x] Back lectures: unlogged timetable slots listed by week or month; log or skip each with a reason.
- [x] Time rules: no logging before the slot starts; a delay reason is required when logging more than 48 hours late.
- [x] Faculty dashboard: every section and batch they teach, with classes held and units/topics covered.

### Slice 20b: Avatar initials
- [x] One `nameInitials()` in `lib/labels.ts` used by every avatar; titles (Dr, Prof, Mr…) never become letters.

### Slice 22: The coordinator never chases anyone
- [x] Daily scan (next to the deadline reminder job) notifies a faculty member who has an allocation but no timetable, a course with no syllabus, or classes left unlogged after the back-log window.
- [x] Coordinators get a Monday list of what is still missing after a week; refused imports and rejected timetable cells are shown at upload. (No separate overview panel.)

### Slice 21: 10-faculty simulation
- [x] `scripts/e2e_demo.py` fixture: 10 faculty covering a faculty-only person, lecture-only, L+T+P, two sections, split lab batches, a coordinator, a bad email, a conflicting student count, and a mismatched lab batch.
- [x] Run against the app: import, master timetable plus individual PDFs, syllabus upload, log/skip/back-log for each faculty. Assert the dashboard numbers equal the sheet values exactly.
- [x] `docs/upload-guide.md`: step-by-step for the coordinator (term dates → allocation sheet → timetable → syllabi → faculty sign in).

### Slice 23: The two bugs the simulation had missed (2026-11-11)
- [x] Syllabus never reached the file list: only `tika-core` is on the classpath, so `parseToString` read 0 characters from every PDF/Word/text upload → 0 topics → Save disabled → file never stored. Text is now read with PDFBox / POI / UTF-8; `.doc` and empty (scanned) files are refused with a reason.
- [x] "No timetable uploaded" for faculty who had uploaded: a coordinator upload deleted every faculty member's slots. It now replaces only the faculty the file has classes for.
- [x] `scripts/simulate_faculty.py` now covers syllabus files in both file lists, a slot for every allocation (lecture/tutorial/practical batch), Today never falling back on any weekday, and coordinator per-faculty uploads (110 checks).

### Slice 24: Root audit — documents, schema, pace, access (2026-11-12)
- [x] The institute's course-description format (`Cloud ML Course Description.docx`, now a fixture) gave 0 topics: Word list items have no bullet text. Lines under a unit heading are topics as written; back matter ends the syllabus; title and L-T-P are read and compared with the sheet.
- [x] Invented target weeks removed (parser spread topics over 11 weeks; apply defaulted week 1).
- [x] Re-uploading a syllabus deleted every topic, and `session_topic_log` cascades → all logged coverage silently erased. Now reconciled; dropping a covered topic is refused by name.
- [x] `topic_name`/`unit_title` were VARCHAR(255) while the parser keeps 500 → V10.
- [x] Tests never ran the real migrations (H2 + create-drop). `PostgresMigrationContractTest` runs V1–V10 on PostgreSQL 16 and validates every entity (Testcontainers 1.21.4: 1.19 can't talk to Docker 29).
- [x] Pace measured against the section's timetabled lectures and the term dates; NO_SYLLABUS / NOT_MEASURED instead of a fake ON_TRACK; report's "on plan" excludes unmeasured sections.
- [x] Timetable rejects one person in two rooms at once (same room = combined class).
- [x] Access: any faculty could overwrite anyone's skills, and read all allocations and loads. Coordinator-only now; the simulation attacks every route.
- [x] `docs/architecture/system-design.md`: sources of truth, formulas, invariants → tests.
- [ ] Frontend component tests; module-boundary ArchUnit rule.
