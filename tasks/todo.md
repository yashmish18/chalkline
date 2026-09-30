# Tasks List: Faculty Dashboard Implementation

## [x] Slice 0: Workspace Scaffolding & Foundational Setup
- [x] Initialize backend project structure (Maven `pom.xml` with Spring Boot 3.3+, Java 21, Spring Security, JPA, Flyway, Postgres)
- [x] Initialize frontend project structure (Vite + React + TypeScript + Tailwind CSS)
- [x] Set up `docker-compose.yml` for PostgreSQL 16 and Mailpit
- [x] Configure Tailwind CSS with Swiss Modernism tokens from `DESIGN.md`

## [x] Slice 1: Identity, Auth & Role Resolution
- [x] Flyway migration `V1__init_auth_and_users.sql` (`app_user`, roles `COORDINATOR`, `FACULTY`)
- [x] Security configuration with session cookie auth, CSRF, and user details service
- [x] Controller: `GET /api/me` returning current user profile and role
- [x] Frontend: Auth context, login simulation / Entra sign-in link, role-aware navigation bar

## [x] Slice 2: Faculty Directory & Teaching Allocations
- [x] Flyway migration `V2__directory_and_academics.sql` (`faculty`, `skill`, `faculty_skill`, `course`, `course_offering`, `teaching_assignment`)
- [x] Services & Repositories for Directory and Academics
- [x] Coordinator UI: Faculty directory with structured skill tags filtering
- [x] Coordinator UI: Course and teaching allocation matrix (course, year, semester, section, theory/lab)

## [x] Slice 3: Deadlines & Dynamic Assignment
- [x] Flyway migration `V3__deadlines.sql` (`deadline`, `deadline_assignment`)
- [x] `DeadlineService.createDeadline`: dynamic resolution of targets (all, selected, by course/semester)
- [x] Coordinator UI: Deadline creation modal with targeting rules
- [x] Faculty UI: My Deadlines list (filtered to current user)

## [x] Slice 4: File Submissions & Versioned Storage Port
- [x] Flyway migration `V4__submissions.sql` (`submission`, `submission_file`)
- [x] `FileStorage` interface with `LocalDiskStorage` implementation
- [x] `SubmissionService.submit`: file stream, MIME verification, SHA-256 hash, version increment
- [x] Faculty UI: Upload file, view status (`Pending`, `Submitted`, `Late`, `Returned`, `Accepted`)
- [x] Coordinator UI: Review submission, download file, accept or return with comments

## [x] Slice 5: Notifications Center (In-App & Email)
- [x] Flyway migration `V5__notifications.sql` (`notification` table)
- [x] `NotificationService`: in-app persistence + async email dispatch
- [x] Controllers: `GET /api/notifications`, `GET /api/notifications/unread-count`, `PATCH /api/notifications/{id}/read`, `POST /api/notifications/read-all`
- [x] Frontend: Notification bell in header, unread badge, dropdown list, mark-as-read, and deep linking
- [x] Background scheduled reminder runner (upcoming due date, overdue alerts)

## [x] Slice 6: Tracking Dashboard & Excel Export
- [x] Tracking query projections: submission rate, on-time rate, overdue count, critical compliance faculty
- [x] Coordinator UI: 4 KPI metric cards and drill-down department tracking table
- [x] Excel report generator using Apache POI: `/api/reports/submissions/export`
- [x] Audit logging for coordinator actions (`audit_log` table)

## [x] Slice 7: End-to-End Verification & UI Polish (UI-UX Pro Max)
- [x] Apply Swiss Modernism 2.0 aesthetics and refine spacing, cards, typography, and badges
- [x] Empty states, loading spinners, and WCAG AA contrast verification
- [x] Run full end-to-end integration flow tests

## [x] Slice 8: Academic Core Refactor & Persistent Sidebar Navigation
- [x] Flyway migration `V7__terms_and_syllabus.sql`: `academic_term` and `syllabus_topic`
- [x] Domain & Repos: `AcademicTerm`, `SyllabusTopic` entities with active term resolution
- [x] Academics API: Term management, Course catalog with syllabus topics, and IDOR protection
- [x] Frontend: Persistent collapsible `Sidebar.tsx` with role switching (Coordinator vs Faculty) and active term indicator

## [x] Slice 9: Faculty Daily Session Logging & "Today" Screen
- [x] Flyway migration `V8__teaching_sessions.sql`: `teaching_session` and `session_topic_log`
- [x] Domain & Service: `TeachingSessionService` with strict ownership check (`faculty_id == currentUser.facultyId`)
- [x] Session API: `POST /api/teaching-sessions`, `GET /api/teaching-sessions/today`
- [x] Frontend: Faculty `TodayView.tsx` with 20-second quick-log form pre-selecting next uncovered topic

