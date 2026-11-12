#!/usr/bin/env python3
"""
Ten-faculty simulation of a semester, driven only through the HTTP API of a running backend.
Every step asserts what the app must do; the first wrong value stops the run with the reason.

What it covers:
  * a sheet with mistakes is refused row by row and saves nothing;
  * the corrected sheet imports, and every stored value equals the sheet's, letter for letter;
  * a person with no course, a visiting faculty note, skills, PBL, roles;
  * LTP load per section, everyone listed (zero load too);
  * a re-import that moves a lab batch removes the old allocation;
  * a real timetable PDF (2-hour labs become one class each) and faculty CSV timetables,
    with a wrong batch rejected and both sides named; the PDF shows up as the faculty's file;
  * class logging: future, not-yet-started, late without reason, LAB stored as PRACTICAL,
    a duplicate lecture number, someone else's class, and the not-logged list shrinking;
  * an unregistered email can't sign in.

Run against an EMPTY database (it creates people and courses), with dev sign-in and CSRF off:
  SPRING_PROFILES_ACTIVE=dev PORT=8090 SPRING_DATASOURCE_URL=jdbc:postgresql://localhost:5433/iet_sim \\
  APP_ADMIN_EMAIL=sim.coordinator@jklu.edu.in APP_SECURITY_CSRF=false mvn -o spring-boot:run
  python3 scripts/simulate_faculty.py [http://localhost:8090]
"""
import datetime as dt
import http.cookiejar
import json
import sys
import urllib.error
import urllib.request
import uuid
from pathlib import Path

BASE = sys.argv[1] if len(sys.argv) > 1 else "http://localhost:8090"
ROOT = Path(__file__).resolve().parent.parent
TODAY = dt.date.today()
TERM_START = TODAY - dt.timedelta(days=TODAY.weekday() + 14)   # the Monday two weeks back
DOMAIN = "jklu.edu.in"
checks = 0


def ok(condition, message):
    global checks
    if not condition:
        sys.exit(f"FAIL: {message}")
    checks += 1


class Client:
    def __init__(self, email):
        self.email = email
        self.opener = urllib.request.build_opener(urllib.request.HTTPCookieProcessor(http.cookiejar.CookieJar()))
        self.me = self.call("POST", "/api/auth/dev-login", {"email": email})

    def call(self, method, path, body=None, raw=None, content_type=None, expect=None):
        data, headers = None, {"Accept": "application/json"}
        if body is not None:
            data, headers["Content-Type"] = json.dumps(body).encode(), "application/json"
        if raw is not None:
            data, headers["Content-Type"] = raw, content_type
        req = urllib.request.Request(BASE + path, data=data, method=method, headers=headers)
        try:
            with self.opener.open(req) as res:
                status, text = res.status, res.read().decode()
        except urllib.error.HTTPError as e:
            status, text = e.code, e.read().decode()
        if expect is not None:
            ok(status == expect, f"{method} {path} as {self.email}: expected {expect}, got {status}: {text[:300]}")
        elif status >= 400:
            sys.exit(f"FAIL: {method} {path} as {self.email} -> {status}: {text[:400]}")
        try:
            return json.loads(text) if text else None
        except json.JSONDecodeError:
            return text

    def upload(self, path, filename, content, mime="text/csv", fields=None):
        boundary = uuid.uuid4().hex
        parts = b""
        for name, value in (fields or {}).items():
            parts += f"--{boundary}\r\nContent-Disposition: form-data; name=\"{name}\"\r\n\r\n{value}\r\n".encode()
        parts += (f"--{boundary}\r\nContent-Disposition: form-data; name=\"file\"; filename=\"{filename}\"\r\n"
                  f"Content-Type: {mime}\r\n\r\n").encode() + content + f"\r\n--{boundary}--\r\n".encode()
        return self.call("POST", path, raw=parts, content_type=f"multipart/form-data; boundary={boundary}")


def step(msg):
    print(f"== {msg}", flush=True)


def last(weekday, before=TODAY, min_days_ago=1):
    """The most recent date with this weekday (0 = Monday) at least min_days_ago before `before`."""
    d = before - dt.timedelta(days=min_days_ago)
    while d.weekday() != weekday:
        d -= dt.timedelta(days=1)
    return d


# ----------------------------------------------------------------------------------- the sheet

HEADER = ("Faculty Name,Email,Designation,Department,Role,Skills,PBL Hours,Course Code,Course Name,L-T-P,"
          "Semester,Section,Section Students,Component,Batch,Batch Students")
