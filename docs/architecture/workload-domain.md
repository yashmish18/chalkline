# Architecture & Process: Workload Sheet as Source of Truth

**Status:** Slices W0–W3 implemented and verified; W4–W5 (declared-load schema, review UI) open
**Verified:** `mvn -o test` → 135 tests green. Importing the real sheet yields exactly the 104
offerings and 110 `(offering, faculty)` assignments `V16` seeded, so re-importing the file is
idempotent.
**Source artefact:** `Course Workload Distributions.xlsx` (IET, 51 faculty rows)
**Fixture:** `backend/src/test/resources/load-sheet.xlsx`
**Executable contract:** `backend/src/test/java/com/iet/dashboard/integration/LoadSheetCompatibilityTest.java`
**Constraints honoured:** `CONSTRAINTS.md` (server-side authz, no silent coercion, no stubs) · `DESIGN.md` (spreadsheet-surface UI language)

---

## 1. Why this exists

The coordinator already owns this spreadsheet. It is the real, authoritative weekly workload
distribution for the department: who teaches which subject, in which semester, to which
sections, how many PBL blocks, and a declared `Total Load`. The dashboard is supposed to
*replace* the spreadsheet (PRD §1), but it cannot even read it.

An audit that pushed the real sheet through both existing import endpoints produced:

| # | Symptom | Root cause | Blast radius |
|---|---|---|---|
| B1 | `POST /api/faculty/import` skips all 51 rows — `0 faculty created` | Importer requires an `email` column; the sheet identifies people by name + abbreviation | Entire sheet unusable |
| B2 | `POST /api/courses/import` writes 46 courses with `LTP 3-12-2`, 16 credits | Header detector accepts **1-letter substring aliases** (`l`, `t`, `p`); `PBL` → `PRACTICAL_HOURS`, `Total Load` → `TUTORIAL_HOURS` | Corrupt catalogue from any non-template sheet |
| B3 | Composite cells become literal entities (`CS1139/CS1218` code, section `"C, F"`) | No multi-value split for `/`-joined codes/semesters/sections | Unusable offerings & assignments |
| B4 | `"1st Sem/5th Sem"` → semester `1` | Semester parser only understands bare integers | Wrong semester on every multi-semester row |
| B5 | Blank cells create an invisible course | NBSP (`\u00A0`) survives Java `trim()` | Ghost rows / unique-key pollution |
| B6 | Offerings hardcoded to `year 3 / semester 5`; section lookup ignores semester | `FacultyImportServiceImpl` literals + `findFirstByCourseIdAndSection` | Cross-semester section collisions |
| B7 | Demo dataset: 2 fictional people, 4 courses, one term | Seeds (V1/V2/V11/V15) never saw the real roster | Dashboard is not representative or demoable |

B1–B6 are defects in the import boundary. B7 is a data-fidelity defect. This document defines
the target architecture that removes the class of defect, not just the instances.

**Status after Slices W1–W3:** B1–B6 are fixed and pinned by tests (`SpreadsheetReaderTest`,
`WorkloadSheetParserTest`, `LoadSheetCompatibilityTest` — the last one no longer `@Disabled`).
B7 is served by the `V16` reseed. `Abbrevation`, `PBL` and declared `Total Load` are parsed but
not yet stored; that is Slice W4.

---

## 2. Principles

1. **The sheet is an input document, never a table.** Import is an idempotent *upsert run*;
   nothing downstream reads the file.
2. **Parse purely.** All spreadsheet grammar lives in one pure, dependency-free unit
   (`WorkloadSheetParser`) that returns typed rows and issues. No Spring, no JPA — it is the
   first thing unit-tested and the last thing rewritten.
3. **Tolerate mess; refuse nonsense.** Normalise aggressively at the boundary (NBSP, stray
   case, typos in values are tolerated). But an unparseable numeric or an unresolvable identity
   produces a **row issue**, never a silent coercion.
4. **One importer path.** The course importer and faculty importer become thin adapters over
   the same detection + normalisation primitives. Two parsers is how B2/B3 happened.
5. **Declared ≠ computed.** The sheet's `Total Load` is *declared* data. The system's LTP load
   (`GET /api/teaching-assignments/faculty-load`) is *computed*. They are stored and shown
   side by side; the system never overwrites one with the other.
