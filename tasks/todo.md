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

## [ ] Slice 4: File Submissions & Versioned Storage Port
- [ ] Flyway migration `V4__submissions.sql` (`submission`, `submission_file`)
- [ ] `FileStorage` interface with `LocalDiskStorage` implementation
- [ ] `SubmissionService.submit`: file stream, MIME verification, SHA-256 hash, version increment
- [ ] Faculty UI: Upload file, view status (`Pending`, `Submitted`, `Late`, `Returned`, `Accepted`)
- [ ] Coordinator UI: Review submission, download file, accept or return with comments

## [ ] Slice 5: Notifications Center (In-App & Email)
- [ ] Flyway migration `V5__notifications.sql` (`notification` table)
- [ ] `NotificationService`: in-app persistence + async email dispatch
- [ ] Controllers: `GET /api/notifications`, `GET /api/notifications/unread-count`, `PATCH /api/notifications/{id}/read`, `POST /api/notifications/read-all`
- [ ] Frontend: Notification bell in header, unread badge, dropdown list, mark-as-read, and deep linking
- [ ] Background scheduled reminder runner (upcoming due date, overdue alerts)

## [ ] Slice 6: Tracking Dashboard & Excel Export
- [ ] Tracking query projections: submission rate, on-time rate, overdue count, critical compliance faculty
- [ ] Coordinator UI: 4 KPI metric cards and drill-down department tracking table
- [ ] Excel report generator using Apache POI: `/api/reports/submissions/export`
- [ ] Audit logging for coordinator actions (`audit_log` table)

## [ ] Slice 7: End-to-End Verification & UI Polish (UI-UX Pro Max)
- [ ] Apply Swiss Modernism 2.0 aesthetics and refine spacing, cards, typography, and badges
- [ ] Empty states, loading spinners, and WCAG AA contrast verification
- [ ] Run full end-to-end integration flow tests
