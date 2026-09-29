# Faculty-Driven Academic Tracker & Compliance Engine

## Problem Statement
How might we shift the data logging burden from the coordinator to faculty via a 20-second daily session logger, so syllabus coverage and compliance are derived dynamically in real time while coordinators focus on targeted review and support?

## Recommended Direction
1. **Relational Academics Core**: Terms, Course Catalog, Offerings, and Assignments model all academic relationships without free-text duplicates.
2. **Syllabus as Data**: Course topics grouped by unit with weekly targets, enabling mathematically computed syllabus completion.
3. **20-Second Faculty Daily Log**: Quick-log interface on "Today" screen pre-selecting the next uncovered topic.
4. **Decision-Driven Coordinator Overview**: 4 KPIs, Worst-First Bar Chart, Conduct Line Chart, Compliance Checkpoint Matrix, and an actionable Needs-Attention list.
5. **Role-Enforced Monolith**: Strict service-layer ownership verification preventing IDOR.

## Key Assumptions to Validate
- [x] Faculty log their classes if the daily interaction is under 20 seconds.
- [x] One department, one active term at any given time.
- [x] Coordinator requires computed pacing and compliance matrices rather than self-reported summary numbers.

## Architecture & Data Flow
- **`academic_term`**: Name (e.g. "Odd 2026-27"), start date, end date, `is_active`.
- **`course`**: Catalog entry with code, title, credits, type (THEORY/LAB).
- **`syllabus_topic`**: Topics grouped by unit (1-5) with `target_week` and sequence order.
- **`course_offering`**: Course in an active term with section.
- **`teaching_assignment`**: Links offering to faculty (supports shared labs).
- **`teaching_session`**: Daily log (date, offering, type, status HELD/CANCELLED/SWAPPED, topics covered, reason/note).

## Core Principles
1. **No Typed Metrics**: Syllabus coverage % and weekly conduct rate are computed from session logs, never typed.
2. **Strict Least Privilege**: Faculty can read/write only their assigned offerings and sessions. IDOR is prevented at the service layer.
3. **Sparse Coordinator Visuals**: Overview contains only 1 row of 4 KPI numbers, Worst-First Bar Chart, Conduct Line Chart, Compliance Checkpoint Matrix, and a computed Needs-Attention list with 1-click reminders.
4. **Normalized Skill Tags**: Tag input backed by a case-insensitive normalized skill lookup to prevent duplicate fragmentation.