ROWS = [
    # 1. coordinator who teaches nothing
    f"Dr. Sim Coordinator,sim.coordinator@{DOMAIN},Professor,CSE,Coordinator,Academic Planning,,,,,,,,,,",
    # 2. lectures in two sections + two lab batches (her real timetable PDF is uploaded below)
    f"Dr. S Taruna,taruna.sim@{DOMAIN},Professor,CSE,Faculty,Databases; Data Mining,2,CS1133,Database Management Systems,3-0-2,3,A,60,Lecture,,",
    f"Dr. S Taruna,taruna.sim@{DOMAIN},,,,,,CS1133,Database Management Systems,3-0-2,3,B,58,Lecture,,",
    f"Dr. S Taruna,taruna.sim@{DOMAIN},,,,,,CS1133,Database Management Systems,3-0-2,3,A,60,Practical,A1,30",
    f"Dr. S Taruna,taruna.sim@{DOMAIN},,,,,,CS1133,Database Management Systems,3-0-2,3,A,60,Practical,A2,30",
    # 3. lecture only
    f"Dr. Amit Sinhal,amit.sim@{DOMAIN},Associate Professor,CSE,Faculty,,1,CS1139,Programming-I,3-0-2,1,C,64,Lecture,,",
    # 4. lecture + tutorial + practical of one section
    f"Dr. Priya Nair,priya.sim@{DOMAIN},Assistant Professor,CSE,Faculty,Networks,,CS2201,Computer Networks,3-1-2,5,A,45,Lecture,,",
    f"Dr. Priya Nair,priya.sim@{DOMAIN},,,,,,CS2201,Computer Networks,3-1-2,5,A,45,Tutorial,,",
    f"Dr. Priya Nair,priya.sim@{DOMAIN},,,,,,CS2201,Computer Networks,3-1-2,5,A,45,Practical,A1,23",
    # 5 + 6. the two lab batches of Amit's section, one teacher each
    f"Dr. Rohan Mehta,rohan.sim@{DOMAIN},Assistant Professor,CSE,Faculty,,,CS1139,Programming-I,3-0-2,1,C,64,Practical,C1,32",
    f"Dr. Meera Iyer,meera.sim@{DOMAIN},Assistant Professor,CSE,Faculty,,,CS1139,Programming-I,3-0-2,1,C,64,Practical,C2,32",
    # 7. joined this semester, no course yet
    f"Dr. New Joiner,new.joiner.sim@{DOMAIN},Assistant Professor,CSE,Faculty,Machine Learning; NLP,,,,,,,,,,",
    # 8. two sections of lectures and a tutorial batch
    f"Dr. Kiran Rao,kiran.sim@{DOMAIN},Professor,AS,Faculty,Calculus,,MA1101,Calculus,3-1-0,1,A,70,Lecture,,",
    f"Dr. Kiran Rao,kiran.sim@{DOMAIN},,,,,,MA1101,Calculus,3-1-0,1,B,66,Lecture,,",
    f"Dr. Kiran Rao,kiran.sim@{DOMAIN},,,,,,MA1101,Calculus,3-1-0,1,A,70,Tutorial,A1,35",
    # 9. visiting faculty, a whole-section practical
    f"Dr. Vikram Shah (Visiting Faculty),vikram.sim@{DOMAIN},,ECE,Faculty,,,EC1105,Digital Electronics,2-0-2,3,D,50,Lecture,,",
    f"Dr. Vikram Shah (Visiting Faculty),vikram.sim@{DOMAIN},,ECE,Faculty,,,EC1105,Digital Electronics,2-0-2,3,D,50,Practical,D,50",
    # 10. the other tutorial batch of Kiran's section
    f"Mr. Arjun Das,arjun.sim@{DOMAIN},Lecturer,AS,Faculty,,,MA1101,Calculus,3-1-0,1,A,70,Tutorial,A2,35",
]
SHEET = HEADER + "\n" + "\n".join(ROWS) + "\n"

