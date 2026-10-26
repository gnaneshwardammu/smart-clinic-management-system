package com.project.back_end.services;

import com.project.back_end.dto.MedicineRequest;
import com.project.back_end.dto.PrescriptionRequest;
import com.project.back_end.dto.PrescriptionUpdateRequest;
import com.project.back_end.exception.ApiException;
import com.project.back_end.models.Appointment;
import com.project.back_end.models.AppointmentStatus;
import com.project.back_end.models.Prescription;
import com.project.back_end.models.Role;
import com.project.back_end.repo.AppointmentRepository;
import com.project.back_end.repo.PrescriptionRepository;
import com.project.back_end.security.AuthUser;
import com.project.back_end.util.NameUtils;
import org.springframework.stereotype.Service;

import java.time.Instant;
import java.util.List;

/**
 * Prescriptions live in MongoDB. Access rules:
 * - Only the doctor of an appointment may create or edit its prescription.
 * - A patient may read only their own prescriptions.
 * - A doctor may read only prescriptions they wrote.
 * - An administrator may read any prescription.
 */
@Service
public class PrescriptionService {

    private final PrescriptionRepository prescriptionRepository;
    private final AppointmentRepository appointmentRepository;

    public PrescriptionService(PrescriptionRepository prescriptionRepository,
                               AppointmentRepository appointmentRepository) {
        this.prescriptionRepository = prescriptionRepository;
        this.appointmentRepository = appointmentRepository;
    }

    public Prescription create(AuthUser user, PrescriptionRequest request) {
        requireDoctor(user);
        Appointment appointment = appointmentRepository.findByIdWithDetails(request.appointmentId())
                .orElseThrow(() -> ApiException.notFound("Appointment not found with id " + request.appointmentId()));
        if (!appointment.getDoctor().getId().equals(user.id())) {
            throw ApiException.forbidden("You can only prescribe for your own appointments");
        }
        if (appointment.getStatus() == AppointmentStatus.CANCELLED) {
            throw ApiException.conflict("Cannot prescribe for a cancelled appointment");
        }
        Prescription prescription = new Prescription();
        prescription.setAppointmentId(appointment.getId());
        prescription.setDoctorId(appointment.getDoctor().getId());
        prescription.setPatientId(appointment.getPatient().getId());
        prescription.setMedicines(toMedicines(request.medicines()));
        prescription.setInstructions(NameUtils.trimToNull(request.instructions()));
        prescription.setCreatedAt(Instant.now());
        prescription.setUpdatedAt(prescription.getCreatedAt());
        return prescriptionRepository.save(prescription);
    }

    public Prescription getById(AuthUser user, String id) {
        Prescription prescription = prescriptionRepository.findById(id)
                .orElseThrow(() -> ApiException.notFound("Prescription not found"));
        boolean allowed = switch (user.role()) {
            case ADMIN -> true;
            case DOCTOR -> user.id().equals(prescription.getDoctorId());
            case PATIENT -> user.id().equals(prescription.getPatientId());
        };
        if (!allowed) {
            throw ApiException.forbidden("You do not have access to this prescription");
        }
        return prescription;
    }

    public List<Prescription> listForPatient(AuthUser user, Long patientId) {
        return switch (user.role()) {
            case ADMIN -> prescriptionRepository.findByPatientIdOrderByCreatedAtDesc(patientId);
            case PATIENT -> {
                if (!user.id().equals(patientId)) {
                    throw ApiException.forbidden("You can only view your own prescriptions");
                }
                yield prescriptionRepository.findByPatientIdOrderByCreatedAtDesc(patientId);
            }
            case DOCTOR -> prescriptionRepository
                    .findByPatientIdAndDoctorIdOrderByCreatedAtDesc(patientId, user.id());
        };
    }

    public List<Prescription> listForDoctor(AuthUser user) {
        requireDoctor(user);
        return prescriptionRepository.findByDoctorIdOrderByCreatedAtDesc(user.id());
    }

    public Prescription update(AuthUser user, String id, PrescriptionUpdateRequest request) {
        requireDoctor(user);
        Prescription prescription = prescriptionRepository.findById(id)
                .orElseThrow(() -> ApiException.notFound("Prescription not found"));
        if (!user.id().equals(prescription.getDoctorId())) {
            throw ApiException.forbidden("You can only edit prescriptions you wrote");
        }
        prescription.setMedicines(toMedicines(request.medicines()));
        prescription.setInstructions(NameUtils.trimToNull(request.instructions()));
        prescription.setUpdatedAt(Instant.now());
        return prescriptionRepository.save(prescription);
    }

    private void requireDoctor(AuthUser user) {
        if (user.role() != Role.DOCTOR) {
            throw ApiException.forbidden("Only doctors can write prescriptions");
        }
    }

    private List<Prescription.Medicine> toMedicines(List<MedicineRequest> requests) {
        return requests.stream()
                .map(m -> new Prescription.Medicine(m.name().trim(), m.dosage().trim(),
                        m.frequency().trim(), m.duration().trim()))
                .toList();
    }
}
