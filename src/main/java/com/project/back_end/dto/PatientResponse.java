package com.project.back_end.dto;

import java.time.LocalDate;
import java.time.LocalDateTime;

public record PatientResponse(Long id, String name, String email, String phone,
                              LocalDate dateOfBirth, String address, LocalDateTime createdAt) {
}
