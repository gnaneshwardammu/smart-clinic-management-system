package com.project.back_end.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Past;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

import java.time.LocalDate;

public record PatientUpdateRequest(
        @NotBlank(message = "Name is required") @Size(max = 100) String name,
        @Pattern(regexp = "^[0-9+\\-() ]{7,20}$", message = "Phone must be 7-20 digits/characters like + - ( )") String phone,
        @Past(message = "Date of birth must be in the past") LocalDate dateOfBirth,
        @Size(max = 255) String address) {
}
