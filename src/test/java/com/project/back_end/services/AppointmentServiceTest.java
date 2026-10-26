package com.project.back_end.services;

import com.project.back_end.dto.AppointmentRequest;
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
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;
import org.springframework.http.HttpStatus;
import org.springframework.test.util.ReflectionTestUtils;

import java.time.DayOfWeek;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class AppointmentServiceTest {

    @Mock AppointmentRepository appointmentRepository;
    @Mock DoctorRepository doctorRepository;
    @Mock PatientRepository patientRepository;

    AppointmentService service;
    Doctor doctor;
    Patient patient;
    AuthUser patientUser;

    @BeforeEach
    void setUp() {
        service = new AppointmentService(appointmentRepository, doctorRepository, patientRepository, 30);
        doctor = new Doctor();
        doctor.setName("Dr. Test");
        ReflectionTestUtils.setField(doctor, "id", 1L);
        patient = new Patient();
        patient.setName("Pat");
        ReflectionTestUtils.setField(patient, "id", 10L);
        patientUser = new AuthUser(10L, "pat@example.test", Role.PATIENT);

        when(patientRepository.findById(10L)).thenReturn(Optional.of(patient));
        when(doctorRepository.findByIdForUpdate(1L)).thenReturn(Optional.of(doctor));
        when(appointmentRepository.save(any(Appointment.class))).thenAnswer(i -> i.getArgument(0));
    }

    private static LocalDateTime weekday(int hour, int minute) {
        LocalDate d = LocalDate.now().plusDays(3);
        while (d.getDayOfWeek() == DayOfWeek.SATURDAY || d.getDayOfWeek() == DayOfWeek.SUNDAY) {
            d = d.plusDays(1);
        }
        return d.atTime(hour, minute);
    }

    private static void assertStatus(Throwable t, HttpStatus expected) {
        assertThat(t).isInstanceOf(ApiException.class);
        assertThat(((ApiException) t).getStatus()).isEqualTo(expected);
    }

    private Appointment appointment(LocalDateTime start, AppointmentStatus status) {
        Appointment a = new Appointment();
        a.setDoctor(doctor);
        a.setPatient(patient);
        a.setStartTime(start);
        a.setEndTime(start.plusMinutes(30));
        a.setStatus(status);
        ReflectionTestUtils.setField(a, "id", 100L);
        when(appointmentRepository.findByIdWithDetails(100L)).thenReturn(Optional.of(a));
        return a;
    }

    // ---------------------------------------------------------------- schedule rules

    @Test
    void scheduleRulesCoverWeekdaysHoursAndSlotBoundaries() {
        assertThat(service.isWithinSchedule(doctor, weekday(9, 0))).isTrue();
        assertThat(service.isWithinSchedule(doctor, weekday(16, 30))).isTrue();   // ends exactly 17:00
        assertThat(service.isWithinSchedule(doctor, weekday(17, 0))).isFalse();   // would end after 17:00
        assertThat(service.isWithinSchedule(doctor, weekday(8, 30))).isFalse();   // before opening
        assertThat(service.isWithinSchedule(doctor, weekday(10, 15))).isFalse();  // not on a 30-minute boundary

        LocalDate saturday = LocalDate.now().plusDays(1);
        while (saturday.getDayOfWeek() != DayOfWeek.SATURDAY) {
            saturday = saturday.plusDays(1);
        }
        assertThat(service.isWithinSchedule(doctor, saturday.atTime(10, 0))).isFalse();

        doctor.setActive(false);
        assertThat(service.isWithinSchedule(doctor, weekday(10, 0))).isFalse();
    }

    // ---------------------------------------------------------------- booking

    @Test
    void bookingSucceedsAndEndsThirtyMinutesLater() {
        LocalDateTime start = weekday(10, 0);
        var response = service.book(patientUser, new AppointmentRequest(1L, start, "  Check-up "));
        assertThat(response.status()).isEqualTo(AppointmentStatus.SCHEDULED);
        assertThat(response.endTime()).isEqualTo(start.plusMinutes(30));
        assertThat(response.reason()).isEqualTo("Check-up");
        verify(appointmentRepository).save(any(Appointment.class));
    }

    @Test
    void onlyPatientsCanBook() {
        AuthUser doctorUser = new AuthUser(1L, "d@clinic.test", Role.DOCTOR);
        assertThatThrownBy(() -> service.book(doctorUser, new AppointmentRequest(1L, weekday(10, 0), null)))
                .satisfies(t -> assertStatus(t, HttpStatus.FORBIDDEN));
    }

    @Test
    void pastTimesAreRejected() {
        assertThatThrownBy(() -> service.book(patientUser,
                new AppointmentRequest(1L, LocalDateTime.now().minusHours(1), null)))
                .satisfies(t -> assertStatus(t, HttpStatus.BAD_REQUEST));
        verify(appointmentRepository, never()).save(any());
    }

    @Test
    void unavailableTimesAndInactiveDoctorsAreRejected() {
        assertThatThrownBy(() -> service.book(patientUser, new AppointmentRequest(1L, weekday(20, 0), null)))
                .satisfies(t -> assertStatus(t, HttpStatus.CONFLICT));
        doctor.setActive(false);
        assertThatThrownBy(() -> service.book(patientUser, new AppointmentRequest(1L, weekday(10, 0), null)))
                .satisfies(t -> assertStatus(t, HttpStatus.CONFLICT));
    }

    @Test
    void overlappingBookingsAreRejectedForDoctorAndForPatient() {
        LocalDateTime start = weekday(10, 0);
        when(appointmentRepository.existsDoctorOverlap(anyLong(), any(), any())).thenReturn(true);
        assertThatThrownBy(() -> service.book(patientUser, new AppointmentRequest(1L, start, null)))
                .satisfies(t -> assertStatus(t, HttpStatus.CONFLICT));

        when(appointmentRepository.existsDoctorOverlap(anyLong(), any(), any())).thenReturn(false);
        when(appointmentRepository.existsPatientOverlap(anyLong(), any(), any())).thenReturn(true);
        assertThatThrownBy(() -> service.book(patientUser, new AppointmentRequest(1L, start, null)))
                .satisfies(t -> assertStatus(t, HttpStatus.CONFLICT));
        verify(appointmentRepository, never()).save(any());
    }

    @Test
    void unknownDoctorIs404() {
        when(doctorRepository.findByIdForUpdate(99L)).thenReturn(Optional.empty());
        assertThatThrownBy(() -> service.book(patientUser, new AppointmentRequest(99L, weekday(10, 0), null)))
                .satisfies(t -> assertStatus(t, HttpStatus.NOT_FOUND));
    }

    // ---------------------------------------------------------------- ownership and status changes

    @Test
    void anotherPatientCannotReadOrCancel() {
        appointment(weekday(10, 0), AppointmentStatus.SCHEDULED);
        AuthUser stranger = new AuthUser(11L, "other@example.test", Role.PATIENT);
        assertThatThrownBy(() -> service.getById(stranger, 100L)).satisfies(t -> assertStatus(t, HttpStatus.FORBIDDEN));
        assertThatThrownBy(() -> service.cancel(stranger, 100L)).satisfies(t -> assertStatus(t, HttpStatus.FORBIDDEN));
    }

    @Test
    void anotherDoctorCannotReadOrChange() {
        appointment(weekday(10, 0), AppointmentStatus.SCHEDULED);
        AuthUser otherDoctor = new AuthUser(2L, "d2@clinic.test", Role.DOCTOR);
        assertThatThrownBy(() -> service.getById(otherDoctor, 100L)).satisfies(t -> assertStatus(t, HttpStatus.FORBIDDEN));
        assertThatThrownBy(() -> service.updateStatus(otherDoctor, 100L, AppointmentStatus.CANCELLED))
                .satisfies(t -> assertStatus(t, HttpStatus.FORBIDDEN));
    }

    @Test
    void patientCanCancelWhenAtLeastTwoHoursAhead() {
        Appointment a = appointment(LocalDateTime.now().plusHours(5), AppointmentStatus.SCHEDULED);
        service.cancel(patientUser, 100L);
        assertThat(a.getStatus()).isEqualTo(AppointmentStatus.CANCELLED);
    }

    @Test
    void patientCannotCancelWithinTwoHours() {
        Appointment a = appointment(LocalDateTime.now().plusMinutes(30), AppointmentStatus.SCHEDULED);
        assertThatThrownBy(() -> service.cancel(patientUser, 100L)).satisfies(t -> assertStatus(t, HttpStatus.CONFLICT));
        assertThat(a.getStatus()).isEqualTo(AppointmentStatus.SCHEDULED);
    }

    @Test
    void finishedAppointmentsCannotChangeAgain() {
        appointment(LocalDateTime.now().minusDays(1), AppointmentStatus.COMPLETED);
        AuthUser doctorUser = new AuthUser(1L, "d@clinic.test", Role.DOCTOR);
        assertThatThrownBy(() -> service.updateStatus(doctorUser, 100L, AppointmentStatus.NO_SHOW))
                .satisfies(t -> assertStatus(t, HttpStatus.CONFLICT));
    }

    @Test
    void statusCannotGoBackToScheduled() {
        appointment(LocalDateTime.now().plusDays(1), AppointmentStatus.SCHEDULED);
        AuthUser admin = new AuthUser(1L, "admin@clinic.test", Role.ADMIN);
        assertThatThrownBy(() -> service.updateStatus(admin, 100L, AppointmentStatus.SCHEDULED))
                .satisfies(t -> assertStatus(t, HttpStatus.BAD_REQUEST));
    }

    @Test
    void listsAreRoleRestricted() {
        assertThatThrownBy(() -> service.listAll(patientUser)).satisfies(t -> assertStatus(t, HttpStatus.FORBIDDEN));
        assertThatThrownBy(() -> service.listForDoctor(patientUser)).satisfies(t -> assertStatus(t, HttpStatus.FORBIDDEN));
        AuthUser doctorUser = new AuthUser(1L, "d@clinic.test", Role.DOCTOR);
        assertThatThrownBy(() -> service.listForPatient(doctorUser)).satisfies(t -> assertStatus(t, HttpStatus.FORBIDDEN));
    }
}
