# Faculty Dashboard — PRD, Feature Inventory & MVP

**Product:** IET Department Faculty Portal
**Audience:** Department Coordinator, Faculty Members
**Stack:** Spring Boot 3 (Java 21) + PostgreSQL 16 + Flyway · React 18 + TypeScript + Vite + Tailwind
**Status:** Phase 1 (Slices 0–7) and Phase 2 (Slices 8–13) implemented in the working tree.
**Source of truth for design:** `DESIGN.md`. Engineering constraints: `CONSTRAINTS.md`. Slice plan: `tasks/plan.md`.

---

## 1. Product Summary

A single department workspace that replaces the coordinator's spreadsheet, email chasing and Apps Script reminders. It has two halves:

1. **Compliance engine (Phase 1):** the coordinator assigns work, sets deadlines, collects and reviews submissions, and watches department compliance live.
2. **Academic tracker (Phase 2):** faculty log what they actually taught each day; syllabus coverage, conduct rate and business pacing are computed from those logs instead of self-reported numbers.

Principle: **no typed metrics.** Coverage %, conduct rate and pacing are always derived from verified session logs and submitted files.

---

## 2. Roles

| Role | Who | Capabilities |
|---|---|---|
| **COORDINATOR** | Department Coordinator | Faculty directory & skills, courses, offerings, teaching allocations, timetable, deadlines, submission review (accept/return), tracking + compliance matrix, audit logs, Excel export, direct reminders, official document upload |
| **FACULTY** | Teaching staff | Own courses, weekly timetable, daily session logging, syllabus pacing, own deadlines & submissions with revision history, notifications & skills profile |
| *(Phase 3)* **DEAN** | Dean-IET | Read-only executive escalation view. Out of current scope. |
| **System / Scheduler** | Background service | Reminder scans (5 days / 1 day / overdue), in-app notification writes, async email dispatch, orphan-file cleanup |

Authorization is server-side. Faculty scope is derived from the security context, never from client-supplied IDs (strict IDOR prevention — see `CONSTRAINTS.md`).

---

## 3. Glossary

| Term | Meaning |
|---|---|
| **LTP** | Lecture–Tutorial–Practical weekly contact-hour structure of a course, written `L-T-P` (e.g. `3-0-2`). Stored on `course.ltp` with decomposed `lecture_hours` / `tutorial_hours` / `practical_hours`. |
| **Course** | Catalog entry: code, title, credits, type, LTP, description. |
| **Offering** | A course taught in a specific term/year/semester/section. |
| **Teaching assignment** | A faculty member attached to an offering with a `component` (THEORY/LAB/LECTURE/TUTORIAL/PRACTICAL) and a `sub_section` batch. |
| **Timetable slot** | One weekly recurring class: day, start/end, room, component, batch. |
| **Teaching session** | One dated log entry: held / cancelled / swapped, unit, topics, conducted hours. |
| **Coverage %** | Covered syllabus topics ÷ total topics for an offering. |
| **Conduct rate %** | Held sessions ÷ scheduled sessions. |
| **Pacing** | Covered topics vs. expected topics by the current academic week (`ON_TRACK` / `AHEAD` / `BEHIND`). |

---

## 4. Current Feature Inventory (shipped)

### 4.1 Identity & Session
- Seeded users with `COORDINATOR` / `FACULTY` roles; `/api/me` session resolution.
- Dev sign-in switcher (coordinator / faculty) and logout. Microsoft SSO is planned, not wired.

### 4.2 Faculty Directory
- List / detail faculty: name, designation, qualification, department, skills.
- Structured skill tags (normalized, case-insensitive) with chip autosuggest input (`TagInput.tsx`).
- CSV/Excel **bulk faculty + allocation import** with a downloadable template (`/api/faculty/import`, `/api/faculty/import-template`).
- Faculty CRUD incl. delete and skill replacement by id or by name.

### 4.3 Courses, Offerings & Allocations
- Course catalog CRUD with LTP + description.
- Offerings by academic year / year-of-study / semester / section / term.
- Teaching allocation table with **semester filter**, single assign, multi-select bulk delete, and full reset ("Reset Sem N" / "Reset All").
- Batch-aware assignment: component `THEORY` / `LAB` plus sub-section `ALL` / `A1` / `A2`.

