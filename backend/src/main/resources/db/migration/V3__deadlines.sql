-- V3__deadlines.sql
-- Deadlines and Assignment Slots

CREATE TABLE deadline (
    id BIGSERIAL PRIMARY KEY,
    title VARCHAR(255) NOT NULL,
    description TEXT,
    type VARCHAR(50) NOT NULL, -- COURSE_FILE, EVALUATION, MARKS_UPLOAD, SYLLABUS_REPORT, OTHER
    due_at TIMESTAMPTZ NOT NULL,
    created_by BIGINT NOT NULL REFERENCES app_user(id),
    created_at TIMESTAMPTZ NOT NULL DEFAULT NOW()
);

CREATE TABLE deadline_assignment (
    id BIGSERIAL PRIMARY KEY,
    deadline_id BIGINT NOT NULL REFERENCES deadline(id) ON DELETE CASCADE,
    faculty_id BIGINT NOT NULL REFERENCES faculty(id) ON DELETE CASCADE,
    offering_id BIGINT REFERENCES course_offering(id) ON DELETE SET NULL,
    created_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    CONSTRAINT uq_deadline_faculty_offering UNIQUE (deadline_id, faculty_id, offering_id)
);

CREATE INDEX idx_deadline_due_at ON deadline(due_at);
CREATE INDEX idx_deadline_assignment_faculty ON deadline_assignment(faculty_id);
CREATE INDEX idx_deadline_assignment_deadline ON deadline_assignment(deadline_id);

-- Seed initial deadlines
INSERT INTO deadline (title, description, type, due_at, created_by)
VALUES
    ('Course File Submission - Mid Term', 'Submit complete course plan, lecture notes, and syllabus coverage document.', 'COURSE_FILE', NOW() + INTERVAL '7 days', 1),
    ('Mid-Term Marks Upload', 'Upload evaluated marks spreadsheet for Mid-Term Exams.', 'MARKS_UPLOAD', NOW() + INTERVAL '14 days', 1),
    ('Initial Syllabus Progress Report', 'Report on week 1-4 syllabus conduct and tutorial coverage.', 'SYLLABUS_REPORT', NOW() - INTERVAL '2 days', 1); -- overdue deadline for testing

-- Seed assignments for initial deadlines
INSERT INTO deadline_assignment (deadline_id, faculty_id, offering_id)
VALUES
    (1, 2, 1), -- Dr. Amit Verma assigned for CN (Offering 1)
    (1, 2, 2), -- Dr. Amit Verma assigned for DBMS (Offering 2)
    (1, 3, 3), -- Prof. Priya Nair assigned for Web Lab (Offering 3)
    (1, 3, 4), -- Prof. Priya Nair assigned for SE (Offering 4)
    (2, 2, 1),
    (2, 3, 4),
    (3, 2, 1);
