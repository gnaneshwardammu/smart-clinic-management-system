# Smart Clinic Management System

A Spring Boot capstone application for a clinic with three roles - **Admin**, **Doctor** and **Patient** - using MySQL for clinic data, MongoDB for prescriptions, JWT security and Thymeleaf/Bootstrap pages.

## 1. Project overview
Patients register, find doctors, book 30-minute appointments and read their prescriptions. Doctors see their own schedule, update appointment status and write prescriptions. Admins manage doctors, see all patients and appointments and clinic statistics.

## 2. Features
- Role-based login (Admin / Doctor / Patient), BCrypt passwords, JWT tokens (60 min).
- Public registration creates **Patients only**; Admin and Doctor accounts are created by an Admin or by the seed script.
- Doctor CRUD (admin), public doctor directory with search by name, filter by specialty and by availability at a date-time, free-slot lookup.
- Appointment booking with conflict, working-hours and ownership rules; cancellation rules.
- Prescriptions in MongoDB with access control.
- MySQL stored procedures for reports; sample data; Docker; GitHub Actions CI; automated tests.

## 3. Technology stack
Java 21 target - Spring Boot 3.5.7 - Spring Web, Data JPA (Hibernate), Data MongoDB, Security, Validation, Thymeleaf - jjwt 0.12.6 - MySQL 8 - MongoDB - Bootstrap 5 (CDN) - Maven - Docker / Docker Compose - GitHub Actions - JUnit 5, Mockito, MockMvc, H2 (tests only).

## 4. Architecture
See [`schema-architecture.md`](schema-architecture.md) (three tiers, request flow, diagram) and [`schema-design.md`](schema-design.md) (MySQL tables, MongoDB collection).

## 5. Project folder structure
```
java-database-capstone/
├── README.md  user_stories.md  schema-design.md  schema-architecture.md  ASSESSMENT_CHECKLIST.md
├── Dockerfile  docker-compose.yml  .env.example  .gitignore
├── .github/ISSUE_TEMPLATE/{admin,doctor,patient}-user-story.md
├── .github/workflows/compile-backend.yml
├── database/ schema.sql  sample-data.sql  stored-procedures.sql
└── app/
    ├── pom.xml
    └── src/
        ├── main/java/com/project/back_end/
        │   ├── BackEndApplication.java
        │   ├── config/ (SecurityConfig)   controllers/   services/   repo/
        │   ├── models/   dto/   security/   exception/   util/
        │   └── (resources) templates/ static/css static/js application.properties
        └── test/java/com/project/back_end/ (unit + integration tests)
```

## 6. Prerequisites
Windows 11, VS Code, Git, **a JDK (21 recommended)**, Maven 3.9, MySQL 8 (running), MongoDB (running), optionally Docker Desktop.

## 7. Java and Maven setup
`pom.xml` sets `<java.version>21</java.version>`, so Maven compiles for Java 21 **even when JDK 25 runs Maven**. Spring Boot 3.5 is built and tested for Java 17-25, so JDK 25 should work. If you see strange errors (especially from Mockito/ByteBuddy in tests) the safest fix is to install JDK 21 and use it for this project:

```powershell
winget install EclipseAdoptium.Temurin.21.JDK
# open a NEW PowerShell window, then point this window at JDK 21 (adjust the folder name to what was installed):
$env:JAVA_HOME = "C:\Program Files\Eclipse Adoptium\jdk-21.0.x-hotspot"
$env:Path = "$env:JAVA_HOME\bin;$env:Path"
java -version ; mvn -version
```
If you stay on JDK 25 and only the tests fail with a ByteBuddy "Java 25 is not supported" message, run: `mvn clean verify "-DargLine=-Dnet.bytebuddy.experimental=true"`.

## 8. MySQL setup
1. Start MySQL. Port 3306 must be reachable (port 8080 is used by Oracle XML DB on your PC, which is why this app uses **8081**).
2. In **MySQL Workbench** open a connection and run, in this order (open each file, press the lightning button / Ctrl+Shift+Enter):
   1. `database/schema.sql` - creates database `capstone` and the four tables (safe to run repeatedly).
   2. `database/sample-data.sql` - 1 admin, 5 doctors, exactly 5 patients, 13 appointments (safe to run repeatedly).
   3. `database/stored-procedures.sql` - creates the three report procedures (safe to run repeatedly).
