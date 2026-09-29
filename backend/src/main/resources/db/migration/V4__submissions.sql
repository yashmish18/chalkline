-- V4__submissions.sql
-- Submissions and Versioned File Storage

CREATE TABLE submission (
    id BIGSERIAL PRIMARY KEY,
    deadline_assignment_id BIGINT UNIQUE NOT NULL REFERENCES deadline_assignment(id) ON DELETE CASCADE,
    status VARCHAR(50) NOT NULL DEFAULT 'PENDING', -- PENDING, SUBMITTED, LATE, RETURNED, ACCEPTED
    submitted_at TIMESTAMPTZ,
    reviewer_comment TEXT,
    updated_at TIMESTAMPTZ NOT NULL DEFAULT NOW()
);

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

CREATE INDEX idx_submission_assignment ON submission(deadline_assignment_id);
CREATE INDEX idx_submission_status ON submission(status);
CREATE INDEX idx_submission_file_submission ON submission_file(submission_id);

-- Initialize submission rows for all deadline assignments created in V3
INSERT INTO submission (deadline_assignment_id, status)
SELECT id, 'PENDING' FROM deadline_assignment;
