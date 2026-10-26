package com.project.back_end;

import com.project.back_end.models.Appointment;
import com.project.back_end.models.AppointmentStatus;
import com.project.back_end.models.Doctor;
import com.project.back_end.models.Patient;
import com.project.back_end.models.Role;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;

import java.time.DayOfWeek;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.LinkedHashMap;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/** Booking rules and, above all, ownership: nobody may see or change someone else's appointment. */
class AppointmentAccessTest extends IntegrationTestBase {

    private Doctor doctorA;
    private Doctor doctorB;
    private Patient patientA;
    private Patient patientB;
    private Appointment apptOfAWithDoctorA;
    private String tokenPatientA;
    private String tokenPatientB;
    private String tokenDoctorA;
    private String tokenDoctorB;

    @BeforeEach
    void setUp() throws Exception {
        doctorA = saveDoctor("Dr. A", "Cardiology", "doc.a@clinic.test");
        doctorB = saveDoctor("Dr. B", "Dermatology", "doc.b@clinic.test");
        patientA = savePatient("Patient A", "pat.a@example.test");
        patientB = savePatient("Patient B", "pat.b@example.test");
        apptOfAWithDoctorA = saveAppointment(doctorA, patientA, futureWeekday(10), AppointmentStatus.SCHEDULED);
        tokenPatientA = login("pat.a@example.test", Role.PATIENT);
        tokenPatientB = login("pat.b@example.test", Role.PATIENT);
        tokenDoctorA = login("doc.a@clinic.test", Role.DOCTOR);
        tokenDoctorB = login("doc.b@clinic.test", Role.DOCTOR);
    }

    private String bookingBody(Doctor d, LocalDateTime start) throws Exception {
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("doctorId", d.getId());
        body.put("startTime", start.toString());
        body.put("reason", "Check-up");
        return json(body);
    }

    // ---------------------------------------------------------------- ownership

