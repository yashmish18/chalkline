-- V1__init_auth_and_users.sql
-- Core user identity and authorization table

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

-- Seed initial coordinator and faculty for dev and testing
INSERT INTO app_user (email, name, role, active)
VALUES 
    ('coordinator@iet.edu', 'Dr. Rajesh Sharma (Coordinator)', 'COORDINATOR', TRUE),
    ('amit.verma@iet.edu', 'Dr. Amit Verma (Faculty)', 'FACULTY', TRUE),
    ('priya.nair@iet.edu', 'Prof. Priya Nair (Faculty)', 'FACULTY', TRUE);
