package com.project.back_end.dto;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import java.util.List;

/** Create a prescription. The patient and doctor are taken from the appointment, not from the client. */
public record PrescriptionRequest(
        @NotNull(message = "appointmentId is required") Long appointmentId,
        @NotEmpty(message = "At least one medicine is required") @Valid List<MedicineRequest> medicines,
        @Size(max = 1000) String instructions) {
}
