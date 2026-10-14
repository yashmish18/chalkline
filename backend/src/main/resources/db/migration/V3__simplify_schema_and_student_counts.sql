-- V3: simpler schema.
--   * audit_log is gone (it was only written by the Excel export).
--   * faculty.department duplicated faculty.department_id; the reference is now the only source.
--   * Student strength per section (course_offering) and per batch (teaching_assignment).

DROP TABLE IF EXISTS audit_log;

-- Backfill the reference from the text column before dropping it.
UPDATE faculty f
SET department_id = d.id
FROM department d
WHERE f.department_id IS NULL
  AND (UPPER(d.code) = UPPER(TRIM(f.department)) OR UPPER(d.name) = UPPER(TRIM(f.department)));

INSERT INTO department (code, name)
SELECT DISTINCT UPPER(LEFT(REGEXP_REPLACE(TRIM(f.department), '[^A-Za-z]', '', 'g'), 50)), TRIM(f.department)
FROM faculty f
WHERE f.department_id IS NULL AND TRIM(f.department) <> ''
ON CONFLICT (code) DO NOTHING;

UPDATE faculty f
SET department_id = d.id
FROM department d
WHERE f.department_id IS NULL AND UPPER(d.name) = UPPER(TRIM(f.department));

ALTER TABLE faculty DROP COLUMN department;

-- Students enrolled in the section, and in a batch when an assignment owns only part of it
-- (NULL on an assignment means "the whole section").
ALTER TABLE course_offering ADD COLUMN student_count INT NOT NULL DEFAULT 0;
ALTER TABLE teaching_assignment ADD COLUMN student_count INT;
