-- V2__directory_and_academics.sql
-- Faculty Directory, Skills, Courses, and Teaching Allocations

CREATE TABLE faculty (
    id BIGSERIAL PRIMARY KEY,
    user_id BIGINT UNIQUE NOT NULL REFERENCES app_user(id) ON DELETE CASCADE,
    name VARCHAR(255) NOT NULL,
    designation VARCHAR(100) NOT NULL,
    qualifications VARCHAR(255) NOT NULL,
    department VARCHAR(100) NOT NULL DEFAULT 'Information Technology',
    created_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    updated_at TIMESTAMPTZ NOT NULL DEFAULT NOW()
);

CREATE TABLE skill (
    id BIGSERIAL PRIMARY KEY,
    name VARCHAR(100) UNIQUE NOT NULL,
    category VARCHAR(100) NOT NULL DEFAULT 'Technical'
);

CREATE TABLE faculty_skill (
    faculty_id BIGINT NOT NULL REFERENCES faculty(id) ON DELETE CASCADE,
    skill_id BIGINT NOT NULL REFERENCES skill(id) ON DELETE CASCADE,
    PRIMARY KEY (faculty_id, skill_id)
);

CREATE TABLE course (
    id BIGSERIAL PRIMARY KEY,
    code VARCHAR(50) UNIQUE NOT NULL,
    name VARCHAR(255) NOT NULL,
    type VARCHAR(50) NOT NULL DEFAULT 'THEORY' -- THEORY, LAB, INTEGRATED
);

CREATE TABLE course_offering (
    id BIGSERIAL PRIMARY KEY,
    course_id BIGINT NOT NULL REFERENCES course(id) ON DELETE CASCADE,
    academic_year VARCHAR(20) NOT NULL, -- e.g. 2026-2027
    year_of_study INT NOT NULL,         -- 1, 2, 3, 4
    semester INT NOT NULL,              -- 1 to 8
    section VARCHAR(10) NOT NULL,       -- A, B, C
    CONSTRAINT uq_course_offering UNIQUE (course_id, academic_year, year_of_study, semester, section)
);

CREATE TABLE teaching_assignment (
    id BIGSERIAL PRIMARY KEY,
    offering_id BIGINT NOT NULL REFERENCES course_offering(id) ON DELETE CASCADE,
    faculty_id BIGINT NOT NULL REFERENCES faculty(id) ON DELETE CASCADE,
    component VARCHAR(50) NOT NULL,     -- THEORY or LAB
    CONSTRAINT uq_teaching_assignment UNIQUE (offering_id, faculty_id, component)
);

CREATE INDEX idx_faculty_user_id ON faculty(user_id);
CREATE INDEX idx_teaching_assignment_faculty ON teaching_assignment(faculty_id);
CREATE INDEX idx_teaching_assignment_offering ON teaching_assignment(offering_id);

-- Seed initial faculty
INSERT INTO faculty (user_id, name, designation, qualifications, department)
VALUES
    (1, 'Dr. Rajesh Sharma', 'Professor & Coordinator', 'Ph.D. in Computer Science', 'Information Technology'),
    (2, 'Dr. Amit Verma', 'Associate Professor', 'Ph.D. in Distributed Systems', 'Information Technology'),
    (3, 'Prof. Priya Nair', 'Assistant Professor', 'M.Tech in Software Engineering', 'Information Technology');

-- Seed skills
INSERT INTO skill (name, category)
VALUES
    ('Computer Networks', 'Systems'),
    ('Operating Systems', 'Systems'),
    ('Database Management Systems', 'Data'),
    ('Web Technologies', 'Software'),
    ('Machine Learning', 'AI/Data'),
    ('Software Engineering', 'Software');

-- Link skills to faculty
INSERT INTO faculty_skill (faculty_id, skill_id)
VALUES
    (1, 1), (1, 2), -- Dr. Rajesh Sharma: Networks, OS
    (2, 1), (2, 3), (2, 5), -- Dr. Amit Verma: Networks, DBMS, ML
    (3, 4), (3, 6); -- Prof. Priya Nair: Web Tech, SE

-- Seed courses
INSERT INTO course (code, name, type)
VALUES
    ('CS501', 'Computer Networks', 'THEORY'),
    ('CS502', 'Database Management Systems', 'THEORY'),
    ('CS503', 'Web Technologies Lab', 'LAB'),
    ('CS504', 'Software Engineering', 'THEORY');

-- Seed offerings (5th semester, 2026-2027)
INSERT INTO course_offering (course_id, academic_year, year_of_study, semester, section)
VALUES
    (1, '2026-2027', 3, 5, 'A'), -- CN Sem 5 Sec A
    (2, '2026-2027', 3, 5, 'A'), -- DBMS Sem 5 Sec A
    (3, '2026-2027', 3, 5, 'A'), -- Web Lab Sem 5 Sec A
    (4, '2026-2027', 3, 5, 'B'); -- SE Sem 5 Sec B

-- Seed assignments
INSERT INTO teaching_assignment (offering_id, faculty_id, component)
VALUES
    (1, 2, 'THEORY'), -- Dr. Amit Verma teaches CN (Sec A)
    (2, 2, 'THEORY'), -- Dr. Amit Verma teaches DBMS (Sec A)
    (3, 3, 'LAB'),    -- Prof. Priya Nair teaches Web Lab (Sec A)
    (4, 3, 'THEORY'); -- Prof. Priya Nair teaches SE (Sec B)
