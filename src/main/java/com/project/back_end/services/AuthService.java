package com.project.back_end.services;

import com.project.back_end.dto.LoginRequest;
import com.project.back_end.dto.LoginResponse;
import com.project.back_end.dto.PatientResponse;
import com.project.back_end.dto.RegisterRequest;
import com.project.back_end.dto.UserProfileResponse;
import com.project.back_end.exception.ApiException;
import com.project.back_end.models.Patient;
import com.project.back_end.repo.AdminRepository;
import com.project.back_end.repo.DoctorRepository;
import com.project.back_end.repo.PatientRepository;
import com.project.back_end.security.AuthUser;
import com.project.back_end.util.NameUtils;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.Optional;

@Service
@Transactional(readOnly = true)
public class AuthService {

    private record Account(Long id, String name, String email, String passwordHash) {
    }

    private final AdminRepository adminRepository;
    private final DoctorRepository doctorRepository;
    private final PatientRepository patientRepository;
    private final PasswordEncoder passwordEncoder;
    private final TokenService tokenService;
    /** Compared against when the email is unknown, so unknown and wrong-password cases take similar time. */
    private final String dummyHash;

    public AuthService(AdminRepository adminRepository, DoctorRepository doctorRepository,
                       PatientRepository patientRepository, PasswordEncoder passwordEncoder,
                       TokenService tokenService) {
        this.adminRepository = adminRepository;
        this.doctorRepository = doctorRepository;
        this.patientRepository = patientRepository;
        this.passwordEncoder = passwordEncoder;
        this.tokenService = tokenService;
        this.dummyHash = passwordEncoder.encode("not-a-real-password");
    }

    public LoginResponse login(LoginRequest request) {
        String email = NameUtils.normalizeEmail(request.email());
        Optional<Account> account = switch (request.role()) {
            case ADMIN -> adminRepository.findByEmailIgnoreCase(email)
                    .map(a -> new Account(a.getId(), a.getName(), a.getEmail(), a.getPasswordHash()));
            case DOCTOR -> doctorRepository.findByEmailIgnoreCase(email)
                    .map(d -> new Account(d.getId(), d.getName(), d.getEmail(), d.getPasswordHash()));
            case PATIENT -> patientRepository.findByEmailIgnoreCase(email)
                    .map(p -> new Account(p.getId(), p.getName(), p.getEmail(), p.getPasswordHash()));
        };

        String hashToCheck = account.map(Account::passwordHash).orElse(dummyHash);
        boolean passwordMatches = matchesPassword(request.password(), hashToCheck);
        if (account.isEmpty() || !passwordMatches) {
            throw ApiException.unauthorized("Invalid email or password");
        }

        Account acc = account.get();
        AuthUser user = new AuthUser(acc.id(), acc.email(), request.role());
        String token = tokenService.generateToken(user);
        Instant expiresAt = Instant.now().plus(tokenService.getExpirationMinutes(), ChronoUnit.MINUTES);
        return new LoginResponse(token, "Bearer", expiresAt, acc.id(), acc.name(), acc.email(), request.role());
    }

    private boolean matchesPassword(String rawPassword, String passwordHash) {
        try {
            return passwordHash != null && passwordEncoder.matches(rawPassword, passwordHash);
        } catch (IllegalArgumentException ex) {
            // A legacy or corrupted stored hash is an invalid credential, not a server error.
            return false;
        }
    }

    /** Public registration: always creates a PATIENT. */
    @Transactional
    public PatientResponse register(RegisterRequest request) {
        String email = NameUtils.normalizeEmail(request.email());
        if (patientRepository.existsByEmailIgnoreCase(email)) {
            throw ApiException.conflict("An account with this email already exists");
        }
        Patient patient = new Patient();
        patient.setName(request.name().trim());
        patient.setEmail(email);
        patient.setPasswordHash(passwordEncoder.encode(request.password()));
        patient.setPhone(NameUtils.trimToNull(request.phone()));
        patient.setDateOfBirth(request.dateOfBirth());
        patient.setAddress(NameUtils.trimToNull(request.address()));
        return PatientService.toResponse(patientRepository.save(patient));
    }

    public UserProfileResponse me(AuthUser user) {
        return switch (user.role()) {
            case ADMIN -> adminRepository.findById(user.id())
                    .map(a -> new UserProfileResponse(a.getId(), a.getName(), a.getEmail(), user.role()))
                    .orElseThrow(() -> ApiException.unauthorized("Account no longer exists"));
            case DOCTOR -> doctorRepository.findById(user.id())
                    .map(d -> new UserProfileResponse(d.getId(), d.getName(), d.getEmail(), user.role()))
                    .orElseThrow(() -> ApiException.unauthorized("Account no longer exists"));
            case PATIENT -> patientRepository.findById(user.id())
                    .map(p -> new UserProfileResponse(p.getId(), p.getName(), p.getEmail(), user.role()))
                    .orElseThrow(() -> ApiException.unauthorized("Account no longer exists"));
        };
    }
}
