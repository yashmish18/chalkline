-- V2: one component vocabulary and one batch grammar for rows written before Phase 4.
-- Mirrors CourseComponent.parse and SubSections.normalise:
--   THEORY -> LECTURE, LAB -> PRACTICAL
--   "Batch C" in section C -> ALL (the whole section's lab), "Batch C1" -> C1, "Batch 1" in D -> D1

CREATE TEMP TABLE ta_target AS
SELECT ta.id,
       ta.offering_id,
       ta.faculty_id,
       CASE ta.component WHEN 'THEORY' THEN 'LECTURE' WHEN 'LAB' THEN 'PRACTICAL' ELSE ta.component END AS component,
       CASE
           WHEN b.v = '' OR b.v = 'ALL' OR b.v = UPPER(co.section) THEN 'ALL'
           WHEN b.v ~ '^[0-9]+$' AND UPPER(co.section) <> 'ALL' THEN UPPER(co.section) || b.v
           ELSE b.v
       END AS sub_section
FROM teaching_assignment ta
JOIN course_offering co ON co.id = ta.offering_id
CROSS JOIN LATERAL (
    SELECT UPPER(TRIM(REGEXP_REPLACE(TRIM(ta.sub_section), '^batch\s*', '', 'i'))) AS v
) b;

-- Two old rows can collapse onto one (e.g. "ALL" and "Batch C"); keep the oldest.
DELETE FROM teaching_assignment
WHERE id IN (
    SELECT id FROM (
        SELECT id, ROW_NUMBER() OVER (
            PARTITION BY offering_id, faculty_id, component, sub_section ORDER BY id) AS rn
        FROM ta_target
    ) ranked
    WHERE rn > 1
);

UPDATE teaching_assignment ta
SET component = t.component,
    sub_section = t.sub_section
FROM ta_target t
WHERE t.id = ta.id
  AND (ta.component <> t.component OR ta.sub_section <> t.sub_section);

DROP TABLE ta_target;

UPDATE teaching_session ts
SET sub_section = CASE
        WHEN b.v = '' OR b.v = 'ALL' OR b.v = b.sec THEN 'ALL'
        WHEN b.v ~ '^[0-9]+$' AND b.sec <> 'ALL' THEN b.sec || b.v
        ELSE b.v
    END
FROM (
    SELECT s.id,
           UPPER(co.section) AS sec,
           UPPER(TRIM(REGEXP_REPLACE(TRIM(s.sub_section), '^batch\s*', '', 'i'))) AS v
    FROM teaching_session s
    JOIN course_offering co ON co.id = s.offering_id
) b
WHERE b.id = ts.id;

UPDATE timetable_slot SET component = 'LECTURE' WHERE component = 'THEORY';
UPDATE timetable_slot SET component = 'PRACTICAL' WHERE component = 'LAB';
