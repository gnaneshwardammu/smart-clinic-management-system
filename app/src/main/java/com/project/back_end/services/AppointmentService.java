package com.project.back_end.services;

import com.project.back_end.dto.AppointmentRequest;
import com.project.back_end.dto.AppointmentResponse;
import com.project.back_end.exception.ApiException;
import com.project.back_end.models.Appointment;
import com.project.back_end.models.AppointmentStatus;
import com.project.back_end.models.Doctor;
import com.project.back_end.models.Patient;
import com.project.back_end.models.Role;
import com.project.back_end.repo.AppointmentRepository;
import com.project.back_end.repo.DoctorRepository;
import com.project.back_end.repo.PatientRepository;
import com.project.back_end.security.AuthUser;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.DayOfWeek;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.util.ArrayList;
import java.util.List;

/**
 * Appointment business rules (see README "Business rules"):
 * - Every appointment lasts app.appointment.duration-minutes (default 30).
 * - A doctor can be booked Monday-Friday inside availableFrom..availableTo, on slot boundaries.
 * - A slot cannot overlap another non-cancelled appointment of the same doctor, nor of the same patient.
 * - Patients may cancel only their own SCHEDULED appointments, at least 2 hours before the start.
 * - Doctors may complete / mark no-show / cancel only their own appointments (complete/no-show only once started).
 * - Finished appointments (anything other than SCHEDULED) can never change again.
 */
@Service
@Transactional(readOnly = true)
public class AppointmentService {

    public static final long CANCEL_NOTICE_HOURS = 2;

    private final AppointmentRepository appointmentRepository;
    private final DoctorRepository doctorRepository;
    private final PatientRepository patientRepository;
    private final long durationMinutes;

    public AppointmentService(AppointmentRepository appointmentRepository,
                              DoctorRepository doctorRepository,
                              PatientRepository patientRepository,
                              @Value("${app.appointment.duration-minutes:30}") long durationMinutes) {
        this.appointmentRepository = appointmentRepository;
        this.doctorRepository = doctorRepository;
        this.patientRepository = patientRepository;
        this.durationMinutes = durationMinutes;
    }

    public long getDurationMinutes() {
        return durationMinutes;
    }

    // ------------------------------------------------------------------ availability

    /** True if the doctor works at this moment: active, weekday, inside working hours, on a slot boundary. */
    public boolean isWithinSchedule(Doctor doctor, LocalDateTime start) {
        if (!doctor.isActive()) {
            return false;
        }
        DayOfWeek day = start.getDayOfWeek();
        if (day == DayOfWeek.SATURDAY || day == DayOfWeek.SUNDAY) {
            return false;
        }
        if (start.getSecond() != 0 || start.getNano() != 0 || start.getMinute() % durationMinutes != 0) {
            return false;
        }
        LocalDateTime end = start.plusMinutes(durationMinutes);
        if (!end.toLocalDate().equals(start.toLocalDate())) {
            return false;
        }
        return !start.toLocalTime().isBefore(doctor.getAvailableFrom())
                && !end.toLocalTime().isAfter(doctor.getAvailableTo());
    }

    /** True if the slot is in the future, inside the doctor's schedule and not already taken. */
    public boolean isFree(Doctor doctor, LocalDateTime start) {
        return start.isAfter(LocalDateTime.now())
                && isWithinSchedule(doctor, start)
                && !appointmentRepository.existsDoctorOverlap(doctor.getId(), start, start.plusMinutes(durationMinutes));
    }

    /** All free slot start times of a doctor on one date. */
    public List<LocalTime> availableSlots(Long doctorId, LocalDate date) {
        Doctor doctor = doctorRepository.findById(doctorId)
                .orElseThrow(() -> ApiException.notFound("Doctor not found with id " + doctorId));
        List<LocalTime> slots = new ArrayList<>();
        DayOfWeek day = date.getDayOfWeek();
        if (!doctor.isActive() || day == DayOfWeek.SATURDAY || day == DayOfWeek.SUNDAY) {
            return slots;
        }
        List<Appointment> booked = appointmentRepository.findActiveByDoctorBetween(
                doctorId, date.atStartOfDay(), date.plusDays(1).atStartOfDay());

        LocalTime t = doctor.getAvailableFrom();
        int remainder = t.getMinute() % (int) durationMinutes;
        if (remainder != 0 || t.getSecond() != 0 || t.getNano() != 0) {
            t = t.withSecond(0).withNano(0).plusMinutes(durationMinutes - remainder);
        }
        LocalDateTime now = LocalDateTime.now();
        while (true) {
            LocalTime slotEnd = t.plusMinutes(durationMinutes);
            if (!slotEnd.isAfter(t) || slotEnd.isAfter(doctor.getAvailableTo())) {
                break;
            }
            final LocalDateTime slotStart = date.atTime(t);
            final LocalDateTime slotStop = date.atTime(slotEnd);
            boolean taken = booked.stream()
                    .anyMatch(b -> b.getStartTime().isBefore(slotStop) && b.getEndTime().isAfter(slotStart));
            if (slotStart.isAfter(now) && !taken) {
                slots.add(t);
            }
            t = slotEnd;
        }
        return slots;
    }

    // ------------------------------------------------------------------ booking