    @Test
    void patientSeesOnlyOwnAppointments() throws Exception {
        mvc.perform(get("/api/appointments/my").header("Authorization", bearer(tokenPatientA)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(1))
                .andExpect(jsonPath("$[0].id").value(apptOfAWithDoctorA.getId()));
        mvc.perform(get("/api/appointments/my").header("Authorization", bearer(tokenPatientB)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(0));
    }

    @Test
    void patientCannotReadOrChangeAnotherPatientsAppointment() throws Exception {
        String url = "/api/appointments/" + apptOfAWithDoctorA.getId();
        mvc.perform(get(url).header("Authorization", bearer(tokenPatientB))).andExpect(status().isForbidden());
        mvc.perform(delete(url).header("Authorization", bearer(tokenPatientB))).andExpect(status().isForbidden());
        mvc.perform(put(url + "/status").header("Authorization", bearer(tokenPatientB))
                        .contentType(MediaType.APPLICATION_JSON).content(json(Map.of("status", "CANCELLED"))))
                .andExpect(status().isForbidden());
        assertThat(appointmentRepository.findById(apptOfAWithDoctorA.getId()).orElseThrow().getStatus())
                .isEqualTo(AppointmentStatus.SCHEDULED);
    }

    @Test
    void doctorSeesOnlyOwnAppointmentsAndCannotOpenAnothers() throws Exception {
        mvc.perform(get("/api/appointments/doctor/my").header("Authorization", bearer(tokenDoctorA)))
                .andExpect(jsonPath("$.length()").value(1))
                .andExpect(jsonPath("$[0].patientName").value("Patient A"));
        mvc.perform(get("/api/appointments/doctor/my").header("Authorization", bearer(tokenDoctorB)))
                .andExpect(jsonPath("$.length()").value(0));

        String url = "/api/appointments/" + apptOfAWithDoctorA.getId();
        mvc.perform(get(url).header("Authorization", bearer(tokenDoctorB))).andExpect(status().isForbidden());
        mvc.perform(get(url).header("Authorization", bearer(tokenDoctorA))).andExpect(status().isOk());
    }

    @Test
    void roleSpecificListsAreNotInterchangeable() throws Exception {
        mvc.perform(get("/api/appointments/doctor/my").header("Authorization", bearer(tokenPatientA)))
                .andExpect(status().isForbidden());
        mvc.perform(get("/api/appointments/my").header("Authorization", bearer(tokenDoctorA)))
                .andExpect(status().isForbidden());
    }

    @Test
    void adminSeesAllAppointments() throws Exception {
        saveAdmin("boss@clinic.test");
        String adminToken = login("boss@clinic.test", Role.ADMIN);
        saveAppointment(doctorB, patientB, futureWeekday(11), AppointmentStatus.SCHEDULED);
        mvc.perform(get("/api/appointments").header("Authorization", bearer(adminToken)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(2));
    }

    // ---------------------------------------------------------------- booking rules

    @Test
    void patientCanBookAFreeSlot() throws Exception {
        LocalDateTime slot = futureWeekday(14);
        mvc.perform(post("/api/appointments").header("Authorization", bearer(tokenPatientB))
                        .contentType(MediaType.APPLICATION_JSON).content(bookingBody(doctorA, slot)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.status").value("SCHEDULED"))
                .andExpect(jsonPath("$.patientName").value("Patient B"));
    }

    @Test
    void conflictingBookingForTheSameDoctorIsRejected() throws Exception {
        // doctorA is already booked at futureWeekday(10) by patientA
        mvc.perform(post("/api/appointments").header("Authorization", bearer(tokenPatientB))
                        .contentType(MediaType.APPLICATION_JSON).content(bookingBody(doctorA, futureWeekday(10))))
                .andExpect(status().isConflict());
    }

    @Test
    void bookingInThePastIsRejected() throws Exception {
        mvc.perform(post("/api/appointments").header("Authorization", bearer(tokenPatientB))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(bookingBody(doctorA, LocalDateTime.now().minusDays(1))))
                .andExpect(status().isBadRequest());
    }

    @Test
    void bookingOnAWeekendIsRejected() throws Exception {
        LocalDate saturday = LocalDate.now().plusDays(1);
        while (saturday.getDayOfWeek() != DayOfWeek.SATURDAY) {
            saturday = saturday.plusDays(1);
        }
        mvc.perform(post("/api/appointments").header("Authorization", bearer(tokenPatientB))
                        .contentType(MediaType.APPLICATION_JSON).content(bookingBody(doctorA, saturday.atTime(10, 0))))
                .andExpect(status().isConflict());
    }

    @Test
    void bookingWithUnknownDoctorReturns404AndDoctorsCannotBook() throws Exception {
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("doctorId", 999999L);
        body.put("startTime", futureWeekday(10).toString());
        mvc.perform(post("/api/appointments").header("Authorization", bearer(tokenPatientB))
                        .contentType(MediaType.APPLICATION_JSON).content(json(body)))
                .andExpect(status().isNotFound());
        mvc.perform(post("/api/appointments").header("Authorization", bearer(tokenDoctorA))
                        .contentType(MediaType.APPLICATION_JSON).content(bookingBody(doctorA, futureWeekday(15))))
                .andExpect(status().isForbidden());
    }

    // ---------------------------------------------------------------- status rules

    @Test
    void patientCanCancelOwnFutureAppointment() throws Exception {
        mvc.perform(delete("/api/appointments/" + apptOfAWithDoctorA.getId())
                        .header("Authorization", bearer(tokenPatientA)))
                .andExpect(status().isNoContent());
        assertThat(appointmentRepository.findById(apptOfAWithDoctorA.getId()).orElseThrow().getStatus())
                .isEqualTo(AppointmentStatus.CANCELLED);
        // a cancelled slot can be booked again
        mvc.perform(post("/api/appointments").header("Authorization", bearer(tokenPatientB))
                        .contentType(MediaType.APPLICATION_JSON).content(bookingBody(doctorA, futureWeekday(10))))
                .andExpect(status().isCreated());
    }

    @Test
    void doctorCannotCompleteAnAppointmentThatHasNotStarted() throws Exception {
        mvc.perform(put("/api/appointments/" + apptOfAWithDoctorA.getId() + "/status")
                        .header("Authorization", bearer(tokenDoctorA))
                        .contentType(MediaType.APPLICATION_JSON).content(json(Map.of("status", "COMPLETED"))))
                .andExpect(status().isConflict());
    }

    @Test
    void doctorCanCompleteAPastAppointmentAndItThenBecomesFinal() throws Exception {
        Appointment past = saveAppointment(doctorA, patientB, LocalDateTime.now().minusDays(2),
                AppointmentStatus.SCHEDULED);
        String url = "/api/appointments/" + past.getId() + "/status";
        mvc.perform(put(url).header("Authorization", bearer(tokenDoctorA))
                        .contentType(MediaType.APPLICATION_JSON).content(json(Map.of("status", "COMPLETED"))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("COMPLETED"));
        mvc.perform(put(url).header("Authorization", bearer(tokenDoctorA))
                        .contentType(MediaType.APPLICATION_JSON).content(json(Map.of("status", "NO_SHOW"))))
                .andExpect(status().isConflict());
    }

    @Test
    void patientsMayOnlyCancelNotComplete() throws Exception {
        mvc.perform(put("/api/appointments/" + apptOfAWithDoctorA.getId() + "/status")
                        .header("Authorization", bearer(tokenPatientA))
                        .contentType(MediaType.APPLICATION_JSON).content(json(Map.of("status", "COMPLETED"))))
                .andExpect(status().isForbidden());
    }
}
