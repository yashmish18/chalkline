-- V1__init_schema.sql
-- Clean unified database schema without mock/demo courses, faculty, or allocations

CREATE TABLE app_user (
    id BIGSERIAL PRIMARY KEY,
    entra_oid VARCHAR(64) UNIQUE,
    email VARCHAR(255) UNIQUE NOT NULL,
    name VARCHAR(255) NOT NULL,
    role VARCHAR(50) NOT NULL CHECK (role IN ('COORDINATOR', 'FACULTY', 'DEAN')),
    active BOOLEAN NOT NULL DEFAULT TRUE,
    created_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    updated_at TIMESTAMPTZ NOT NULL DEFAULT NOW()
);

CREATE INDEX idx_app_user_email ON app_user(email);
CREATE INDEX idx_app_user_entra_oid ON app_user(entra_oid);
CREATE INDEX idx_app_user_role ON app_user(role);

CREATE TABLE department (
    id BIGSERIAL PRIMARY KEY,
    code VARCHAR(50) UNIQUE NOT NULL,
    name VARCHAR(255) NOT NULL
);

CREATE TABLE faculty (
    id BIGSERIAL PRIMARY KEY,
    user_id BIGINT UNIQUE NOT NULL REFERENCES app_user(id) ON DELETE CASCADE,
    name VARCHAR(255) NOT NULL,
    designation VARCHAR(100) NOT NULL,
    qualifications VARCHAR(255) NOT NULL,
    department VARCHAR(100) NOT NULL DEFAULT 'Information Technology',
    department_id BIGINT REFERENCES department(id) ON DELETE SET NULL,
    pbl_hours INT NOT NULL DEFAULT 0,
    created_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    updated_at TIMESTAMPTZ NOT NULL DEFAULT NOW()
);

CREATE INDEX idx_faculty_user_id ON faculty(user_id);

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

CREATE TABLE academic_term (
    id BIGSERIAL PRIMARY KEY,
    name VARCHAR(100) NOT NULL,
    academic_year VARCHAR(20) NOT NULL,
    semester INT NOT NULL,
    is_active BOOLEAN NOT NULL DEFAULT FALSE,
    start_date DATE NOT NULL,
    end_date DATE NOT NULL,
    created_at TIMESTAMPTZ NOT NULL DEFAULT NOW()
);

CREATE INDEX idx_term_active ON academic_term(is_active);

CREATE TABLE course (
    id BIGSERIAL PRIMARY KEY,
    code VARCHAR(50) UNIQUE NOT NULL,
    name VARCHAR(255) NOT NULL,
    type VARCHAR(50) NOT NULL DEFAULT 'THEORY',
    credits INT NOT NULL DEFAULT 3,
    ltp VARCHAR(10) NOT NULL DEFAULT '3-0-0',
    lecture_hours INT NOT NULL DEFAULT 3,
    tutorial_hours INT NOT NULL DEFAULT 0,
    practical_hours INT NOT NULL DEFAULT 0,
    description TEXT,
    category VARCHAR(40) NOT NULL DEFAULT 'CORE',
    department_id BIGINT REFERENCES department(id) ON DELETE SET NULL
);

CREATE TABLE course_offering (
    id BIGSERIAL PRIMARY KEY,
    course_id BIGINT NOT NULL REFERENCES course(id) ON DELETE CASCADE,
    academic_year VARCHAR(20) NOT NULL,
    year_of_study INT NOT NULL,
    semester INT NOT NULL,
    section VARCHAR(10) NOT NULL,
    term_id BIGINT REFERENCES academic_term(id) ON DELETE SET NULL,
    CONSTRAINT uq_course_offering UNIQUE (course_id, academic_year, year_of_study, semester, section)
);

CREATE TABLE teaching_assignment (
    id BIGSERIAL PRIMARY KEY,
    offering_id BIGINT NOT NULL REFERENCES course_offering(id) ON DELETE CASCADE,
    faculty_id BIGINT NOT NULL REFERENCES faculty(id) ON DELETE CASCADE,
    component VARCHAR(50) NOT NULL,
    sub_section VARCHAR(50) NOT NULL DEFAULT 'ALL',
    contact_hours INT NOT NULL DEFAULT 0,
    CONSTRAINT uq_teaching_assignment UNIQUE (offering_id, faculty_id, component, sub_section)
);

CREATE INDEX idx_teaching_assignment_faculty ON teaching_assignment(faculty_id);
CREATE INDEX idx_teaching_assignment_offering ON teaching_assignment(offering_id);