BAD_ROWS = ROWS + [
    f"Dr. Typo Students,typo.sim@{DOMAIN},,CSE,Faculty,,,CS1139,Programming-I,3-0-2,1,C,6O,Lecture,,",       # 6O
    f"Dr. Guessed Email,,,CSE,Faculty,,,CS1139,Programming-I,3-0-2,1,D,64,Lecture,,",                        # no email
    f"Dr. Wrong Name,wrong.sim@{DOMAIN},,CSE,Faculty,,,CS1139,Programming I,3-0-2,1,E,64,Lecture,,",        # course name
    f"Dr. Too Many,many.sim@{DOMAIN},,CSE,Faculty,,,CS1139,Programming-I,3-0-2,1,C,64,Practical,C3,10",     # 32+32+10 > 64
]
BAD_SHEET = HEADER + "\n" + "\n".join(BAD_ROWS) + "\n"


def expected_allocations(rows):
    """(faculty, code, course, ltp, sem, section, section students, component, batch, batch students, hours)."""
    out = set()
    for row in rows:
        c = row.split(",")
        name, code = c[0].split(" (")[0], c[7]
        if not code:
            continue
        l, t, p = (int(x) for x in c[9].split("-"))
        component = c[13].upper()
        batch = c[14].upper() if c[14] and c[14].upper() != c[11].upper() else "ALL"
        hours = {"LECTURE": l, "TUTORIAL": t, "PRACTICAL": p}[component]
        out.add((name, code, c[8], c[9], int(c[10]), c[11], int(c[12]), component, batch,
                 int(c[15]) if batch != "ALL" else int(c[12]), hours))
    return out


# ----------------------------------------------------------------------------------- run

step(f"Sign in as the coordinator on {BASE}")
coord = Client(f"sim.coordinator@{DOMAIN}")
# The sim coordinator is the admin of this fresh database: the first sign-in chooses the admin password.
coord.call("POST", "/api/auth/admin/setup", {"password": "Sim-Admin-Password-2026!"})
term = coord.call("GET", "/api/terms/active")
coord.call("PUT", "/api/terms/active", {"name": term["name"] if term else "Odd Semester", "academicYear": "2026-2027",
                                         "startDate": TERM_START.isoformat(), "endDate": (TODAY + dt.timedelta(days=60)).isoformat()})

step("A sheet with mistakes is refused, every mistake named, nothing saved")
before = len(coord.call("GET", "/api/faculty"))
bad = coord.upload("/api/allocations/import", "bad.csv", BAD_SHEET.encode())
ok(bad["imported"] is False, "bad sheet must not import")
messages = " | ".join(i["message"] for i in bad["issues"])
for needle in ['"6O" must be a whole number', "Email is empty", 'name of course CS1139 is "Programming I"', "add up to 74"]:
    ok(needle in messages, f"refusal should mention {needle!r}; got: {messages[:600]}")
ok(len(coord.call("GET", "/api/faculty")) == before, "a refused sheet saves no people")

step("The corrected sheet imports cleanly")
res = coord.upload("/api/allocations/import", "allocations.csv", SHEET.encode())
ok(res["imported"] and not [i for i in res["issues"] if i["severity"] != "INFO"], f"clean import, got {res['issues']}")
ok(res["assignmentsCreated"] == len(expected_allocations(ROWS)), f"one allocation per course row, got {res}")

step("Every stored value equals the sheet's")
stored = set()
for a in coord.call("GET", "/api/teaching-assignments"):
    o, c = a["offering"], a["offering"]["course"]
    stored.add((a["facultyName"], c["code"], c["name"], c["ltp"], o["semester"], o["section"], o["studentCount"],
                a["component"], a["subSection"], a["studentCount"], a["contactHours"]))
expected = expected_allocations(ROWS)
ok(stored == expected, f"stored != sheet\n  missing: {expected - stored}\n  extra:   {stored - expected}")

people = {f["email"]: f for f in coord.call("GET", "/api/faculty")}
ok(len([e for e in people if e.endswith(".sim@" + DOMAIN) or e.startswith("sim.")]) == 10, "ten faculty")
ok(people[f"vikram.sim@{DOMAIN}"]["name"] == "Dr. Vikram Shah", "visiting note is not part of the name")
ok(people[f"vikram.sim@{DOMAIN}"]["designation"] == "Visiting Faculty", "visiting note becomes the designation")
ok(sorted(s["name"] for s in people[f"new.joiner.sim@{DOMAIN}"]["skills"]) == ["Machine Learning", "NLP"], "skills")
ok(people[f"taruna.sim@{DOMAIN}"]["pblHours"] == 2 and people[f"amit.sim@{DOMAIN}"]["pblHours"] == 1, "PBL hours")
ok(people[f"rohan.sim@{DOMAIN}"]["pblHours"] == 0, "no PBL given, none invented")
ok(people[f"arjun.sim@{DOMAIN}"]["designation"] == "Lecturer", "designation exactly as written")

