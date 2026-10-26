/* Admin pages: dashboard, doctor list, doctor add/edit form. */
document.addEventListener('DOMContentLoaded', () => {
  const page = document.body.dataset.page;
  if (page === 'admin-dashboard') initAdminDashboard();
  if (page === 'admin-doctors') initAdminDoctors();
  if (page === 'admin-doctor-form') initDoctorForm();
});

const STATUSES = ['SCHEDULED', 'COMPLETED', 'CANCELLED', 'NO_SHOW'];

async function initAdminDashboard() {
  if (!Clinic.requireRole('ADMIN')) return;
  const statsEl = document.getElementById('stats');
  const apptEl = document.getElementById('appointments');
  const patEl = document.getElementById('patients');
  statsEl.innerHTML = Clinic.loading();

  async function load() {
    try {
      const [stats, appts, patients] = await Promise.all([
        Clinic.api('/api/admin/stats'), Clinic.api('/api/appointments'), Clinic.api('/api/admin/patients')]);
      const cards = [['Doctors', stats.totalDoctors + ' (' + stats.activeDoctors + ' active)'],
        ['Patients', stats.totalPatients], ['Appointments', stats.totalAppointments],
        ['Scheduled', stats.scheduledAppointments], ['Completed', stats.completedAppointments],
        ['Today', stats.appointmentsToday]];
      statsEl.innerHTML = cards.map(([label, value]) =>
        '<div class="col-6 col-md-4 col-xl-2"><div class="card stat-card h-100"><div class="card-body text-center">' +
        '<div class="stat-value">' + Clinic.esc(value) + '</div><div class="text-muted small">' + Clinic.esc(label) +
        '</div></div></div></div>').join('');

      apptEl.innerHTML = appts.length === 0 ? Clinic.empty('No appointments yet.') :
        '<div class="table-responsive"><table class="table table-hover align-middle mb-0 bg-white"><thead><tr>' +
        '<th>When</th><th>Doctor</th><th>Patient</th><th>Reason</th><th>Status</th></tr></thead><tbody>' +
        appts.map(a => '<tr><td>' + Clinic.esc(Clinic.fmtDateTime(a.startTime)) + '</td><td>' + Clinic.esc(a.doctorName) +
          '<div class="small text-muted">' + Clinic.esc(a.doctorSpecialty) + '</div></td><td>' + Clinic.esc(a.patientName) +
          '</td><td>' + Clinic.esc(a.reason || '') + '</td><td>' + statusCell(a) + '</td></tr>').join('') +
        '</tbody></table></div>';

      patEl.innerHTML = patients.length === 0 ? Clinic.empty('No patients registered yet.') :
        '<div class="table-responsive"><table class="table table-hover mb-0 bg-white"><thead><tr><th>Name</th><th>Email</th>' +
        '<th>Phone</th><th>Registered</th></tr></thead><tbody>' +
        patients.map(p => '<tr><td>' + Clinic.esc(p.name) + '</td><td>' + Clinic.esc(p.email) + '</td><td>' +
          Clinic.esc(p.phone || '') + '</td><td>' + Clinic.esc(Clinic.fmtDateTime(p.createdAt)) + '</td></tr>').join('') +
        '</tbody></table></div>';
    } catch (err) {
      statsEl.innerHTML = '';
      Clinic.toast(Clinic.errorText(err), 'error');
    }
  }

  function statusCell(a) {
    if (a.status !== 'SCHEDULED') return Clinic.statusBadge(a.status);
    return '<select class="form-select form-select-sm status-select" data-id="' + a.id + '" aria-label="Change status">' +
      STATUSES.map(s => '<option value="' + s + '"' + (s === a.status ? ' selected' : '') + '>' + s + '</option>').join('') + '</select>';
  }

  apptEl.addEventListener('change', async (e) => {
    if (!e.target.classList.contains('status-select')) return;
    const status = e.target.value;
    if (status === 'SCHEDULED') return;
    if (!window.confirm('Change this appointment to ' + status + '? This cannot be undone.')) { load(); return; }
    try {
      await Clinic.api('/api/appointments/' + e.target.dataset.id + '/status', { method: 'PUT', body: { status } });
      Clinic.toast('Appointment updated');
    } catch (err) {
      Clinic.toast(Clinic.errorText(err), 'error');
    }
    load();
  });
  load();
}