CREATE TABLE deadline (
    id BIGSERIAL PRIMARY KEY,
    title VARCHAR(255) NOT NULL,
    description TEXT,
    type VARCHAR(50) NOT NULL,
    due_at TIMESTAMPTZ NOT NULL,
    created_by BIGINT NOT NULL REFERENCES app_user(id),
    created_at TIMESTAMPTZ NOT NULL DEFAULT NOW()
);

CREATE INDEX idx_deadline_due_at ON deadline(due_at);

CREATE TABLE deadline_assignment (
    id BIGSERIAL PRIMARY KEY,
    deadline_id BIGINT NOT NULL REFERENCES deadline(id) ON DELETE CASCADE,
    faculty_id BIGINT NOT NULL REFERENCES faculty(id) ON DELETE CASCADE,
    offering_id BIGINT REFERENCES course_offering(id) ON DELETE SET NULL,
    created_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    CONSTRAINT uq_deadline_faculty_offering UNIQUE (deadline_id, faculty_id, offering_id)
);

CREATE INDEX idx_deadline_assignment_faculty ON deadline_assignment(faculty_id);
CREATE INDEX idx_deadline_assignment_deadline ON deadline_assignment(deadline_id);

CREATE TABLE submission (
    id BIGSERIAL PRIMARY KEY,
    deadline_assignment_id BIGINT UNIQUE NOT NULL REFERENCES deadline_assignment(id) ON DELETE CASCADE,
    status VARCHAR(50) NOT NULL DEFAULT 'PENDING',
    submitted_at TIMESTAMPTZ,
    reviewer_comment TEXT,
    updated_at TIMESTAMPTZ NOT NULL DEFAULT NOW()
);

CREATE INDEX idx_submission_assignment ON submission(deadline_assignment_id);
CREATE INDEX idx_submission_status ON submission(status);

CREATE TABLE submission_file (
    id BIGSERIAL PRIMARY KEY,
    submission_id BIGINT NOT NULL REFERENCES submission(id) ON DELETE CASCADE,
    version INT NOT NULL DEFAULT 1,
    storage_key VARCHAR(255) NOT NULL,
    original_name VARCHAR(255) NOT NULL,
    content_type VARCHAR(100) NOT NULL,
    size_bytes BIGINT NOT NULL,
    sha256 VARCHAR(64) NOT NULL,
    uploaded_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    CONSTRAINT uq_submission_version UNIQUE (submission_id, version)
);

CREATE INDEX idx_submission_file_submission ON submission_file(submission_id);

CREATE TABLE notification (
    id BIGSERIAL PRIMARY KEY,
    user_id BIGINT NOT NULL REFERENCES app_user(id) ON DELETE CASCADE,
    title VARCHAR(255) NOT NULL,
    message TEXT NOT NULL,
    type VARCHAR(50) NOT NULL,
    target_url VARCHAR(255),
    read_at TIMESTAMPTZ,
    created_at TIMESTAMPTZ NOT NULL DEFAULT NOW()
);

CREATE INDEX idx_notification_user_unread ON notification(user_id, read_at);
CREATE INDEX idx_notification_created ON notification(created_at DESC);

CREATE TABLE audit_log (
    id BIGSERIAL PRIMARY KEY,
    user_id BIGINT REFERENCES app_user(id) ON DELETE SET NULL,
    action VARCHAR(100) NOT NULL,
    target_entity VARCHAR(100) NOT NULL,
    target_id BIGINT,
    details TEXT,
    ip_address VARCHAR(45),
    created_at TIMESTAMPTZ NOT NULL DEFAULT NOW()
);

CREATE INDEX idx_audit_log_created ON audit_log(created_at DESC);
CREATE INDEX idx_audit_log_user ON audit_log(user_id);

CREATE TABLE syllabus_topic (
    id BIGSERIAL PRIMARY KEY,
    course_id BIGINT NOT NULL REFERENCES course(id) ON DELETE CASCADE,
    unit_number INT NOT NULL,
    unit_title VARCHAR(255) NOT NULL,
    topic_name VARCHAR(255) NOT NULL,
    target_week INT NOT NULL,
    lecture_order INT NOT NULL,
    created_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    CONSTRAINT uq_course_lecture_order UNIQUE (course_id, lecture_order)
);

CREATE INDEX idx_syllabus_course ON syllabus_topic(course_id);
CREATE INDEX idx_syllabus_unit ON syllabus_topic(course_id, unit_number);