step("LTP load is per section, and everyone is listed")
load = {l["facultyName"]: l for l in coord.call("GET", "/api/teaching-assignments/faculty-load")}
ok(load["Dr. New Joiner"]["totalHours"] == 0, "a faculty member with no course is listed with 0 hours")
t = load["Dr. S Taruna"]
ok((t["lectureHours"], t["practicalHours"], t["pblHours"], t["totalHours"]) == (6, 4, 2, 12),
   f"Taruna: 3+3 lecture, 2+2 practical, 2 PBL = 12, got {t}")
ok(len(t["lines"]) == 4 and {(l["section"], l["batch"], l["hours"]) for l in t["lines"]}
   == {("A", "ALL", 3), ("B", "ALL", 3), ("A", "A1", 2), ("A", "A2", 2)}, f"section lines {t['lines']}")
p = load["Dr. Priya Nair"]
ok((p["lectureHours"], p["tutorialHours"], p["practicalHours"]) == (3, 1, 2), f"Priya L-T-P 3-1-2, got {p}")

step("Re-importing with Meera's batch moved from C2 to C3 removes the C2 allocation")
moved = SHEET.replace("Practical,C2,32", "Practical,C3,32")
res = coord.upload("/api/allocations/import", "allocations.csv", moved.encode())
ok(res["imported"] and res["assignmentsRemoved"] == 1 and res["assignmentsCreated"] == 1, f"moved batch, got {res}")
batches = {a["subSection"] for a in coord.call("GET", "/api/teaching-assignments") if a["facultyName"] == "Dr. Meera Iyer"}
ok(batches == {"C3"}, f"Meera teaches only C3 now, got {batches}")
coord.upload("/api/allocations/import", "allocations.csv", SHEET.encode())   # back to the real sheet

step("Taruna uploads her real timetable PDF")
taruna = Client(f"taruna.sim@{DOMAIN}")
pdf = (ROOT / "Dr_S_Taruna_Timetable_3rd_Sem.pdf").read_bytes()
tt = taruna.upload("/api/timetable/import", "Dr_S_Taruna_Timetable_3rd_Sem.pdf", pdf, mime="application/pdf")
ok(tt["slotsCreated"] == 8 and not tt["issues"], f"6 lectures + 2 two-hour labs, got {tt}")
slots = taruna.call("GET", "/api/academics/timetable")
labs = [s for s in slots if s["component"] == "PRACTICAL"]
ok(sorted((s["dayOfWeek"], s["subSection"], s["startTime"][:5], s["endTime"][:5]) for s in labs)
   == [("MONDAY", "A1", "15:00", "16:50"), ("WEDNESDAY", "A2", "09:00", "10:50")], f"labs {labs}")
files = taruna.call("GET", "/api/academic-documents?category=MASTER_TIMETABLE")
ok(any(f["uploadedByFacultyId"] == taruna.me["id"] for f in files), "her PDF is kept as her timetable file")

step("Other faculty upload CSV timetables; a wrong batch is rejected with both sides named")
TT_HEADER = "Day,Start,End,Room,Course Code,Semester,Section,Component,Batch\n"
timetables = {
    "amit": "Mon,09:00,09:50,LT-1,CS1139,1,C,Lecture,\nTue,09:00,09:50,LT-1,CS1139,1,C,Lecture,\nThu,09:00,09:50,LT-1,CS1139,1,C,Lecture,\n",
    "priya": "Mon,10:00,10:50,EB-101,CS2201,5,A,Lecture,\nTue,11:00,11:50,EB-101,CS2201,5,A,Tutorial,\nWed,14:00,15:50,Lab-2,CS2201,5,A,Practical,A1\n",
    "rohan": "Wed,14:00,15:50,Lab-3,CS1139,1,C,Practical,C1\nFri,14:00,15:50,Lab-3,CS1139,1,C,Practical,C3\n",
    "meera": "Wed,14:00,15:50,Lab-4,CS1139,1,C,Practical,C2\n",
    "kiran": "Mon,11:00,11:50,LT-2,MA1101,1,A,Lecture,\nTue,11:00,11:50,LT-3,MA1101,1,B,Lecture,\nFri,14:00,14:50,T-1,MA1101,1,A,Tutorial,A1\n",
    "vikram": "Mon,12:00,12:50,EB-201,EC1105,3,D,Lecture,\nThu,14:00,15:50,Lab-5,EC1105,3,D,Practical,\n",
    "arjun": "Thu,10:00,10:50,T-2,MA1101,1,A,Tutorial,A2\n",
}
clients = {"taruna": taruna}
for who, rows in timetables.items():
    clients[who] = Client(f"{who}.sim@{DOMAIN}")
    res = clients[who].upload("/api/timetable/import", f"{who}.csv", (TT_HEADER + rows).encode())
    if who == "rohan":
        ok(res["slotsCreated"] == 1 and len(res["issues"]) == 1, f"rohan: one good, one rejected, got {res}")
        msg = res["issues"][0]["message"]
        ok("batch C3" in msg and "practical batch C1" in msg, f"rejection names both batches: {msg}")
    else:
        ok(not res["issues"], f"{who}'s timetable imports cleanly, got {res['issues']}")
