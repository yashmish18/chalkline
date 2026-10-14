-- V7: coordinator access needs a verified email and a coordinator password.
--   * The allocation sheet (Role = Coordinator) or APP_COORDINATOR_EMAIL only *invites* someone;
--     they become coordinator after opening the emailed link while signed in as that email,
--     and choosing a password. Only a BCrypt hash of that password is stored.
--   * Failed password attempts lock coordinator access for a while.

ALTER TABLE app_user ADD COLUMN coordinator_password_hash VARCHAR(100);
ALTER TABLE app_user ADD COLUMN coordinator_failed_attempts INT NOT NULL DEFAULT 0;
ALTER TABLE app_user ADD COLUMN coordinator_locked_until TIMESTAMPTZ;

CREATE TABLE coordinator_invite (
    id          BIGSERIAL PRIMARY KEY,
    user_id     BIGINT       NOT NULL REFERENCES app_user (id) ON DELETE CASCADE,
    token_hash  VARCHAR(64)  NOT NULL UNIQUE,   -- SHA-256 of the emailed token; the token itself is never stored
    expires_at  TIMESTAMPTZ  NOT NULL,
    used_at     TIMESTAMPTZ,
    created_at  TIMESTAMPTZ  NOT NULL DEFAULT NOW()
);
CREATE INDEX idx_coordinator_invite_user ON coordinator_invite (user_id);
