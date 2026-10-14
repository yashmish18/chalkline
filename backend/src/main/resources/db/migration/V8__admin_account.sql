-- V8: one admin account (APP_ADMIN_EMAIL) protected by an admin password; coordinators come
-- straight from the allocation sheet's Role column, so emailed coordinator invites are gone.
ALTER TABLE app_user ADD COLUMN admin BOOLEAN NOT NULL DEFAULT FALSE;
DROP TABLE IF EXISTS coordinator_invite;