ok(len(clients["kiran"].call("GET", "/api/academics/timetable")) == 3, "Kiran sees exactly his three classes")
ok(clients["vikram"].call("GET", "/api/academics/timetable")[1]["subSection"] == "ALL", "whole-section practical")

step("Class logging rules")
mon = last(0, min_days_ago=3)   # a Monday at least 3 days ago: late
classes = taruna.call("GET", f"/api/teaching-sessions/today?date={mon}")
lab = next(c for c in classes if c["component"] == "PRACTICAL")
base = {"offeringId": lab["offeringId"], "sessionType": "LAB", "subSection": "A1", "status": "HELD",
        "topicIds": [], "customTopicName": "ER diagrams lab", "lecturesTaken": 2}
taruna.call("POST", "/api/teaching-sessions", {**base, "sessionDate": (TODAY + dt.timedelta(days=1)).isoformat()}, expect=400)
taruna.call("POST", "/api/teaching-sessions", {**base, "sessionDate": mon.isoformat()}, expect=400)   # late, no reason
saved = taruna.call("POST", "/api/teaching-sessions", {**base, "sessionDate": mon.isoformat(), "lectureNumber": 1,
                                                      "delayReason": "Network outage on campus"}, expect=200)
ok(saved["sessionType"] == "PRACTICAL" and saved["lectureNumber"] == 1, f"LAB stored as PRACTICAL #1, got {saved}")
again = dict(base, sessionDate=mon.isoformat(), lectureNumber=1, delayReason="dup")
taruna.call("POST", "/api/teaching-sessions", again, expect=400)                                    # duplicate number
amit_offering = clients["amit"].call("GET", "/api/academics/timetable")[0]["offeringId"]
taruna.call("POST", "/api/teaching-sessions", {**base, "offeringId": amit_offering, "sessionType": "LECTURE",
                                              "subSection": "ALL", "sessionDate": mon.isoformat(), "delayReason": "x"}, expect=403)

step("The not-logged list shrinks as classes are logged")
pending = taruna.call("GET", "/api/teaching-sessions/pending")
ok(len(pending) > 0, "past timetabled classes with no log are listed")
ok(not any(p["date"] == mon.isoformat() and p["subSection"] == "A1" for p in pending), "the logged lab is not pending")
first = pending[0]
day = dt.date.fromisoformat(first["date"])
body = {"offeringId": first["offeringId"], "sessionType": first["component"], "subSection": first["subSection"],
        "status": "CANCELLED", "reasonNotHeld": "Institutional Holiday / Event", "sessionDate": first["date"],
        "topicIds": []}
if day < TODAY - dt.timedelta(days=2):
    body["delayReason"] = "Back-logging the holiday"
taruna.call("POST", "/api/teaching-sessions", body, expect=200)
ok(len(taruna.call("GET", "/api/teaching-sessions/pending")) == len(pending) - 1, "skipping a class removes it")

history = taruna.call("GET", f"/api/teaching-sessions/offering/{lab['offeringId']}")
ok(any(h["customTopicName"] == "ER diagrams lab" and h["delayReason"] == "Network outage on campus" for h in history),
   "classes taken show the topic and the late reason")