### 4.4 Timetable
- Weekly timetable grid (Mon–Fri) with day filter, component badges, room, batch, and LTP label.
- Slot delete for coordinators.
- **Official institutional documents:** upload/download/replace Master Timetable and Academic Calendar (PDF/DOC/DOCX, up to 25 MB), MIME byte-sniffed via Apache Tika, stored under opaque UUID keys.

### 4.5 Daily Teaching Log ("Today")
- Date selector bar (prev/next, native picker, Today / Yesterday pills) with sync state badge.
- Per-class card: component, batch, LTP, room, running topic/unit, syllabus progress bar, one-tap log.
- Session modal: status (HELD / CANCELLED / SWAPPED), **1-click LTP component override (L=1h, T=1h, P=2h)**, section/batch `<optgroup>` selector, unit, syllabus topic pick, editable custom topic, conducted hours, reason for non-held, teaching note.
- Remove a logged session; coverage recomputes immediately.

### 4.6 My Courses & Pacing
- Per-offering syllabus unit breakdown with covered-topic checkmarks.
- Coverage %, conduct rate, expected-vs-actual topic delta, pacing status/label.
- Course overview modal (`CourseDescriptionModal.tsx`) with LTP breakdown, weekly contact hours, description and unit pacing.
- Course-specific syllabus documents (`/api/academic-documents/course/{id}/latest`).

### 4.7 Deadlines & Submissions
- Coordinator deadline creation with targeting: all faculty, selected faculty, by semester, or by offering; auto-generates one assignment + empty submission slot per assignee.
- Faculty submission upload against an assignment; status progression Pending → Submitted/Late → Returned → Accepted.
- Versioned history: every resubmission kept, coordinator return-with-reason, faculty resubmission.
- Coordinator submission review (accept / return) and deadline delete.

### 4.8 Notifications
- In-app notification center: bell with unread badge, polling `/api/notifications/unread-count` (45s), dropdown list, mark-one/mark-all read, deep links.
- Automated reminder scheduler: upcoming (5 days / 1 day) and overdue.
- Coordinator **1-click direct reminder** to a faculty member (`/api/notifications/remind-faculty`) with async email dispatch.

### 4.9 Tracking, Compliance & Reporting
- Metrics: submission rate, on-time rate, overdue count, per-faculty compliance; thresholds On track ≥ 85%, Watch 60–84%, Critical < 60%.
- **Coordinator Overview** (sparse): 4 KPI tiles, syllabus-coverage worst-first bar chart (actual vs target), weekly conduct-rate line chart, milestone checkpoint matrix (Done / Pending / Overdue), and an actionable "Needs Attention" list.
- Tracking dashboard: by-deadline and by-faculty drill-down rosters.
- **Excel export** of submission status via Apache POI (`/api/reports/submissions/export`).
- Audit log for coordinator and faculty actions.

### 4.10 Platform
- Modular-monolith backend with ArchUnit boundary tests, Flyway migrations V1–V11, Jacoco coverage, floor-guard script, WCAG AA component audit.

---

## 5. PRD — Requirements

### 5.1 Goals
1. Remove email/spreadsheet chasing: one place for allocation, deadlines, submissions and compliance.
2. Make compliance evidence-based: coverage/conduct computed from faculty logs, not typed.
3. Surface problems early: worst-first views and a Needs-Attention list with a one-click nudge.
4. Keep faculty effort under ~20 seconds per class logged.

### 5.2 Non-goals (current)
- Student records, attendance, marks entry.
- Automatic timetable *generation* (slots are entered, not solved).
- Multi-department / multi-tenant.
- Mobile app, offline mode.
- Dean executive view (Phase 3).

### 5.3 Functional requirements

