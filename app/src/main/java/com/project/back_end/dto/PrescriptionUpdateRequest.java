package com.project.back_end.dto;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.Size;

import java.util.List;

public record PrescriptionUpdateRequest(
        @NotEmpty(message = "At least one medicine is required") @Valid List<MedicineRequest> medicines,
        @Size(max = 1000) String instructions) {
}