async function initAdminDoctors() {
  if (!Clinic.requireRole('ADMIN')) return;
  const listEl = document.getElementById('doctorList');
  const searchEl = document.getElementById('doctorFilter');
  let doctors = [];

  function render() {
    const q = searchEl.value.trim().toLowerCase();
    const rows = doctors.filter(d => !q || d.name.toLowerCase().includes(q) || d.specialty.toLowerCase().includes(q));
    listEl.innerHTML = rows.length === 0 ? Clinic.empty('No doctors match your search.') :
      '<div class="table-responsive"><table class="table table-hover align-middle mb-0 bg-white"><thead><tr><th>Name</th>' +
      '<th>Specialty</th><th>Email</th><th>Hours (Mon-Fri)</th><th>Status</th><th class="text-end">Actions</th></tr></thead><tbody>' +
      rows.map(d => '<tr><td>' + Clinic.esc(d.name) + '</td><td>' + Clinic.esc(d.specialty) + '</td><td>' + Clinic.esc(d.email) +
        '</td><td>' + Clinic.esc(Clinic.fmtTime(d.availableFrom) + ' - ' + Clinic.fmtTime(d.availableTo)) + '</td><td>' +
        (d.active ? '<span class="badge text-bg-success">Active</span>' : '<span class="badge text-bg-secondary">Inactive</span>') +
        '</td><td class="text-end text-nowrap"><a class="btn btn-sm btn-outline-primary me-1" href="/admin/doctors/' + d.id +
        '/edit">Edit</a><button class="btn btn-sm btn-outline-danger delete-btn" data-id="' + d.id + '" data-name="' +
        Clinic.esc(d.name) + '">Delete</button></td></tr>').join('') + '</tbody></table></div>';
  }

  async function load() {
    listEl.innerHTML = Clinic.loading();
    try { doctors = await Clinic.api('/api/admin/doctors'); render(); }
    catch (err) { listEl.innerHTML = ''; Clinic.toast(Clinic.errorText(err), 'error'); }
  }

  searchEl.addEventListener('input', render);
  listEl.addEventListener('click', async (e) => {
    const btn = e.target.closest('.delete-btn');
    if (!btn) return;
    if (!window.confirm('Delete ' + btn.dataset.name + '? This cannot be undone.')) return;
    try {
      await Clinic.api('/api/doctors/' + btn.dataset.id, { method: 'DELETE' });
      Clinic.toast('Doctor deleted');
      load();
    } catch (err) {
      Clinic.toast(Clinic.errorText(err), 'error');
    }
  });
  load();
}

async function initDoctorForm() {
  if (!Clinic.requireRole('ADMIN')) return;
  const form = document.getElementById('doctorForm');
  const alertBox = document.getElementById('formAlert');
  const id = document.body.dataset.doctorId;
  const f = form.elements;

  if (id) {
    document.getElementById('formTitle').textContent = 'Edit doctor';
    f.password.required = false;
    document.getElementById('passwordHelp').textContent = 'Leave blank to keep the current password.';
    try {
      const d = await Clinic.api('/api/admin/doctors/' + id);
      f.name.value = d.name; f.specialty.value = d.specialty; f.email.value = d.email;
      f.phone.value = d.phone || ''; f.availableFrom.value = Clinic.fmtTime(d.availableFrom);
      f.availableTo.value = Clinic.fmtTime(d.availableTo); f.active.checked = d.active;
    } catch (err) {
      Clinic.showAlert(alertBox, Clinic.errorText(err));
    }
  }

  form.addEventListener('submit', async (e) => {
    e.preventDefault();
    Clinic.hideAlert(alertBox);
    if (!form.checkValidity()) { form.classList.add('was-validated'); return; }
    const body = {
      name: f.name.value.trim(), specialty: f.specialty.value.trim(), email: f.email.value.trim(),
      password: f.password.value ? f.password.value : null, phone: f.phone.value.trim() || null,
      availableFrom: f.availableFrom.value, availableTo: f.availableTo.value, active: f.active.checked
    };
    const btn = form.querySelector('button[type=submit]');
    Clinic.setBusy(btn, true, 'Saving');
    try {
      await Clinic.api(id ? '/api/doctors/' + id : '/api/doctors', { method: id ? 'PUT' : 'POST', body });
      Clinic.toast(id ? 'Doctor updated' : 'Doctor added successfully');
      setTimeout(() => { window.location.href = '/admin/doctors'; }, 900);
    } catch (err) {
      Clinic.showAlert(alertBox, Clinic.errorText(err));
      Clinic.setBusy(btn, false);
    }
  });
}
