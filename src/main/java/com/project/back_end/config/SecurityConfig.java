package com.project.back_end.config;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.project.back_end.dto.ErrorResponse;
import com.project.back_end.security.JwtAuthenticationFilter;
import com.project.back_end.services.TokenService;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.security.config.annotation.method.configuration.EnableMethodSecurity;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configurers.AbstractHttpConfigurer;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter;

import java.io.IOException;
import java.time.Instant;

/**
 * ONE security filter chain for the whole app.
 *
 * - /api/** is protected with stateless JWT bearer tokens (no sessions, no cookies).
 * - The Thymeleaf page routes only serve HTML shells; every piece of data on those pages is loaded
 *   through /api/** with the JWT, so the pages cannot reveal data without a valid token.
 * - CSRF protection is disabled on purpose: CSRF attacks rely on the browser automatically sending
 *   credentials (cookies). This app never uses cookies for authentication; the token is sent
 *   explicitly in the Authorization header.
 * - No CORS is configured: the pages and the API are served from the same origin.
 */
@Configuration
@EnableMethodSecurity
public class SecurityConfig {

    @Bean
    public PasswordEncoder passwordEncoder() {
        return new BCryptPasswordEncoder();
    }

    @Bean
    public SecurityFilterChain securityFilterChain(HttpSecurity http, TokenService tokenService,
                                                   ObjectMapper objectMapper) throws Exception {
        http
                .csrf(AbstractHttpConfigurer::disable)
                .cors(AbstractHttpConfigurer::disable)
                .httpBasic(AbstractHttpConfigurer::disable)
                .formLogin(AbstractHttpConfigurer::disable)
                .logout(AbstractHttpConfigurer::disable)
                .sessionManagement(s -> s.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
                .exceptionHandling(e -> e
                        .authenticationEntryPoint((request, response, ex) ->
                                writeError(objectMapper, request, response, HttpStatus.UNAUTHORIZED,
                                        "Authentication is required. Send a valid 'Authorization: Bearer <token>' header."))
                        .accessDeniedHandler((request, response, ex) ->
                                writeError(objectMapper, request, response, HttpStatus.FORBIDDEN,
                                        "You do not have permission to perform this action")))
                .authorizeHttpRequests(auth -> auth
                        // ---- public API ----
                        .requestMatchers(HttpMethod.GET, "/api/health").permitAll()
                        .requestMatchers(HttpMethod.POST, "/api/auth/login", "/api/auth/register", "/api/auth/logout").permitAll()
                        // a doctor's own profile (must come BEFORE the public /api/doctors/{id} rule)
                        .requestMatchers("/api/doctors/me").hasRole("DOCTOR")
                        // public doctor directory (read-only, no email/phone/password)
                        .requestMatchers(HttpMethod.GET,
                                "/api/doctors", "/api/doctors/search", "/api/doctors/availability",
                                "/api/doctors/specialties", "/api/doctors/{id}", "/api/doctors/{id}/slots").permitAll()
                        // ---- protected API ----
                        .requestMatchers("/api/doctors/**").hasRole("ADMIN")
                        .requestMatchers("/api/admin/**").hasRole("ADMIN")
                        .requestMatchers("/api/**").authenticated()
                        // ---- pages and static files (HTML shells; data still needs the JWT) ----
                        .anyRequest().permitAll())
                .addFilterBefore(new JwtAuthenticationFilter(tokenService), UsernamePasswordAuthenticationFilter.class);
        return http.build();
    }

    private void writeError(ObjectMapper mapper, HttpServletRequest request, HttpServletResponse response,
                            HttpStatus status, String message) throws IOException {
        response.setStatus(status.value());
        response.setContentType(MediaType.APPLICATION_JSON_VALUE);
        mapper.writeValue(response.getOutputStream(), new ErrorResponse(Instant.now(), status.value(),
                status.getReasonPhrase(), message, request.getRequestURI(), null));
    }
}
