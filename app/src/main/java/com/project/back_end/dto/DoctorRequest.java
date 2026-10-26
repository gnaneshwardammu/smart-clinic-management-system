package com.project.back_end.dto;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

import java.time.LocalTime;

/** Used by an administrator to create or update a doctor. On update, a null password keeps the old one. */
public record DoctorRequest(
        @NotBlank(message = "Name is required") @Size(max = 100) String name,
        @NotBlank(message = "Specialty is required") @Size(max = 100) String specialty,
        @NotBlank(message = "Email is required") @Email(message = "Email is not valid") @Size(max = 150) String email,
        @Size(min = 8, max = 72, message = "Password must be 8 to 72 characters") String password,
        @Pattern(regexp = "^[0-9+\\-() ]{7,20}$", message = "Phone must be 7-20 digits/characters like + - ( )") String phone,
        @NotNull(message = "availableFrom is required (e.g. 09:00)") LocalTime availableFrom,
        @NotNull(message = "availableTo is required (e.g. 17:00)") LocalTime availableTo,
        Boolean active) {
}
