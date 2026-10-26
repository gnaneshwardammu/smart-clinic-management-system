# Assessment checklist - all 26 questions

Legend: **File** = already in the repository after you push. **You capture** = you must run it and take the real screenshot/output yourself. Never use example output as evidence.

Prepare once (PowerShell, in `app`):
```powershell
$env:DB_PASSWORD = "your-mysql-password"
$env:JWT_SECRET  = "paste-a-random-48-character-string-here-please-123"
mvn spring-boot:run
```
Load the SQL first (README section 8): `schema.sql`, `sample-data.sql`, `stored-procedures.sql`.

| Q | Deliverable | Where / what to do | Evidence |
|---|---|---|---|
| 1 | GitHub issues + user stories | `user_stories.md`; create 3 issues as described at the end of that file | You: 3 issue URLs |
| 2 | Public link to `schema-design.md` | push, then open the file on GitHub | You: the GitHub URL |
| 3 | Doctor model | `app/src/main/java/com/project/back_end/models/Doctor.java` | File link on GitHub |
| 4 | Appointment model | `.../models/Appointment.java` | File link |
| 5 | Doctor controller | `.../controllers/DoctorController.java` | File link |
| 6 | Appointment service | `.../services/AppointmentService.java` | File link |
| 7 | Prescription controller | `.../controllers/PrescriptionController.java` | File link |
| 8 | Patient repository | `.../repo/PatientRepository.java` | File link |
| 9 | Token service | `.../services/TokenService.java` | File link |
| 10 | Doctor service | `.../services/DoctorService.java` | File link |
| 11 | Dockerfile | `Dockerfile` (repository root) | File link |
| 12 | GitHub Actions workflow | `.github/workflows/compile-backend.yml` | File link (and the green run on the Actions tab) |

## Screenshots (app running on port 8081)
- **Q13** open <http://localhost:8081/admin/login> -> screenshot of the page.
- **Q14** open <http://localhost:8081/doctor/login> -> screenshot.
- **Q15** open <http://localhost:8081/patient/login> -> screenshot.
- **Q16** log in at `/admin/login` as `admin@clinic.test` / `Password@123` -> **Doctors** -> **Add doctor**, fill the form (e.g. Dr. Neha Gupta, Neurology, `neha.gupta@clinic.test`, password `Password@123`) -> **Save doctor**. Screenshot the green success message / the new doctor in the list.
- **Q17** log in at `/patient/login` as `anita.sharma@example.test` -> **Find doctors** -> type `Verma` in *Doctor name* -> **Search**. Screenshot the result card.
- **Q18** log in at `/doctor/login` as `asha.verma@clinic.test` -> **Appointments**. Screenshot the table.

## MySQL (Workbench; capture the Result Grid)
- **Q19** `USE capstone; SHOW TABLES;`
- **Q20** `SELECT * FROM patient LIMIT 5;` (or `SELECT id, name, email, phone FROM patient ORDER BY id LIMIT 5;`) - five rows with the sample data.
- **Q21** `CALL GetDailyAppointmentReportByDoctor(1, '2026-08-04');`
- **Q22** `CALL GetDoctorWithMostPatientsByMonth(2026, 8);`
- **Q23** `CALL GetDoctorWithMostPatientsByYear(2026);`

## curl (PowerShell, copy the full console output)
- **Q24** `curl.exe -i http://localhost:8081/api/doctors` (this endpoint is public, so no token is needed).
- **Q25** patient's appointments (token required):
```powershell
# 1) put the login JSON in a file (avoids PowerShell quote problems)
Set-Content -Path login.json -Value '{"email":"anita.sharma@example.test","password":"Password@123","role":"PATIENT"}' -Encoding ascii
# 2) log in and keep the token
curl.exe -s -X POST http://localhost:8081/api/auth/login -H "Content-Type: application/json" --data-binary "@login.json"
$token = (curl.exe -s -X POST http://localhost:8081/api/auth/login -H "Content-Type: application/json" --data-binary "@login.json" | ConvertFrom-Json).token
# 3) call the endpoint with the token
curl.exe -i http://localhost:8081/api/appointments/my -H "Authorization: Bearer $token"
```
  Capture the output of step 3 (and step 2 if you want to show the login). Without the header the same call must return `401`:
  `curl.exe -i http://localhost:8081/api/appointments/my`
- **Q26** doctors by specialty and time (date must be in the future; with the sample data Nov 3 2026 is a Tuesday - if that date has passed, use any later weekday, the seeded bookings are then simply not in the way):
```powershell
curl.exe -i "http://localhost:8081/api/doctors/availability?specialty=Cardiology&dateTime=2026-11-03T12:00"
curl.exe -i "http://localhost:8081/api/doctors/availability?specialty=Cardiology&dateTime=2026-11-03T11:00"
curl.exe -i "http://localhost:8081/api/doctors/availability?specialty=Cardiology&dateTime=2026-11-03T10:00"
```
  The sample data is designed so that 12:00 returns both cardiologists, 11:00 only Dr. Asha Verma (Dr. Sameer Khan starts at 12:00) and 10:00 returns none (Dr. Verma is booked, Dr. Khan is not working yet). Run it yourself and capture what you actually get.

## Before you submit
1. `cd app; mvn clean verify` passes.
2. `git add . ; git commit -m "..." ; git push` - for the architecture document use the commit message the course requires: `Added architecture design for Smart Clinic app`.
3. Check the files are visible on GitHub while logged out of GitHub (public repository).
