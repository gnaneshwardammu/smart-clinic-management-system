package com.project.back_end.dto;

import com.project.back_end.models.Role;

import java.time.Instant;

public record LoginResponse(String token, String tokenType, Instant expiresAt,
                            Long userId, String name, String email, Role role) {
}