6. **No new module unless earned (YAGNI).** The sheet spans Directory + Academics, but both are
   already reachable from the existing importer. Ship inside `directory`; extract a `workload`
   module only when a second consumer appears.

---

## 3. Target domain model

Existing tables carry most of it. Four additions, staged in Slice W4:

```
faculty
  + abbreviation            VARCHAR(20)  -- sheet "Abbrevation"; NOT unique (sheet reuses "SM","MS","RS")

workload_import             -- one row per uploaded sheet (audit + idempotency key)
  id, source_filename, content_sha256, academic_term_id,
  uploaded_by (app_user), total_rows, faculty_created, faculty_updated,
  courses_created, offerings_created, assignments_created, created_at

workload_import_issue       -- replaces the "messages: List<String>" black box
  id, import_id, row_number, severity (INFO|WARN|ERROR), field, raw_value, message

faculty_semester_load       -- the sheet's declared per-faculty row
  id, faculty_id, academic_term_id, declared_total_load, pbl_blocks,
  source_import_id, UNIQUE (faculty_id, academic_term_id)
```

Everything else reuses `app_user`, `faculty`, `course`, `course_offering`,
`teaching_assignment` unchanged.

**Identity rule (fixes B1).** A faculty row resolves identity in this order:
1. explicit `email` column, else
2. deterministic slug `firstname.lastname@iet.edu` (collision suffix `-2`, `-3`, …), else
3. the raw name as `name` only — never silently skipped.

`abbreviation` is stored but **never used as a key** (it is not unique in the real sheet).

---

## 4. Import pipeline

```
 upload (CSV/XLSX, ≤ 25 MB, coordinator-only)
    │
    ▼
 SpreadsheetReader.read                ← normalise NBSP→space, collapse ws, keep column positions
    │
    ▼
 detectColumns(HEADER_RULES)           ← exact alias (any length) → substring alias (len ≥ 3)
    │                                    unmapped / ambiguous headers reported, never guessed
    ▼
 WorkloadSheetParser.parse(rows)       ← PURE. emits List<WorkloadRow> + List<RowIssue>
    │   • split codes on '/'           • align semesters & section-groups by index
    │   • sections on ','              • ordinal semester ("5th Sem") → int; year = ceil(sem/2)
    │   • strip "(N Sections)", "..(Batch C,F)" qualifiers from names
    ▼
 WorkloadImportService.apply(rows)     ← @Transactional, idempotent upsert
    │   ├─ identity  → app_user + faculty (abbreviation)
    │   ├─ course    → upsert by code (skip blank/NBSP; never invent a code)
    │   ├─ offering  → upsert by (course, academic_year, year_of_study, semester, section)
    │   └─ assignment→ upsert by (offering, faculty, component, sub_section)
    ▼
 persist WorkloadImport + WorkloadImportIssue + faculty_semester_load
    │
    ▼
 WorkloadImportResultDto  → UI review table (counts + per-row issues + declared vs computed load)
```

### 4.1 Header-matching rule (fixes B2)

`matchRule` gets three guards, in this order:

1. **Exact alias match wins outright** and accepts any alias length, so a literal `L`/`T`/`P`
   column keeps working.
2. **Substring matching requires `alias.length() >= 3`**, so a header merely containing the
   letter `l` no longer becomes `LECTURE_HOURS`.
3. **The most precise header wins a column**: exact beats substring, then longer alias beats
   shorter. Length ≥ 3 was not enough on its own — `sub` (3 chars) swallowed "Subject Name".

An alias written `=name` is **exact-only**: it matches its own header but is never substring
matched, which is how generic words like `name`, `type`, `sub` and `title` stay precise. Headers
no rule claims are reported back to the coordinator instead of being silently dropped.

### 4.2 Cell-normalisation rule (fixes B5)

`SpreadsheetReader` normalises every header and every cell once:
`value.replace('\u00A0', ' ').replaceAll("\\s+", " ").trim()`. One guard, every importer.

### 4.3 Row-expansion rule (fixes B3, B4, B6)

