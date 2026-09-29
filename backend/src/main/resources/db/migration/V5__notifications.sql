-- V5__notifications.sql
-- In-app and Email Notifications Center

CREATE TABLE notification (
    id BIGSERIAL PRIMARY KEY,
    user_id BIGINT NOT NULL REFERENCES app_user(id) ON DELETE CASCADE,
    title VARCHAR(255) NOT NULL,
    message TEXT NOT NULL,
    type VARCHAR(50) NOT NULL, -- DEADLINE_ASSIGNED, UPCOMING_REMINDER, OVERDUE_ALERT, SUBMISSION_ACCEPTED, SUBMISSION_RETURNED, GENERAL
    target_url VARCHAR(255),
    read_at TIMESTAMPTZ,
    created_at TIMESTAMPTZ NOT NULL DEFAULT NOW()
);

CREATE INDEX idx_notification_user_unread ON notification(user_id, read_at);
CREATE INDEX idx_notification_created ON notification(created_at DESC);

-- Seed initial notification for coordinator and sample faculty
INSERT INTO notification (user_id, title, message, type, target_url, created_at)
VALUES 
(1, 'Welcome to Coordinator Portal', 'Your department coordinator account is active. You can now configure course allocations and broadcast deadlines.', 'GENERAL', '/deadlines', NOW()),
(2, 'Course File Deadline Approaching', 'Reminder: Course File & Syllabus Conduct Report is due soon. Please upload your materials.', 'UPCOMING_REMINDER', '/deadlines', NOW());
