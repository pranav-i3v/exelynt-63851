-- =====================================================================
-- Resource Booking System - PostgreSQL - teardown (destructive)
--   psql -U booking_app -d booking_db -f db/postgresql/99_drop.sql
-- =====================================================================
DROP TABLE IF EXISTS audit_logs;
DROP TABLE IF EXISTS refresh_tokens;
DROP TABLE IF EXISTS reservations;
DROP TABLE IF EXISTS resources;
DROP TABLE IF EXISTS users;