step("Lecture, tutorial and practical of one section are logged separately")
priya = clients["priya"]
for weekday, component, batch in [(0, "LECTURE", "ALL"), (1, "TUTORIAL", "ALL"), (2, "PRACTICAL", "A1")]:
    day = last(weekday)
    cls = next(c for c in priya.call("GET", f"/api/teaching-sessions/today?date={day}") if c["component"] == component)
    ok(cls["subSection"] == batch and not cls["isLogged"], f"{component} on {day} starts unlogged: {cls}")
    log = {"offeringId": cls["offeringId"], "sessionDate": day.isoformat(), "sessionType": component, "subSection": batch,
           "status": "HELD", "topicIds": [], "customTopicName": f"{component.title()} topic", "lectureNumber": cls["nextLectureNumber"]}
    if day < TODAY - dt.timedelta(days=2):
        log["delayReason"] = "Simulated back-log"
    priya.call("POST", "/api/teaching-sessions", log, expect=200)
    after = priya.call("GET", f"/api/teaching-sessions/today?date={day}")
    ok([c["isLogged"] for c in after if c["component"] == component] == [True], f"{component} now logged")
    ok(all(not c["isLogged"] for c in after if c["component"] != component), f"logging the {component} logs nothing else")
sessions = priya.call("GET", f"/api/teaching-sessions/offering/{cls['offeringId']}")
ok(sorted(s["sessionType"] for s in sessions) == ["LECTURE", "PRACTICAL", "TUTORIAL"], f"one of each, got {sessions}")

step("Everyone else")
joiner = Client(f"new.joiner.sim@{DOMAIN}")
ok(joiner.call("GET", "/api/teaching-sessions/today") == [], "no course, no classes")
ok(joiner.call("GET", "/api/teaching-sessions/pending") == [], "no course, nothing pending")
try:
    Client(f"not.in.sheet@{DOMAIN}")
    ok(False, "an email not in the sheet must not sign in")
except SystemExit as e:
    ok("404" in str(e), f"unregistered sign-in is refused: {e}")
docs = coord.call("GET", f"/api/academic-documents?facultyId={people[f'taruna.sim@{DOMAIN}']['id']}")
ok(any(d["category"] == "MASTER_TIMETABLE" for d in docs), "Taruna's file list shows her timetable")

step("A syllabus upload is kept as a file and shows in the faculty member's files")
assignments = coord.call("GET", "/api/teaching-assignments")
course_of = {a["facultyName"]: a["offering"]["course"]["id"] for a in assignments}
SYLLABUS = (b"CS1133 Database Management Systems\n\nUNIT I - Introduction\n1.1 Database system concepts\n"
            b"1.2 Data models\n\nUNIT II: Relational Model\n2.1 Relational algebra\n2.2 SQL basics\n")


def upload_syllabus(client, course_id, code):
    """What SyllabusImportModal does: preview, confirm the topics, then keep the file with the course."""
    parsed = client.upload(f"/api/courses/{course_id}/syllabus/preview", f"{code}.txt", SYLLABUS, mime="text/plain")
    client.call("POST", f"/api/courses/{course_id}/syllabus/confirm", parsed)
    client.upload("/api/academic-documents/upload", f"{code}.txt", SYLLABUS, mime="text/plain",
                  fields={"category": "COURSE_SYLLABUS", "courseId": course_id, "title": f"{code} syllabus"})


upload_syllabus(taruna, course_of["Dr. S Taruna"], "CS1133")
ok(len(taruna.call("GET", f"/api/courses/{course_of['Dr. S Taruna']}/syllabus")) == 4, "four topics saved, as written")
mine = taruna.call("GET", f"/api/academic-documents?courseId={course_of['Dr. S Taruna']}")
ok(any(d["category"] == "COURSE_SYLLABUS" for d in mine), "the syllabus is in the course's files (My Courses)")
for who in ["taruna", "kiran"]:
    if who == "kiran":   # a coordinator uploading a course's syllabus: it is the teacher's file too
        upload_syllabus(coord, course_of["Dr. Kiran Rao"], "MA1101")
    fid = people[f"{who}.sim@{DOMAIN}"]["id"]
    files = coord.call("GET", f"/api/academic-documents?facultyId={fid}")
    ok(any(d["category"] == "COURSE_SYLLABUS" for d in files), f"{who}'s file list (faculty profile) shows the syllabus")
other = coord.call("GET", f"/api/academic-documents?facultyId={people[f'amit.sim@{DOMAIN}']['id']}")
ok(not any(d["category"] == "COURSE_SYLLABUS" for d in other), "someone else's syllabus is not in Amit's files")

step("Every section, component and batch someone teaches has its class in the timetable")


