#!/usr/bin/env python3
"""
End-to-end exercise of the Faculty Dashboard on a DEMO database, driven only through the HTTP API.

Real input:   docs/samples/allocations-odd-2026.csv  (this semester's load sheet, 215 allocations)
Generated (clearly demo, never for the live database):
  * section strengths, a conflict-free weekly timetable, syllabus topic lists,
  * ten weeks of class logs written by each faculty member through POST /api/teaching-sessions,
  * continuous assessments, remedial plans and two deadlines with submissions.

Usage (backend must point at an empty demo DB):
  SPRING_DATASOURCE_URL=jdbc:postgresql://localhost:5433/iet_dashboard_demo mvn spring-boot:run
  python3 scripts/e2e_demo.py
Then check the dashboard, or GET /api/reports/semester.
"""
import csv
import datetime as dt
import hashlib
import http.cookiejar
import io
import json
import os
import random
import sys
import urllib.error
import urllib.request
from collections import defaultdict
from pathlib import Path

BASE = "http://localhost:8080"
ROOT = Path(__file__).resolve().parent.parent
# The Postgres database the running backend points at, for the timetable backdate step only.
DB_NAME = os.environ.get("DASHBOARD_DB", "iet_dashboard_demo")
LOAD_SHEET = ROOT / "docs" / "samples" / "allocations-odd-2026.csv"
TERM_START = dt.date(2026, 8, 3)   # a Monday
TERM_END = dt.date(2026, 12, 4)
TODAY = dt.date.today()
rng = random.Random(2026)


# --------------------------------------------------------------------------- HTTP helpers

class Client:
    def __init__(self, email):
        self.jar = http.cookiejar.CookieJar()
        self.opener = urllib.request.build_opener(urllib.request.HTTPCookieProcessor(self.jar))
        self.call("POST", "/api/auth/dev-login", {"email": email})

    def call(self, method, path, body=None, raw=None, content_type=None):
        data, headers = None, {}
        if body is not None:
            data, headers["Content-Type"] = json.dumps(body).encode(), "application/json"
        if raw is not None:
            data, headers["Content-Type"] = raw, content_type
        req = urllib.request.Request(BASE + path, data=data, method=method, headers=headers)
        try:
            with self.opener.open(req) as res:
                text = res.read().decode() or "null"
                return json.loads(text) if res.headers.get("Content-Type", "").startswith("application/json") else text
        except urllib.error.HTTPError as e:
            raise RuntimeError(f"{method} {path} -> {e.code}: {e.read().decode()[:300]}") from None

    def upload(self, path, filename, content, mime="text/csv", field="file"):
        boundary = "----e2e" + hashlib.md5(content).hexdigest()
        body = (f"--{boundary}\r\nContent-Disposition: form-data; name=\"{field}\"; filename=\"{filename}\"\r\n"
                f"Content-Type: {mime}\r\n\r\n").encode() + content + f"\r\n--{boundary}--\r\n".encode()
        return self.call("POST", path, raw=body, content_type=f"multipart/form-data; boundary={boundary}")


def step(msg):
    print(f"\n== {msg}", flush=True)


def week_of(d):
    return (d - TERM_START).days // 7 + 1


# --------------------------------------------------------------------------- 1. semester + allocations

coord = Client("coordinator@iet.edu")
step("Semester settings")
coord.call("PUT", "/api/terms/active", {"name": "Odd Semester 2026–27", "academicYear": "2026-2027",
                                         "startDate": str(TERM_START), "endDate": str(TERM_END),
                                         "requiredAssessments": 3})
print(f"term {TERM_START} → {TERM_END}, today {TODAY} = week {week_of(TODAY)}")

step("Import the real load sheet (with demo section strengths)")
rows = list(csv.DictReader(LOAD_SHEET.open()))
for r in rows:
    if not r["Section Students"]:
        seed = int(hashlib.md5((r["Course Code"] + r["Semester"] + r["Section"]).encode()).hexdigest(), 16)
        r["Section Students"] = str(120 + seed % 61 if r["Section"] == "ALL" else 55 + seed % 12)