**FR-1 Directory.** Structured skills only; coordinator can filter "who can teach X". Bulk import from CSV/Excel with a template.
**FR-2 Allocation.** Every class must trace to (course, offering, section, component, batch, faculty). Allocations are the backbone: deadlines, timetable and tracking all hang off them.
**FR-3 Timetable.** Weekly slots with day/time/room/component/batch; official PDF/DOC artifacts attachable.
**FR-4 Deadlines.** Targeting rules generate one submission slot per assignee; return requires a reason; every version retained.
**FR-5 Daily log.** ≤ 20 s per class; pre-selects the next uncovered topic; supports cancelled/swapped with reason; supports LTP component override and batch.
**FR-6 Derived metrics.** Coverage, conduct rate and pacing are query-derived; no manual entry path exists.
**FR-7 Overview.** 4 KPIs + worst-first coverage + conduct trajectory + milestone matrix + Needs-Attention actions.
**FR-8 Notifications.** Dual channel (in-app + email) for assignment, feedback, reminders, escalations.
**FR-9 Reports.** Excel/CSV export of submission status.
**FR-10 Security.** Server-side authorization on every endpoint; faculty can never read another faculty member's data.
**FR-11 Documents.** MIME-sniffed, size-limited, opaque-keyed storage for institutional PDF/DOC.

### 5.4 User stories (sample)
- *As coordinator,* I create a deadline and target everyone teaching semester 5, so I don't email each person.
- *As coordinator,* I see all overdue submissions sorted by days overdue, so I know whom to chase today.
- *As coordinator,* I open the Overview and see the courses lagging behind, so I can act before month end.
- *As coordinator,* I send a one-click reminder from Needs Attention, so the nudge is logged and emailed.
- *As faculty,* I log today's teaching in under 20 seconds and see my syllabus coverage move.
- *As faculty,* I upload against a deadline, get returned feedback, and resubmit without losing history.
- *As faculty,* I can never see another faculty member's submissions.

### 5.5 Non-functional
Security (server-side authz, Tika validation, opaque storage, audit trail) · Availability in business hours, nightly backups · Scale of dozens of users and thousands of files/year, so simplicity over horizontal scaling · WCAG AA contrast, keyboard accessible, ≥ 40×40px touch targets.

---

## 6. MVP Definition

**MVP = Phase 1 (compliance loop) + the minimum Phase 2 slice that makes it evidence-based.**

### In MVP
- Identity + roles (`COORDINATOR`, `FACULTY`) and `/api/me`.
- Faculty directory with structured skills and CSV/Excel import.
- Courses, offerings, teaching allocations (component + batch).
- Deadlines with targeting; submissions with review, return-with-reason and version history.
- In-app notifications with unread badge + email dispatch on assignment/feedback/reminders.
- Tracking metrics + worst-first overview + Excel export.
- Daily teaching log (Today) and derived coverage/conduct/pacing.
- Weekly timetable read view + official document storage.

### Explicitly out of MVP
- Dean executive view; SSO/Microsoft sign-in hardening; automatic timetable generation; student data, attendance, marks; notifications digest settings; multi-department tenancy; mobile.

### MVP acceptance criteria
1. A coordinator creates a deadline targeted at "semester 5", and every matching faculty member sees it in-app and by email.
2. A faculty member uploads a file, gets it returned with a reason, resubmits, and the coordinator accepts — with all three versions retrievable.
3. A faculty member logs a held session with a topic in ≤ 20 s, and department coverage + conduct rate update on the coordinator Overview without any manual entry.
4. No faculty account can read another faculty member's submissions, assignments or sessions (verified by service-layer tests).
5. `mvn test`, `npm test` and `npm run typecheck` are green; ArchUnit boundary tests pass.

---

## 7. Architecture & Data Model

```
Identity & Auth (app_user, roles, /api/me)
  └── Directory & Academics (faculty, skill, faculty_skill, course, course_offering,
          teaching_assignment, timetable_slot, academic_term, syllabus_topic, academic_document)
        └── Deadlines & Targeting (deadline, deadline_assignment)
              ├── Submissions & Files (submission, submission_file, FileStorage → LocalDiskStorage)
              ├── Notifications (notification, unread polling, async email)
              └── Tracking & Analytics (metrics, rosters, overview, compliance matrix, Excel export)
                    └── Audit Trail
```

### Migrations
`V1` auth/users · `V2` directory & academics · `V3` deadlines · `V4` submissions · `V5` notifications · `V6` tracking & audit · `V7` terms & syllabus · `V8` teaching sessions · `V9` LTP, sub-sections, timetable · `V10` academic documents · `V11` term cleanup, coordinator allocations, deletes.