def everyones_slots():
    """The coordinator's view of each teaching faculty member's timetable, all together."""
    ids = {a["facultyId"] for a in coord.call("GET", "/api/teaching-assignments")}
    return [dict(s, facultyId=fid) for fid in sorted(ids)
            for s in coord.call("GET", f"/api/academics/timetable?facultyId={fid}")]


slot_keys = {(s["facultyId"], s["offeringId"], s["component"], s["subSection"]) for s in everyones_slots()}
for a in assignments:
    key = (a["facultyId"], a["offering"]["id"], a["component"], a["subSection"])
    ok(key in slot_keys, f"{a['facultyName']} {a['offering']['course']['code']} Sec {a['offering']['section']} "
                         f"{a['component']} {a['subSection']}: no timetable slot")
for who, client in clients.items():
    for weekday in range(5):
        for c in client.call("GET", f"/api/teaching-sessions/today?date={last(weekday)}"):
            ok(c["scheduled"], f"{who} on weekday {weekday}: {c['courseCode']} shown as 'timetable not uploaded'")

step("A coordinator uploading timetables one faculty at a time keeps everyone else's")
before = {(s["facultyId"], s["dayOfWeek"], s["startTime"]) for s in everyones_slots()}
ok(len({f for f, _, _ in before}) == 8, f"all eight teaching faculty have a timetable, got {len({f for f, _, _ in before})}")
MASTER = "Day,Start,End,Room,Course Code,Semester,Section,Component,Batch,Faculty Email\n"
for who in ["amit", "kiran"]:
    rows = "".join(f"{r},{who}.sim@{DOMAIN}\n" for r in timetables[who].strip().split("\n"))
    res = coord.upload("/api/timetable/import", f"{who}-by-coordinator.csv", (MASTER + rows).encode())
    ok(not res["issues"] and res["facultyCovered"] == 1, f"{who}'s sheet by the coordinator, got {res}")
after = {(s["facultyId"], s["dayOfWeek"], s["startTime"]) for s in everyones_slots()}
ok(after == before, f"same timetable for everyone\n  lost: {before - after}\n  new:  {after - before}")

step("The real course description: read exactly as written, checked against the sheet, history kept on re-upload")
DOCX = (ROOT / "backend/src/test/resources/syllabus/cloud-ml-course-description.docx").read_bytes()
DOCX_MIME = "application/vnd.openxmlformats-officedocument.wordprocessingml.document"
priya_course = course_of["Dr. Priya Nair"]
preview = priya.upload(f"/api/courses/{priya_course}/syllabus/preview", "Cloud ML Course Description.docx", DOCX, mime=DOCX_MIME)
ok([len(u["topics"]) for u in preview["units"]] == [6, 9, 7, 9, 12, 12], f"55 topics in 6 units, got {[len(u['topics']) for u in preview['units']]}")
ok(preview["units"][0]["topics"][0]["topicName"] == "AI, ML, Deep Learning, and Generative AI", "first topic letter for letter")
ok(all(t["targetWeek"] == 0 for u in preview["units"] for t in u["topics"]), "no invented weeks")
mm = " | ".join(preview["mismatches"])
ok(len(preview["mismatches"]) == 2 and "3-0-2" in mm and "3-1-2" in mm and '"Computer Networks"' in mm,
   f"both differences from the sheet are named: {mm}")
priya.call("POST", f"/api/courses/{priya_course}/syllabus/confirm", preview)
saved = priya.call("GET", f"/api/courses/{priya_course}/syllabus")
ok(len(saved) == 55, f"55 topics saved, got {len(saved)}")
first_topic = saved[0]
day = last(0, min_days_ago=8)
cls = next(c for c in priya.call("GET", f"/api/teaching-sessions/today?date={day}") if c["component"] == "LECTURE")
priya.call("POST", "/api/teaching-sessions", {"offeringId": cls["offeringId"], "sessionDate": day.isoformat(),
           "sessionType": "LECTURE", "subSection": "ALL", "status": "HELD", "topicIds": [first_topic["id"]],
           "lectureNumber": cls["nextLectureNumber"], "delayReason": "Simulated back-log"}, expect=200)


def covered_now():
    mine = [c for c in priya.call("GET", "/api/academics/my-courses") if c["courseId"] == priya_course]
    return mine[0]


