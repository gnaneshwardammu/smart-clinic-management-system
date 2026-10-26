package com.project.back_end.dto;

import com.project.back_end.models.Role;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

public record LoginRequest(
        @NotBlank(message = "Email is required") @Email(message = "Email is not valid") String email,
        @NotBlank(message = "Password is required") String password,
        @NotNull(message = "Role is required (ADMIN, DOCTOR or PATIENT)") Role role) {
}
