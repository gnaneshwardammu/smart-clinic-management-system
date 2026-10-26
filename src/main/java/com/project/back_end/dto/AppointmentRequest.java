package com.project.back_end.dto;

import jakarta.validation.constraints.Future;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import java.time.LocalDateTime;

public record AppointmentRequest(
        @NotNull(message = "doctorId is required") Long doctorId,
        @NotNull(message = "startTime is required, e.g. 2026-11-03T10:00") @Future(message = "startTime must be in the future") LocalDateTime startTime,
        @Size(max = 255) String reason) {
}