ok(covered_now()["coveredTopics"] == 1, "the logged class covers one topic")
priya.call("POST", f"/api/courses/{priya_course}/syllabus/confirm", preview)          # same document again
ok(priya.call("GET", f"/api/courses/{priya_course}/syllabus")[0]["id"] == first_topic["id"], "same topic, same id")
ok(covered_now()["coveredTopics"] == 1, "re-uploading the syllabus keeps what the logged classes covered")
dropped = dict(preview, units=preview["units"][1:])
refused = priya.call("POST", f"/api/courses/{priya_course}/syllabus/confirm", dropped, expect=400)
ok("AI, ML, Deep Learning, and Generative AI" in refused.get("detail", ""), f"dropping a taught topic is refused by name: {refused}")
ok(covered_now()["coveredTopics"] == 1, "a refused upload changes nothing")
pace = covered_now()
ok(pace["pacingStatus"] in ("AHEAD", "ON_TRACK", "BEHIND") and "timetabled lectures are past" in pace["pacingLabel"],
   f"pace is measured against the timetable: {pace['pacingStatus']} {pace['pacingLabel']}")
no_syll = [c for c in clients["vikram"].call("GET", "/api/academics/my-courses")][0]
ok(no_syll["pacingStatus"] == "NO_SYLLABUS", f"no syllabus is not 'on track': {no_syll['pacingStatus']}")

step("A faculty member can't read or change a colleague's data")
amit = clients["amit"]
T = people[f"taruna.sim@{DOMAIN}"]["id"]
t_offer = next(a["offering"]["id"] for a in assignments if a["facultyName"] == "Dr. S Taruna")
t_slot = taruna.call("GET", "/api/academics/timetable")[0]["id"]
for method, path, body, why in [
    ("PUT", f"/api/faculty/{T}/skills-by-name", {"skills": ["Hacked"]}, "change her skills"),
    ("GET", f"/api/faculty/{T}/profile", None, "read her profile"),
    ("GET", f"/api/faculty/{T}/teaching-assignments", None, "read her allocations"),
    ("GET", "/api/teaching-assignments", None, "read everyone's allocations"),
    ("GET", "/api/teaching-assignments/faculty-load", None, "read everyone's load"),
    ("GET", f"/api/teaching-sessions/offering/{t_offer}", None, "read her class history"),
    ("POST", f"/api/courses/{course_of['Dr. S Taruna']}/syllabus/confirm", preview, "replace her syllabus"),
    ("DELETE", f"/api/academics/timetable/{t_slot}", None, "delete her class"),
    ("GET", "/api/tracking/overview-summary", None, "read the coordinator overview"),
]:
    amit.call(method, path, body, expect=403)
ok(sorted(x["name"] for x in coord.call("GET", f"/api/faculty/{T}")["skills"]) == ["Data Mining", "Databases"],
   "Taruna's skills are untouched")
mine = amit.call("GET", f"/api/academics/timetable?facultyId={T}")
ok(all(x["offeringId"] != t_offer for x in mine), "asking for her timetable returns his own")

step("A holiday's classes are not owed")
term_now = coord.call("GET", "/api/terms/active")
pending_before = clients["kiran"].call("GET", "/api/teaching-sessions/pending")
holiday = pending_before[0]["date"]
on_that_day = [p for p in pending_before if p["date"] == holiday]
coord.call("PUT", "/api/terms/active", {"name": term_now["name"], "academicYear": term_now["academicYear"],
           "startDate": term_now["startDate"], "endDate": term_now["endDate"],
           "holidays": [{"date": holiday, "name": "Sim Holiday"}]})
ok(coord.call("GET", "/api/terms/active")["holidays"] == [{"date": holiday, "name": "Sim Holiday"}], "the holiday is saved as typed")
pending_after = clients["kiran"].call("GET", "/api/teaching-sessions/pending")
ok(len(pending_after) == len(pending_before) - len(on_that_day) and all(p["date"] != holiday for p in pending_after),
   f"the holiday's {len(on_that_day)} class(es) are no longer 'not logged'")
ok(clients["kiran"].call("GET", f"/api/teaching-sessions/today?date={holiday}") == [], "nothing to log on a holiday")
coord.call("PUT", "/api/terms/active", {"name": term_now["name"], "academicYear": term_now["academicYear"],
           "startDate": term_now["startDate"], "endDate": term_now["endDate"],
           "holidays": [{"date": (TODAY + dt.timedelta(days=400)).isoformat(), "name": "Outside"}]}, expect=400)

print(f"\nPASS: {checks} checks")
