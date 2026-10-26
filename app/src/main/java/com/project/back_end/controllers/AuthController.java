package com.project.back_end.controllers;

import com.project.back_end.dto.LoginRequest;
import com.project.back_end.dto.LoginResponse;
import com.project.back_end.dto.PatientResponse;
import com.project.back_end.dto.RegisterRequest;
import com.project.back_end.dto.UserProfileResponse;
import com.project.back_end.security.AuthUser;
import com.project.back_end.services.AuthService;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/auth")
public class AuthController {

    private final AuthService authService;

    public AuthController(AuthService authService) {
        this.authService = authService;
    }

    /** Public registration; always creates a PATIENT account. */
    @PostMapping("/register")
    public ResponseEntity<PatientResponse> register(@Valid @RequestBody RegisterRequest request) {
        return ResponseEntity.status(HttpStatus.CREATED).body(authService.register(request));
    }

    @PostMapping("/login")
    public LoginResponse login(@Valid @RequestBody LoginRequest request) {
        return authService.login(request);
    }

    /**
     * Stateless JWT logout: the server keeps no session, so logging out means the client discards its token.
     * This endpoint exists so clients have one place to call; it always answers 204.
     */
    @PostMapping("/logout")
    public ResponseEntity<Void> logout() {
        return ResponseEntity.noContent().build();
    }

    @GetMapping("/me")
    public UserProfileResponse me(@AuthenticationPrincipal AuthUser user) {
        return authService.me(user);
    }
}
