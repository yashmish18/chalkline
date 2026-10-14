# Import formats

Both files can be `.csv` or `.xlsx`. Headers are matched by name (case, spaces and order don't
matter). Upload allocations first, then the timetable. Re-uploading updates in place.

## 1. Allocations — `allocations-odd-2026.csv`

**Upload:** Course Allocations → Import Spreadsheet (coordinator).
One row per **faculty × course × section × component × batch**.

| Column | Required | Example | Notes |
|---|---|---|---|
| Faculty Name | yes | `Dr. Amit Sinhal` | A note like `(Visiting Faculty)` becomes the designation |
| Email | | `amit.sinhal@iet.edu` | Blank → derived from the name |
| Designation | | `Assistant Professor` | |
| Department | | `CSE` | Code or full name |
| Course Code | yes | `CS1139` | |
| Course Name | | `Programming-I` | |
| L-T-P | | `3-0-2` | Weekly lecture-tutorial-practical hours; sets the course plan |
| Semester | yes | `1` | 1–8 |
| Section | | `C` | Blank or `ALL` = whole cohort (electives) |
| Section Students | | `64` | Students enrolled in the section |
| Component | | `Lecture` | `Lecture`, `Tutorial` or `Practical` (blank = Lecture) |
| Batch | | `C1` | Blank = the whole section. Only write a batch if the section is split |
| Batch Students | | `32` | Students in that batch |
| Weekly Hours | | `2` | Blank = taken from L-T-P |
| PBL Hours | | `2` | Per faculty member, weekly |

The sample is this semester's real allocations exported from the system. Section Students is
blank because the original load sheet has no strengths — add them and re-import.

## 2. Timetable — `timetable-sample.csv`

**Upload:** Timetable → Import timetable (CSV). The coordinator's upload is the master timetable
and replaces every slot. A faculty member can upload their own (no email column needed), which
replaces only their slots.

| Column | Required | Example |
|---|---|---|
| Day | yes | `Mon`, `Tuesday` |
| Start / End | yes | `09:00`, `2:00 PM` |
| Room | | `LT-2` |
| Course Code | yes | `CS1139` |
| Semester | | `1` (needed only if the same course and section run in two semesters) |
| Section | yes | `C` |
| Component | | `Lecture` / `Tutorial` / `Practical` |
| Batch | | `H` |
| Faculty Email | yes for the master timetable | `amit.sinhal@iet.edu` |

A slot is accepted only if an allocation gives that faculty member that course, section, component
and batch; anything else is listed as a row error. The sample covers two faculty members and is
illustrative — the times and rooms are not the department's real timetable.

## 3. Reports — `semester-report-sample.pdf` (summary) / `semester-report-full-sample.pdf`

Semester report → choose **Summary for meetings** or **Full detail** → Print or save as PDF (coordinator).
Built by `GET /api/reports/semester` to the structure in `system-generated-report.md`:

1. Scorecard — each measure against its target (enrolments, allocation, pacing, logging, CIE, remedial plans, course files)
2. Critical cases — numbered C-/W- ledger: not logging, overdue submissions, unassigned sections, sections behind
   plan, low assessment scores, assessments behind schedule, repeated cancellations
3. Allocation mismatches — every section/component whose allocated hours or owners differ from its L-T-P plan
4. Course performance by year — per course (summary) or per section (full): seats, syllabus, held/due, CIE, remedial
5. Faculty — load, students taught, logging rate and every course/section they teach across years
6. Continuous assessment progress (CIE-1..n) and the remedial plans filed
7. Data sources and sign-off

Per-faculty and per-course versions of the same data open from the dashboard (click a name or a course code),
and each faculty member sees their own under **My report**.

### How the sample PDFs were made — demo data

The PDFs come from a separate demo database, `iet_dashboard_demo`, built by `scripts/e2e_demo.py` through the
real HTTP API. The allocations are this semester's real load sheet (`allocations-odd-2026.csv`). Everything else is
**generated demo data** and is not true for the department: section strengths, the timetable
(`timetable-demo-generated.csv`), syllabus topics, ten weeks of class logs, assessment results, remedial plans and
submissions. The allocation mismatches in section 3 are real — they come from the load sheet.

Re-run:

```bash
docker exec iet-dashboard-postgres sh -c 'psql -U $POSTGRES_USER -d postgres -c "drop database if exists iet_dashboard_demo" -c "create database iet_dashboard_demo"'
cd backend && SPRING_DATASOURCE_URL=jdbc:postgresql://localhost:5433/iet_dashboard_demo mvn spring-boot:run   # terminal 1
python3 scripts/e2e_demo.py                                                                                      # terminal 2
```
