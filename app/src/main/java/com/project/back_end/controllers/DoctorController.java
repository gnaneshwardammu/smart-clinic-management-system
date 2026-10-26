package com.project.back_end.controllers;

import com.project.back_end.dto.DoctorDetailResponse;
import com.project.back_end.dto.DoctorProfileRequest;
import com.project.back_end.dto.DoctorRequest;
import com.project.back_end.dto.DoctorResponse;
import com.project.back_end.security.AuthUser;
import com.project.back_end.services.AppointmentService;
import com.project.back_end.services.DoctorService;
import jakarta.validation.Valid;
import org.springframework.format.annotation.DateTimeFormat;
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
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.util.List;

@RestController
@RequestMapping("/api/doctors")
public class DoctorController {

    private final DoctorService doctorService;
    private final AppointmentService appointmentService;

    public DoctorController(DoctorService doctorService, AppointmentService appointmentService) {
        this.doctorService = doctorService;
        this.appointmentService = appointmentService;
    }

    // ---------------------------------------------------------------- public directory

    @GetMapping
    public List<DoctorResponse> getAll() {
        return doctorService.getActiveDoctors();
    }

    /** GET /api/doctors/search?name=smith&specialty=Cardiology (both optional, combined with AND). */
    @GetMapping("/search")
    public List<DoctorResponse> search(@RequestParam(required = false) String name,
                                       @RequestParam(required = false) String specialty) {
        return doctorService.search(name, specialty);
    }

    /** GET /api/doctors/availability?specialty=Cardiology&dateTime=2026-11-03T10:00 */
    @GetMapping("/availability")
    public List<DoctorResponse> availability(
            @RequestParam(required = false) String specialty,
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) LocalDateTime dateTime) {
        return doctorService.findAvailable(specialty, dateTime);
    }

    @GetMapping("/specialties")
    public List<String> specialties() {
        return doctorService.getSpecialties();
    }

    @GetMapping("/{id}")
    public DoctorResponse getById(@PathVariable Long id) {
        return doctorService.getPublic(id);
    }

    /** GET /api/doctors/5/slots?date=2026-11-03 : free appointment start times that day. */
    @GetMapping("/{id}/slots")
    public List<LocalTime> slots(@PathVariable Long id,
                                 @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate date) {
        return appointmentService.availableSlots(id, date);
    }

    // ---------------------------------------------------------------- the logged-in doctor

    @GetMapping("/me")
    @PreAuthorize("hasRole('DOCTOR')")
    public DoctorDetailResponse getMe(@AuthenticationPrincipal AuthUser user) {
        return doctorService.getDetail(user.id());
    }

    @PutMapping("/me")
    @PreAuthorize("hasRole('DOCTOR')")
    public DoctorDetailResponse updateMe(@AuthenticationPrincipal AuthUser user,
                                         @Valid @RequestBody DoctorProfileRequest request) {
        return doctorService.updateProfile(user.id(), request);
    }

    // ---------------------------------------------------------------- admin only

    @PostMapping
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<DoctorDetailResponse> create(@Valid @RequestBody DoctorRequest request) {
        return ResponseEntity.status(HttpStatus.CREATED).body(doctorService.create(request));
    }

    @PutMapping("/{id}")
    @PreAuthorize("hasRole('ADMIN')")
    public DoctorDetailResponse update(@PathVariable Long id, @Valid @RequestBody DoctorRequest request) {
        return doctorService.update(id, request);
    }

    @DeleteMapping("/{id}")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<Void> delete(@PathVariable Long id) {
        doctorService.delete(id);
        return ResponseEntity.noContent().build();
    }
}
