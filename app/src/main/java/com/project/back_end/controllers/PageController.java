package com.project.back_end.controllers;

import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;

/**
 * Serves the Thymeleaf pages. The pages are HTML shells; their data is loaded from the REST API with
 * the user's JWT (see static/js/app.js), so a page without a valid token shows nothing and redirects to login.
 */
@Controller
public class PageController {

    @GetMapping("/")
    public String index() {
        return "index";
    }

    @GetMapping("/admin/login")
    public String adminLogin(Model model) {
        return login(model, "ADMIN", "Administrator", "Sign in to manage doctors, patients and appointments.", "admin");
    }

    @GetMapping("/doctor/login")
    public String doctorLogin(Model model) {
        return login(model, "DOCTOR", "Doctor", "Sign in to view your appointments and write prescriptions.", "doctor");
    }

    @GetMapping("/patient/login")
    public String patientLogin(Model model) {
        return login(model, "PATIENT", "Patient", "Sign in to book appointments and see your history.", "patient");
    }

    private String login(Model model, String role, String label, String blurb, String theme) {
        model.addAttribute("role", role);
        model.addAttribute("roleLabel", label);
        model.addAttribute("blurb", blurb);
        model.addAttribute("theme", theme);
        return "login";
    }

    @GetMapping("/register")
    public String register() {
        return "register";
    }

    // ---- admin ----
    @GetMapping("/admin/dashboard")
    public String adminDashboard() {
        return "admin/dashboard";
    }

    @GetMapping("/admin/doctors")
    public String adminDoctors() {
        return "admin/doctors";
    }

    @GetMapping("/admin/doctors/new")
    public String adminDoctorNew(Model model) {
        model.addAttribute("doctorId", "");
        return "admin/doctor-form";
    }

    @GetMapping("/admin/doctors/{id}/edit")
    public String adminDoctorEdit(@PathVariable Long id, Model model) {
        model.addAttribute("doctorId", id);
        return "admin/doctor-form";
    }

    // ---- doctor ----
    @GetMapping("/doctor/dashboard")
    public String doctorDashboard() {
        return "doctor/dashboard";
    }

    @GetMapping("/doctor/appointments")
    public String doctorAppointments() {
        return "doctor/appointments";
    }

    // ---- patient ----
    @GetMapping("/patient/dashboard")
    public String patientDashboard() {
        return "patient/dashboard";
    }

    @GetMapping("/patient/doctors")
    public String patientDoctors() {
        return "patient/doctors";
    }

    @GetMapping("/patient/book")
    public String patientBook() {
        return "patient/book";
    }

    @GetMapping("/patient/appointments")
    public String patientAppointments() {
        return "patient/appointments";
    }

    // ---- shared ----
    @GetMapping("/prescriptions")
    public String prescriptions() {
        return "prescriptions";
    }

    @GetMapping("/access-denied")
    public String accessDenied() {
        return "access-denied";
    }
}
