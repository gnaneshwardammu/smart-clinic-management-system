package com.project.back_end.repo;

import com.project.back_end.models.Appointment;
import com.project.back_end.models.AppointmentStatus;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

public interface AppointmentRepository extends JpaRepository<Appointment, Long> {

    @Query("""
            select a from Appointment a
            join fetch a.doctor join fetch a.patient
            where a.id = :id
            """)
    Optional<Appointment> findByIdWithDetails(@Param("id") Long id);

    @Query("""
            select a from Appointment a
            join fetch a.doctor join fetch a.patient
            order by a.startTime desc
            """)
    List<Appointment> findAllWithDetails();

    @Query("""
            select a from Appointment a
            join fetch a.doctor join fetch a.patient
            where a.patient.id = :patientId
            order by a.startTime desc
            """)
    List<Appointment> findByPatientIdWithDetails(@Param("patientId") Long patientId);

    @Query("""
            select a from Appointment a
            join fetch a.doctor join fetch a.patient
            where a.doctor.id = :doctorId
            order by a.startTime asc
            """)
    List<Appointment> findByDoctorIdWithDetails(@Param("doctorId") Long doctorId);

    /** True if the doctor already has a non-cancelled appointment overlapping [start, end). */
    @Query("""
            select count(a) > 0 from Appointment a
            where a.doctor.id = :doctorId
              and a.status <> com.project.back_end.models.AppointmentStatus.CANCELLED
              and a.startTime < :end and a.endTime > :start
            """)
    boolean existsDoctorOverlap(@Param("doctorId") Long doctorId,
                                @Param("start") LocalDateTime start,
                                @Param("end") LocalDateTime end);

    /** True if the patient already has a non-cancelled appointment overlapping [start, end). */
    @Query("""
            select count(a) > 0 from Appointment a
            where a.patient.id = :patientId
              and a.status <> com.project.back_end.models.AppointmentStatus.CANCELLED
              and a.startTime < :end and a.endTime > :start
            """)
    boolean existsPatientOverlap(@Param("patientId") Long patientId,
                                 @Param("start") LocalDateTime start,
                                 @Param("end") LocalDateTime end);

    /** Non-cancelled appointments of a doctor within a time window (used to compute free slots). */
    @Query("""
            select a from Appointment a
            where a.doctor.id = :doctorId
              and a.status <> com.project.back_end.models.AppointmentStatus.CANCELLED
              and a.startTime >= :from and a.startTime < :to
            """)
    List<Appointment> findActiveByDoctorBetween(@Param("doctorId") Long doctorId,
                                                @Param("from") LocalDateTime from,
                                                @Param("to") LocalDateTime to);

    long countByDoctorId(Long doctorId);

    boolean existsByDoctorIdAndPatientId(Long doctorId, Long patientId);

    long countByStatus(AppointmentStatus status);

    long countByStartTimeGreaterThanEqualAndStartTimeLessThan(LocalDateTime from, LocalDateTime to);
}
