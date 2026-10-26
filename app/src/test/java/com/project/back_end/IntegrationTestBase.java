package com.project.back_end;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.project.back_end.models.Admin;
import com.project.back_end.models.Appointment;
import com.project.back_end.models.AppointmentStatus;
import com.project.back_end.models.Doctor;
import com.project.back_end.models.Patient;
import com.project.back_end.models.Role;
import com.project.back_end.repo.AdminRepository;
import com.project.back_end.repo.AppointmentRepository;
import com.project.back_end.repo.DoctorRepository;
import com.project.back_end.repo.PatientRepository;
import com.project.back_end.repo.PrescriptionRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.transaction.annotation.Transactional;

import java.time.DayOfWeek;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.Map;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Shared setup for integration tests: real Spring context, in-memory H2 database, real security.
 * Every test runs in a transaction that is rolled back, so tests never affect each other.
 * MongoDB is replaced by a mock so the tests do not need a Mongo server.
 */
@SpringBootTest
@AutoConfigureMockMvc
@Transactional
abstract class IntegrationTestBase {

    protected static final String PASSWORD = "Password@123";

    @Autowired protected MockMvc mvc;
    @Autowired protected ObjectMapper objectMapper;
    @Autowired protected AdminRepository adminRepository;
    @Autowired protected DoctorRepository doctorRepository;
    @Autowired protected PatientRepository patientRepository;
    @Autowired protected AppointmentRepository appointmentRepository;
    @Autowired protected PasswordEncoder passwordEncoder;

    @MockitoBean protected PrescriptionRepository prescriptionRepository;

    protected Admin saveAdmin(String email) {
        return adminRepository.save(new Admin("Test Admin", email, passwordEncoder.encode(PASSWORD)));
    }

    protected Doctor saveDoctor(String name, String specialty, String email) {
        Doctor d = new Doctor();
        d.setName(name);
        d.setSpecialty(specialty);
        d.setEmail(email);
        d.setPasswordHash(passwordEncoder.encode(PASSWORD));
        return doctorRepository.save(d);
    }

    protected Patient savePatient(String name, String email) {
        Patient p = new Patient();
        p.setName(name);
        p.setEmail(email);
        p.setPasswordHash(passwordEncoder.encode(PASSWORD));
        return patientRepository.save(p);
    }

    protected Appointment saveAppointment(Doctor d, Patient p, LocalDateTime start, AppointmentStatus status) {
        Appointment a = new Appointment();
        a.setDoctor(d);
        a.setPatient(p);
        a.setStartTime(start);
        a.setEndTime(start.plusMinutes(30));
        a.setStatus(status);
        return appointmentRepository.save(a);
    }

    /** A weekday a few days from now at the given hour (inside the default 09:00-17:00 doctor hours). */
    protected static LocalDateTime futureWeekday(int hour) {
        LocalDate d = LocalDate.now().plusDays(3);
        while (d.getDayOfWeek() == DayOfWeek.SATURDAY || d.getDayOfWeek() == DayOfWeek.SUNDAY) {
            d = d.plusDays(1);
        }
        return d.atTime(hour, 0);
    }

    protected String json(Object value) throws Exception {
        return objectMapper.writeValueAsString(value);
    }

    protected String login(String email, Role role) throws Exception {
        MvcResult result = mvc.perform(post("/api/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json(Map.of("email", email, "password", PASSWORD, "role", role.name()))))
                .andExpect(status().isOk())
                .andReturn();
        return objectMapper.readTree(result.getResponse().getContentAsString()).get("token").asText();
    }

    protected static String bearer(String token) {
        return "Bearer " + token;
    }
}
