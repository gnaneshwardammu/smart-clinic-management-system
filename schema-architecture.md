# Smart Clinic - Architecture

## Architecture Summary

The Smart Clinic Management System is a **three-tier** Spring Boot application:

1. **Presentation tier:** Thymeleaf HTML pages styled with Bootstrap 5. JavaScript in the pages calls the REST API with the browser's Fetch API.
2. **Application tier:** Spring MVC page controllers, REST controllers, service classes with the business rules, Spring Security with JWT, DTOs, Bean Validation and one central exception handler.
3. **Data tier:** Spring Data JPA/Hibernate repositories for **MySQL** (admins, doctors, patients, appointments) and Spring Data MongoDB for **MongoDB** (prescriptions).

Two kinds of requests exist:

- **MVC request (pages):** `GET /patient/dashboard` -> `PageController` returns the Thymeleaf template name -> Thymeleaf renders an HTML *shell* -> the browser loads `app.js`/`patient.js`.
- **REST request (data):** the page's JavaScript calls `GET /api/appointments/my` with `Authorization: Bearer <JWT>` -> security filter -> controller -> service -> repository -> database -> JSON back to the page.

```mermaid
flowchart LR
    B[Browser: Thymeleaf pages + Bootstrap + JS] -->|1. HTTP + JWT| F[Security filter chain<br/>JwtAuthenticationFilter]
    F -->|2. authenticated user| C[Controllers<br/>@RestController / @Controller]
    C -->|3. DTO| S[Services<br/>business rules + transactions]
    S -->|4. entities| R[Repositories<br/>Spring Data JPA / MongoDB]
    R -->|JPA/Hibernate| M[(MySQL capstone)]
    R -->|Mongo driver| G[(MongoDB clinic_prescriptions)]
    M --> R
    G --> R
    R --> S --> C -->|JSON / HTML| B
    C -.exceptions.-> E[GlobalExceptionHandler<br/>uniform JSON errors]
```

## 1. User Request

A user opens a page such as `http://localhost:8081/patient/login`, fills the form and clicks *Sign in*. The browser's JavaScript sends `POST /api/auth/login` with JSON `{email, password, role}`. Later requests carry the JWT returned by the login call in the `Authorization: Bearer ...` header. The token is kept in `sessionStorage`, so it disappears when the tab is closed.

## 2. Presentation Layer

- Templates in `src/main/resources/templates` (`login.html`, `register.html`, `admin/*`, `doctor/*`, `patient/*`, `prescriptions.html`, error pages) share one fragment file `fragments/layout.html` (head, navigation bar, scripts).
- Static assets in `static/css` and `static/js`. `app.js` stores the token, calls the API, builds the navigation per role and shows notifications; `admin.js`, `doctor.js`, `patient.js`, `prescriptions.js`, `auth.js` contain the page logic.
- The server-rendered pages are only shells. All real data is loaded through the REST API, so a page opened without a valid token shows nothing and JavaScript redirects to the matching login page.

## 3. Controller Layer

- `PageController` (`@Controller`) maps page URLs to templates.
- REST controllers (`@RestController`): `AuthController`, `DoctorController`, `PatientController`, `AdminController`, `AppointmentController`, `PrescriptionController`, `HealthController`.
- Controllers only: bind JSON to DTOs, trigger `@Valid` validation, read the authenticated user (`@AuthenticationPrincipal AuthUser`), call one service method and choose the HTTP status. They contain no business logic.
- **Authentication/authorization:** `SecurityConfig` defines the single filter chain. `JwtAuthenticationFilter` validates the token with `TokenService` and stores the user (id, email, role) in the security context. URL rules plus `@PreAuthorize("hasRole(...)")` on controller methods enforce roles; the services additionally enforce *ownership* (a patient may only touch their own appointments, a doctor only theirs).

## 4. Service Layer

- `AuthService` (login with BCrypt check, patient-only registration), `TokenService` (JWT create/validate), `DoctorService`, `PatientService`, `AdminService` (statistics), `AppointmentService` (availability, booking, conflicts, cancellation rules), `PrescriptionService`.
- Services hold all business rules and are injected through **constructors**. Write operations are `@Transactional`; booking locks the doctor row so two simultaneous bookings cannot take the same slot.
- Services throw `ApiException` (with a status such as 404 or 409) for rule violations.

## 5. Repository Layer

- JPA repositories (`DoctorRepository`, `PatientRepository`, `AdminRepository`, `AppointmentRepository`) extend `JpaRepository`; custom JPQL queries handle searching, overlap detection and fetching appointments together with their doctor and patient (`join fetch`, so no lazy-loading errors).
- `PrescriptionRepository` extends `MongoRepository`.
- Spring Data generates the implementations; Hibernate turns entity operations into SQL.

## 6. Database Layer

- **MySQL `capstone`:** tables `admin`, `doctor`, `patient`, `appointment` with primary keys, foreign keys, unique emails, indexes and CHECK constraints (see `schema-design.md`). Stored procedures produce the daily appointment report and the "doctor with most patients" month/year reports.
- **MongoDB `clinic_prescriptions`:** collection `prescriptions` with nested medicine documents.
- Passwords are stored only as BCrypt hashes.

## 7. Response Flow

1. The repository returns entities (or throws).
2. The service maps entities to response **DTOs**. Entities with password hashes are never returned to the client.
3. The controller wraps the DTO in a response: `200 OK`, `201 Created` or `204 No Content`.
4. Jackson converts the DTO to JSON; the browser's JavaScript renders it into the page.
5. **Failures:** `@Valid` failures -> `400` with per-field messages; bad credentials / missing or invalid token -> `401`; wrong role or someone else's data -> `403`; missing record -> `404`; duplicate email, overlapping appointment or database constraint violation -> `409`; anything unexpected -> `500` with a generic message (details only in the server log). All are produced by `GlobalExceptionHandler` or the security entry points in the same JSON shape (`timestamp, status, error, message, path, fieldErrors`). Stack traces, SQL or secrets are never sent to the client.
