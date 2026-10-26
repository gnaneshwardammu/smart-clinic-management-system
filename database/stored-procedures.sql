-- =====================================================================
-- Smart Clinic Management System - REPORTING STORED PROCEDURES
-- Run in MySQL Workbench (whole script: Ctrl+Shift+Enter). Safe to re-run.
-- Requires MySQL 8.0+ (uses window functions).
--
-- Definition used by the "distinct patients" reports:
--   appointments with status CANCELLED are ignored; a patient who visited the same doctor
--   several times is counted ONCE.
-- Tie handling: if several doctors share the highest distinct-patient count, ALL of them
--   are returned (one row each), ordered by doctor name.
-- =====================================================================
USE capstone;

DELIMITER $$

DROP PROCEDURE IF EXISTS GetDailyAppointmentReportByDoctor$$
CREATE PROCEDURE GetDailyAppointmentReportByDoctor(
    IN p_doctor_id   BIGINT,
    IN p_report_date DATE
)
BEGIN
    -- All appointments (any status) of one doctor on one calendar day, with patient details.
    SELECT a.id          AS appointment_id,
           a.start_time,
           a.end_time,
           a.status,
           a.reason,
           d.name        AS doctor_name,
           p.id          AS patient_id,
           p.name        AS patient_name,
           p.email       AS patient_email,
           p.phone       AS patient_phone
    FROM appointment a
    JOIN doctor  d ON d.id = a.doctor_id
    JOIN patient p ON p.id = a.patient_id
    WHERE a.doctor_id  = p_doctor_id
      AND a.start_time >= p_report_date
      AND a.start_time <  DATE_ADD(p_report_date, INTERVAL 1 DAY)
    ORDER BY a.start_time;
END$$

DROP PROCEDURE IF EXISTS GetDoctorWithMostPatientsByMonth$$
CREATE PROCEDURE GetDoctorWithMostPatientsByMonth(
    IN p_year  INT,
    IN p_month INT
)
BEGIN
    DECLARE v_start DATE;

    IF p_month < 1 OR p_month > 12 OR p_year < 1900 OR p_year > 2200 THEN
        SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT = 'Invalid year or month';
    END IF;
    SET v_start = STR_TO_DATE(CONCAT(p_year, '-', p_month, '-01'), '%Y-%m-%d');

    SELECT ranked.doctor_id, ranked.doctor_name, ranked.specialty, ranked.distinct_patients
    FROM (
        SELECT d.id   AS doctor_id,
               d.name AS doctor_name,
               d.specialty,
               COUNT(DISTINCT a.patient_id) AS distinct_patients,
               RANK() OVER (ORDER BY COUNT(DISTINCT a.patient_id) DESC) AS rnk
        FROM doctor d
        JOIN appointment a ON a.doctor_id = d.id
        WHERE a.status <> 'CANCELLED'
          AND a.start_time >= v_start
          AND a.start_time <  DATE_ADD(v_start, INTERVAL 1 MONTH)
        GROUP BY d.id, d.name, d.specialty
    ) ranked
    WHERE ranked.rnk = 1
    ORDER BY ranked.doctor_name;
END$$

DROP PROCEDURE IF EXISTS GetDoctorWithMostPatientsByYear$$
CREATE PROCEDURE GetDoctorWithMostPatientsByYear(
    IN p_year INT
)
BEGIN
    DECLARE v_start DATE;

    IF p_year < 1900 OR p_year > 2200 THEN
        SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT = 'Invalid year';
    END IF;
    SET v_start = STR_TO_DATE(CONCAT(p_year, '-01-01'), '%Y-%m-%d');

    SELECT ranked.doctor_id, ranked.doctor_name, ranked.specialty, ranked.distinct_patients
    FROM (
        SELECT d.id   AS doctor_id,
               d.name AS doctor_name,
               d.specialty,
               COUNT(DISTINCT a.patient_id) AS distinct_patients,
               RANK() OVER (ORDER BY COUNT(DISTINCT a.patient_id) DESC) AS rnk
        FROM doctor d
        JOIN appointment a ON a.doctor_id = d.id
        WHERE a.status <> 'CANCELLED'
          AND a.start_time >= v_start
          AND a.start_time <  DATE_ADD(v_start, INTERVAL 1 YEAR)
        GROUP BY d.id, d.name, d.specialty
    ) ranked
    WHERE ranked.rnk = 1
    ORDER BY ranked.doctor_name;
END$$

DELIMITER ;

-- ---------------------------------------------------------------------
-- Example calls that match the sample data (run each separately and capture the output):
--   CALL GetDailyAppointmentReportByDoctor(1, '2026-08-04');   -- Dr. Asha Verma, 2 appointments
--   CALL GetDoctorWithMostPatientsByMonth(2026, 8);            -- Dr. Asha Verma, 3 distinct patients
--   CALL GetDoctorWithMostPatientsByYear(2026);                -- Dr. Asha Verma, 4 distinct patients
-- ---------------------------------------------------------------------
