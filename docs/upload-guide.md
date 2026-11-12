# Setting up a semester: what to upload, in order

The coordinator uploads **one sheet**. Everything after that is done by each faculty member, and the
dashboard reminds them by itself. Nothing you don't type is ever filled in for you.

## 1. Semester dates (once)
**Overview → Semester settings.** Name, academic year, first and last teaching day. Classes before
the first day can't be logged, and the "not logged" list starts there.

## 2. The allocation sheet (coordinator)
**Allocations → Import → Template** downloads the template. One row per faculty member, course,
section, component and batch:

| Column | Rule |
|---|---|
| Faculty Name | As it should appear. `Dr. X (Visiting Faculty)` makes the designation Visiting Faculty. |
| Email | **Required.** The address they sign in with. No email, no import. |
| Designation, Department, Role, Skills, PBL Hours | About the person. Write them on any one of their rows; if two rows disagree, the import stops. Role is `Faculty` or `Coordinator`. Skills are separated by `;`. |
| Course Code | Blank = a person with no course this semester (only the person columns are read). |
| Course Name, L-T-P | Same on every row of that course, letter for letter. L-T-P looks like `3-0-2`. |
| Semester, Section | Semester 1–8. Section as the timetable writes it (A, B, … or ALL for an elective group). |
| Section Students | **Required.** Same number on every row of that section. |
| Component | `Lecture`, `Tutorial` or `Practical`. |
| Batch | Required for a practical: `A1`, `A2`, … or the section letter / `ALL` for the whole section. Blank for a lecture. |
| Batch Students | Required for a batch. The batches of a section can't add up to more than the section. |

Weekly hours are **not** typed: a lecture row counts L hours, a tutorial row T, each practical batch P.

If anything is wrong the sheet is **not imported at all**, and every problem is listed with its row.
Fix them and upload the same file again. Uploading again later updates the semester: rows you
removed are removed from the dashboard too (with their timetable classes).

## 3. Timetables (each faculty member)
Each faculty member signs in and uploads **their own** timetable PDF from the portal under
**Timetable → Upload my timetable**. A two-hour lab becomes one class. A class that doesn't match the
allocation sheet is rejected with both sides named, e.g. *"the timetable has the practical of CS1133
Sec A batch A1, but the allocation sheet gives this faculty member practical batch A3"* — correct
whichever is wrong.

The coordinator can also upload timetables: either one master sheet for everyone (with a
**Faculty Email** column) or one person's sheet at a time. An upload replaces the timetable of each
person it has classes for and **leaves everyone else's untouched**.

## 4. Syllabus (each faculty member)
**My Courses → a course → Syllabus → Upload.** PDF (with selectable text), Word **.docx** or **.doc**, or
plain text. A scanned PDF has no text to read: upload the original Word file instead. Review the topics,
confirm; the file is then kept in the course's files and in the faculty member's file list (the
coordinator can upload it for them too).

Topics are taken exactly as the document writes them, unit by unit (`Unit I — Title`, then one topic
per line); objectives, outcomes and *Online resources* are skipped. Topics are numbered 1, 2, 3… in
that order across all units, and **that number is the lecture number**: logging *Lecture 15* picks
topic 15 (change it if you covered something else). If the document's course title or
L-T-P differs from the allocation sheet, the preview says so: correct whichever is wrong.

Uploading a corrected syllabus later keeps every class already logged: an unchanged topic stays the
same topic, and a typo fix in place is an edit. A topic that classes were already logged against
can't be dropped; the save is refused with its name.

**Holidays.** The coordinator lists them under **Overview → Semester settings → Holidays** (date and
name). A holiday's classes are never "not logged", Today says it's a holiday, and pace doesn't expect
lectures on it.

**Pace** is measured against your timetable: by today, the share of the term's timetabled lectures
that are past should match the share of topics covered (one topic either way counts as on pace). It
reads *not measured* until the semester dates, your timetable and the syllabus are all in.

## 5. Daily logging (each faculty member)

**Back-logging is allowed.** Missed classes are listed under *Classes not logged yet* (by week or
month). Log each as held or not held; anything more than 48 hours old asks for the reason it's late.

**Today** lists the day's classes. Log each as held (lecture number, unit, topic) or not held (reason).
A class can't be logged before it starts. Classes more than two days old need a reason for the late
log. Missed classes are listed by week or month under **Classes not logged yet**.

## What happens without anyone chasing
Every morning each faculty member gets a reminder for whatever of theirs is missing: timetable,
a course syllabus, classes left unlogged. Every Monday the coordinators get one list of what is still
missing after a week.
