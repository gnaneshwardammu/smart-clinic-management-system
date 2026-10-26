package com.project.back_end.services;

import com.project.back_end.models.Role;
import com.project.back_end.security.AuthUser;
import io.jsonwebtoken.Claims;
import io.jsonwebtoken.JwtException;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import javax.crypto.SecretKey;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.Date;
import java.util.Optional;

/**
 * Creates and validates JWT access tokens (HS256).
 * The signing secret comes from the JWT_SECRET environment variable; there is no default.
 */
@Service
public class TokenService {

    private static final int MIN_SECRET_BYTES = 32;

    private final SecretKey key;
    private final long expirationMinutes;

    public TokenService(@Value("${app.jwt.secret:}") String secret,
                        @Value("${app.jwt.expiration-minutes:60}") long expirationMinutes) {
        if (secret == null || secret.getBytes(StandardCharsets.UTF_8).length < MIN_SECRET_BYTES) {
            throw new IllegalStateException(
                    "JWT_SECRET is missing or too short. Set the JWT_SECRET environment variable to a random "
                            + "string of at least " + MIN_SECRET_BYTES + " characters.");
        }
        this.key = Keys.hmacShaKeyFor(secret.getBytes(StandardCharsets.UTF_8));
        this.expirationMinutes = expirationMinutes;
    }

    public long getExpirationMinutes() {
        return expirationMinutes;
    }

    /** Builds a signed token with subject=email and claims uid (user id) and role. */
    public String generateToken(AuthUser user) {
        Instant now = Instant.now();
        return Jwts.builder()
                .subject(user.email())
                .claim("uid", user.id())
                .claim("role", user.role().name())
                .issuedAt(Date.from(now))
                .expiration(Date.from(now.plus(expirationMinutes, ChronoUnit.MINUTES)))
                .signWith(key)
                .compact();
    }

    /** Returns the user inside a valid, unexpired token, or empty if the token is invalid in any way. */
    public Optional<AuthUser> validateToken(String token) {
        if (token == null || token.isBlank()) {
            return Optional.empty();
        }
        try {
            Claims claims = Jwts.parser().verifyWith(key).build().parseSignedClaims(token).getPayload();
            Number uid = claims.get("uid", Number.class);
            String role = claims.get("role", String.class);
            if (uid == null || role == null || claims.getSubject() == null) {
                return Optional.empty();
            }
            return Optional.of(new AuthUser(uid.longValue(), claims.getSubject(), Role.valueOf(role)));
        } catch (JwtException | IllegalArgumentException e) {
            return Optional.empty();
        }
    }
}
