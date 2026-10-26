package com.project.back_end.controllers;

import com.project.back_end.dto.PatientResponse;
import com.project.back_end.dto.PatientUpdateRequest;
import com.project.back_end.security.AuthUser;
import com.project.back_end.services.PatientService;
import jakarta.validation.Valid;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/patients")
public class PatientController {

    private final PatientService patientService;

    public PatientController(PatientService patientService) {
        this.patientService = patientService;
    }

    @GetMapping("/me")
    @PreAuthorize("hasRole('PATIENT')")
    public PatientResponse getMe(@AuthenticationPrincipal AuthUser user) {
        return patientService.getMe(user);
    }

    @PutMapping("/me")
    @PreAuthorize("hasRole('PATIENT')")
    public PatientResponse updateMe(@AuthenticationPrincipal AuthUser user,
                                    @Valid @RequestBody PatientUpdateRequest request) {
        return patientService.updateMe(user, request);
    }

    /** Admin: any patient. Patient: only self. Doctor: only patients they have an appointment with. */
    @GetMapping("/{id}")
    public PatientResponse getById(@AuthenticationPrincipal AuthUser user, @PathVariable Long id) {
        return patientService.getById(user, id);
    }
}
