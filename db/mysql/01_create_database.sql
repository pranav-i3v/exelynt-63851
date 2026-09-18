-- =====================================================================
-- Resource Booking System - MySQL 8+ - STEP 1: user + database
-- Run as root:
--   mysql -u root -p < db/mysql/01_create_database.sql
-- Change the password below before running; it must match DB_PASSWORD.
-- =====================================================================

CREATE DATABASE IF NOT EXISTS booking_db
    CHARACTER SET utf8mb4
    COLLATE utf8mb4_unicode_ci;

CREATE USER IF NOT EXISTS 'booking_app'@'%' IDENTIFIED BY 'change_me_local_only';

GRANT ALL PRIVILEGES ON booking_db.* TO 'booking_app'@'%';
FLUSH PRIVILEGES;
