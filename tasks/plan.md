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