```
codes     = split(codeCell,       '/')  → keep only ^[A-Z]{2,3}\d{3,4}$
semesters = split(semCell,        '/')  → ordinal→int, aligned to codes by index
sections  = split(sectionCell,    '/')  → aligned to codes; each group split(',') → letters
yearOfStudy = ceil(semester / 2)
```

- `N codes == 1` → one offering per section key found in the section cell.
- `N codes > 1` → group *j* takes `sections[j]`; each group opens one offering per key.
- Fewer groups than codes (no `/` group at all) → reuse the last group.
- An **empty** group (`"D, I/"`) means the sheet says nothing for that course: sections become
  `ALL`. This is *not* the same as reusing the previous group, and getting it wrong cost exactly
  one phantom offering (row 16, `AS1109/`).
- Every semester named in a group opens an offering, so `"3rd Sem/5th Sem & 7th Sem"` is three.
- Unreadable semester → issue `ERROR`, that row's offerings skipped (not defaulted to 1).
- A malformed code becomes an issue `WARN`; a blank or non-breaking-space code is dropped
  silently, because the sheet genuinely has eight such rows.

### 4.4 Idempotency

Re-uploading the same file with the same `content_sha256` returns the recorded run and changes
nothing. Partial re-uploads upsert per `(course, …)` and `(offering, faculty, component,
sub_section)` keys, so a corrected sheet heals without duplicates.

---

## 5. Layering & boundaries

| Concern | Home | Rule |
|---|---|---|
| Sheet grammar + normalisation | `shared.spreadsheet` | Pure static utils; no Spring |
| Row parsing | `shared.spreadsheet.WorkloadSheetParser` | Pure; unit-tested in isolation |
| Orchestration | `directory.service.WorkloadImportService` | `@Transactional`; may read Academics repos (already does today) |
| HTTP | `directory.web.FacultyController` (`POST /api/faculty/import`) | `@PreAuthorize("hasRole('COORDINATOR')")`; no repo access (ArchUnit) |
| Review UI | `frontend` import modal | Reads `WorkloadImportResultDto` |

ArchUnit stays satisfied: controllers keep depending on services only; the new parser lives in
`shared`, not a service, so the `..service..` access rule is untouched.

**Deferred (Slice W4, only if triggered):** promote to a `workload` module with its own
`web`/`service`/`repo` when the parser gains a second entry point (e.g. CLI backfill).

---

## 6. Validation & non-functional

- **Boundary validation (never lazy):** coordinator-only; size/type limits unchanged; a sheet
  with no detectable code column or no identity column fails with which header was expected.
- **Reconciliation surfaced, not enforced:** Overview shows `declared` vs `computed` load per
  faculty with a mismatch flag; mismatches are normal (the sheet predates catalogued LTP).
- **Security:** issues and runs are coordinator-scoped; no client-supplied faculty id is trusted
  (CONSTRAINTS floor).
- **Performance:** 51 rows is trivial; the pipeline stays O(rows) with one upsert per entity.

---

## 7. Data-quality findings in the sheet itself

The importer must tolerate these; the reseed must not pretend they are clean:

- Duplicate abbreviations: `SM` (Shobha Mangal, Supriya Mishra), `MS` (Megha Sahu, Monika Soni),
  `RS` (Rohit Singh, Richa Sharma) — abbreviation is **not** a key.
- Non-personal rows: `NF1-CSE`, `NF1-MATHS`, `NF1-CCAT` (visiting/adjunct allocations) — valid
  faculty rows, no personal email.
- Duplicate profession: `Dr. Sushant Pokhriyal` and `Dr. Sushant Pokhriyal` (row 18) vs a
  second subject line — same abbreviation reused across rows.
- Typos and casing: `Abbrevation`, `tuorial`, `Buisness`, `Eentrepreneurship`, `Prespectives`,
  `Digital System Design`.
- Multi-semester / multi-code rows where the `/` groups do not align 1:1 (e.g. 3 codes, 2 named
  subjects) — aligned best-effort, mismatch reported.
- `Total Load` / `PBL` are declared integers that do **not** reconcile with sections — stored as
  declared, flagged against computed.

---

## 8. Slice map

See `tasks/plan.md` §"Phase 3 — Workload Sheet as Source of Truth" and `tasks/todo.md`.
The one thing that must go green first is `LoadSheetCompatibilityTest` (Slice W1–W3).
