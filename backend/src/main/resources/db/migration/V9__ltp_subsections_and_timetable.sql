-- V9__ltp_subsections_and_timetable.sql
-- Variable LTP structure, Course Descriptions, Sub-sections (A1, A2), and Faculty Timetable Schedule

-- 1. Extend course with LTP structure and description
ALTER TABLE course ADD COLUMN IF NOT EXISTS ltp VARCHAR(10) NOT NULL DEFAULT '3-0-0';
ALTER TABLE course ADD COLUMN IF NOT EXISTS lecture_hours INT NOT NULL DEFAULT 3;
ALTER TABLE course ADD COLUMN IF NOT EXISTS tutorial_hours INT NOT NULL DEFAULT 0;
ALTER TABLE course ADD COLUMN IF NOT EXISTS practical_hours INT NOT NULL DEFAULT 0;
ALTER TABLE course ADD COLUMN IF NOT EXISTS description TEXT;

-- Update course metadata with realistic LTP and descriptions
UPDATE course
SET ltp = '3-0-2',
    lecture_hours = 3,
    tutorial_hours = 0,
    practical_hours = 2,
    credits = 4,
    description = 'Covers fundamental concepts of computer networking, the 7-layer OSI and TCP/IP protocol architectures, packet routing algorithms, transport layer flow and congestion control, and network programming.'
WHERE code = 'CS501';

UPDATE course
SET ltp = '3-1-0',
    lecture_hours = 3,
    tutorial_hours = 1,
    practical_hours = 0,
    credits = 4,
    description = 'Covers relational database design, relational algebra, SQL querying, normalization up to BCNF, ACID transaction processing, 2PL concurrency control, and B+ tree indexing structures.'
WHERE code = 'CS502';

UPDATE course
SET ltp = '0-0-4',
    lecture_hours = 0,
    tutorial_hours = 0,
    practical_hours = 4,
    credits = 2,
    description = 'Hands-on laboratory sessions focusing on modern web application development, DOM manipulation, asynchronous programming, RESTful API design, and client-server architectures.'
WHERE code = 'CS503';

UPDATE course
SET ltp = '3-0-0',
    lecture_hours = 3,
    tutorial_hours = 0,
    practical_hours = 0,
    credits = 3,
    description = 'Software engineering principles, Agile and Scrum methodologies, requirement engineering, architectural design patterns, software testing, verification, and CI/CD pipelines.'
WHERE code = 'CS504';

-- 2. Extend teaching_assignment with sub-section / batch (e.g. 'ALL', 'A1', 'A2')
ALTER TABLE teaching_assignment ADD COLUMN IF NOT EXISTS sub_section VARCHAR(50) NOT NULL DEFAULT 'ALL';

-- Drop old unique constraint on (offering_id, faculty_id, component) and replace with one including sub_section
ALTER TABLE teaching_assignment DROP CONSTRAINT IF EXISTS uq_teaching_assignment;
ALTER TABLE teaching_assignment ADD CONSTRAINT uq_teaching_assignment UNIQUE (offering_id, faculty_id, component, sub_section);

-- 3. Extend teaching_session with unit_number, custom_topic_name, sub_section, and lectures_taken
ALTER TABLE teaching_session ADD COLUMN IF NOT EXISTS unit_number INT;
ALTER TABLE teaching_session ADD COLUMN IF NOT EXISTS custom_topic_name VARCHAR(500);
ALTER TABLE teaching_session ADD COLUMN IF NOT EXISTS sub_section VARCHAR(50) NOT NULL DEFAULT 'ALL';
ALTER TABLE teaching_session ADD COLUMN IF NOT EXISTS lectures_taken INT NOT NULL DEFAULT 1;

-- 4. Create timetable_slot table for weekly faculty schedule
CREATE TABLE timetable_slot (
    id BIGSERIAL PRIMARY KEY,
    offering_id BIGINT NOT NULL REFERENCES course_offering(id) ON DELETE CASCADE,
    faculty_id BIGINT NOT NULL REFERENCES faculty(id) ON DELETE CASCADE,
    day_of_week VARCHAR(20) NOT NULL, -- MONDAY, TUESDAY, WEDNESDAY, THURSDAY, FRIDAY
    start_time TIME NOT NULL,
    end_time TIME NOT NULL,
    room VARCHAR(100) NOT NULL,
    component VARCHAR(20) NOT NULL,    -- LECTURE, TUTORIAL, PRACTICAL
    sub_section VARCHAR(50) NOT NULL DEFAULT 'ALL', -- ALL, A1, A2, B1, etc.
    created_at TIMESTAMPTZ NOT NULL DEFAULT NOW()
);

