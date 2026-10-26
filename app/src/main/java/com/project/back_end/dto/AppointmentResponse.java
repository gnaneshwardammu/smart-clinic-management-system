package com.project.back_end.dto;

import com.project.back_end.models.AppointmentStatus;

import java.time.LocalDateTime;

public record AppointmentResponse(Long id,
                                  Long doctorId, String doctorName, String doctorSpecialty,
                                  Long patientId, String patientName, String patientEmail, String patientPhone,
                                  LocalDateTime startTime, LocalDateTime endTime,
                                  AppointmentStatus status, String reason) {
}