### API surface (current)
| Area | Endpoints |
|---|---|
| Auth | `POST /api/auth/dev-login`, `POST /api/auth/logout`, `GET /api/me` |
| Directory | `GET/POST /api/faculty`, `GET /api/faculty/{id}`, `PUT /api/faculty/{id}/skills`, `PUT /api/faculty/{id}/skills-by-name`, `DELETE /api/faculty/{id}`, `GET /api/skills`, `POST /api/faculty/import`, `GET /api/faculty/import-template` |
| Academics | `GET/POST /api/courses`, `DELETE /api/courses/{id}`, `GET /api/offerings`, `GET/POST /api/teaching-assignments`, `DELETE /api/teaching-assignments/{id}`, `DELETE /api/teaching-assignments`, `POST /api/teaching-assignments/bulk-delete`, `GET /api/faculty/{id}/teaching-assignments` |
| Terms & syllabus | `GET /api/terms`, `GET /api/terms/active`, `POST /api/terms/{id}/activate`, `GET /api/courses/{id}/syllabus` |
| Pacing / timetable | `GET /api/academics/my-courses`, `GET /api/academics/offerings/{id}/progress`, `GET /api/academics/timetable`, `DELETE /api/academics/timetable/{id}` |
| Documents | `POST /api/academic-documents/upload`, `GET /api/academic-documents`, `GET /api/academic-documents/latest`, `GET /api/academic-documents/course/{id}/latest`, `GET /api/academic-documents/{id}/download`, `DELETE /api/academic-documents/{id}` |
| Deadlines | `GET/POST /api/deadlines`, `GET /api/deadlines/{id}`, `GET /api/deadlines/{id}/assignments`, `GET /api/my-deadlines`, `DELETE /api/deadlines/{id}` |
| Submissions | `GET /api/deadline-assignments/{id}/submission`, `POST /api/deadline-assignments/{id}/submissions`, `POST /api/submissions/{id}/review`, `GET /api/submissions/{id}/history`, `GET /api/deadlines/{id}/submissions`, `GET /api/submission-files/{id}/download` |
| Notifications | `GET /api/notifications`, `GET /api/notifications/unread-count`, `PATCH /api/notifications/{id}/read`, `POST /api/notifications/read-all`, `POST /api/notifications/trigger-reminders`, `POST /api/notifications/remind-faculty` |
| Teaching sessions | `POST /api/teaching-sessions`, `GET /api/teaching-sessions/today`, `GET /api/teaching-sessions/offering/{id}`, `DELETE /api/teaching-sessions/{id}` |
| Tracking | `GET /api/tracking/metrics`, `/by-deadline`, `/by-faculty`, `/overview-summary`, `/compliance-matrix`, `/audit-logs`, `GET /api/reports/submissions/export` |

---

## 8. How LTP Works Today (and the ideal model)

### 8.1 Current implementation

LTP lives in **one place**: the course catalog.

- `course.ltp` (e.g. `3-0-2`) plus `lecture_hours`, `tutorial_hours`, `practical_hours`, `description` (`V9` migration).
- It is **not** stored on the allocation. `teaching_assignment` carries only `component` (`THEORY`/`LAB`, and `LECTURE`/`TUTORIAL`/`PRACTICAL` are accepted values) and `sub_section` (`ALL`/`A1`/`A2`).
- `timetable_slot` is where hours become concrete: each row is one weekly class with `component` + start/end time + room + batch.
- `teaching_session` records actuals: `session_type` (component), `lectures_taken` (contact hours), `sub_section`, status, topics.
- Display only: the LTP string is shown raw (allocation table, timetable, Today card, My Courses) and split into L/T/P for the course modal (`CourseDescriptionModal.tsx`). `PacingCalculationServiceImpl` passes it through DTOs unchanged.

**Consequence:** nothing links the three layers. A `3-0-2` course can have zero practical slots scheduled, or a lab assigned to faculty with no `PRACTICAL` timetable rows, and the system still shows a green `3-0-2`. Per-faculty LTP load is implicit and unverifiable.

### 8.2 Recommended model (ideal)