CREATE INDEX idx_timetable_faculty ON timetable_slot(faculty_id);
CREATE INDEX idx_timetable_offering ON timetable_slot(offering_id);
CREATE INDEX idx_timetable_day ON timetable_slot(day_of_week);

-- Seed timetable slots for Dr. Amit Verma (faculty_id = 2)
-- CS501 (Sec A): Mon 10:00-11:00 (Lecture, Room 304), Wed 10:00-11:00 (Lecture, Room 304), Fri 10:00-11:00 (Lecture, Room 304)
-- CS501 (Sec A, Lab Batch A1): Tue 14:00-16:00 (Practical, Lab 2)
-- CS501 (Sec A, Lab Batch A2): Thu 14:00-16:00 (Practical, Lab 2)
-- CS502 (Sec A): Mon 14:00-15:00 (Lecture, Room 201), Wed 14:00-15:00 (Lecture, Room 201), Thu 11:00-12:00 (Lecture, Room 201)
-- CS502 (Sec A, Tutorial T1): Fri 14:00-15:00 (Tutorial, Room 201)

INSERT INTO timetable_slot (offering_id, faculty_id, day_of_week, start_time, end_time, room, component, sub_section)
VALUES
    (1, 2, 'MONDAY', '10:00', '11:00', 'Room 304 (Lecture Hall)', 'LECTURE', 'ALL'),
    (1, 2, 'WEDNESDAY', '10:00', '11:00', 'Room 304 (Lecture Hall)', 'LECTURE', 'ALL'),
    (1, 2, 'FRIDAY', '10:00', '11:00', 'Room 304 (Lecture Hall)', 'LECTURE', 'ALL'),
    (1, 2, 'TUESDAY', '14:00', '16:00', 'Lab 2 (Networking Systems Lab)', 'PRACTICAL', 'A1'),
    (1, 2, 'THURSDAY', '14:00', '16:00', 'Lab 2 (Networking Systems Lab)', 'PRACTICAL', 'A2'),
    (2, 2, 'MONDAY', '14:00', '15:00', 'Room 201 (Smart Class)', 'LECTURE', 'ALL'),
    (2, 2, 'WEDNESDAY', '14:00', '15:00', 'Room 201 (Smart Class)', 'LECTURE', 'ALL'),
    (2, 2, 'THURSDAY', '11:00', '12:00', 'Room 201 (Smart Class)', 'LECTURE', 'ALL'),
    (2, 2, 'FRIDAY', '14:00', '15:00', 'Room 201 (Smart Class)', 'TUTORIAL', 'T1');

-- Seed timetable slots for Prof. Priya Nair (faculty_id = 3)
-- CS503 (Web Lab): Mon 09:00-11:00 (Practical, Lab 1, Batch A1), Wed 09:00-11:00 (Practical, Lab 1, Batch A2)
-- CS504 (Software Engg): Tue 11:00-12:00 (Lecture, Room 202), Thu 09:00-10:00 (Lecture, Room 202), Fri 11:00-12:00 (Lecture, Room 202)
INSERT INTO timetable_slot (offering_id, faculty_id, day_of_week, start_time, end_time, room, component, sub_section)
VALUES
    (3, 3, 'MONDAY', '09:00', '11:00', 'Lab 1 (Web Engineering Lab)', 'PRACTICAL', 'A1'),
    (3, 3, 'WEDNESDAY', '09:00', '11:00', 'Lab 1 (Web Engineering Lab)', 'PRACTICAL', 'A2'),
    (4, 3, 'TUESDAY', '11:00', '12:00', 'Room 202 (Lecture Hall)', 'LECTURE', 'ALL'),
    (4, 3, 'THURSDAY', '09:00', '10:00', 'Room 202 (Lecture Hall)', 'LECTURE', 'ALL'),
    (4, 3, 'FRIDAY', '11:00', '12:00', 'Room 202 (Lecture Hall)', 'LECTURE', 'ALL');

-- Add practical sub-section allocations to teaching_assignment for completeness
INSERT INTO teaching_assignment (offering_id, faculty_id, component, sub_section)
VALUES
    (1, 2, 'PRACTICAL', 'A1'),
    (1, 2, 'PRACTICAL', 'A2')
ON CONFLICT (offering_id, faculty_id, component, sub_section) DO NOTHING;
