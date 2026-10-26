package com.project.back_end.services;

import com.project.back_end.dto.AdminStatsResponse;
import com.project.back_end.models.AppointmentStatus;
import com.project.back_end.repo.AppointmentRepository;
import com.project.back_end.repo.DoctorRepository;
import com.project.back_end.repo.PatientRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;

@Service
@Transactional(readOnly = true)
public class AdminService {

    private final DoctorRepository doctorRepository;
    private final PatientRepository patientRepository;
    private final AppointmentRepository appointmentRepository;

    public AdminService(DoctorRepository doctorRepository, PatientRepository patientRepository,
                        AppointmentRepository appointmentRepository) {
        this.doctorRepository = doctorRepository;
        this.patientRepository = patientRepository;
        this.appointmentRepository = appointmentRepository;
    }

    public AdminStatsResponse getStats() {
        LocalDate today = LocalDate.now();
        long activeDoctors = doctorRepository.findAll().stream().filter(d -> d.isActive()).count();
        return new AdminStatsResponse(
                doctorRepository.count(),
                activeDoctors,
                patientRepository.count(),
                appointmentRepository.count(),
                appointmentRepository.countByStatus(AppointmentStatus.SCHEDULED),
                appointmentRepository.countByStatus(AppointmentStatus.COMPLETED),
                appointmentRepository.countByStatus(AppointmentStatus.CANCELLED),
                appointmentRepository.countByStartTimeGreaterThanEqualAndStartTimeLessThan(
                        today.atStartOfDay(), today.plusDays(1).atStartOfDay()));
    }
}
