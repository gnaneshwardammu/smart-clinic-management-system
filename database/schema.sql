-- =====================================================================
-- Smart Clinic Management System - MySQL schema (database: capstone)
-- Safe to run more than once (CREATE ... IF NOT EXISTS). Run it in MySQL Workbench
-- BEFORE or AFTER starting the app: both use exactly the same table definitions
-- (the Spring Boot entities use the same table, column, index and constraint names).
-- =====================================================================
CREATE DATABASE IF NOT EXISTS capstone CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci;
USE capstone;

CREATE TABLE IF NOT EXISTS admin (
    id            BIGINT       NOT NULL AUTO_INCREMENT,
    name          VARCHAR(100) NOT NULL,
    email         VARCHAR(150) NOT NULL,
    password_hash VARCHAR(100) NOT NULL,
    created_at    DATETIME(6)  NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
    PRIMARY KEY (id),
    CONSTRAINT uk_admin_email UNIQUE (email)
) ENGINE=InnoDB;

CREATE TABLE IF NOT EXISTS doctor (
    id             BIGINT       NOT NULL AUTO_INCREMENT,
    name           VARCHAR(100) NOT NULL,
    specialty      VARCHAR(100) NOT NULL,
    email          VARCHAR(150) NOT NULL,
    password_hash  VARCHAR(100) NOT NULL,
    phone          VARCHAR(20)  NULL,
    available_from TIME(6)      NOT NULL DEFAULT '09:00:00',
    available_to   TIME(6)      NOT NULL DEFAULT '17:00:00',
    active         BIT(1)       NOT NULL DEFAULT b'1',
    created_at     DATETIME(6)  NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
    PRIMARY KEY (id),
    CONSTRAINT uk_doctor_email UNIQUE (email),
    CONSTRAINT chk_doctor_hours CHECK (available_from < available_to),
    INDEX idx_doctor_name (name),
    INDEX idx_doctor_specialty (specialty)
) ENGINE=InnoDB;

CREATE TABLE IF NOT EXISTS patient (
    id            BIGINT       NOT NULL AUTO_INCREMENT,
    name          VARCHAR(100) NOT NULL,
    email         VARCHAR(150) NOT NULL,
    password_hash VARCHAR(100) NOT NULL,
    phone         VARCHAR(20)  NULL,
    date_of_birth DATE         NULL,
    address       VARCHAR(255) NULL,
    created_at    DATETIME(6)  NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
    PRIMARY KEY (id),
    CONSTRAINT uk_patient_email UNIQUE (email),
    INDEX idx_patient_name (name)
) ENGINE=InnoDB;

-- Repair legacy rows created before timestamp defaults were applied. MySQL Connector/J
-- rejects zero dates while Hibernate loads Admin and Doctor entities.
UPDATE admin
SET created_at = CURRENT_TIMESTAMP(6)
WHERE YEAR(created_at) = 0;

UPDATE doctor
SET created_at = CURRENT_TIMESTAMP(6)
WHERE YEAR(created_at) = 0;

UPDATE patient
SET created_at = CURRENT_TIMESTAMP(6)
WHERE YEAR(created_at) = 0;

CREATE TABLE IF NOT EXISTS appointment (
    id         BIGINT       NOT NULL AUTO_INCREMENT,
    doctor_id  BIGINT       NOT NULL,
    patient_id BIGINT       NOT NULL,
    start_time DATETIME(6)  NOT NULL,
    end_time   DATETIME(6)  NOT NULL,
    status     VARCHAR(20)  NOT NULL DEFAULT 'SCHEDULED',
    reason     VARCHAR(255) NULL,
    created_at DATETIME(6)  NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
    updated_at DATETIME(6)  NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
    PRIMARY KEY (id),
    CONSTRAINT fk_appointment_doctor  FOREIGN KEY (doctor_id)  REFERENCES doctor (id),
    CONSTRAINT fk_appointment_patient FOREIGN KEY (patient_id) REFERENCES patient (id),
    CONSTRAINT chk_appointment_status CHECK (status IN ('SCHEDULED','COMPLETED','CANCELLED','NO_SHOW')),
    CONSTRAINT chk_appointment_times  CHECK (start_time < end_time),
    INDEX idx_appointment_doctor_start  (doctor_id, start_time),
    INDEX idx_appointment_patient_start (patient_id, start_time),
    INDEX idx_appointment_status (status)
) ENGINE=InnoDB;
