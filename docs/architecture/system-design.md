# System design: Faculty Dashboard

Last verified: 2026-11-12 — `mvn test` 301 green (incl. the Postgres contract test), `npm test` 29 green,
`scripts/simulate_faculty.py` 135 checks against a live backend.

This is the contract the code is held to. When code and this page disagree, one of them is a bug.

## 1. The one rule

**Every number on screen is either typed/uploaded by a person, or computed from such values by a
formula written on this page.** Nothing is defaulted, guessed or spread. When a value is missing,
the screen says what is missing (`NO_SYLLABUS`, `NOT_MEASURED`, "No timetable uploaded").

## 2. Sources of truth

| Data | Comes from | Entered by | Validated at |
|---|---|---|---|
| People, courses, sections, students, batches, L-T-P, skills, PBL | Allocation sheet (one template) | Coordinator | `AllocationImportServiceImpl`: whole file first; any error → nothing saved |
| Semester name and dates | Semester settings | Coordinator | `AcademicsController /terms/active` |
| Weekly classes (day, time, room, component, batch) | Timetable PDF or sheet | Each faculty member, or the coordinator for anyone | `TimetableImportServiceImpl`: every cell checked against the allocation |
| Units and topics, in order | Course description (.docx / PDF / .txt) | Faculty member of the course, or coordinator | `SyllabusTextParser` + preview; differences from the sheet are listed |
| Classes held / not held, lecture number, topics covered, delay reason | Class log | Faculty member | `TeachingSessionServiceImpl` time and ownership rules |

## 3. Modules and layers

`identity` (users, roles, sign-in) · `directory` (faculty, skills, departments) · `academics`
(courses, offerings, allocations, timetable, syllabus, pacing) · `teaching` (class logs) ·
`tracking` (overview, reports) · `deadlines` · `submissions` · `notifications` · `shared`.

Each module is `web → service → repo → domain`. **Enforced** by `ArchitectureTest`: controllers never
use repositories; services are reached only from controllers, services, config and notifications;
entities never live in `web`. **Not enforced yet:** module-to-module boundaries
(e.g. `academics` pacing reads `teaching` repositories directly).

## 4. Pipeline, layer by layer

```
 upload ──► parse ──► validate ──► store ──► derive ──► present
 (file)    (exact    (against     (one       (formulas  (states say
           text)     the sheet)   transaction) §5)       what's missing)
```

1. **Parse** reads the file's own text: PDFBox for PDF, POI for .docx/.doc and sheets, UTF-8 for text.
   (tika-core is used only to *detect* the type; it has no parsers.) A scanned PDF (no text) is
   refused with a reason, never read as empty.
2. **Validate** checks every row against what already exists and lists every problem with its row.
3. **Store** writes in one transaction. Re-uploads *reconcile*; they don't delete and re-insert (§6).
4. **Derive** computes the numbers in §5 at read time; nothing derived is stored.
5. **Present** shows the facts behind each status ("2 of 10 covered; 12 of 24 timetabled lectures are past").

## 5. Derived numbers

| Number | Formula |
|---|---|
| Load per allocation | Lecture row = L, tutorial row = T, each practical batch = P; + PBL once per person |
| Timetabled lectures of a section | Each LECTURE slot × the dates in [term start, term end] on its weekday |
| Expected topics by today | round(topics × lectures dated before today ÷ all the term's timetabled lectures) |
| Pace | covered − expected: > 1 AHEAD, < −1 BEHIND, else ON_TRACK (one topic is rounding). No topics → NO_SYLLABUS. No term dates, no lecture slots, or before the term → NOT_MEASURED |
| Teaching week | 1 + days since term start ÷ 7, capped at the term's last week |
| Lecture number ↔ topic | Topics are numbered 1…N in document order across units (`lectureOrder`); Lecture N pre-selects topic N |

Holidays (date + name, entered by the coordinator, inside the term, one per date) are skipped by
every count of timetabled classes: not-logged list, Today, pace, and the coordinator's "not logging" check.

## 6. Invariants and where they are enforced

| Invariant | Enforced in | Proven by |
|---|---|---|
| Whole allocation sheet valid or nothing saved | `AllocationImportServiceImpl` | `AllocationImport*Test`, simulation |
| A repeated value (course name, L-T-P, section strength) is identical on every row | same | same |
| A timetable cell must match an allocation (course, section, component, batch) | `TimetableImportServiceImpl` | `TimetableImport*Test` |
| An upload replaces the timetable only of the people it has classes for | same | `aCoordinatorUploadKeepsTheTimetablesOfFacultyNotInTheFile`, simulation |
| One person is never in two rooms at once (same room = combined class, allowed) | same | `aFacultyMemberBookedInTwoRoomsAtOnceIsRejected` |
| A two-hour lab is one class | `mergeBackToBackPracticals` | `TimetablePdfParserTest`, simulation |
| Topics are kept exactly as written; no week is invented | `SyllabusTextParser` | `readsTheRealCourseDescriptionExactlyAsWritten` (the institute's real .docx) |
| Document vs sheet: title and L-T-P differences are named | `SyllabusImportServiceImpl.parse(file, courseId)` | `namesEveryDifference…`, simulation |
| Re-uploading a syllabus never erases logged coverage (`session_topic_log` cascades) | `SyllabusImportServiceImpl.apply` reconcile: same words, or same place in an unchanged-size unit, keeps the row; a covered topic can't be dropped | `reuploadingKeepsLoggedTopics…`, Postgres contract test, simulation |
| Component vocabulary is LECTURE / TUTORIAL / PRACTICAL everywhere | `CourseComponent`, V9 | `TeachingSessionServiceTest` |
| No log before the class starts; > 48 h late needs a reason; lecture numbers unique per section/batch | `TeachingSessionServiceImpl` | same, simulation |
| The schema the code expects is the one the migrations build | Flyway V1–V10 + Hibernate `validate` | `PostgresMigrationContractTest` (real PostgreSQL 16) |

## 7. Access control

Identity always comes from the server-side session, never from a request parameter
(`CONSTRAINTS.md`). Coordinator-only: allocations (all reads and writes), department load, skills,
courses, terms, overview and reports. Faculty: their own timetable, classes, class history, and the
syllabus of a course they teach; an id naming someone else is ignored or refused.
Department documents (syllabi, calendar, timetables) are readable by all staff by design.
The simulation's last step attacks a colleague's data through every one of these routes.

## 8. How it is verified

| Layer | What runs | Catches |
|---|---|---|
| Unit / slice (H2) | `mvn test` | rules, parsers on real files, controller authorisation |
| Schema contract (PostgreSQL 16 via Testcontainers ≥ 1.21) | `PostgresMigrationContractTest` | entity/migration drift, Postgres-only SQL, constraint behaviour |
| Architecture | `ArchitectureTest` | layer violations |
| End to end | `scripts/simulate_faculty.py` against a running backend on an empty DB | the whole flow for ten faculty, letter-for-letter round trip, cross-faculty attacks |
| Frontend | `npm run typecheck`, `npm test` (Vitest; component tests on jsdom with Testing Library) | types, helpers, lecture numbering, key components |

## 9. Known gaps

- Frontend component tests cover the pace badge, syllabus preview and semester/holiday editor; the
  large views (Today, My Courses, Allocations) still have none, and there are no browser tests.
- Module boundaries (§3) are convention, not enforced.
- Scanned PDFs have no text to read.
