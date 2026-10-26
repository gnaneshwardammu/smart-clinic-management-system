package com.project.back_end.services;

import com.project.back_end.dto.DoctorDetailResponse;
import com.project.back_end.dto.DoctorProfileRequest;
import com.project.back_end.dto.DoctorRequest;
import com.project.back_end.dto.DoctorResponse;
import com.project.back_end.exception.ApiException;
import com.project.back_end.models.Doctor;
import com.project.back_end.repo.AppointmentRepository;
import com.project.back_end.repo.DoctorRepository;
import com.project.back_end.util.NameUtils;
import org.springframework.data.domain.Sort;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;

@Service
@Transactional(readOnly = true)
public class DoctorService {

    private final DoctorRepository doctorRepository;
    private final AppointmentRepository appointmentRepository;
    private final AppointmentService appointmentService;
    private final PasswordEncoder passwordEncoder;

    public DoctorService(DoctorRepository doctorRepository, AppointmentRepository appointmentRepository,
                         AppointmentService appointmentService, PasswordEncoder passwordEncoder) {
        this.doctorRepository = doctorRepository;
        this.appointmentRepository = appointmentRepository;
        this.appointmentService = appointmentService;
        this.passwordEncoder = passwordEncoder;
    }

    // ------------------------------------------------------------------ public reads

    public List<DoctorResponse> getActiveDoctors() {
        return doctorRepository.search("", "").stream().map(this::toPublic).toList();
    }

    /** Search active doctors by (part of) name and/or exact specialty. Blank filters are ignored. */
    public List<DoctorResponse> search(String name, String specialty) {
        return doctorRepository.search(NameUtils.trimToEmpty(name), NameUtils.trimToEmpty(specialty))
                .stream().map(this::toPublic).toList();
    }

    /** Active doctors (optionally of one specialty) who are free for a new appointment starting at "when". */
    public List<DoctorResponse> findAvailable(String specialty, LocalDateTime when) {
        return doctorRepository.search("", NameUtils.trimToEmpty(specialty)).stream()
                .filter(d -> appointmentService.isFree(d, when))
                .map(this::toPublic)
                .toList();
    }

    public List<String> getSpecialties() {
        return doctorRepository.findActiveSpecialties();
    }

    public DoctorResponse getPublic(Long id) {
        return toPublic(load(id));
    }

    // ------------------------------------------------------------------ admin / doctor reads

    public DoctorDetailResponse getDetail(Long id) {
        return toDetail(load(id));
    }

    public List<DoctorDetailResponse> getAllDetails() {
        return doctorRepository.findAll(Sort.by("name")).stream().map(this::toDetail).toList();
    }

    // ------------------------------------------------------------------ writes

    @Transactional
    public DoctorDetailResponse create(DoctorRequest request) {
        validateHours(request);
        if (request.password() == null || request.password().isBlank()) {
            throw ApiException.badRequest("A password is required when adding a doctor");
        }
        String email = NameUtils.normalizeEmail(request.email());
        if (doctorRepository.existsByEmailIgnoreCase(email)) {
            throw ApiException.conflict("A doctor with this email already exists");
        }
        Doctor doctor = new Doctor();
        apply(doctor, request, email);
        doctor.setPasswordHash(passwordEncoder.encode(request.password()));
        return toDetail(doctorRepository.save(doctor));
    }

    @Transactional
    public DoctorDetailResponse update(Long id, DoctorRequest request) {
        validateHours(request);
        Doctor doctor = load(id);
        String email = NameUtils.normalizeEmail(request.email());
        boolean emailTaken = doctorRepository.findByEmailIgnoreCase(email)
                .filter(other -> !other.getId().equals(id)).isPresent();
        if (emailTaken) {
            throw ApiException.conflict("A doctor with this email already exists");
        }
        apply(doctor, request, email);
        if (request.password() != null && !request.password().isBlank()) {
            doctor.setPasswordHash(passwordEncoder.encode(request.password()));
        }
        return toDetail(doctorRepository.save(doctor));
    }

    /** A doctor editing their own profile: only name and phone. */
    @Transactional
    public DoctorDetailResponse updateProfile(Long doctorId, DoctorProfileRequest request) {
        Doctor doctor = load(doctorId);
        doctor.setName(request.name().trim());
        doctor.setPhone(NameUtils.trimToNull(request.phone()));
        return toDetail(doctorRepository.save(doctor));
    }

    /** Deleting is only allowed for doctors without any appointment history; otherwise deactivate. */
    @Transactional
    public void delete(Long id) {
        Doctor doctor = load(id);
        if (appointmentRepository.countByDoctorId(id) > 0) {
            throw ApiException.conflict("This doctor has appointments and cannot be deleted. "
                    + "Edit the doctor and set active to false instead.");
        }
        doctorRepository.delete(doctor);
    }

    // ------------------------------------------------------------------ helpers

    private Doctor load(Long id) {
        return doctorRepository.findById(id)
                .orElseThrow(() -> ApiException.notFound("Doctor not found with id " + id));
    }

    private void validateHours(DoctorRequest request) {
        if (!request.availableFrom().isBefore(request.availableTo())) {
            throw ApiException.badRequest("availableFrom must be earlier than availableTo");
        }
    }

    private void apply(Doctor doctor, DoctorRequest request, String normalizedEmail) {
        doctor.setName(request.name().trim());
        doctor.setSpecialty(request.specialty().trim());
        doctor.setEmail(normalizedEmail);
        doctor.setPhone(NameUtils.trimToNull(request.phone()));
        doctor.setAvailableFrom(request.availableFrom());
        doctor.setAvailableTo(request.availableTo());
        doctor.setActive(request.active() == null || request.active());
    }

    private DoctorResponse toPublic(Doctor d) {
        return new DoctorResponse(d.getId(), d.getName(), d.getSpecialty(),
                d.getAvailableFrom(), d.getAvailableTo(), d.isActive());
    }

    private DoctorDetailResponse toDetail(Doctor d) {
        return new DoctorDetailResponse(d.getId(), d.getName(), d.getSpecialty(), d.getEmail(), d.getPhone(),
                d.getAvailableFrom(), d.getAvailableTo(), d.isActive(), d.getCreatedAt());
    }
}
