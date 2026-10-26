# User Stories - Smart Clinic Management System

Three stories, one per role. Each can be created as a GitHub issue (see the end of this file).

---

## 1. Admin - Manage clinic operations

**Story title:** Admin manages doctors and monitors appointments

**User story:** As an Admin, I want to securely log in, add and manage doctors, and view clinic appointments so that I can manage clinic operations.

**Preconditions**
- An Admin account exists (created by the seed script `database/sample-data.sql`; there is no public admin registration).
- The application, MySQL and MongoDB are running.

**Acceptance criteria**
1. The Admin can log in at `/admin/login` with email and password; wrong credentials show an error and no token is issued.
2. After login the Admin sees a dashboard with clinic statistics, all appointments and all registered patients.
3. The Admin can add a doctor (name, specialty, unique email, password, phone, working hours) and the doctor then appears in the doctor list and in the public directory.
4. The Admin can edit a doctor, including setting the doctor inactive; a blank password on edit keeps the old password.
5. The Admin can delete a doctor only if the doctor has no appointments; otherwise the system explains that the doctor should be deactivated.
6. The Admin can search doctors by name or specialty.
7. The Admin can change the status of a scheduled appointment.
8. Patients and doctors cannot open or call any admin endpoint (HTTP 403); anonymous callers get HTTP 401.

**Expected behavior**
- Passwords are stored as BCrypt hashes and never returned by the API.
- Duplicate doctor email -> HTTP 409. Invalid data -> HTTP 400 with per-field messages.

**Relevant API endpoints**
`POST /api/auth/login`, `GET /api/admin/stats`, `GET /api/admin/patients`, `GET /api/admin/doctors`, `GET /api/admin/doctors/{id}`, `POST /api/doctors`, `PUT /api/doctors/{id}`, `DELETE /api/doctors/{id}`, `GET /api/appointments`, `PUT /api/appointments/{id}/status`

**Test scenarios**
1. Log in as `admin@clinic.test` -> dashboard shows 5 doctors, 5 patients, 13 appointments (with the sample data).
2. Add a doctor with a new email -> 201 Created; add again with the same email -> 409.
3. Submit the add-doctor form with an empty name -> field error, nothing saved.
4. Delete a doctor that has appointments -> 409 and a helpful message.
5. Call `GET /api/admin/patients` with a patient token -> 403; with no token -> 401.

---

## 2. Doctor - Manage consultations

**Story title:** Doctor views schedule and patient details

**User story:** As a Doctor, I want to log in and view my scheduled appointments and relevant patient details so that I can manage my consultations.

**Preconditions**
- A Doctor account exists (created by an Admin or the seed script).
- The doctor has at least one appointment.

**Acceptance criteria**
1. The Doctor can log in at `/doctor/login`.
2. The dashboard shows today's, upcoming and total appointment counts and the doctor's profile.
3. The appointments page lists only appointments assigned to this doctor, with date/time, status and the patient's name, email and phone.
4. The Doctor can mark an appointment COMPLETED or NO_SHOW once it has started, or CANCELLED; finished appointments cannot be changed again.
5. The Doctor can update their own name and phone, but not specialty, email or hours (admin only).
6. The Doctor can write and edit a prescription for their own appointments; it is stored in MongoDB.
7. Changing an appointment id in the URL/API to another doctor's appointment returns HTTP 403.

**Expected behavior**
- A doctor never sees another doctor's appointments or prescriptions.

**Relevant API endpoints**
`POST /api/auth/login`, `GET /api/doctors/me`, `PUT /api/doctors/me`, `GET /api/appointments/doctor/my`, `GET /api/appointments/{id}`, `PUT /api/appointments/{id}/status`, `POST /api/prescriptions`, `PUT /api/prescriptions/{id}`, `GET /api/prescriptions/doctor/my`

**Test scenarios**
1. Log in as `asha.verma@clinic.test` -> appointments page shows her 6 appointments only.
2. Try `GET /api/appointments/6` (belongs to Dr. Priya Nair) with her token -> 403.
3. Mark a future appointment COMPLETED -> 409 ("before it starts").
4. Create a prescription for her own appointment -> 201; for another doctor's appointment -> 403.

---

## 3. Patient - Access clinic services

**Story title:** Patient registers, finds a doctor and books an appointment

**User story:** As a Patient, I want to register, log in, search doctors, book appointments, and view my appointment history so that I can access clinic services conveniently.

**Preconditions**
- The application is running and at least one active doctor exists.

**Acceptance criteria**
1. A visitor can register at `/register`; the account is always a Patient (a role cannot be chosen).
2. Registration rejects a duplicate email (409) and invalid fields (400).
3. The Patient can log in at `/patient/login`.
4. The Patient can search doctors by name, filter by specialty and optionally filter by "available at" date-time.
5. The Patient can pick a weekday, see the doctor's free 30-minute slots and book one.
6. A slot that is taken, in the past, outside working hours or on a weekend cannot be booked (409/400).
7. The Patient sees only their own appointments with status, and can cancel a scheduled appointment at least 2 hours before it starts.
8. The Patient can view their own prescriptions only.
9. Opening or cancelling another patient's appointment returns HTTP 403.

**Expected behavior**
- Meaningful HTTP codes and messages for every rule above.

**Relevant API endpoints**
`POST /api/auth/register`, `POST /api/auth/login`, `GET /api/doctors`, `GET /api/doctors/search`, `GET /api/doctors/specialties`, `GET /api/doctors/availability`, `GET /api/doctors/{id}/slots`, `POST /api/appointments`, `GET /api/appointments/my`, `DELETE /api/appointments/{id}`, `GET /api/prescriptions/patient/{patientId}`

**Test scenarios**
1. Register `new.patient@example.test`, log in, search "Verma" -> Dr. Asha Verma is returned.
2. Filter specialty "Cardiology" -> two doctors.
3. Book Dr. Verma on a free weekday slot -> 201; book the same slot as another patient -> 409.
4. Try to book a Saturday -> 409; a time in the past -> 400.
5. Cancel own appointment -> 204; cancel someone else's -> 403.

---

## How to create the three GitHub issues (Q1)

1. Open https://github.com/gnaneshwardammu/java-database-capstone/issues and click **New issue**.
2. After you push the `.github/ISSUE_TEMPLATE` folder, choose the template **Admin user story**, **Doctor user story** or **Patient user story** (otherwise choose "Open a blank issue" and paste the text below).
3. Use these exact titles:
   - `[Admin] Admin manages doctors and monitors appointments`
   - `[Doctor] Doctor views schedule and patient details`
   - `[Patient] Patient registers, finds a doctor and books an appointment`
4. Paste the matching user story statement, acceptance criteria, preconditions, expected behavior, endpoints and test scenarios from this file into the body, then click **Submit new issue**.
5. Repeat for all three roles. Copy the three issue URLs from your browser for your submission (do not guess them - they exist only after you create the issues).
