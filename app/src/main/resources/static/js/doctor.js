/* Doctor pages: dashboard and appointment list. */
document.addEventListener('DOMContentLoaded', () => {
  const page = document.body.dataset.page;
  if (page === 'doctor-dashboard') initDoctorDashboard();
  if (page === 'doctor-appointments') initDoctorAppointments();
});

async function initDoctorDashboard() {
  if (!Clinic.requireRole('DOCTOR')) return;
  const form = document.getElementById('profileForm');
  const alertBox = document.getElementById('formAlert');
  try {
    const [me, appts] = await Promise.all([Clinic.api('/api/doctors/me'), Clinic.api('/api/appointments/doctor/my')]);
    document.getElementById('welcome').textContent = 'Welcome, ' + me.name;
    form.elements.name.value = me.name;
    form.elements.phone.value = me.phone || '';
    document.getElementById('profileInfo').innerHTML =
      '<dt class="col-4">Specialty</dt><dd class="col-8">' + Clinic.esc(me.specialty) + '</dd>' +
      '<dt class="col-4">Email</dt><dd class="col-8">' + Clinic.esc(me.email) + '</dd>' +
      '<dt class="col-4">Hours</dt><dd class="col-8">Mon-Fri, ' + Clinic.esc(Clinic.fmtTime(me.availableFrom) + ' - ' + Clinic.fmtTime(me.availableTo)) + '</dd>';
    const now = new Date();
    const upcoming = appts.filter(a => a.status === 'SCHEDULED' && new Date(a.startTime) >= now);
    const today = appts.filter(a => new Date(a.startTime).toDateString() === now.toDateString() && a.status !== 'CANCELLED');
    document.getElementById('statUpcoming').textContent = upcoming.length;
    document.getElementById('statToday').textContent = today.length;
    document.getElementById('statTotal').textContent = appts.length;
    document.getElementById('nextList').innerHTML = upcoming.length === 0 ? Clinic.empty('No upcoming appointments.') :
      '<ul class="list-group list-group-flush">' + upcoming.slice(0, 5).map(a =>
        '<li class="list-group-item"><strong>' + Clinic.esc(Clinic.fmtDateTime(a.startTime)) + '</strong> - ' +
        Clinic.esc(a.patientName) + '<div class="small text-muted">' + Clinic.esc(a.reason || '') + '</div></li>').join('') + '</ul>';
  } catch (err) {
    Clinic.toast(Clinic.errorText(err), 'error');
  }

  form.addEventListener('submit', async (e) => {
    e.preventDefault();
    Clinic.hideAlert(alertBox);
    if (!form.checkValidity()) { form.classList.add('was-validated'); return; }
    const btn = form.querySelector('button[type=submit]');
    Clinic.setBusy(btn, true, 'Saving');
    try {
      await Clinic.api('/api/doctors/me', { method: 'PUT',
        body: { name: form.elements.name.value.trim(), phone: form.elements.phone.value.trim() || null } });
      Clinic.toast('Profile updated');
    } catch (err) {
      Clinic.showAlert(alertBox, Clinic.errorText(err));
    }
    Clinic.setBusy(btn, false);
  });
}

async function initDoctorAppointments() {
  if (!Clinic.requireRole('DOCTOR')) return;
  const listEl = document.getElementById('appointmentList');
  const filterEl = document.getElementById('statusFilter');
  let appts = [];

  function actions(a) {
    if (a.status !== 'SCHEDULED') {
      return '<a class="btn btn-sm btn-outline-primary" href="/prescriptions?appointmentId=' + a.id + '">Prescription</a>';
    }
    return '<div class="btn-group btn-group-sm" role="group" aria-label="Appointment actions">' +
      '<button class="btn btn-outline-success act-btn" data-id="' + a.id + '" data-status="COMPLETED">Complete</button>' +
      '<button class="btn btn-outline-warning act-btn" data-id="' + a.id + '" data-status="NO_SHOW">No-show</button>' +
      '<button class="btn btn-outline-danger act-btn" data-id="' + a.id + '" data-status="CANCELLED">Cancel</button></div> ' +
      '<a class="btn btn-sm btn-outline-primary" href="/prescriptions?appointmentId=' + a.id + '">Prescribe</a>';
  }

  function render() {
    const f = filterEl.value;
    const rows = appts.filter(a => !f || a.status === f);
    listEl.innerHTML = rows.length === 0 ? Clinic.empty('No appointments to show.') :
      '<div class="table-responsive"><table class="table table-hover align-middle mb-0 bg-white"><thead><tr><th>When</th>' +
      '<th>Patient</th><th>Contact</th><th>Reason</th><th>Status</th><th>Actions</th></tr></thead><tbody>' +
      rows.map(a => '<tr><td>' + Clinic.esc(Clinic.fmtDateTime(a.startTime)) + '</td><td>' + Clinic.esc(a.patientName) +
        '</td><td class="small">' + Clinic.esc(a.patientEmail) + '<br>' + Clinic.esc(a.patientPhone || '') + '</td><td>' +
        Clinic.esc(a.reason || '') + '</td><td>' + Clinic.statusBadge(a.status) + '</td><td class="text-nowrap">' + actions(a) +
        '</td></tr>').join('') + '</tbody></table></div>';
  }

  async function load() {
    listEl.innerHTML = Clinic.loading();
    try { appts = await Clinic.api('/api/appointments/doctor/my'); render(); }
    catch (err) { listEl.innerHTML = ''; Clinic.toast(Clinic.errorText(err), 'error'); }
  }

  filterEl.addEventListener('change', render);
  listEl.addEventListener('click', async (e) => {
    const btn = e.target.closest('.act-btn');
    if (!btn) return;
    if (!window.confirm('Mark this appointment as ' + btn.dataset.status + '? This cannot be undone.')) return;
    try {
      await Clinic.api('/api/appointments/' + btn.dataset.id + '/status', { method: 'PUT', body: { status: btn.dataset.status } });
      Clinic.toast('Appointment updated');
      load();
    } catch (err) {
      Clinic.toast(Clinic.errorText(err), 'error');
    }
  });
  load();
}