    @Transactional
    public AppointmentResponse book(AuthUser user, AppointmentRequest request) {
        if (user.role() != Role.PATIENT) {
            throw ApiException.forbidden("Only patients can book appointments");
        }
        Patient patient = patientRepository.findById(user.id())
                .orElseThrow(() -> ApiException.unauthorized("Account no longer exists"));
        // Row lock: concurrent bookings for the same doctor are processed one at a time.
        Doctor doctor = doctorRepository.findByIdForUpdate(request.doctorId())
                .orElseThrow(() -> ApiException.notFound("Doctor not found with id " + request.doctorId()));

        LocalDateTime start = request.startTime();
        if (start == null || !start.isAfter(LocalDateTime.now())) {
            throw ApiException.badRequest("The appointment time must be in the future");
        }
        if (!doctor.isActive()) {
            throw ApiException.conflict("This doctor is not currently accepting appointments");
        }
        if (!isWithinSchedule(doctor, start)) {
            throw ApiException.conflict("The doctor is not available at that time. Doctors work Monday-Friday "
                    + "within their listed hours, in " + durationMinutes + "-minute slots");
        }
        LocalDateTime end = start.plusMinutes(durationMinutes);
        if (appointmentRepository.existsDoctorOverlap(doctor.getId(), start, end)) {
            throw ApiException.conflict("That time slot is already booked for this doctor");
        }
        if (appointmentRepository.existsPatientOverlap(patient.getId(), start, end)) {
            throw ApiException.conflict("You already have an appointment at that time");
        }

        Appointment appointment = new Appointment();
        appointment.setDoctor(doctor);
        appointment.setPatient(patient);
        appointment.setStartTime(start);
        appointment.setEndTime(end);
        appointment.setStatus(AppointmentStatus.SCHEDULED);
        appointment.setReason(request.reason() == null || request.reason().isBlank() ? null : request.reason().trim());
        return toResponse(appointmentRepository.save(appointment));
    }

    // ------------------------------------------------------------------ reading

    public AppointmentResponse getById(AuthUser user, Long id) {
        Appointment appointment = load(id);
        assertCanAccess(user, appointment);
        return toResponse(appointment);
    }

    public List<AppointmentResponse> listForPatient(AuthUser user) {
        if (user.role() != Role.PATIENT) {
            throw ApiException.forbidden("Only patients have personal appointment lists");
        }
        return appointmentRepository.findByPatientIdWithDetails(user.id()).stream().map(this::toResponse).toList();
    }

    public List<AppointmentResponse> listForDoctor(AuthUser user) {
        if (user.role() != Role.DOCTOR) {
            throw ApiException.forbidden("Only doctors have a doctor appointment list");
        }
        return appointmentRepository.findByDoctorIdWithDetails(user.id()).stream().map(this::toResponse).toList();
    }

    public List<AppointmentResponse> listAll(AuthUser user) {
        if (user.role() != Role.ADMIN) {
            throw ApiException.forbidden("Only administrators can list all appointments");
        }
        return appointmentRepository.findAllWithDetails().stream().map(this::toResponse).toList();
    }

    // ------------------------------------------------------------------ status changes

    @Transactional
    public AppointmentResponse updateStatus(AuthUser user, Long id, AppointmentStatus newStatus) {
        Appointment appointment = load(id);
        assertCanAccess(user, appointment);

        if (newStatus == AppointmentStatus.SCHEDULED) {
            throw ApiException.badRequest("An appointment cannot be set back to SCHEDULED");
        }
        if (appointment.getStatus() != AppointmentStatus.SCHEDULED) {
            throw ApiException.conflict("This appointment is already " + appointment.getStatus()
                    + " and can no longer be changed");
        }

        LocalDateTime now = LocalDateTime.now();
        if (user.role() == Role.PATIENT) {
            if (newStatus != AppointmentStatus.CANCELLED) {
                throw ApiException.forbidden("Patients can only cancel their appointments");
            }
            if (now.plusHours(CANCEL_NOTICE_HOURS).isAfter(appointment.getStartTime())) {
                throw ApiException.conflict("Appointments can only be cancelled at least "
                        + CANCEL_NOTICE_HOURS + " hours before they start");
            }
        } else if (user.role() == Role.DOCTOR) {
            boolean finishing = newStatus == AppointmentStatus.COMPLETED || newStatus == AppointmentStatus.NO_SHOW;
            if (finishing && appointment.getStartTime().isAfter(now)) {
                throw ApiException.conflict("An appointment cannot be marked " + newStatus + " before it starts");
            }
        }

        appointment.setStatus(newStatus);
        return toResponse(appointmentRepository.save(appointment));
    }

    /** Cancel = status CANCELLED, with the same rules as updateStatus. */
    @Transactional
    public void cancel(AuthUser user, Long id) {
        updateStatus(user, id, AppointmentStatus.CANCELLED);
    }

    // ------------------------------------------------------------------ helpers

    private Appointment load(Long id) {
        return appointmentRepository.findByIdWithDetails(id)
                .orElseThrow(() -> ApiException.notFound("Appointment not found with id " + id));
    }

    /** Ownership check: admin sees all, a doctor only their own, a patient only their own. */
    private void assertCanAccess(AuthUser user, Appointment appointment) {
        boolean allowed = switch (user.role()) {
            case ADMIN -> true;
            case DOCTOR -> appointment.getDoctor().getId().equals(user.id());
            case PATIENT -> appointment.getPatient().getId().equals(user.id());
        };
        if (!allowed) {
            throw ApiException.forbidden("You do not have access to this appointment");
        }
    }

    private AppointmentResponse toResponse(Appointment a) {
        return new AppointmentResponse(a.getId(),
                a.getDoctor().getId(), a.getDoctor().getName(), a.getDoctor().getSpecialty(),
                a.getPatient().getId(), a.getPatient().getName(), a.getPatient().getEmail(), a.getPatient().getPhone(),
                a.getStartTime(), a.getEndTime(), a.getStatus(), a.getReason());
    }
}
