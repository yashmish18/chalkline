-- V4: semester settings and fair logging expectations.
--   * academic_term.semester was NOT NULL but never mapped, so creating a term from the app failed.
--     Odd/even is carried by the term name; the column goes.
--   * timetable_slot.created_at: a faculty member is only expected to log classes from the day their
--     timetable existed, not retroactively from the start of the semester.

ALTER TABLE academic_term DROP COLUMN IF EXISTS semester;

ALTER TABLE timetable_slot ADD COLUMN created_at TIMESTAMPTZ NOT NULL DEFAULT NOW();
