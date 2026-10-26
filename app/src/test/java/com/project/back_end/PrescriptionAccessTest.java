package com.project.back_end;

import com.project.back_end.models.Appointment;
import com.project.back_end.models.AppointmentStatus;
import com.project.back_end.models.Doctor;
import com.project.back_end.models.Patient;
import com.project.back_end.models.Prescription;
import com.project.back_end.models.Role;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;

import java.util.List;
import java.util.Map;
import java.util.Optional;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/** Prescription authorization through the real security chain; MongoDB itself is mocked. */
class PrescriptionAccessTest extends IntegrationTestBase {

    private Doctor doctorA;
    private Doctor doctorB;
    private Patient patientA;
    private Patient patientB;
    private Appointment appointment;
    private String tokenDoctorA;
    private String tokenDoctorB;
    private String tokenPatientA;
    private String tokenPatientB;
    private Prescription stored;

    @BeforeEach
    void setUp() throws Exception {
        doctorA = saveDoctor("Dr. A", "Cardiology", "doc.a@clinic.test");
        doctorB = saveDoctor("Dr. B", "Dermatology", "doc.b@clinic.test");
        patientA = savePatient("Patient A", "pat.a@example.test");
        patientB = savePatient("Patient B", "pat.b@example.test");
        appointment = saveAppointment(doctorA, patientA, futureWeekday(10), AppointmentStatus.SCHEDULED);
        tokenDoctorA = login("doc.a@clinic.test", Role.DOCTOR);
        tokenDoctorB = login("doc.b@clinic.test", Role.DOCTOR);
        tokenPatientA = login("pat.a@example.test", Role.PATIENT);
        tokenPatientB = login("pat.b@example.test", Role.PATIENT);

        stored = new Prescription();
        stored.setId("rx1");
        stored.setDoctorId(doctorA.getId());
        stored.setPatientId(patientA.getId());
        stored.setAppointmentId(appointment.getId());
        stored.setMedicines(List.of(new Prescription.Medicine("Aspirin", "75 mg", "Once daily", "30 days")));
        when(prescriptionRepository.findById("rx1")).thenReturn(Optional.of(stored));
        when(prescriptionRepository.save(any(Prescription.class))).thenAnswer(i -> i.getArgument(0));
    }

    private String rxBody(Long appointmentId) throws Exception {
        return json(Map.of("appointmentId", appointmentId,
                "medicines", List.of(Map.of("name", "Aspirin", "dosage", "75 mg",
                        "frequency", "Once daily", "duration", "30 days")),
                "instructions", "After food"));
    }

    @Test
    void doctorOfTheAppointmentCanCreateAPrescription() throws Exception {
        mvc.perform(post("/api/prescriptions").header("Authorization", bearer(tokenDoctorA))
                        .contentType(MediaType.APPLICATION_JSON).content(rxBody(appointment.getId())))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.patientId").value(patientA.getId()))
                .andExpect(jsonPath("$.doctorId").value(doctorA.getId()));
    }

    @Test
    void anotherDoctorCannotPrescribeForSomeoneElsesAppointment() throws Exception {
        mvc.perform(post("/api/prescriptions").header("Authorization", bearer(tokenDoctorB))
                        .contentType(MediaType.APPLICATION_JSON).content(rxBody(appointment.getId())))
                .andExpect(status().isForbidden());
    }

    @Test
    void patientsCannotCreatePrescriptions() throws Exception {
        mvc.perform(post("/api/prescriptions").header("Authorization", bearer(tokenPatientA))
                        .contentType(MediaType.APPLICATION_JSON).content(rxBody(appointment.getId())))
                .andExpect(status().isForbidden());
    }

    @Test
    void invalidPrescriptionReturns400() throws Exception {
        mvc.perform(post("/api/prescriptions").header("Authorization", bearer(tokenDoctorA))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json(Map.of("appointmentId", appointment.getId(), "medicines", List.of()))))
                .andExpect(status().isBadRequest());
    }

    @Test
    void onlyTheRightPatientAndDoctorCanReadAPrescription() throws Exception {
        mvc.perform(get("/api/prescriptions/rx1").header("Authorization", bearer(tokenPatientA))).andExpect(status().isOk());
        mvc.perform(get("/api/prescriptions/rx1").header("Authorization", bearer(tokenDoctorA))).andExpect(status().isOk());
        mvc.perform(get("/api/prescriptions/rx1").header("Authorization", bearer(tokenPatientB))).andExpect(status().isForbidden());
        mvc.perform(get("/api/prescriptions/rx1").header("Authorization", bearer(tokenDoctorB))).andExpect(status().isForbidden());
        mvc.perform(get("/api/prescriptions/rx1")).andExpect(status().isUnauthorized());
    }

    @Test
    void patientCannotListAnotherPatientsPrescriptions() throws Exception {
        mvc.perform(get("/api/prescriptions/patient/" + patientA.getId())
                        .header("Authorization", bearer(tokenPatientB)))
                .andExpect(status().isForbidden());
        when(prescriptionRepository.findByPatientIdOrderByCreatedAtDesc(patientA.getId())).thenReturn(List.of(stored));
        mvc.perform(get("/api/prescriptions/patient/" + patientA.getId())
                        .header("Authorization", bearer(tokenPatientA)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(1));
    }
}
