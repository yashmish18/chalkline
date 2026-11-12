-- A course description's unit titles and topic lines are kept exactly as written; the parser allows
-- 500 characters, so the columns must too (255 made a long topic line fail the save).
ALTER TABLE syllabus_topic ALTER COLUMN topic_name TYPE VARCHAR(500);
ALTER TABLE syllabus_topic ALTER COLUMN unit_title TYPE VARCHAR(500);
