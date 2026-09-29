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

## [ ] Slice 9: Faculty Daily Session Logging & "Today" Screen
- [ ] Flyway migration `V8__teaching_sessions.sql`: `teaching_session` and `session_topic_log`
- [ ] Domain & Service: `TeachingSessionService` with strict ownership check (`faculty_id == currentUser.facultyId`)
- [ ] Session API: `POST /api/teaching-sessions`, `GET /api/teaching-sessions/today`
- [ ] Frontend: Faculty `TodayView.tsx` with 20-second quick-log form pre-selecting next uncovered topic

## [ ] Slice 10: Syllabus as Data, Progress Pacing & "My Courses" View
- [ ] Pacing calculation engine: Dynamic computation of coverage %, conduct rate %, and ahead/behind topic delta
- [ ] Faculty API: `GET /api/academics/my-courses` with per-offering syllabus progress
- [ ] Frontend: `MyCoursesView.tsx` with unit-by-unit syllabus breakdown, topic checkmarks, and pacing indicator

## [ ] Slice 11: Sparse Coordinator Overview Dashboard & Compliance Matrix
- [ ] Query projections: Worst-first coverage ranking, weekly conduct trajectory, and milestone compliance matrix
- [ ] Web API: `/api/tracking/overview-summary` and `/api/tracking/compliance-matrix`
- [ ] Frontend: Sparse `CoordinatorOverviewView.tsx` with 4 KPI numbers, Worst-First Bar Chart, Conduct Line Chart, Compliance Matrix, and actionable Needs-Attention list with 1-click reminders

## [ ] Slice 12: Skills Tag Input & Final E2E Verification
- [ ] Backend: Normalized `skill` table with case-insensitive unique lookup and dynamic insertion
- [ ] Frontend: `TagInput.tsx` chip component with autosuggest
- [ ] Integration tests: Complete daily log -> coverage calculation -> coordinator review flow
- [ ] Floor guard check: Zero suppressions, stubs, secret leaks, or weakened tests

