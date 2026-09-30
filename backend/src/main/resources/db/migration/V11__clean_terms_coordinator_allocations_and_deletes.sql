-- V11__clean_terms_coordinator_allocations_and_deletes.sql
-- Clean term naming, coordinator teaching allocations, and cascade cleanup

-- 1. Rename active term from 'Odd 2026-27' to 'Semester 1 (2026-27)'
UPDATE academic_term
SET name = 'Semester 1 (2026-27)'
WHERE name = 'Odd 2026-27';

-- 2. Add offerings for Section B of CS501 and CS502 for the active term if not exists
INSERT INTO course_offering (course_id, academic_year, year_of_study, semester, section, term_id)
SELECT 1, '2026-2027', 3, 5, 'B', (SELECT id FROM academic_term WHERE is_active = TRUE LIMIT 1)
WHERE NOT EXISTS (
    SELECT 1 FROM course_offering WHERE course_id = 1 AND section = 'B'
);

INSERT INTO course_offering (course_id, academic_year, year_of_study, semester, section, term_id)
SELECT 2, '2026-2027', 3, 5, 'B', (SELECT id FROM academic_term WHERE is_active = TRUE LIMIT 1)
WHERE NOT EXISTS (
    SELECT 1 FROM course_offering WHERE course_id = 2 AND section = 'B'
);

-- 3. Assign Dr. Rajesh Sharma (Coordinator, faculty_id = 1) to CS501 Sec B and CS502 Sec B
INSERT INTO teaching_assignment (offering_id, faculty_id, component, sub_section)
SELECT co.id, 1, 'THEORY', 'ALL'
FROM course_offering co
WHERE co.course_id = 1 AND co.section = 'B'
ON CONFLICT (offering_id, faculty_id, component, sub_section) DO NOTHING;

INSERT INTO teaching_assignment (offering_id, faculty_id, component, sub_section)
SELECT co.id, 1, 'THEORY', 'ALL'
FROM course_offering co
WHERE co.course_id = 2 AND co.section = 'B'
ON CONFLICT (offering_id, faculty_id, component, sub_section) DO NOTHING;

-- 4. Seed timetable slots for Dr. Rajesh Sharma (faculty_id = 1)
-- CS501 Sec B: Monday 11:00-12:00, Wednesday 11:00-12:00, Friday 11:00-12:00
-- CS502 Sec B: Tuesday 10:00-11:00, Thursday 10:00-11:00
INSERT INTO timetable_slot (offering_id, faculty_id, day_of_week, start_time, end_time, room, component, sub_section)
SELECT co.id, 1, 'MONDAY', '11:00', '12:00', 'Room 305 (Lecture Hall)', 'LECTURE', 'ALL'
FROM course_offering co WHERE co.course_id = 1 AND co.section = 'B'
ON CONFLICT DO NOTHING;

INSERT INTO timetable_slot (offering_id, faculty_id, day_of_week, start_time, end_time, room, component, sub_section)
SELECT co.id, 1, 'WEDNESDAY', '11:00', '12:00', 'Room 305 (Lecture Hall)', 'LECTURE', 'ALL'
FROM course_offering co WHERE co.course_id = 1 AND co.section = 'B'
ON CONFLICT DO NOTHING;

INSERT INTO timetable_slot (offering_id, faculty_id, day_of_week, start_time, end_time, room, component, sub_section)
SELECT co.id, 1, 'FRIDAY', '11:00', '12:00', 'Room 305 (Lecture Hall)', 'LECTURE', 'ALL'
FROM course_offering co WHERE co.course_id = 1 AND co.section = 'B'
ON CONFLICT DO NOTHING;

INSERT INTO timetable_slot (offering_id, faculty_id, day_of_week, start_time, end_time, room, component, sub_section)
SELECT co.id, 1, 'TUESDAY', '10:00', '11:00', 'Room 203 (Smart Class)', 'LECTURE', 'ALL'
FROM course_offering co WHERE co.course_id = 2 AND co.section = 'B'
ON CONFLICT DO NOTHING;

INSERT INTO timetable_slot (offering_id, faculty_id, day_of_week, start_time, end_time, room, component, sub_section)
SELECT co.id, 1, 'THURSDAY', '10:00', '11:00', 'Room 203 (Smart Class)', 'LECTURE', 'ALL'
FROM course_offering co WHERE co.course_id = 2 AND co.section = 'B'
ON CONFLICT DO NOTHING;
