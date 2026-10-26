package com.project.back_end.services;

import com.project.back_end.dto.PatientResponse;
import com.project.back_end.dto.PatientUpdateRequest;
import com.project.back_end.exception.ApiException;
import com.project.back_end.models.Patient;
import com.project.back_end.models.Role;
import com.project.back_end.repo.AppointmentRepository;
import com.project.back_end.repo.PatientRepository;
import com.project.back_end.security.AuthUser;
import com.project.back_end.util.NameUtils;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Service
@Transactional(readOnly = true)
public class PatientService {

    private final PatientRepository patientRepository;
    private final AppointmentRepository appointmentRepository;

    public PatientService(PatientRepository patientRepository, AppointmentRepository appointmentRepository) {
        this.patientRepository = patientRepository;
        this.appointmentRepository = appointmentRepository;
    }

    public static PatientResponse toResponse(Patient p) {
        return new PatientResponse(p.getId(), p.getName(), p.getEmail(), p.getPhone(),
                p.getDateOfBirth(), p.getAddress(), p.getCreatedAt());
    }

    public PatientResponse getMe(AuthUser user) {
        return toResponse(load(user.id()));
    }

    @Transactional
    public PatientResponse updateMe(AuthUser user, PatientUpdateRequest request) {
        Patient patient = load(user.id());
        patient.setName(request.name().trim());
        patient.setPhone(NameUtils.trimToNull(request.phone()));
        patient.setDateOfBirth(request.dateOfBirth());
        patient.setAddress(NameUtils.trimToNull(request.address()));
        return toResponse(patientRepository.save(patient));
    }

    /**
     * Admin: any patient. Patient: only themselves.
     * Doctor: only patients who have (or had) an appointment with that doctor.
     */
    public PatientResponse getById(AuthUser user, Long patientId) {
        boolean allowed = switch (user.role()) {
            case ADMIN -> true;
            case PATIENT -> user.id().equals(patientId);
            case DOCTOR -> appointmentRepository.existsByDoctorIdAndPatientId(user.id(), patientId);
        };
        if (!allowed) {
            throw ApiException.forbidden("You do not have access to this patient record");
        }
        return toResponse(load(patientId));
    }

    /** Admin only (also enforced in the controller). */
    public List<PatientResponse> listAll(AuthUser user) {
        if (user.role() != Role.ADMIN) {
            throw ApiException.forbidden("Only administrators can list patients");
        }
        return patientRepository.findAll(Sort.by("name")).stream().map(PatientService::toResponse).toList();
    }

    private Patient load(Long id) {
        return patientRepository.findById(id)
                .orElseThrow(() -> ApiException.notFound("Patient not found with id " + id));
    }
}