CREATE TABLE teaching_session (
    id BIGSERIAL PRIMARY KEY,
    offering_id BIGINT NOT NULL REFERENCES course_offering(id) ON DELETE CASCADE,
    faculty_id BIGINT NOT NULL REFERENCES faculty(id) ON DELETE CASCADE,
    session_date DATE NOT NULL,
    session_type VARCHAR(50) NOT NULL DEFAULT 'LECTURE',
    status VARCHAR(50) NOT NULL DEFAULT 'HELD',
    unit_number INT,
    custom_topic_name VARCHAR(500),
    sub_section VARCHAR(50) NOT NULL DEFAULT 'ALL',
    lectures_taken INT NOT NULL DEFAULT 1,
    note TEXT,
    reason_not_held TEXT,
    created_at TIMESTAMPTZ NOT NULL DEFAULT NOW()
);

CREATE INDEX idx_teaching_session_offering_date ON teaching_session(offering_id, session_date);
CREATE INDEX idx_teaching_session_faculty_date ON teaching_session(faculty_id, session_date);

CREATE TABLE session_topic_log (
    session_id BIGINT NOT NULL REFERENCES teaching_session(id) ON DELETE CASCADE,
    topic_id BIGINT NOT NULL REFERENCES syllabus_topic(id) ON DELETE CASCADE,
    PRIMARY KEY (session_id, topic_id)
);

CREATE INDEX idx_session_topic_topic ON session_topic_log(topic_id);

CREATE TABLE timetable_slot (
    id BIGSERIAL PRIMARY KEY,
    offering_id BIGINT NOT NULL REFERENCES course_offering(id) ON DELETE CASCADE,
    faculty_id BIGINT NOT NULL REFERENCES faculty(id) ON DELETE CASCADE,
    day_of_week VARCHAR(20) NOT NULL,
    start_time TIME NOT NULL,
    end_time TIME NOT NULL,
    room VARCHAR(100) NOT NULL,
    component VARCHAR(20) NOT NULL,
    sub_section VARCHAR(50) NOT NULL DEFAULT 'ALL'
);

CREATE INDEX idx_timetable_faculty ON timetable_slot(faculty_id);
CREATE INDEX idx_timetable_offering ON timetable_slot(offering_id);
CREATE INDEX idx_timetable_day ON timetable_slot(day_of_week);

CREATE TABLE academic_document (
    id BIGSERIAL PRIMARY KEY,
    category VARCHAR(50) NOT NULL,
    title VARCHAR(255) NOT NULL,
    description TEXT,
    file_name VARCHAR(255) NOT NULL,
    file_type VARCHAR(100) NOT NULL,
    file_size BIGINT NOT NULL,
    storage_key VARCHAR(255) NOT NULL UNIQUE,
    term_id BIGINT REFERENCES academic_term(id) ON DELETE SET NULL,
    course_id BIGINT REFERENCES course(id) ON DELETE SET NULL,
    uploaded_by_faculty_id BIGINT REFERENCES faculty(id) ON DELETE SET NULL,
    uploaded_by_name VARCHAR(255) NOT NULL,
    created_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP
);

CREATE INDEX idx_academic_doc_category ON academic_document(category);
CREATE INDEX idx_academic_doc_term ON academic_document(term_id);
CREATE INDEX idx_academic_doc_course ON academic_document(course_id);

-- Baseline Reference Data (Departments, Active Term, Coordinator Account)
INSERT INTO department (code, name) VALUES
    ('CSE', 'Computer Science & Engineering'),
    ('ECE', 'Electronics & Communication Engineering'),
    ('AS', 'Applied Sciences'),
    ('ES', 'Environmental Science'),
    ('HSS', 'Humanities & Social Sciences'),
    ('ID', 'Interdisciplinary Studies'),
    ('IT', 'Information Technology')
ON CONFLICT (code) DO NOTHING;

INSERT INTO app_user (email, name, role) VALUES
    ('coordinator@iet.edu', 'Coordinator', 'COORDINATOR')
ON CONFLICT (email) DO NOTHING;

INSERT INTO faculty (user_id, name, designation, qualifications, department, department_id)
SELECT u.id, 'Coordinator', 'Professor & Coordinator', 'Ph.D.', 'Information Technology', d.id
FROM app_user u
JOIN department d ON d.code = 'IT'
WHERE u.email = 'coordinator@iet.edu'
ON CONFLICT (user_id) DO NOTHING;

INSERT INTO academic_term (name, academic_year, semester, is_active, start_date, end_date)
VALUES ('Odd Semester 2026-2027', '2026-2027', 1, TRUE, '2026-07-01', '2026-12-31');
