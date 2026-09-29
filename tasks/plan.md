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
