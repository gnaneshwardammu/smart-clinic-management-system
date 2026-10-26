-- =====================================================================
-- Smart Clinic Management System - SAMPLE DATA (LOCAL DEVELOPMENT ONLY)
-- Run AFTER database/schema.sql (or after the app has created the tables).
-- Re-runnable: rows are matched by email / appointment slot and skipped if they already exist.
-- Every account below uses the dev password:  Password@123   (stored only as a BCrypt hash)
-- Expected ids on a FRESH database: doctors 1-5, patients 1-5, appointments 1-13.
-- =====================================================================
USE capstone;

INSERT IGNORE INTO admin (name, email, password_hash)
VALUES ('Clinic Administrator', 'admin@clinic.test', '$2a$10$noyLGrS.EOtCMFNvJwQMAuLQJUUX8tgnoxk4we5w2Tod1UCb1YQte');

-- Doctors (Cardiology appears twice so the specialty filter has something to show)
INSERT IGNORE INTO doctor (name, specialty, email, password_hash, phone, available_from, available_to, active) VALUES
  ('Dr. Asha Verma', 'Cardiology', 'asha.verma@clinic.test', '$2a$10$noyLGrS.EOtCMFNvJwQMAuLQJUUX8tgnoxk4we5w2Tod1UCb1YQte', '+91-9000000001', '09:00:00', '17:00:00', b'1'),
  ('Dr. Rahul Mehta', 'Dermatology', 'rahul.mehta@clinic.test', '$2a$10$noyLGrS.EOtCMFNvJwQMAuLQJUUX8tgnoxk4we5w2Tod1UCb1YQte', '+91-9000000002', '09:00:00', '17:00:00', b'1'),
  ('Dr. Priya Nair', 'Pediatrics', 'priya.nair@clinic.test', '$2a$10$noyLGrS.EOtCMFNvJwQMAuLQJUUX8tgnoxk4we5w2Tod1UCb1YQte', '+91-9000000003', '10:00:00', '16:00:00', b'1'),
  ('Dr. Kiran Rao', 'Orthopedics', 'kiran.rao@clinic.test', '$2a$10$noyLGrS.EOtCMFNvJwQMAuLQJUUX8tgnoxk4we5w2Tod1UCb1YQte', '+91-9000000004', '08:00:00', '14:00:00', b'1'),
  ('Dr. Sameer Khan', 'Cardiology', 'sameer.khan@clinic.test', '$2a$10$noyLGrS.EOtCMFNvJwQMAuLQJUUX8tgnoxk4we5w2Tod1UCb1YQte', '+91-9000000005', '12:00:00', '18:00:00', b'1');

-- Exactly five sample patients
INSERT IGNORE INTO patient (name, email, password_hash, phone, date_of_birth, address) VALUES
  ('Anita Sharma', 'anita.sharma@example.test', '$2a$10$noyLGrS.EOtCMFNvJwQMAuLQJUUX8tgnoxk4we5w2Tod1UCb1YQte', '+91-9100000001', '1990-04-12', '12 Lake Road, Hyderabad'),
  ('Vikram Singh', 'vikram.singh@example.test', '$2a$10$noyLGrS.EOtCMFNvJwQMAuLQJUUX8tgnoxk4we5w2Tod1UCb1YQte', '+91-9100000002', '1985-09-30', '45 Park Street, Hyderabad'),
  ('Meera Iyer', 'meera.iyer@example.test', '$2a$10$noyLGrS.EOtCMFNvJwQMAuLQJUUX8tgnoxk4we5w2Tod1UCb1YQte', '+91-9100000003', '1996-01-22', '7 Temple Lane, Hyderabad'),
  ('Rohan Das', 'rohan.das@example.test', '$2a$10$noyLGrS.EOtCMFNvJwQMAuLQJUUX8tgnoxk4we5w2Tod1UCb1YQte', '+91-9100000004', '2001-07-05', '88 Hill View, Hyderabad'),
  ('Sneha Reddy', 'sneha.reddy@example.test', '$2a$10$noyLGrS.EOtCMFNvJwQMAuLQJUUX8tgnoxk4we5w2Tod1UCb1YQte', '+91-9100000005', '1978-12-18', '3 Garden Colony, Hyderabad');

-- Appointments: past ones (COMPLETED / NO_SHOW / CANCELLED) feed the reports; November 2026 ones are SCHEDULED.
INSERT INTO appointment (doctor_id, patient_id, start_time, end_time, status, reason)
SELECT d.id, p.id, '2026-08-04 10:00:00', '2026-08-04 10:30:00', 'COMPLETED', 'Chest pain follow-up' FROM doctor d, patient p
WHERE d.email = 'asha.verma@clinic.test' AND p.email = 'anita.sharma@example.test'
  AND NOT EXISTS (SELECT 1 FROM appointment x WHERE x.doctor_id = d.id AND x.start_time = '2026-08-04 10:00:00');

INSERT INTO appointment (doctor_id, patient_id, start_time, end_time, status, reason)
SELECT d.id, p.id, '2026-08-04 10:30:00', '2026-08-04 11:00:00', 'COMPLETED', 'Blood pressure check' FROM doctor d, patient p
WHERE d.email = 'asha.verma@clinic.test' AND p.email = 'vikram.singh@example.test'
  AND NOT EXISTS (SELECT 1 FROM appointment x WHERE x.doctor_id = d.id AND x.start_time = '2026-08-04 10:30:00');

INSERT INTO appointment (doctor_id, patient_id, start_time, end_time, status, reason)
SELECT d.id, p.id, '2026-08-11 11:00:00', '2026-08-11 11:30:00', 'COMPLETED', 'ECG review' FROM doctor d, patient p
WHERE d.email = 'asha.verma@clinic.test' AND p.email = 'meera.iyer@example.test'
  AND NOT EXISTS (SELECT 1 FROM appointment x WHERE x.doctor_id = d.id AND x.start_time = '2026-08-11 11:00:00');