3. You may also just start the application first: with `DDL_AUTO=update` (the default) Hibernate creates the same tables; then run steps 2 and 3. Both ways produce identical table, column, index and constraint names.
4. Your earlier experiment tables are not touched. If an old `doctor` table from the first attempt exists with different columns, either drop it in Workbench (`DROP TABLE IF EXISTS doctor;` - only if you do not need its data) and restart, or drop the whole database: `DROP DATABASE capstone;` and run step 2.1 again.

## 9. MongoDB setup
- Windows service: `Get-Service MongoDB` (should be *Running*); start with `net start MongoDB` in an Administrator PowerShell.
- Default address `mongodb://localhost:27017`; the database `clinic_prescriptions` is created automatically on the first prescription.
- Verify prescriptions are stored (needs `mongosh`): `mongosh "mongodb://localhost:27017/clinic_prescriptions" --eval "db.prescriptions.find().pretty()"`
- Not installed? Download MongoDB Community Server from mongodb.com (or run `docker run -d -p 27017:27017 mongo:7`).

## 10. Environment variable configuration
Secrets are **never** stored in the source code. The app reads:

| Variable | Meaning | Default |
|---|---|---|
| `JWT_SECRET` | **Required**, 32+ characters. The app refuses to start without it. | none |
| `DB_PASSWORD` | MySQL password | empty |
| `DB_USERNAME` | MySQL user | `root` |
| `DB_URL` | JDBC URL | `jdbc:mysql://localhost:3306/capstone?...` |
| `MONGODB_URI` | MongoDB URI | `mongodb://localhost:27017/clinic_prescriptions` |
| `SERVER_PORT` | HTTP port | `8081` |
| `DDL_AUTO` | Hibernate schema mode (`update` for development, `validate` for production) | `update` |
| `JWT_EXPIRATION_MINUTES` | token lifetime | `60` |

PowerShell (applies to the current window only):
```powershell
$env:DB_PASSWORD = "your-mysql-password"
$env:JWT_SECRET  = -join ((48..57)+(65..90)+(97..122) | Get-Random -Count 48 | ForEach-Object {[char]$_})
```
Templates: [`.env.example`](.env.example) and `app/src/main/resources/application-example.properties`.

**Local vs production:** local development uses `DDL_AUTO=update`, the sample data and the dev password below. In production use `DDL_AUTO=validate` with `schema.sql`, a unique long `JWT_SECRET`, real passwords, HTTPS, and do **not** load `sample-data.sql`.

> **Security reminder:** an earlier version of `application.properties` in your Git history contained your MySQL password in plain text. If that commit was ever pushed to GitHub, change your MySQL password, because anything pushed stays visible in history.

## 11. Build and run the backend
```powershell
cd "C:\Users\dammu gnaneshwar\OneDrive\Desktop\java-database-capstone\app"
$env:DB_PASSWORD = "your-mysql-password"
$env:JWT_SECRET  = "paste-a-random-48-character-string-here-please-123"
mvn clean verify          # compiles and runs all tests (no MySQL/MongoDB needed for tests)
mvn spring-boot:run       # starts on http://localhost:8081
```
Success looks like `Started BackEndApplication` and:
```powershell
curl.exe -i http://localhost:8081/api/health     # HTTP/1.1 200 and {"status":"UP",...}
```
Likely errors: *"JWT_SECRET is missing"* -> set the variable in the same window; *"Access denied for user 'root'"* -> wrong `DB_PASSWORD`; *"Communications link failure"* -> MySQL not running; *"Port 8081 already in use"* -> stop the other copy or set `SERVER_PORT`; MongoDB errors appear only when a prescription is saved/read.

## 12. How to access the frontend
Open <http://localhost:8081/>. Direct links: `/admin/login`, `/doctor/login`, `/patient/login`, `/register`.

## 13. Test accounts (LOCAL DEVELOPMENT ONLY)
All created by `database/sample-data.sql`; **password for every account: `Password@123`** (stored as a BCrypt hash).

