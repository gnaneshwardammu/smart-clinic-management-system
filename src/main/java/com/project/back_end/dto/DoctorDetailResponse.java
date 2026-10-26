package com.project.back_end.dto;

import java.time.LocalDateTime;
import java.time.LocalTime;

/** Full view of a doctor for the admin and for the doctor themselves. Never contains the password hash. */
public record DoctorDetailResponse(Long id, String name, String specialty, String email, String phone,
                                   LocalTime availableFrom, LocalTime availableTo, boolean active,
                                   LocalDateTime createdAt) {
}
