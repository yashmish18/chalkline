-- V6__tracking_and_audit.sql
-- Coordinator Actions Audit Trail

CREATE TABLE audit_log (
    id BIGSERIAL PRIMARY KEY,
    user_id BIGINT REFERENCES app_user(id) ON DELETE SET NULL,
    action VARCHAR(100) NOT NULL, -- CREATE_DEADLINE, REVIEW_ACCEPT, REVIEW_RETURN, EXPORT_REPORT
    target_entity VARCHAR(100) NOT NULL,
    target_id BIGINT,
    details TEXT,
    ip_address VARCHAR(45),
    created_at TIMESTAMPTZ NOT NULL DEFAULT NOW()
);

CREATE INDEX idx_audit_log_created ON audit_log(created_at DESC);
CREATE INDEX idx_audit_log_user ON audit_log(user_id);
