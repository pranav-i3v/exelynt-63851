-- =====================================================================
-- Resource Booking System - MySQL - teardown (destructive)
--   mysql -u booking_app -p booking_db < db/mysql/99_drop.sql
-- =====================================================================
USE booking_db;
SET FOREIGN_KEY_CHECKS = 0;
DROP TABLE IF EXISTS audit_logs;
DROP TABLE IF EXISTS refresh_tokens;
DROP TABLE IF EXISTS reservations;
DROP TABLE IF EXISTS resources;
DROP TABLE IF EXISTS users;
SET FOREIGN_KEY_CHECKS = 1;
