package com.project.back_end;

import com.project.back_end.models.AppointmentStatus;
import com.project.back_end.models.Doctor;
import com.project.back_end.models.Patient;
import com.project.back_end.models.Role;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;

import java.time.LocalDateTime;
import java.util.LinkedHashMap;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

class DoctorControllerTest extends IntegrationTestBase {

    private Map<String, Object> doctorBody(String email) {
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("name", "Dr. New Doctor");
        body.put("specialty", "Neurology");
        body.put("email", email);
        body.put("password", "Secret123");
        body.put("phone", "+91-9000000099");
        body.put("availableFrom", "09:00");
        body.put("availableTo", "17:00");
        body.put("active", true);
        return body;
    }

    private String adminToken() throws Exception {
        saveAdmin("boss@clinic.test");
        return login("boss@clinic.test", Role.ADMIN);
    }

    @Test
    void doctorDirectoryIsPublicAndHidesPrivateFields() throws Exception {
        saveDoctor("Dr. Asha Verma", "Cardiology", "asha@clinic.test");
        mvc.perform(get("/api/doctors"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].name").value("Dr. Asha Verma"))
                .andExpect(jsonPath("$[0].email").doesNotExist())
                .andExpect(jsonPath("$[0].passwordHash").doesNotExist());
    }

    @Test
    void adminCanAddDoctorAndPasswordIsStoredHashed() throws Exception {
        String token = adminToken();
        mvc.perform(post("/api/doctors").header("Authorization", bearer(token))
                        .contentType(MediaType.APPLICATION_JSON).content(json(doctorBody("new.doc@clinic.test"))))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.email").value("new.doc@clinic.test"))
                .andExpect(jsonPath("$.password").doesNotExist())
                .andExpect(jsonPath("$.passwordHash").doesNotExist());
        Doctor saved = doctorRepository.findByEmailIgnoreCase("new.doc@clinic.test").orElseThrow();
        assertThat(saved.getPasswordHash()).startsWith("$2");
    }

    @Test
    void duplicateDoctorEmailReturns409() throws Exception {
        String token = adminToken();
        saveDoctor("Existing", "Cardiology", "taken@clinic.test");
        mvc.perform(post("/api/doctors").header("Authorization", bearer(token))
                        .contentType(MediaType.APPLICATION_JSON).content(json(doctorBody("taken@clinic.test"))))
                .andExpect(status().isConflict());
    }

    @Test
    void invalidDoctorReturns400() throws Exception {
        String token = adminToken();
        Map<String, Object> body = doctorBody("bad@clinic.test");
        body.put("name", "");
        mvc.perform(post("/api/doctors").header("Authorization", bearer(token))
                        .contentType(MediaType.APPLICATION_JSON).content(json(body)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.fieldErrors.name").exists());
    }

    @Test
    void onlyAdminsMayAddDoctors() throws Exception {
        savePatient("Pat", "pat@example.test");
        String patientToken = login("pat@example.test", Role.PATIENT);
        mvc.perform(post("/api/doctors").header("Authorization", bearer(patientToken))
                        .contentType(MediaType.APPLICATION_JSON).content(json(doctorBody("x@clinic.test"))))
                .andExpect(status().isForbidden());
        mvc.perform(post("/api/doctors").contentType(MediaType.APPLICATION_JSON).content(json(doctorBody("y@clinic.test"))))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void searchFiltersByNameAndSpecialty() throws Exception {
        saveDoctor("Dr. Asha Verma", "Cardiology", "asha@clinic.test");
        saveDoctor("Dr. Rahul Mehta", "Dermatology", "rahul@clinic.test");
        mvc.perform(get("/api/doctors/search").param("name", "verm"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(1))
                .andExpect(jsonPath("$[0].name").value("Dr. Asha Verma"));
        mvc.perform(get("/api/doctors/search").param("specialty", "dermatology"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(1))
                .andExpect(jsonPath("$[0].name").value("Dr. Rahul Mehta"));
        mvc.perform(get("/api/doctors/search"))
                .andExpect(jsonPath("$.length()").value(2));
    }

    @Test
    void availabilityEndpointExcludesBookedDoctors() throws Exception {
        Doctor free = saveDoctor("Dr. Free", "Cardiology", "free@clinic.test");
        Doctor busy = saveDoctor("Dr. Busy", "Cardiology", "busy@clinic.test");
        Patient p = savePatient("Pat", "pat@example.test");
        LocalDateTime slot = futureWeekday(10);
        saveAppointment(busy, p, slot, AppointmentStatus.SCHEDULED);

        mvc.perform(get("/api/doctors/availability")
                        .param("specialty", "Cardiology").param("dateTime", slot.toString()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(1))
                .andExpect(jsonPath("$[0].id").value(free.getId()));
    }

    @Test
    void availabilityWithBadDateReturns400() throws Exception {
        mvc.perform(get("/api/doctors/availability").param("dateTime", "tomorrow"))
                .andExpect(status().isBadRequest());
    }

    @Test
    void unknownDoctorReturns404() throws Exception {
        mvc.perform(get("/api/doctors/999999")).andExpect(status().isNotFound());
    }

    @Test
    void doctorWithoutAppointmentsCanBeDeletedButWithAppointmentsCannot() throws Exception {
        String token = adminToken();
        Doctor empty = saveDoctor("Dr. Empty", "Cardiology", "empty@clinic.test");
        Doctor used = saveDoctor("Dr. Used", "Cardiology", "used@clinic.test");
        saveAppointment(used, savePatient("Pat", "pat@example.test"), futureWeekday(11), AppointmentStatus.SCHEDULED);

        mvc.perform(delete("/api/doctors/" + empty.getId()).header("Authorization", bearer(token)))
                .andExpect(status().isNoContent());
        mvc.perform(delete("/api/doctors/" + used.getId()).header("Authorization", bearer(token)))
                .andExpect(status().isConflict());
    }
}
