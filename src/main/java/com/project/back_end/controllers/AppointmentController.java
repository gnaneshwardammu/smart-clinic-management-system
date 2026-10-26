package com.project.back_end.controllers;

import com.project.back_end.dto.AppointmentRequest;
import com.project.back_end.dto.AppointmentResponse;
import com.project.back_end.dto.StatusUpdateRequest;
import com.project.back_end.security.AuthUser;
import com.project.back_end.services.AppointmentService;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/api/appointments")
public class AppointmentController {

    private final AppointmentService appointmentService;

    public AppointmentController(AppointmentService appointmentService) {
        this.appointmentService = appointmentService;
    }

    @PostMapping
    @PreAuthorize("hasRole('PATIENT')")
    public ResponseEntity<AppointmentResponse> book(@AuthenticationPrincipal AuthUser user,
                                                    @Valid @RequestBody AppointmentRequest request) {
        return ResponseEntity.status(HttpStatus.CREATED).body(appointmentService.book(user, request));
    }

    /** Clinic-wide list: administrators only. */
    @GetMapping
    @PreAuthorize("hasRole('ADMIN')")
    public List<AppointmentResponse> all(@AuthenticationPrincipal AuthUser user) {
        return appointmentService.listAll(user);
    }

    /** The logged-in patient's own appointments. */
    @GetMapping("/my")
    @PreAuthorize("hasRole('PATIENT')")
    public List<AppointmentResponse> mine(@AuthenticationPrincipal AuthUser user) {
        return appointmentService.listForPatient(user);
    }

    /** The logged-in doctor's own appointments. */
    @GetMapping("/doctor/my")
    @PreAuthorize("hasRole('DOCTOR')")
    public List<AppointmentResponse> doctorMine(@AuthenticationPrincipal AuthUser user) {
        return appointmentService.listForDoctor(user);
    }

    @GetMapping("/{id}")
    public AppointmentResponse get(@AuthenticationPrincipal AuthUser user, @PathVariable Long id) {
        return appointmentService.getById(user, id);
    }

    @PutMapping("/{id}/status")
    public AppointmentResponse updateStatus(@AuthenticationPrincipal AuthUser user, @PathVariable Long id,
                                            @Valid @RequestBody StatusUpdateRequest request) {
        return appointmentService.updateStatus(user, id, request.status());
    }

    /** Cancels the appointment (sets status CANCELLED; the row is kept for history). */
    @DeleteMapping("/{id}")
    public ResponseEntity<Void> cancel(@AuthenticationPrincipal AuthUser user, @PathVariable Long id) {
        appointmentService.cancel(user, id);
        return ResponseEntity.noContent().build();
    }
}
