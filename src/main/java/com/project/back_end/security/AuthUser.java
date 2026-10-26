package com.project.back_end.security;

import com.project.back_end.models.Role;

/** The authenticated caller, rebuilt from the JWT on every request. */
public record AuthUser(Long id, String email, Role role) {
}
