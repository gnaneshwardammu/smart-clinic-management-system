package com.project.back_end.controllers;

import com.project.back_end.dto.PrescriptionRequest;
import com.project.back_end.dto.PrescriptionUpdateRequest;
import com.project.back_end.models.Prescription;
import com.project.back_end.security.AuthUser;
import com.project.back_end.services.PrescriptionService;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/api/prescriptions")
public class PrescriptionController {

    private final PrescriptionService prescriptionService;

    public PrescriptionController(PrescriptionService prescriptionService) {
        this.prescriptionService = prescriptionService;
    }

    @PostMapping
    @PreAuthorize("hasRole('DOCTOR')")
    public ResponseEntity<Prescription> create(@AuthenticationPrincipal AuthUser user,
                                               @Valid @RequestBody PrescriptionRequest request) {
        return ResponseEntity.status(HttpStatus.CREATED).body(prescriptionService.create(user, request));
    }

    /** Prescriptions written by the logged-in doctor. */
    @GetMapping("/doctor/my")
    @PreAuthorize("hasRole('DOCTOR')")
    public List<Prescription> doctorMine(@AuthenticationPrincipal AuthUser user) {
        return prescriptionService.listForDoctor(user);
    }

    @GetMapping("/patient/{patientId}")
    public List<Prescription> forPatient(@AuthenticationPrincipal AuthUser user, @PathVariable Long patientId) {
        return prescriptionService.listForPatient(user, patientId);
    }

    @GetMapping("/{id}")
    public Prescription get(@AuthenticationPrincipal AuthUser user, @PathVariable String id) {
        return prescriptionService.getById(user, id);
    }

    @PutMapping("/{id}")
    @PreAuthorize("hasRole('DOCTOR')")
    public Prescription update(@AuthenticationPrincipal AuthUser user, @PathVariable String id,
                               @Valid @RequestBody PrescriptionUpdateRequest request) {
        return prescriptionService.update(user, id, request);
    }
}
