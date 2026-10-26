package com.project.back_end.controllers;

import com.project.back_end.dto.AdminStatsResponse;
import com.project.back_end.dto.DoctorDetailResponse;
import com.project.back_end.dto.PatientResponse;
import com.project.back_end.security.AuthUser;
import com.project.back_end.services.AdminService;
import com.project.back_end.services.DoctorService;
import com.project.back_end.services.PatientService;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/api/admin")
@PreAuthorize("hasRole('ADMIN')")
public class AdminController {

    private final AdminService adminService;
    private final PatientService patientService;
    private final DoctorService doctorService;

    public AdminController(AdminService adminService, PatientService patientService, DoctorService doctorService) {
        this.adminService = adminService;
        this.patientService = patientService;
        this.doctorService = doctorService;
    }

    @GetMapping("/stats")
    public AdminStatsResponse stats() {
        return adminService.getStats();
    }

    @GetMapping("/patients")
    public List<PatientResponse> patients(@AuthenticationPrincipal AuthUser user) {
        return patientService.listAll(user);
    }

    /** All doctors, including inactive ones, with contact details. */
    @GetMapping("/doctors")
    public List<DoctorDetailResponse> doctors() {
        return doctorService.getAllDetails();
    }

    @GetMapping("/doctors/{id}")
    public DoctorDetailResponse doctor(@PathVariable Long id) {
        return doctorService.getDetail(id);
    }
}
