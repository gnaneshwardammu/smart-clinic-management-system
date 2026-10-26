package com.project.back_end;

import com.project.back_end.models.Admin;
import com.project.back_end.models.Patient;
import com.project.back_end.models.Role;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;

import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

class AuthIntegrationTest extends IntegrationTestBase {

    private String registerBody(String email, String password) throws Exception {
        return json(Map.of("name", "New Patient", "email", email, "password", password));
    }

    @Test
    void healthEndpointIsPublic() throws Exception {
        mvc.perform(get("/api/health"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("UP"));
    }

    @Test
    void publicRegistrationCreatesPatientAndHashesPassword() throws Exception {
        mvc.perform(post("/api/auth/register").contentType(MediaType.APPLICATION_JSON)
                        .content(registerBody("new.patient@example.test", "Secret123")))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.email").value("new.patient@example.test"))
                .andExpect(jsonPath("$.password").doesNotExist())
                .andExpect(jsonPath("$.passwordHash").doesNotExist());

        Patient saved = patientRepository.findByEmailIgnoreCase("new.patient@example.test").orElseThrow();
        assertThat(saved.getPasswordHash()).startsWith("$2").isNotEqualTo("Secret123");
    }

    @Test
    void duplicateRegistrationIsRejectedWith409() throws Exception {
        savePatient("Existing", "dup@example.test");
        mvc.perform(post("/api/auth/register").contentType(MediaType.APPLICATION_JSON)
                        .content(registerBody("DUP@example.test", "Secret123")))
                .andExpect(status().isConflict());
    }

    @Test
    void invalidRegistrationReturns400WithFieldErrors() throws Exception {
        mvc.perform(post("/api/auth/register").contentType(MediaType.APPLICATION_JSON)
                        .content(registerBody("not-an-email", "short")))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.fieldErrors.email").exists())
                .andExpect(jsonPath("$.fieldErrors.password").exists());
    }

    @Test
    void registrationCannotCreateAnAdmin() throws Exception {
        // a "role" field in the body is simply ignored: the DTO has no such field
        String body = json(Map.of("name", "Sneaky", "email", "sneaky@example.test",
                "password", "Secret123", "role", "ADMIN"));
        mvc.perform(post("/api/auth/register").contentType(MediaType.APPLICATION_JSON).content(body))
                .andExpect(status().isCreated());
        assertThat(adminRepository.findByEmailIgnoreCase("sneaky@example.test")).isEmpty();
        mvc.perform(post("/api/auth/login").contentType(MediaType.APPLICATION_JSON)
                        .content(json(Map.of("email", "sneaky@example.test", "password", "Secret123", "role", "ADMIN"))))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void loginReturnsTokenAndMeShowsIdentity() throws Exception {
        savePatient("Pat Example", "pat@example.test");
        String token = login("pat@example.test", Role.PATIENT);
        mvc.perform(get("/api/auth/me").header("Authorization", bearer(token)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.email").value("pat@example.test"))
                .andExpect(jsonPath("$.role").value("PATIENT"));
    }

    @Test
    void adminDoctorAndPatientLoginReturnMatchingRoles() throws Exception {
        saveAdmin("boss@clinic.test");
        saveDoctor("Dr. Who", "Cardiology", "who@clinic.test");
        savePatient("Pat Example", "pat@example.test");

        assertLoginRole("boss@clinic.test", Role.ADMIN);
        assertLoginRole("who@clinic.test", Role.DOCTOR);
        assertLoginRole("pat@example.test", Role.PATIENT);
    }

    @Test
    void malformedStoredPasswordHashReturns401() throws Exception {
        Admin admin = adminRepository.save(new Admin("Broken Admin", "broken@clinic.test", "not-a-bcrypt-hash"));

        mvc.perform(post("/api/auth/login").contentType(MediaType.APPLICATION_JSON)
                        .content(json(Map.of("email", admin.getEmail(), "password", PASSWORD, "role", "ADMIN"))))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.message").value("Invalid email or password"));
    }

    @Test
    void wrongPasswordUnknownEmailAndWrongRoleAreAll401() throws Exception {
        savePatient("Pat Example", "pat@example.test");
        mvc.perform(post("/api/auth/login").contentType(MediaType.APPLICATION_JSON)
                        .content(json(Map.of("email", "pat@example.test", "password", "WrongPass1", "role", "PATIENT"))))
                .andExpect(status().isUnauthorized());
        mvc.perform(post("/api/auth/login").contentType(MediaType.APPLICATION_JSON)
                        .content(json(Map.of("email", "nobody@example.test", "password", PASSWORD, "role", "PATIENT"))))
                .andExpect(status().isUnauthorized());
        mvc.perform(post("/api/auth/login").contentType(MediaType.APPLICATION_JSON)
                        .content(json(Map.of("email", "pat@example.test", "password", PASSWORD, "role", "DOCTOR"))))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void protectedEndpointsRequireAValidToken() throws Exception {
        mvc.perform(get("/api/appointments/my")).andExpect(status().isUnauthorized());
        mvc.perform(get("/api/appointments/my").header("Authorization", "Bearer not.a.real.token"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void rolesAreEnforced() throws Exception {
        savePatient("Pat Example", "pat@example.test");
        saveDoctor("Dr. Who", "Cardiology", "who@clinic.test");
        String patientToken = login("pat@example.test", Role.PATIENT);
        String doctorToken = login("who@clinic.test", Role.DOCTOR);

        mvc.perform(get("/api/admin/patients").header("Authorization", bearer(patientToken)))
                .andExpect(status().isForbidden());
        mvc.perform(get("/api/admin/patients").header("Authorization", bearer(doctorToken)))
                .andExpect(status().isForbidden());
        mvc.perform(get("/api/appointments").header("Authorization", bearer(patientToken)))
                .andExpect(status().isForbidden());
        mvc.perform(get("/api/doctors/me").header("Authorization", bearer(patientToken)))
                .andExpect(status().isForbidden());
    }

    @Test
    void adminCanListPatients() throws Exception {
        saveAdmin("boss@clinic.test");
        savePatient("Pat Example", "pat@example.test");
        String token = login("boss@clinic.test", Role.ADMIN);
        mvc.perform(get("/api/admin/patients").header("Authorization", bearer(token)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].email").value("pat@example.test"))
                .andExpect(jsonPath("$[0].passwordHash").doesNotExist());
    }

    private void assertLoginRole(String email, Role role) throws Exception {
        String token = login(email, role);
        mvc.perform(get("/api/auth/me").header("Authorization", bearer(token)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.email").value(email))
                .andExpect(jsonPath("$.role").value(role.name()));
    }
}