| Role | Email | Notes |
|---|---|---|
| Admin | `admin@clinic.test` | |
| Doctor | `asha.verma@clinic.test` | Cardiology, 09:00-17:00, id 1 |
| Doctor | `rahul.mehta@clinic.test` | Dermatology, id 2 |
| Doctor | `priya.nair@clinic.test` | Pediatrics 10:00-16:00, id 3 |
| Doctor | `kiran.rao@clinic.test` | Orthopedics 08:00-14:00, id 4 |
| Doctor | `sameer.khan@clinic.test` | Cardiology 12:00-18:00, id 5 |
| Patient | `anita.sharma@example.test` | id 1 (has 5 appointments) |
| Patient | `vikram.singh@example.test`, `meera.iyer@example.test`, `rohan.das@example.test`, `sneha.reddy@example.test` | ids 2-5 |

## 14. Authentication instructions
1. `POST /api/auth/login` with `{"email":"...","password":"...","role":"ADMIN|DOCTOR|PATIENT"}` returns `{"token":"...","userId":..,"role":..}`.
2. Send `Authorization: Bearer <token>` on every protected request. Tokens expire after 60 minutes.
3. **Logout is stateless:** `POST /api/auth/logout` returns 204 and the browser deletes its token; the server keeps no session, so a stolen token stays valid until it expires (see limitations).
4. Business rules (assumptions) - see [section 23](#23-known-limitations-and-assumptions).

## 15. REST API documentation
All errors use one JSON shape: `{"timestamp","status","error","message","path","fieldErrors"}` (`fieldErrors` only for validation errors). Codes: 200, 201, 204, 400 validation/bad input, 401 missing/invalid token or credentials, 403 wrong role or someone else's data, 404 not found, 409 conflict (duplicate email, taken slot, rule violation), 500 unexpected.

### Health
| Method & path | Auth | Response |
|---|---|---|
| `GET /api/health` | public | `200 {"status":"UP","application":"Smart Clinic Management System","timestamp":"..."}` |

### Authentication
| Endpoint | Auth | Details |
|---|---|---|
| `POST /api/auth/register` | public | body `{"name","email","password"(8-72),"phone"?,"dateOfBirth"?,"address"?}` -> `201` patient (no role field exists; always a Patient). `409` duplicate email, `400` invalid. |
| `POST /api/auth/login` | public | body `{"email","password","role"}` -> `200 {"token","tokenType":"Bearer","expiresAt","userId","name","email","role"}`; `401` "Invalid email or password". |
| `POST /api/auth/logout` | public | `204` (client discards token). |
| `GET /api/auth/me` | any logged-in | `{"id","name","email","role"}` |

### Doctors
| Endpoint | Auth | Details |
|---|---|---|
| `GET /api/doctors` | public | active doctors: `[{"id","name","specialty","availableFrom","availableTo","active"}]` (no email/phone). |
| `GET /api/doctors/{id}` | public | one doctor; `404` if missing. |
| `GET /api/doctors/search?name=verma&specialty=Cardiology` | public | both optional; name is a case-insensitive "contains", specialty is an exact (case-insensitive) match. |
| `GET /api/doctors/specialties` | public | list of specialties. |
| `GET /api/doctors/availability?specialty=Cardiology&dateTime=2026-11-03T12:00` | public | doctors (optionally of one specialty) who are free for an appointment starting at that date-time (future, weekday, inside hours, slot not taken). `400` if `dateTime` is missing/invalid. |
| `GET /api/doctors/{id}/slots?date=2026-11-03` | public | free start times that day, e.g. `["09:00:00","09:30:00"]`. |
| `GET /api/doctors/me`, `PUT /api/doctors/me` | Doctor | own details (with email/phone); update `{"name","phone"}`. |
| `POST /api/doctors` | Admin | body `{"name","specialty","email","password","phone","availableFrom":"09:00","availableTo":"17:00","active":true}` -> `201`; `409` duplicate email; `400` invalid. |
| `PUT /api/doctors/{id}` | Admin | same body; `password` optional (null keeps the old one). |
| `DELETE /api/doctors/{id}` | Admin | `204`; `409` if the doctor has any appointments (deactivate instead). |
| `GET /api/admin/doctors`, `GET /api/admin/doctors/{id}` | Admin | all doctors incl. inactive, with contact details. |

### Patients
| Endpoint | Auth | Details |
|---|---|---|
| `GET /api/patients/me`, `PUT /api/patients/me` | Patient | own record; update `{"name","phone","dateOfBirth","address"}`. |
| `GET /api/patients/{id}` | Admin; the patient themself; a doctor who has an appointment with that patient | otherwise `403`. |
| `GET /api/admin/patients` | Admin | all patients. |
| `GET /api/admin/stats` | Admin | clinic counts. |

### Appointments
| Endpoint | Auth | Details |
|---|---|---|
| `POST /api/appointments` | Patient | `{"doctorId":1,"startTime":"2026-11-10T10:00","reason":"..."}` -> `201` appointment. `400` past time, `404` unknown doctor, `409` unavailable / taken / you already have one then. |
| `GET /api/appointments` | Admin | all appointments. |
| `GET /api/appointments/my` | Patient | only the caller's appointments. |
| `GET /api/appointments/doctor/my` | Doctor | only the caller's appointments. |
| `GET /api/appointments/{id}` | owner patient, owner doctor, Admin | otherwise `403`. |
| `PUT /api/appointments/{id}/status` | owner patient (CANCELLED only), owner doctor, Admin | `{"status":"COMPLETED"}`; `409` for finished appointments, cancelling within 2 h, completing before start. |
| `DELETE /api/appointments/{id}` | same as cancelling | `204`; sets status CANCELLED (history is kept). |

Appointment response example:
```json
{"id":9,"doctorId":1,"doctorName":"Dr. Asha Verma","doctorSpecialty":"Cardiology","patientId":4,"patientName":"Rohan Das",
 "patientEmail":"rohan.das@example.test","patientPhone":"+91-9100000004","startTime":"2026-11-03T10:00:00",
 "endTime":"2026-11-03T10:30:00","status":"SCHEDULED","reason":"Palpitations"}
```

### Prescriptions (MongoDB)
| Endpoint | Auth | Details |
|---|---|---|
| `POST /api/prescriptions` | Doctor of the appointment | `{"appointmentId":3,"medicines":[{"name","dosage","frequency","duration"}],"instructions":"..."}` -> `201`. Patient and doctor are taken from the appointment. `403` another doctor's appointment, `409` cancelled appointment, `400` empty medicines. |
| `GET /api/prescriptions/{id}` | Admin; the doctor who wrote it; the patient it belongs to | otherwise `403`. |
| `GET /api/prescriptions/patient/{patientId}` | the patient themself (own id only); a doctor sees only the ones they wrote; Admin sees all | |
| `GET /api/prescriptions/doctor/my` | Doctor | prescriptions written by the caller. |
| `PUT /api/prescriptions/{id}` | the doctor who wrote it | `{"medicines":[...],"instructions":"..."}` |

## 16. SQL schema and sample data instructions
See section 8. Verify in Workbench:
```sql
USE capstone;
SHOW TABLES;                       -- admin, appointment, doctor, patient
SELECT * FROM patient LIMIT 5;     -- exactly 5 rows with the sample data
SELECT id, name, email, phone FROM patient ORDER BY id LIMIT 5;
```

## 17. Stored procedure instructions
Run `database/stored-procedures.sql`, then each call separately (Workbench shows one result tab per call). Rules: CANCELLED appointments are ignored; a patient visiting the same doctor several times counts once; if doctors tie for first place all tied doctors are returned.
```sql
USE capstone;
CALL GetDailyAppointmentReportByDoctor(1, '2026-08-04');  -- doctor id, date
CALL GetDoctorWithMostPatientsByMonth(2026, 8);           -- year, month
CALL GetDoctorWithMostPatientsByYear(2026);               -- year
```
With the sample data the results should be: 2 appointments for Dr. Asha Verma on 2026-08-04; Dr. Asha Verma with 3 distinct patients in Aug 2026 (Sept 2026 -> Dr. Rahul Mehta with 2); Dr. Asha Verma with 4 distinct patients in 2026. **These values are what the sample data is designed to produce - always run the calls yourself and capture your own output.** The doctor ids assume a fresh `capstone` database (run `SELECT id, name FROM doctor;` to check).

## 18. Docker instructions
1. Install Docker Desktop for Windows and start it.
2. `Copy-Item .env.example .env` and edit `.env` (set `MYSQL_ROOT_PASSWORD` and `JWT_SECRET`).
3. Stop any locally running copy of the app (it also uses 8081).
4. From the repository root: `docker compose up --build`
5. Open <http://localhost:8081/>. MySQL is on host port 3307 and MongoDB on 27018 (so they do not clash with your local installs). The first start loads `schema.sql`, `sample-data.sql`, `stored-procedures.sql` automatically.
6. Stop: `docker compose down` (add `-v` to also delete the database volumes and start fresh).

Build only the image: `docker build -t smart-clinic .`

## 19. GitHub Actions information
`.github/workflows/compile-backend.yml` runs on every push and pull request: checks out the code, installs Temurin Java 21 with Maven caching, then runs `mvn -B clean verify` inside `app/`. The job fails if compilation or any test fails. Tests use H2 in memory, so the runner needs no database. Check results on the repository's **Actions** tab.

## 20. Test execution instructions
```powershell
cd app
mvn clean verify          # everything
mvn test                  # tests only
mvn -Dtest=AppointmentAccessTest test   # one class
```
Test classes: `BackEndApplicationTests` (context), `TokenServiceTest`, `DoctorServiceTest`, `AppointmentServiceTest` (business rules, ownership), `AuthIntegrationTest` (login, registration, roles, public endpoints), `DoctorControllerTest` (admin CRUD, search, availability, validation, duplicates), `AppointmentAccessTest` (cross-patient and cross-doctor access blocked, booking conflicts, status rules), `PrescriptionAccessTest`. Integration tests use in-memory H2 and mock MongoDB; they never touch your real databases.

## 21. Troubleshooting common errors
| Symptom | Fix |
|---|---|
| `JWT_SECRET is missing or too short` | set `$env:JWT_SECRET` (32+ chars) in the window that runs Maven. |
| `Table 'capstone.doctor' doesn't exist` / column mismatch | run `database/schema.sql`, or let the app create tables with `DDL_AUTO=update`; drop old experimental tables first (section 8.4). |
| `Unknown column ... in field list` | an old table with different columns exists - see section 8.4. |
| 401 on every call | token missing/expired - log in again; header must be `Authorization: Bearer <token>`. |
| PowerShell breaks JSON in `curl.exe` | put the JSON in a file and use `-d "@login.json"` (see checklist). |
| `Cannot find symbol` / compile errors | run `mvn clean verify` and send me the first error lines. |
| Stored procedure syntax error | needs MySQL 8.0+; run the whole script in Workbench so `DELIMITER` works. |
| Page shows nothing / redirects to login | the token expired or you opened a page for another role; log in again. |

## 22. Instructions for collecting assessment evidence
See [`ASSESSMENT_CHECKLIST.md`](ASSESSMENT_CHECKLIST.md) - exact commands and click paths for all 26 questions.

## 23. Known limitations and assumptions
**Business rules (assumptions):**
- Appointment duration: **30 minutes** (`app.appointment.duration-minutes`).
- A doctor works **Monday-Friday** between their `availableFrom` and `availableTo`; bookings start on :00 or :30 and must finish within the hours. No holidays/leave calendar.
- Two non-cancelled appointments of one doctor (or of one patient) may not overlap; cancelled slots can be re-booked.
- Times are clinic-local (no time zones).
- Patients may cancel only their own SCHEDULED appointments at least **2 hours** before the start. Doctors may mark COMPLETED/NO_SHOW only after the start time. Anything other than SCHEDULED is final.
- A doctor with any appointment history cannot be deleted - deactivate instead.

**Limitations:**
- JWT logout is client-side only (no server-side token revocation); tokens last 60 minutes. The token is kept in `sessionStorage`, which JavaScript can read, so any XSS bug would expose it (all rendered data is HTML-escaped).
- Page URLs are public HTML shells; protection is on the API (data cannot be loaded without a valid token).
- No password-reset or email verification; no rate limiting on login.
- No database migration tool; schema comes from `schema.sql` or Hibernate `update`.
- Bootstrap is loaded from a CDN, so pages need internet access.
- **Verification status:** the SQL scripts were executed against MariaDB 10.11 and behaved as documented. The Java code, tests, Docker and CI files were written without being able to run Maven, Docker or GitHub Actions - run `mvn clean verify` first and treat any failure as a bug to fix.
