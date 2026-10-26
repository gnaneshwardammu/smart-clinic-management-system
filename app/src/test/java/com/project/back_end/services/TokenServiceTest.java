package com.project.back_end.services;

import com.project.back_end.models.Role;
import com.project.back_end.security.AuthUser;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class TokenServiceTest {

    private static final String SECRET = "unit-test-secret-unit-test-secret-123456";

    @Test
    void tokenRoundTripKeepsIdentityAndRole() {
        TokenService service = new TokenService(SECRET, 60);
        String token = service.generateToken(new AuthUser(42L, "a@b.test", Role.DOCTOR));
        AuthUser user = service.validateToken(token).orElseThrow();
        assertThat(user.id()).isEqualTo(42L);
        assertThat(user.email()).isEqualTo("a@b.test");
        assertThat(user.role()).isEqualTo(Role.DOCTOR);
    }

    @Test
    void tamperedAndGarbageTokensAreRejected() {
        TokenService service = new TokenService(SECRET, 60);
        String token = service.generateToken(new AuthUser(1L, "a@b.test", Role.PATIENT));
        assertThat(service.validateToken(token + "x")).isEmpty();
        assertThat(service.validateToken("garbage")).isEmpty();
        assertThat(service.validateToken("")).isEmpty();
        assertThat(service.validateToken(null)).isEmpty();
    }

    @Test
    void tokenSignedWithAnotherSecretIsRejected() {
        TokenService other = new TokenService("another-secret-another-secret-123456789", 60);
        String foreign = other.generateToken(new AuthUser(1L, "a@b.test", Role.ADMIN));
        assertThat(new TokenService(SECRET, 60).validateToken(foreign)).isEmpty();
    }

    @Test
    void expiredTokenIsRejected() {
        TokenService expired = new TokenService(SECRET, -5);
        String token = expired.generateToken(new AuthUser(1L, "a@b.test", Role.PATIENT));
        assertThat(expired.validateToken(token)).isEmpty();
    }

    @Test
    void missingOrShortSecretPreventsStartup() {
        assertThatThrownBy(() -> new TokenService("", 60)).isInstanceOf(IllegalStateException.class);
        assertThatThrownBy(() -> new TokenService("too-short", 60)).isInstanceOf(IllegalStateException.class);
    }
}
