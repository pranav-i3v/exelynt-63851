-- =====================================================================
-- Resource Booking System - PostgreSQL - STEP 1: role + database
-- Run as a superuser (e.g. postgres):
--   psql -U postgres -f db/postgresql/01_create_database.sql
-- Change the password below before running; it must match DB_PASSWORD.
-- =====================================================================

-- Create the application role (login user).
DO $$
BEGIN
    IF NOT EXISTS (SELECT 1 FROM pg_roles WHERE rolname = 'booking_app') THEN
        CREATE ROLE booking_app LOGIN PASSWORD 'change_me_local_only';
    END IF;
END
$$;

-- Create the database owned by that role.
-- CREATE DATABASE cannot run inside a DO block / transaction, hence \gexec.
SELECT 'CREATE DATABASE booking_db OWNER booking_app ENCODING ''UTF8'''
WHERE NOT EXISTS (SELECT 1 FROM pg_database WHERE datname = 'booking_db')
\gexec

GRANT ALL PRIVILEGES ON DATABASE booking_db TO booking_app;
