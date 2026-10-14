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
docker compose up -d postgres          # PostgreSQL on localhost:5433
bash scripts/dev-backend.sh            # backend on :8080, reloads itself on code changes
cd frontend && npm install && npm run dev   # app on http://localhost:5173
```

Open http://localhost:5173 and use **Development sign-in → Coordinator**. Dev sign-in and
"delete everything" exist only in dev mode; production never shows them.

Then load the sample data (in this order):

| Step | Where | File |
|---|---|---|
| 1. Semester dates | Overview → Semester settings | — |
| 2. Allocation sheet | Allocations → Import | `docs/samples/complete/allocations-complete.csv` |
| 3. Master timetable | Timetable → Import timetable | `docs/samples/complete/timetable-master-complete.csv` |
| 4. A personal timetable | Sign in as that faculty → Timetable → Upload my timetable | `docs/samples/complete/timetable-personal-mayank.csv` |

The sample sheet makes Dr. S Taruna coordinator (Role column). In dev mode there is no mail
server, so her activation link is shown in the import result and printed in the backend log.

Tests: `cd backend && mvn test` · `cd frontend && npx vitest run && npx tsc --noEmit -p .`

---

## How it works

### Sign-in and roles
- Everyone signs in with their **Microsoft (@jklu.edu.in)** account. Only people who already
  have an account here get in. Accounts are created by the allocation sheet, so nobody
  self-registers. Others see "not registered".
- **Faculty** reach their own data only. Identity always comes from the signed-in session,
  never from a request parameter.
- **Coordinators** need two things: Microsoft sign-in and a **coordinator password**.

### Becoming coordinator
1. Someone is invited, either by `Role = Coordinator` in the allocation sheet or by
   `APP_COORDINATOR_EMAIL` on the server (only needed for the very first coordinator).
2. They get an email with a one-time link (valid 72 h).
3. They open it, sign in with Microsoft **as that email**, and choose a coordinator password.
   Only a BCrypt hash is stored.
4. Every later session asks for that password before coordinator tools open. After 5 wrong
   tries access locks for 15 minutes. "Forgot it?" emails a reset link to their own inbox.

The sheet only invites. It never removes anyone's coordinator access.

### The allocation sheet (one row per faculty × course × section × component × batch)

`Faculty Name, Email, Designation, Department, Course Code, Course Name, L-T-P, Semester,
Section, Section Students, Component, Batch, Batch Students, Weekly Hours, PBL Hours, Role`

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
2. Import the allocation sheet. Check the import result for warnings (guessed emails, unknown roles).
3. Import the master timetable, or let faculty upload their own.
4. Day to day: **Overview** (who is behind or silent), **Tracking** (pacing per section),
   **Reports** (faculty, course and the printable semester report), **Deadlines** (milestones
   and reminders).

**Faculty**
1. **Today**: log each class you taught (topics covered).
2. **My Courses**: syllabus pace per section, assessments (CIE) and remedial plans.
3. **Timetable**: your week; **Upload my timetable** if the department's isn't in yet.
4. **Submissions**: upload what deadlines ask for.

---

## Deploying

Frontend on **Vercel**, backend + PostgreSQL on **Render**. Step by step, including the
Microsoft Entra registration, is in [DEPLOY.md → Hosting on Vercel + Render](DEPLOY.md).

Secrets live only in environment variables. Never commit `.env`.
