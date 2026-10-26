package com.project.back_end.dto;

import com.project.back_end.models.AppointmentStatus;
import jakarta.validation.constraints.NotNull;

public record StatusUpdateRequest(@NotNull(message = "status is required") AppointmentStatus status) {
}