Make LTP flow down one direction and be validated at each hop:

| Layer | Source of truth | Meaning |
|---|---|---|
| **Plan** | `course.ltp` + L/T/P hours | What the syllabus requires per week. |
| **Ownership** | `teaching_assignment` gains component **hours share** (e.g. theory owns 3L, lab owns 2P split A1/A2) | Who is accountable for which hours. |
| **Schedule** | `timetable_slot` durations summed per component | What is actually on the weekly grid. |
| **Actuals** | `teaching_session.lectures_taken` summed per component | What was really conducted. |

Rules to enforce in the service layer:
1. **Allocation validation** — an assignment's component must appear in the course LTP (`0-0-4` cannot get a THEORY assignment).
2. **Timetable validation** — Σ slot duration per component == LTP hours × batch share; reject or flag mismatches on slot create.
3. **Load as a projection** — faculty weekly LTP load is computed from assignments + slots, never stored as a typed number.
4. **Three-way reconciliation on Overview** — Planned (LTP) vs Scheduled (slots) vs Conducted (sessions), which upgrades the current conduct-rate chart from "held vs scheduled" to "held vs planned".

This keeps the course as the single authority for LTP (good: it is a syllabus property, not a person property) while making the allocation and timetable responsible for delivering it.

---

## 9. Roadmap

### 9.1 Shipped since the first draft

| Area | Change |
|---|---|
| **Theme** | Dark graphite product canvas (`DESIGN.md` §1.0) with a single lavender-blue accent, replacing the flat light canvas. |
| **Auth** | Centred sign-in card with **Continue with Microsoft**; `POST /api/auth/microsoft` is the Entra ID seam (`UserService.authenticateSso`). Auto-dev-login on 401 removed so the login screen is reachable. |
| **LTP ownership** | `teaching_assignment.contact_hours` + `CourseComponent.ALL`; component validated against the course plan; hours default to the plan and are never typed by hand. `GET /api/teaching-assignments/faculty-load` projects weekly L-T-P load per faculty. Allocation screen shows owned hours, a plan-vs-owned reconciliation banner and the load table. |
| **Enum bug** | `CourseComponent` was missing `PRACTICAL`, which `V9` already seeded — loading any assignment threw and 500'd the coordinator overview. Enum extended. |
| **Course setup** | `POST /api/courses` now takes credits + an LTP plan (hours derived from it) + category; `POST /api/offerings` opens a section. One "Add Course & Section" modal does both. |
| **Course category** | `course.category` — Core / Department Elective / Open Elective (`V13`), with input normalisation. Nature (theory vs lab) is derived from the LTP plan. |
| **Notification fan-out** | New `SubmissionSubmittedEvent` → every coordinator gets an in-app + email alert on upload/resubmission (late flagged). Previously faculty uploads produced **no** notification at all. |

### 9.2 Open gaps

1. `CoordinatorOverviewView` still collapses `/api/tracking/overview-summary` and `/api/tracking/compliance-matrix` into one generic error — needs per-endpoint surfaces and a 401/500 split.
2. LTP is validated at allocation time but **timetable slots are not yet reconciled** against the plan (planned vs scheduled vs conducted).
3. Entra ID is a seam, not a real OAuth exchange; the endpoint resolves the seeded institutional account.
4. Notifications have no per-user preference or digest controls.
5. Excel export covers submission status only.
6. `academic_term` supports one active term; multi-term switching UI is minimal.

### 9.3 Designed, not yet built

**Slice 14 — Syllabus ingestion from PDF.** Faculty upload the course description; the backend extracts text with Apache Tika and parses units/topics into `syllabus_topic` rows, with a preview-and-confirm step before persisting. Today the daily log's topic dropdown depends on topics that must already exist in the database.

**Slice 15 — Multi-department.** A `department` table; `faculty.department` and `course.category` become real foreign keys; department filters on Faculty Directory, Course Allocations and the Overview; coordinator scope per department. Requires a data migration for the existing `faculty.department` string.

**Slice 16 — Faculty profile.** Clicking a faculty card opens a full profile: department, every course/section they own with LTP share, submission history with on-time rate, and compliance record. Replaces the inline skill-tag editing on the directory card.
