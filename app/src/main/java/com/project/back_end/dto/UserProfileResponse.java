package com.project.back_end.dto;

import com.project.back_end.models.Role;

public record UserProfileResponse(Long id, String name, String email, Role role) {
}
