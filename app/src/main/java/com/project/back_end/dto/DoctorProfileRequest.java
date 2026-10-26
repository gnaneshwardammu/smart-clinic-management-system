package com.project.back_end.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

/** The fields a doctor may change on their own profile. */
public record DoctorProfileRequest(
        @NotBlank(message = "Name is required") @Size(max = 100) String name,
        @Pattern(regexp = "^[0-9+\\-() ]{7,20}$", message = "Phone must be 7-20 digits/characters like + - ( )") String phone) {
}