INSERT INTO appointment (doctor_id, patient_id, start_time, end_time, status, reason)
SELECT d.id, p.id, '2026-09-08 09:30:00', '2026-09-08 10:00:00', 'COMPLETED', 'Skin rash' FROM doctor d, patient p
WHERE d.email = 'rahul.mehta@clinic.test' AND p.email = 'anita.sharma@example.test'
  AND NOT EXISTS (SELECT 1 FROM appointment x WHERE x.doctor_id = d.id AND x.start_time = '2026-09-08 09:30:00');

INSERT INTO appointment (doctor_id, patient_id, start_time, end_time, status, reason)
SELECT d.id, p.id, '2026-09-08 10:00:00', '2026-09-08 10:30:00', 'NO_SHOW', 'Acne consultation' FROM doctor d, patient p
WHERE d.email = 'rahul.mehta@clinic.test' AND p.email = 'rohan.das@example.test'
  AND NOT EXISTS (SELECT 1 FROM appointment x WHERE x.doctor_id = d.id AND x.start_time = '2026-09-08 10:00:00');

INSERT INTO appointment (doctor_id, patient_id, start_time, end_time, status, reason)
SELECT d.id, p.id, '2026-09-15 10:00:00', '2026-09-15 10:30:00', 'COMPLETED', 'Child vaccination' FROM doctor d, patient p
WHERE d.email = 'priya.nair@clinic.test' AND p.email = 'sneha.reddy@example.test'
  AND NOT EXISTS (SELECT 1 FROM appointment x WHERE x.doctor_id = d.id AND x.start_time = '2026-09-15 10:00:00');

INSERT INTO appointment (doctor_id, patient_id, start_time, end_time, status, reason)
SELECT d.id, p.id, '2026-09-22 14:00:00', '2026-09-22 14:30:00', 'COMPLETED', 'Cardiac check-up' FROM doctor d, patient p
WHERE d.email = 'asha.verma@clinic.test' AND p.email = 'anita.sharma@example.test'
  AND NOT EXISTS (SELECT 1 FROM appointment x WHERE x.doctor_id = d.id AND x.start_time = '2026-09-22 14:00:00');

INSERT INTO appointment (doctor_id, patient_id, start_time, end_time, status, reason)
SELECT d.id, p.id, '2026-10-06 09:00:00', '2026-10-06 09:30:00', 'CANCELLED', 'Knee pain' FROM doctor d, patient p
WHERE d.email = 'kiran.rao@clinic.test' AND p.email = 'vikram.singh@example.test'
  AND NOT EXISTS (SELECT 1 FROM appointment x WHERE x.doctor_id = d.id AND x.start_time = '2026-10-06 09:00:00');

INSERT INTO appointment (doctor_id, patient_id, start_time, end_time, status, reason)
SELECT d.id, p.id, '2026-11-03 10:00:00', '2026-11-03 10:30:00', 'SCHEDULED', 'Palpitations' FROM doctor d, patient p
WHERE d.email = 'asha.verma@clinic.test' AND p.email = 'rohan.das@example.test'
  AND NOT EXISTS (SELECT 1 FROM appointment x WHERE x.doctor_id = d.id AND x.start_time = '2026-11-03 10:00:00');

INSERT INTO appointment (doctor_id, patient_id, start_time, end_time, status, reason)
SELECT d.id, p.id, '2026-11-03 10:30:00', '2026-11-03 11:00:00', 'SCHEDULED', 'Medication review' FROM doctor d, patient p
WHERE d.email = 'asha.verma@clinic.test' AND p.email = 'anita.sharma@example.test'
  AND NOT EXISTS (SELECT 1 FROM appointment x WHERE x.doctor_id = d.id AND x.start_time = '2026-11-03 10:30:00');

INSERT INTO appointment (doctor_id, patient_id, start_time, end_time, status, reason)
SELECT d.id, p.id, '2026-11-04 11:00:00', '2026-11-04 11:30:00', 'SCHEDULED', 'Skin follow-up' FROM doctor d, patient p
WHERE d.email = 'rahul.mehta@clinic.test' AND p.email = 'anita.sharma@example.test'
  AND NOT EXISTS (SELECT 1 FROM appointment x WHERE x.doctor_id = d.id AND x.start_time = '2026-11-04 11:00:00');

INSERT INTO appointment (doctor_id, patient_id, start_time, end_time, status, reason)
SELECT d.id, p.id, '2026-11-05 10:30:00', '2026-11-05 11:00:00', 'SCHEDULED', 'Family consultation' FROM doctor d, patient p
WHERE d.email = 'priya.nair@clinic.test' AND p.email = 'vikram.singh@example.test'
  AND NOT EXISTS (SELECT 1 FROM appointment x WHERE x.doctor_id = d.id AND x.start_time = '2026-11-05 10:30:00');

INSERT INTO appointment (doctor_id, patient_id, start_time, end_time, status, reason)
SELECT d.id, p.id, '2026-11-06 09:00:00', '2026-11-06 09:30:00', 'SCHEDULED', 'Shoulder pain' FROM doctor d, patient p
WHERE d.email = 'kiran.rao@clinic.test' AND p.email = 'sneha.reddy@example.test'
  AND NOT EXISTS (SELECT 1 FROM appointment x WHERE x.doctor_id = d.id AND x.start_time = '2026-11-06 09:00:00');

-- Quick checks
SELECT id, name, email FROM patient ORDER BY id;
SELECT id, name, specialty FROM doctor ORDER BY id;
SELECT COUNT(*) AS appointment_count FROM appointment;
