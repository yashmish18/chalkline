-- V5: continuous internal evaluation (CIE) and remedial plans.

-- How many continuous assessments each section must hold this semester (department policy).
ALTER TABLE academic_term ADD COLUMN required_assessments INT NOT NULL DEFAULT 3;

-- One row per assessment actually held for a section.
CREATE TABLE assessment (
    id BIGSERIAL PRIMARY KEY,
    offering_id BIGINT NOT NULL REFERENCES course_offering(id) ON DELETE CASCADE,
    name VARCHAR(100) NOT NULL,
    kind VARCHAR(30) NOT NULL,
    weight_pct INT NOT NULL CHECK (weight_pct BETWEEN 0 AND 100),
    held_on DATE NOT NULL,
    students_assessed INT,
    average_pct INT CHECK (average_pct BETWEEN 0 AND 100),
    below_40_count INT,
    recorded_by_faculty_id BIGINT REFERENCES faculty(id) ON DELETE SET NULL,
    created_at TIMESTAMPTZ NOT NULL DEFAULT NOW()
);

CREATE INDEX idx_assessment_offering ON assessment(offering_id);

-- What a faculty member commits to when a section falls behind or scores poorly.
CREATE TABLE remedial_plan (
    id BIGSERIAL PRIMARY KEY,
    offering_id BIGINT NOT NULL REFERENCES course_offering(id) ON DELETE CASCADE,
    faculty_id BIGINT NOT NULL REFERENCES faculty(id) ON DELETE CASCADE,
    reason VARCHAR(30) NOT NULL,
    lagging_topics TEXT,
    action TEXT NOT NULL,
    extra_sessions INT NOT NULL DEFAULT 0,
    target_date DATE NOT NULL,
    status VARCHAR(20) NOT NULL DEFAULT 'SUBMITTED',
    created_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    completed_at TIMESTAMPTZ
);

CREATE INDEX idx_remedial_offering ON remedial_plan(offering_id);