buf = io.StringIO()
w = csv.DictWriter(buf, fieldnames=rows[0].keys())
w.writeheader()
w.writerows(rows)
result = coord.upload("/api/allocations/import", "allocations.csv", buf.getvalue().encode())
print({k: v for k, v in result.items() if k != "issues"}, "issues:", len(result["issues"]))
assert not [i for i in result["issues"] if i["severity"] == "ERROR"], result["issues"][:5]

assignments = coord.call("GET", "/api/teaching-assignments")
faculty = coord.call("GET", "/api/faculty")
emails = {}
users = {f["id"]: f for f in faculty}
for row in rows:
    emails[row["Faculty Name"].split(" (")[0].strip()] = row["Email"]
print(f"{len(assignments)} allocations, {len(faculty)} faculty")

# --------------------------------------------------------------------------- 2. timetable

step("Generate a conflict-free timetable and import it")
DAYS = ["MONDAY", "TUESDAY", "WEDNESDAY", "THURSDAY", "FRIDAY"]
HOURS = [9, 10, 11, 12, 14, 15, 16]
busy_faculty, busy_group, busy_room = set(), set(), set()
LECTURE_ROOMS = [f"LT-{i}" for i in range(1, 15)]
LABS = [f"Lab {i}" for i in range(1, 9)]
slots = []


def group_keys(a):
    off = a["offering"]
    base = f"{off['course']['code']}|{off['semester']}|{off['section']}"
    batch = a.get("subSection") or "ALL"
    return [base] if batch == "ALL" else [base + "|" + batch, base]


def place(a, length, component):
    for _ in range(400):
        day, start = rng.choice(DAYS), rng.choice(HOURS)
        hours = [start + i for i in range(length)]
        if any(h not in HOURS for h in hours) or (13 in range(start, start + length)):
            continue
        keys = group_keys(a)
        if any((a["facultyId"], day, h) in busy_faculty for h in hours):
            continue
        if any((k, day, h) in busy_group for k in keys for h in hours):
            continue
        pool = LABS if component == "PRACTICAL" else LECTURE_ROOMS
        room = next((r for r in pool if all((r, day, h) not in busy_room for h in hours)), None)
        if not room:
            continue
        for h in hours:
            busy_faculty.add((a["facultyId"], day, h))
            busy_room.add((room, day, h))
            for k in keys:
                busy_group.add((k, day, h))
        return day, start, room
    return None