## [x] Slice 10: Syllabus as Data, Progress Pacing & "My Courses" View
- [x] Pacing calculation engine: Dynamic computation of coverage %, conduct rate %, and ahead/behind topic delta
- [x] Faculty API: `GET /api/academics/my-courses` with per-offering syllabus progress
- [x] Frontend: `MyCoursesView.tsx` with unit-by-unit syllabus breakdown, topic checkmarks, and pacing indicator

## [x] Slice 11: Sparse Coordinator Overview Dashboard & Compliance Matrix
- [x] Query projections: Worst-first coverage ranking, weekly conduct trajectory, and milestone compliance matrix
- [x] Web API: `/api/tracking/overview-summary` and `/api/tracking/compliance-matrix`
- [x] Frontend: Sparse `CoordinatorOverviewView.tsx` with 4 KPI numbers, Worst-First Bar Chart, Conduct Line Chart, Compliance Matrix, and actionable Needs-Attention list with 1-click reminders

## [x] Slice 12: Skills Tag Input & Dynamic Persistence
- [x] Backend: Normalized `skill` table with case-insensitive unique lookup and dynamic insertion (`PUT /api/faculty/{id}/skills-by-name`)
- [x] Frontend: `TagInput.tsx` chip component with keyboard navigation, autosuggest dropdown, and chip deletion
- [x] Faculty directory integration: Dynamic skill addition with backend synchronization

## [x] Slice 13: LTP Contact Hours, Sub-sections/Batches & Master Timetable (Industry-Grade Swiss Modernism 2.0 UI)
- [x] Flyway migration `V9__ltp_subsections_and_timetable.sql`: `course.ltp`, `lecture_hours`, `tutorial_hours`, `practical_hours`, `course.description`, `sub_section` (`ALL`, `A1`, `A2`), and `timetable_slot` table
- [x] Backend domain, repositories, DTOs & endpoints: `GET /api/academics/timetable`, unit-grouped topics (`UnitOptionDto`), variable duration & sub-section session logging
- [x] Unlocked Session Logging: Faculty can select active academic unit (1–5), syllabus topic, or write/edit custom topic name and notes
- [x] Master Timetable Schedule: 5-day grid (Mon–Fri) with L/T/P colored badges, room allocations, and sub-sections in `TimetableView.tsx`
- [x] Course Overview Modal: `CourseDescriptionModal.tsx` showing LTP breakdown, weekly contact hours, course description, and unit syllabus pacing
- [x] App-wide UI Polish: Full Swiss Modernism 2.0 overhaul across Sidebar, Today, Timetable, My Courses, Allocations, Directory, Overview, Deadlines, and Submissions

## [x] Slice 14: PDF & DOC/DOCX Document Upload & Repository (Timetable, Academic Calendar & Course Syllabus)
- [x] Flyway migration `V10__academic_documents.sql`: `academic_document` table with categories `MASTER_TIMETABLE`, `ACADEMIC_CALENDAR`, `COURSE_SYLLABUS`
- [x] Backend domain & security: `AcademicDocumentService` with real Apache Tika MIME sniffing (PDF, Word DOC, DOCX), opaque UUID storage keys, and 25MB limits
- [x] Web endpoints: `POST /api/academic-documents/upload` (`COORDINATOR`), `GET /api/academic-documents/latest`, `GET /api/academic-documents/{id}/download` (in-browser inline preview / download)
- [x] Frontend UI: `UploadAcademicDocumentModal.tsx` drag-and-drop file uploader, integrated into `TimetableView.tsx` (Timetable & Calendar) and `CourseDescriptionModal.tsx` (Course Syllabus & Scheme)
- [x] Automated Tests: 73/73 Maven unit & integration tests passing, vitest tests passing, 0 type errors, and floor-guard certified
## [x] Slice 15: Linear / Raycast UI Redesign & High-Precision Date-Driven Timetable Logging
- [x] Adopt Linear / Raycast design tokens: `#5E6AD2` purple accent, hairline borders, elevated surfaces, and updated `DESIGN.md` specification
- [x] Date Picker Toolbar: High-precision `<input type="date">` with previous/next day chevron navigation, `Today` and `Yesterday` 1-click pills, live day-of-week formatting, and timetable sync badges (`Live Timetable`, `Historical Log`, `Future Schedule`)
- [x] Dynamic Session Logging: `GET /api/teaching-sessions/today?date=YYYY-MM-DD` syncs schedule cards dynamically to selected date; session logs submit exact `sessionDate: selectedDate`
- [x] 1-Click Segmented LTP Pills: `[Lecture (L)]` (1 contact hour), `[Tutorial (T)]` (1 contact hour), `[Practical (P)]` (2 contact hours) for instant timetable override
- [x] Grouped Section & Lab Batch Selector: Structured `<optgroup>` hierarchy (`Section A (Entire Division / Theory)` vs `Section A — Batch A1/A2 (Lab/Tutorial)`)
- [x] Full Verification: 73/73 Maven backend tests, vitest frontend tests, TypeScript typecheck, and quality floor guard passed
