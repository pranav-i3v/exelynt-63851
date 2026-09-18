-- =====================================================================
-- Resource Booking System - MySQL 8+ - STEP 2: schema
--   mysql -u booking_app -p booking_db < db/mysql/02_schema.sql
-- Idempotent: safe to re-run.
-- =====================================================================

USE booking_db;

-- ---------------------------------------------------------------------
-- users
-- ---------------------------------------------------------------------
CREATE TABLE IF NOT EXISTS users (
    id         BIGINT       NOT NULL AUTO_INCREMENT,
    username   VARCHAR(50)  NOT NULL,
    password   VARCHAR(100) NOT NULL,
    role       VARCHAR(20)  NOT NULL,
    enabled    BOOLEAN      NOT NULL DEFAULT TRUE,
    created_at DATETIME(6)  NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
    PRIMARY KEY (id),
    UNIQUE KEY uk_users_username (username),
    CONSTRAINT ck_users_role CHECK (role IN ('ADMIN', 'USER'))
) ENGINE = InnoDB;

-- ---------------------------------------------------------------------
-- resources
-- ---------------------------------------------------------------------
CREATE TABLE IF NOT EXISTS resources (
    id          BIGINT       NOT NULL AUTO_INCREMENT,
    name        VARCHAR(100) NOT NULL,
    type        VARCHAR(20)  NOT NULL,
    description VARCHAR(500),
    active      BOOLEAN      NOT NULL DEFAULT TRUE,
    created_at  DATETIME(6)  NOT NULL,
    updated_at  DATETIME(6),
    created_by  VARCHAR(50),
    updated_by  VARCHAR(50),
    PRIMARY KEY (id),
    UNIQUE KEY uk_resources_name (name),
    KEY ix_resources_type (type),
    KEY ix_resources_active (active),
    CONSTRAINT ck_resources_type CHECK (type IN ('ROOM', 'VEHICLE', 'EQUIPMENT'))
) ENGINE = InnoDB;

-- ---------------------------------------------------------------------
-- reservations
-- ---------------------------------------------------------------------
CREATE TABLE IF NOT EXISTS reservations (
    id          BIGINT        NOT NULL AUTO_INCREMENT,
    resource_id BIGINT        NOT NULL,
    user_id     BIGINT        NOT NULL,
    start_time  DATETIME(6)   NOT NULL,
    end_time    DATETIME(6)   NOT NULL,
    status      VARCHAR(20)   NOT NULL,
    price       DECIMAL(10,2) NOT NULL,
    created_at  DATETIME(6)   NOT NULL,
    updated_at  DATETIME(6),
    created_by  VARCHAR(50),
    updated_by  VARCHAR(50),
    PRIMARY KEY (id),
    KEY ix_reservations_user (user_id),
    KEY ix_reservations_resource (resource_id),
    KEY ix_reservations_status (status),
    KEY ix_reservations_period (resource_id, start_time, end_time),
    CONSTRAINT fk_reservations_resource FOREIGN KEY (resource_id) REFERENCES resources (id),
    CONSTRAINT fk_reservations_user     FOREIGN KEY (user_id)     REFERENCES users (id),
    CONSTRAINT ck_reservations_status   CHECK (status IN ('PENDING', 'CONFIRMED', 'CANCELLED')),
    CONSTRAINT ck_reservations_period   CHECK (end_time > start_time),
    CONSTRAINT ck_reservations_price    CHECK (price >= 0)
) ENGINE = InnoDB;

-- ---------------------------------------------------------------------
-- refresh_tokens (opaque token, only the SHA-256 hash is stored)
-- ---------------------------------------------------------------------
CREATE TABLE IF NOT EXISTS refresh_tokens (
    id         BIGINT      NOT NULL AUTO_INCREMENT,
    user_id    BIGINT      NOT NULL,
    token_hash VARCHAR(64) NOT NULL,
    expires_at DATETIME(6) NOT NULL,
    revoked    BOOLEAN     NOT NULL DEFAULT FALSE,
    created_at DATETIME(6) NOT NULL,
    PRIMARY KEY (id),
    UNIQUE KEY uk_refresh_tokens_hash (token_hash),
    KEY ix_refresh_tokens_user (user_id),
    CONSTRAINT fk_refresh_tokens_user FOREIGN KEY (user_id) REFERENCES users (id) ON DELETE CASCADE
) ENGINE = InnoDB;

-- ---------------------------------------------------------------------
-- audit_logs
-- ---------------------------------------------------------------------
CREATE TABLE IF NOT EXISTS audit_logs (
    id             BIGINT      NOT NULL AUTO_INCREMENT,
    actor          VARCHAR(50),
    action         VARCHAR(60) NOT NULL,
    entity_type    VARCHAR(40),
    entity_id      VARCHAR(40),
    event_time     DATETIME(6) NOT NULL,
    correlation_id VARCHAR(64),
    PRIMARY KEY (id),
    KEY ix_audit_logs_actor (actor),
    KEY ix_audit_logs_action (action),
    KEY ix_audit_logs_time (event_time)
) ENGINE = InnoDB;
