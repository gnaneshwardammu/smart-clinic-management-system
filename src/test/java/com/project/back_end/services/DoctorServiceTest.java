package com.project.back_end.services;

import com.project.back_end.dto.DoctorDetailResponse;
import com.project.back_end.dto.DoctorRequest;
import com.project.back_end.exception.ApiException;
import com.project.back_end.models.Doctor;
import com.project.back_end.repo.AppointmentRepository;
import com.project.back_end.repo.DoctorRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;
import org.springframework.http.HttpStatus;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.util.ReflectionTestUtils;

import java.time.LocalTime;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class DoctorServiceTest {

    @Mock DoctorRepository doctorRepository;
    @Mock AppointmentRepository appointmentRepository;
    @Mock AppointmentService appointmentService;
    @Mock PasswordEncoder passwordEncoder;

    DoctorService service;

    @BeforeEach
    void setUp() {
        service = new DoctorService(doctorRepository, appointmentRepository, appointmentService, passwordEncoder);
        when(doctorRepository.save(any(Doctor.class))).thenAnswer(i -> i.getArgument(0));
        when(passwordEncoder.encode("Secret123")).thenReturn("HASHED");
    }

    private DoctorRequest request(String email, String password, LocalTime from, LocalTime to) {
        return new DoctorRequest("Dr. Test", "Cardiology", email, password, "+91-9000000000", from, to, true);
    }

    private static void assertStatus(Throwable t, HttpStatus expected) {
        assertThat(t).isInstanceOf(ApiException.class);
        assertThat(((ApiException) t).getStatus()).isEqualTo(expected);
    }

    @Test
    void createHashesPasswordAndNormalizesEmail() {
        DoctorDetailResponse created = service.create(request("  Test@Clinic.TEST ", "Secret123",
                LocalTime.of(9, 0), LocalTime.of(17, 0)));
        ArgumentCaptor<Doctor> captor = ArgumentCaptor.forClass(Doctor.class);
        verify(doctorRepository).save(captor.capture());
        assertThat(captor.getValue().getPasswordHash()).isEqualTo("HASHED");
        assertThat(captor.getValue().getEmail()).isEqualTo("test@clinic.test");
        assertThat(created.email()).isEqualTo("test@clinic.test");
    }

    @Test
    void createRejectsDuplicateEmail() {
        when(doctorRepository.existsByEmailIgnoreCase("test@clinic.test")).thenReturn(true);
        assertThatThrownBy(() -> service.create(request("test@clinic.test", "Secret123",
                LocalTime.of(9, 0), LocalTime.of(17, 0))))
                .satisfies(t -> assertStatus(t, HttpStatus.CONFLICT));
        verify(doctorRepository, never()).save(any());
    }

    @Test
    void createRejectsMissingPasswordAndBadHours() {
        assertThatThrownBy(() -> service.create(request("a@clinic.test", null, LocalTime.of(9, 0), LocalTime.of(17, 0))))
                .satisfies(t -> assertStatus(t, HttpStatus.BAD_REQUEST));
        assertThatThrownBy(() -> service.create(request("a@clinic.test", "Secret123", LocalTime.of(17, 0), LocalTime.of(9, 0))))
                .satisfies(t -> assertStatus(t, HttpStatus.BAD_REQUEST));
    }

    @Test
    void updateWithoutPasswordKeepsOldHash() {
        Doctor existing = new Doctor();
        existing.setEmail("old@clinic.test");
        existing.setPasswordHash("OLDHASH");
        ReflectionTestUtils.setField(existing, "id", 5L);
        when(doctorRepository.findById(5L)).thenReturn(Optional.of(existing));
        when(doctorRepository.findByEmailIgnoreCase("old@clinic.test")).thenReturn(Optional.of(existing));

        service.update(5L, request("old@clinic.test", null, LocalTime.of(9, 0), LocalTime.of(17, 0)));
        assertThat(existing.getPasswordHash()).isEqualTo("OLDHASH");
    }

    @Test
    void updateRejectsEmailOwnedByAnotherDoctor() {
        Doctor target = new Doctor();
        ReflectionTestUtils.setField(target, "id", 5L);
        Doctor other = new Doctor();
        ReflectionTestUtils.setField(other, "id", 6L);
        when(doctorRepository.findById(5L)).thenReturn(Optional.of(target));
        when(doctorRepository.findByEmailIgnoreCase("taken@clinic.test")).thenReturn(Optional.of(other));
        assertThatThrownBy(() -> service.update(5L, request("taken@clinic.test", null,
                LocalTime.of(9, 0), LocalTime.of(17, 0))))
                .satisfies(t -> assertStatus(t, HttpStatus.CONFLICT));
    }

    @Test
    void missingDoctorIs404() {
        when(doctorRepository.findById(99L)).thenReturn(Optional.empty());
        assertThatThrownBy(() -> service.getPublic(99L)).satisfies(t -> assertStatus(t, HttpStatus.NOT_FOUND));
    }

    @Test
    void deleteIsBlockedWhenDoctorHasAppointments() {
        Doctor d = new Doctor();
        when(doctorRepository.findById(7L)).thenReturn(Optional.of(d));
        when(appointmentRepository.countByDoctorId(7L)).thenReturn(3L);
        assertThatThrownBy(() -> service.delete(7L)).satisfies(t -> assertStatus(t, HttpStatus.CONFLICT));
        verify(doctorRepository, never()).delete(any());
    }

    @Test
    void deleteWorksWithoutAppointments() {
        Doctor d = new Doctor();
        when(doctorRepository.findById(7L)).thenReturn(Optional.of(d));
        when(appointmentRepository.countByDoctorId(7L)).thenReturn(0L);
        service.delete(7L);
        verify(doctorRepository).delete(d);
    }

    @Test
    void searchTreatsNullFiltersAsEmpty() {
        when(doctorRepository.search("", "")).thenReturn(List.of());
        assertThat(service.search(null, "  ")).isEmpty();
        verify(doctorRepository).search("", "");
    }
}
