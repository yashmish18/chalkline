-- Class logging: the lecture number a class covered, and why it was logged late.
ALTER TABLE teaching_session ADD COLUMN lecture_number INT;
ALTER TABLE teaching_session ADD COLUMN delay_reason TEXT;

-- One vocabulary for components everywhere: LECTURE, TUTORIAL, PRACTICAL.
UPDATE teaching_session SET session_type = 'PRACTICAL' WHERE UPPER(session_type) IN ('LAB', 'P', 'PRACTICAL');
UPDATE teaching_session SET session_type = 'LECTURE' WHERE UPPER(session_type) IN ('THEORY', 'L', 'LECTURE');
UPDATE teaching_session SET session_type = 'TUTORIAL' WHERE UPPER(session_type) IN ('T', 'TUT', 'TUTORIAL');
