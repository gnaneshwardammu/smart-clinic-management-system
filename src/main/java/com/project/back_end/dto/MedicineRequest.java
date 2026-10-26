package com.project.back_end.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record MedicineRequest(
        @NotBlank(message = "Medicine name is required") @Size(max = 100) String name,
        @NotBlank(message = "Dosage is required") @Size(max = 100) String dosage,
        @NotBlank(message = "Frequency is required") @Size(max = 100) String frequency,
        @NotBlank(message = "Duration is required") @Size(max = 100) String duration) {
}
