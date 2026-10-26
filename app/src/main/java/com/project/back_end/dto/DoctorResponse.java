package com.project.back_end.dto;

import java.time.LocalTime;

/** Public view of a doctor (no email, phone or password). */
public record DoctorResponse(Long id, String name, String specialty,
                             LocalTime availableFrom, LocalTime availableTo, boolean active) {
}