unplaced = 0
for a in sorted(assignments, key=lambda x: (x["component"] != "PRACTICAL", x["id"])):
    comp = a["component"]
    hours = a["contactHours"] or 0
    blocks = [2] * (hours // 2) + [1] * (hours % 2) if comp == "PRACTICAL" else [1] * hours
    for length in blocks:
        placed = place(a, length, comp)
        if not placed:
            unplaced += 1
            continue
        day, start, room = placed
        off = a["offering"]
        slots.append({"Day": day[:3].title(), "Start": f"{start:02d}:00", "End": f"{start + length:02d}:00",
                      "Room": room, "Course Code": off["course"]["code"], "Semester": off["semester"],
                      "Section": off["section"], "Component": comp.title(),
                      "Batch": "" if (a.get("subSection") or "ALL") == "ALL" else a["subSection"],
                      "Faculty Email": emails.get(a["facultyName"].split(" (")[0].strip(), "")})
buf = io.StringIO()
w = csv.DictWriter(buf, fieldnames=slots[0].keys())
w.writeheader()
w.writerows(slots)
(ROOT / "docs" / "samples" / "timetable-demo-generated.csv").write_text(buf.getvalue())
tt = coord.upload("/api/timetable/import", "timetable.csv", buf.getvalue().encode())
print({k: v for k, v in tt.items() if k != "issues"}, "issues:", len(tt["issues"]), "unplaced blocks:", unplaced)
for i in tt["issues"][:5]:
    print("  ", i)

# --------------------------------------------------------------------------- 3. syllabi

step("Load a syllabus for every course")
courses = {}
for a in assignments:
    c = a["offering"]["course"]
    courses[c["id"]] = c
topics_by_course = {}
for cid, c in courses.items():
    lecture_like = (c.get("lectureHours") or 0) + (c.get("tutorialHours") or 0)
    n = 30 if lecture_like >= 3 else 20 if lecture_like > 0 else 12
    units, per_unit = [], n // 5
    for u in range(1, 6):
        units.append({"unitNumber": u, "unitTitle": f"Unit {u}",
                      "topics": [{"topicName": f"{c['code']} U{u}.{t}",
                                  "targetWeek": min(16, 1 + ((u - 1) * per_unit + t - 1) * 16 // n)}
                                 for t in range(1, per_unit + 1)]})
    coord.call("POST", f"/api/courses/{cid}/syllabus/confirm",
               {"sourceFileName": "demo-generated", "extractedCharacters": 0, "units": units})
    topics_by_course[cid] = coord.call("GET", f"/api/courses/{cid}/syllabus")
print(f"{len(courses)} courses, {sum(len(t) for t in topics_by_course.values())} topics")

# --------------------------------------------------------------------------- 4. class logs

step("Faculty log ten weeks of classes")
print("Backdating the demo timetable to the semester start (it stands in for an upload in week 1)")
# Done with SQL by the caller script; see main() note below.
print(json.dumps({"sql": "UPDATE timetable_slot SET created_at = '" + str(TERM_START) + "'"}))
input_ready = "--skip-backdate" in sys.argv
if not input_ready:
    import subprocess
    subprocess.run(["docker", "exec", "iet-dashboard-postgres", "sh", "-c",
                    f"psql -U $POSTGRES_USER -d {DB_NAME} -qc \"UPDATE timetable_slot SET created_at = '{TERM_START}'\""],
                   check=True)

fac_ids = sorted({a["facultyId"] for a in assignments})
profiles = {}
for fid in fac_ids:
    roll = rng.random()
    profiles[fid] = ("diligent" if roll < 0.70 else "slow" if roll < 0.85 else "stopped" if roll < 0.95 else "never")
slots_by_faculty = defaultdict(list)
my_slots = {}
covered = defaultdict(set)   # offeringId -> topic ids covered
logged = 0
for fid in fac_ids:
    profile = profiles[fid]
    if profile == "never":
        continue
    name = next(a["facultyName"] for a in assignments if a["facultyId"] == fid)
    email = emails.get(name.split(" (")[0].strip())
    if not email:
        continue
    fc = Client(email)
    timetable = fc.call("GET", "/api/academics/timetable")
    stop_after = TODAY - dt.timedelta(days=rng.randint(10, 16)) if profile == "stopped" else TODAY
    log_rate = 0.97 if profile == "diligent" else 0.86
    pace = rng.uniform(0.95, 1.12) if profile == "diligent" else rng.uniform(0.70, 0.82)
    d = TERM_START
    while d < TODAY and d < stop_after:
        for s in [s for s in timetable if s["dayOfWeek"] == d.strftime("%A").upper()]:
            if rng.random() > log_rate:
                continue
            off_id, comp = s["offeringId"], s["component"]
            course_id = next(a["offering"]["course"]["id"] for a in assignments if a["offering"]["id"] == off_id)
            topics = topics_by_course[course_id]
            body = {"offeringId": off_id, "sessionDate": str(d), "sessionType": comp,
                    "subSection": s["subSection"],
                    "lecturesTaken": 2 if comp == "PRACTICAL" else 1}
            if rng.random() < 0.04:
                body.update(status="CANCELLED", reasonNotHeld=rng.choice(
                    ["Institute event", "Faculty on leave", "Exam duty", "Room unavailable"]))
            else:
                body["status"] = "HELD"
                due = [t for t in topics if t["targetWeek"] <= week_of(d)]
                target = min(len(topics), round(len(due) * pace))
                new = [t["id"] for t in topics if t["id"] not in covered[off_id]][: max(0, target - len(covered[off_id]))]
                if comp == "PRACTICAL":
                    body["customTopicName"] = f"Lab experiment, week {week_of(d)}"
                else:
                    body["topicIds"] = new[:3]
                    covered[off_id].update(new[:3])
            fc.call("POST", "/api/teaching-sessions", body)
            logged += 1
        d += dt.timedelta(days=1)
print(f"{logged} classes logged; profiles: " +
      ", ".join(f"{p}={sum(1 for v in profiles.values() if v == p)}" for p in ["diligent", "slow", "stopped", "never"]))

# --------------------------------------------------------------------------- 5. assessments + remedial plans

step("Continuous assessments and remedial plans")
lecture_owner = {}
for a in assignments:
    if a["component"] in ("LECTURE", "ALL"):
        lecture_owner.setdefault(a["offering"]["id"], a)
recorded = filed = 0
for off_id, a in lecture_owner.items():
    profile = profiles[a["facultyId"]]
    email = emails.get(a["facultyName"].split(" (")[0].strip())
    if profile == "never" or not email:
        continue
    fc = Client(email)
    students = a["offering"]["studentCount"] or 60
    plan = [("CIE-1 Mid-term", "MID_TERM", 30, TERM_START + dt.timedelta(weeks=6, days=2))]
    if profile == "diligent" and rng.random() < 0.6:
        plan.append(("CIE-2 Quiz", "QUIZ", 10, TERM_START + dt.timedelta(weeks=8, days=3)))
    for name, kind, weight, held in plan:
        if rng.random() < 0.08:
            continue
        weak_share = rng.choice([0.04, 0.06, 0.08, 0.10, 0.12, 0.22]) if profile == "diligent" else rng.uniform(0.12, 0.28)
        below = round(students * weak_share)
        fc.call("POST", "/api/assessments", {"offeringId": off_id, "name": name, "kind": kind, "weightPct": weight,
                                             "heldOn": str(held), "studentsAssessed": students,
                                             "averagePct": rng.randint(52, 74), "below40Count": below})
        recorded += 1
    if profile == "slow" and rng.random() < 0.6:
        fc.call("POST", "/api/remedial-plans", {"offeringId": off_id, "reason": "BEHIND_SYLLABUS",
                                                "laggingTopics": "Units 2–3",
                                                "action": "Two extra tutorial hours on Saturdays until back on plan",
                                                "extraSessions": 4, "targetDate": str(TODAY + dt.timedelta(days=21))})
        filed += 1
print(f"{recorded} assessments, {filed} remedial plans")

# --------------------------------------------------------------------------- 6. deadlines + submissions

step("Deadlines and submissions")
qb = coord.call("POST", "/api/deadlines", {"title": "Mid-semester question bank", "type": "MID_SEM_QUESTION_BANK",
                                           "dueAt": f"{TERM_START + dt.timedelta(weeks=5)}T18:00:00Z",
                                           "targetRule": "ALL_FACULTY"})
cf = coord.call("POST", "/api/deadlines", {"title": "Course file (units 1–3)", "type": "COURSE_FILE",
                                           "dueAt": f"{TODAY + dt.timedelta(days=10)}T18:00:00Z",
                                           "targetRule": "ALL_FACULTY"})
pdf = b"%PDF-1.4\n1 0 obj<</Type/Catalog/Pages 2 0 R>>endobj 2 0 obj<</Type/Pages/Kids[]/Count 0>>endobj\ntrailer<</Root 1 0 R>>\n%%EOF\n"
submitted = 0
for fid in fac_ids:
    name = next(a["facultyName"] for a in assignments if a["facultyId"] == fid)
    email = emails.get(name.split(" (")[0].strip())
    if not email or profiles[fid] in ("never",) or rng.random() < 0.15:
        continue
    fc = Client(email)
    for da in fc.call("GET", "/api/my-deadlines"):
        if da["deadlineId"] == qb["id"] or (da["deadlineId"] == cf["id"] and rng.random() < 0.4):
            fc.upload(f"/api/deadline-assignments/{da['id']}/submissions", "submission.pdf", pdf, mime="application/pdf")
            submitted += 1
print(f"2 deadlines, {submitted} submissions")

step("Semester report")
report = coord.call("GET", "/api/reports/semester")
for line in report["scorecard"]:
    print(f"  {line['metric']:<24} {line['actual']:<22} {line['status']:<8} {line['note']}")
print(f"  critical cases: {sum(1 for c in report['criticalCases'] if c['severity'] == 'CRITICAL')}, "
      f"warnings: {sum(1 for c in report['criticalCases'] if c['severity'] == 'WARNING')}, "
      f"mismatches: {len(report['mismatches'])}")
print("  data sources:", report["dataSources"])
