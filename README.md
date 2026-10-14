# Chalkline: Faculty Dashboard

Teaching load, syllabus pace, class logs and timetables for a department, in one place.
Spring Boot 3 / Java 21 + PostgreSQL backend, React + Vite frontend, Microsoft Entra sign-in.

- **Coordinators** upload the allocation sheet and the timetable, and track every faculty
  member's pacing, logging, assessments and reports.
- **Faculty** see today's classes, log what they taught, track their courses' syllabus pace, and
  can upload their own timetable (PDF from the portal, CSV or Excel).

---

## Quick start (your machine)

Needs Docker, Java 21, Maven and Node 20+.

```bash
docker compose up -d                  # PostgreSQL on :5433 + Mailpit (catches emails, UI on :8025)
bash scripts/dev-backend.sh            # backend on :8080, reloads itself on code changes
cd frontend && npm install && npm run dev   # app on http://localhost:5173
```

Open http://localhost:5173 and type an account email under **Development sign-in**. Dev sign-in and
"delete everything" exist only in dev mode; production never shows them.

Then load the sample data (in this order):

| Step | Where | File |
|---|---|---|
| 1. Semester dates | Overview → Semester settings | — |
| 2. Allocation sheet | Course Allocations → Import | `docs/samples/complete/allocations-complete.csv` |
| 3. Master timetable | Timetable → Import timetable | `docs/samples/complete/timetable-master-complete.csv` |
| 4. A personal timetable | Sign in as that faculty → Timetable → Upload my timetable | `docs/samples/complete/timetable-personal-mayank.csv` |

Locally the admin is `s.taruna@jklu.edu.in`: type it under Development sign-in, choose the admin
password, and import the sample sheet. Any other email from the sheet signs in without a password.

Tests: `cd backend && mvn test` · `cd frontend && npx vitest run && npx tsc --noEmit -p .`

---

## How it works

### Sign-in and roles
- Everyone signs in with their **Microsoft (@jklu.edu.in)** account. Only people who already
  have an account here get in. Accounts are created by the allocation sheet, so nobody
  self-registers. Others see "not registered".
- **Faculty** reach their own data only. Identity always comes from the signed-in session,
  never from a request parameter.
- **The admin** also enters the admin password after Microsoft sign-in.

### Admin, coordinators, faculty
1. The **admin** is set on the server (`APP_ADMIN_EMAIL`, e.g. `s.taruna@jklu.edu.in`). At their
   first Microsoft sign-in they choose the admin password (stored only as a BCrypt hash); every
   later sign-in asks for it.
2. The admin uploads the allocation sheet. Its **Role** column makes each person `Coordinator`
   or `Faculty`.
3. Everyone else signs in with Microsoft and lands on their dashboard, matched by email. No
   password is needed.

The admin and the person uploading are never demoted by a sheet.

### The allocation sheet (one row per faculty × course × section × component × batch)

`Faculty Name, Email, Designation, Department, Course Code, Course Name, L-T-P, Semester,
Section, Section Students, Component, Batch, Batch Students, Weekly Hours, PBL Hours, Role`

- **Role**: `Coordinator` or `Faculty` (blank keeps the person's current role).

- **Component**: Lecture, Tutorial or Practical (L/T/P and Lab also work).
- **Batch**: `ALL` (or blank) means the whole section. `C1`, `C2`, ... are lab batches.
- **Email** must be the person's real Microsoft email. A blank email is guessed from the name
  (`s.taruna@jklu.edu.in`), and the import warns you.
- Departments are created from the sheet. Re-importing updates rows instead of duplicating them.

### Timetables
- **Master (coordinator):** CSV/Excel with `Day, Start, End, Room, Course Code, Semester,
  Section, Component, Batch, Faculty Email`. It replaces the whole timetable.
- **Personal (any faculty member):** the timetable PDF from the portal (a week grid), or the same
  CSV without the faculty column. It replaces **only that person's** classes. A PDF cell without
  a course code is matched to their only fitting allocation.
- Every slot is matched to an allocation, and each person's **Today** screen lists that day's
  classes to log.

---

## Using the app

**Coordinator**
1. Set the semester dates (Overview → Semester settings).
2. Import the allocation sheet (Course Allocations → Import). Check the import result for warnings (guessed emails, unknown roles).
3. Import the master timetable, or let faculty upload their own.
4. Day to day: **Overview** (who is behind or silent), **Compliance Tracking** (pacing per section),
   **Semester report** (printable; faculty and course reports inside), **Deadlines** (milestones
   and reminders), **Submissions**.

**Faculty**
1. **Today's Classes**: log each class you taught (topics covered).
2. **My Courses**: syllabus pace per section, assessments (CIE) and remedial plans.
3. **Timetable**: your week; **Upload my timetable** if the department's isn't in yet.
4. **Deadlines**: upload what each deadline asks for; **My report** shows your semester summary.

---

## Deploying

Frontend on **Vercel**, backend + PostgreSQL on **Render**. Step by step, including the
Microsoft Entra registration, is in [DEPLOY.md → Hosting on Vercel + Render](DEPLOY.md).

Secrets live only in environment variables. Never commit `.env`.
