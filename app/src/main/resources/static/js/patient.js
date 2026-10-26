/* Patient pages: dashboard, doctor directory, booking, appointment list. */
document.addEventListener('DOMContentLoaded', () => {
  const page = document.body.dataset.page;
  if (page === 'patient-dashboard') initPatientDashboard();
  if (page === 'patient-doctors') initPatientDoctors();
  if (page === 'patient-book') initPatientBook();
  if (page === 'patient-appointments') initPatientAppointments();
});

function doctorCard(d) {
  const initials = d.name.replace(/^Dr\.?\s*/i, '').split(/\s+/).map(p => p[0]).slice(0, 2).join('').toUpperCase();
  return '<div class="col-md-6 col-xl-4"><div class="card h-100"><div class="card-body d-flex flex-column">' +
    '<div class="d-flex align-items-center gap-3 mb-3"><div class="doctor-avatar" aria-hidden="true">' + Clinic.esc(initials) +
    '</div><div><h2 class="h6 mb-0">' + Clinic.esc(d.name) + '</h2><span class="text-muted small">' + Clinic.esc(d.specialty) +
    '</span></div></div><p class="small mb-3">Available Mon-Fri, ' + Clinic.esc(Clinic.fmtTime(d.availableFrom) + ' - ' + Clinic.fmtTime(d.availableTo)) +
    '</p><a class="btn btn-clinic btn-sm mt-auto" href="/patient/book?doctorId=' + d.id + '">Book appointment</a></div></div></div>';
}

async function initPatientDashboard() {
  const auth = Clinic.requireRole('PATIENT');
  if (!auth) return;
  document.getElementById('welcome').textContent = 'Welcome, ' + auth.name;
  document.getElementById('quickSearch').addEventListener('submit', (e) => {
    e.preventDefault();
    window.location.href = '/patient/doctors?name=' + encodeURIComponent(e.target.elements.name.value.trim());
  });
  const listEl = document.getElementById('upcoming');
  listEl.innerHTML = Clinic.loading();
  try {
    const appts = await Clinic.api('/api/appointments/my');
    const upcoming = appts.filter(a => a.status === 'SCHEDULED' && new Date(a.startTime) >= new Date())
      .sort((a, b) => new Date(a.startTime) - new Date(b.startTime));
    document.getElementById('statUpcoming').textContent = upcoming.length;
    document.getElementById('statTotal').textContent = appts.length;
    listEl.innerHTML = upcoming.length === 0 ? Clinic.empty('You have no upcoming appointments.') :
      '<ul class="list-group list-group-flush">' + upcoming.slice(0, 5).map(a =>
        '<li class="list-group-item"><strong>' + Clinic.esc(Clinic.fmtDateTime(a.startTime)) + '</strong> with ' +
        Clinic.esc(a.doctorName) + ' <span class="text-muted small">(' + Clinic.esc(a.doctorSpecialty) + ')</span></li>').join('') + '</ul>';
  } catch (err) {
    listEl.innerHTML = '';
    Clinic.toast(Clinic.errorText(err), 'error');
  }
}

async function initPatientDoctors() {
  if (!Clinic.requireRole('PATIENT')) return;
  const form = document.getElementById('searchForm');
  const resultsEl = document.getElementById('results');
  const specialtyEl = form.elements.specialty;

  try {
    const specialties = await Clinic.api('/api/doctors/specialties', { anonymous: true });
    specialtyEl.innerHTML = '<option value="">All specialties</option>' +
      specialties.map(s => '<option value="' + Clinic.esc(s) + '">' + Clinic.esc(s) + '</option>').join('');
  } catch (err) { /* the filter still works without the list */ }

  const q = Clinic.params();
  if (q.get('name')) form.elements.name.value = q.get('name');

  async function search() {
    resultsEl.innerHTML = Clinic.loading();
    const name = form.elements.name.value.trim();
    const specialty = specialtyEl.value;
    const when = form.elements.dateTime.value;
    try {
      let url;
      if (when) {
        url = '/api/doctors/availability?dateTime=' + encodeURIComponent(when) + (specialty ? '&specialty=' + encodeURIComponent(specialty) : '');
      } else {
        url = '/api/doctors/search?name=' + encodeURIComponent(name) + '&specialty=' + encodeURIComponent(specialty);
      }
      let doctors = await Clinic.api(url, { anonymous: true });
      if (when && name) doctors = doctors.filter(d => d.name.toLowerCase().includes(name.toLowerCase()));
      document.getElementById('resultCount').textContent = doctors.length + ' doctor' + (doctors.length === 1 ? '' : 's') + ' found';
      resultsEl.innerHTML = doctors.length === 0 ? '<div class="col-12">' + Clinic.empty('No doctors match your search.') + '</div>' :
        doctors.map(doctorCard).join('');
    } catch (err) {
      resultsEl.innerHTML = '';
      Clinic.toast(Clinic.errorText(err), 'error');
    }
  }

  form.addEventListener('submit', (e) => { e.preventDefault(); search(); });
  form.addEventListener('reset', () => setTimeout(search, 0));
  search();
}

async function initPatientBook() {
  if (!Clinic.requireRole('PATIENT')) return;
  const doctorId = Clinic.params().get('doctorId');
  const infoEl = document.getElementById('doctorInfo');
  const dateEl = document.getElementById('date');
  const slotsEl = document.getElementById('slots');
  const form = document.getElementById('bookForm');
  const alertBox = document.getElementById('formAlert');
  let chosen = null;

  if (!doctorId) {
    infoEl.innerHTML = Clinic.empty('No doctor selected. Pick one from the doctor directory.');
    form.classList.add('d-none');
    return;
  }
  try {
    const d = await Clinic.api('/api/doctors/' + encodeURIComponent(doctorId), { anonymous: true });
    infoEl.innerHTML = '<h2 class="h5 mb-1">' + Clinic.esc(d.name) + '</h2><div class="text-muted">' + Clinic.esc(d.specialty) +
      '</div><div class="small mt-1">Available Mon-Fri, ' + Clinic.esc(Clinic.fmtTime(d.availableFrom) + ' - ' + Clinic.fmtTime(d.availableTo)) + '</div>';
  } catch (err) {
    infoEl.innerHTML = Clinic.empty(Clinic.errorText(err));
    form.classList.add('d-none');
    return;
  }

  const today = new Date();
  dateEl.min = today.getFullYear() + '-' + String(today.getMonth() + 1).padStart(2, '0') + '-' + String(today.getDate()).padStart(2, '0');

  async function loadSlots() {
    chosen = null;
    if (!dateEl.value) { slotsEl.innerHTML = ''; return; }
    slotsEl.innerHTML = Clinic.loading();
    try {
      const slots = await Clinic.api('/api/doctors/' + encodeURIComponent(doctorId) + '/slots?date=' + dateEl.value, { anonymous: true });
      slotsEl.innerHTML = slots.length === 0 ? Clinic.empty('No free slots on this date. Try another weekday.') :
        slots.map(t => '<button type="button" class="btn btn-outline-primary slot-btn me-2 mb-2" data-time="' +
          Clinic.esc(Clinic.fmtTime(t)) + '">' + Clinic.esc(Clinic.fmtTime(t)) + '</button>').join('');
    } catch (err) {
      slotsEl.innerHTML = '';
      Clinic.showAlert(alertBox, Clinic.errorText(err));
    }
  }

  dateEl.addEventListener('change', () => { Clinic.hideAlert(alertBox); loadSlots(); });
  slotsEl.addEventListener('click', (e) => {
    const btn = e.target.closest('.slot-btn');
    if (!btn) return;
    slotsEl.querySelectorAll('.slot-btn').forEach(b => { b.classList.remove('active'); b.setAttribute('aria-pressed', 'false'); });
    btn.classList.add('active');
    btn.setAttribute('aria-pressed', 'true');
    chosen = btn.dataset.time;
  });

  form.addEventListener('submit', async (e) => {
    e.preventDefault();
    Clinic.hideAlert(alertBox);
    if (!dateEl.value || !chosen) { Clinic.showAlert(alertBox, 'Please choose a date and a time slot.'); return; }
    const btn = form.querySelector('button[type=submit]');
    Clinic.setBusy(btn, true, 'Booking');
    try {
      await Clinic.api('/api/appointments', { method: 'POST', body: {
        doctorId: Number(doctorId), startTime: dateEl.value + 'T' + chosen,
        reason: form.elements.reason.value.trim() || null } });
      Clinic.toast('Appointment booked');
      setTimeout(() => { window.location.href = '/patient/appointments'; }, 900);
    } catch (err) {
      Clinic.showAlert(alertBox, Clinic.errorText(err));
      Clinic.setBusy(btn, false);
      loadSlots();
    }
  });
}

async function initPatientAppointments() {
  if (!Clinic.requireRole('PATIENT')) return;
  const listEl = document.getElementById('appointmentList');

  async function load() {
    listEl.innerHTML = Clinic.loading();
    try {
      const appts = await Clinic.api('/api/appointments/my');
      listEl.innerHTML = appts.length === 0 ? Clinic.empty('You have not booked any appointments yet.') :
        '<div class="table-responsive"><table class="table table-hover align-middle mb-0 bg-white"><thead><tr><th>When</th>' +
        '<th>Doctor</th><th>Reason</th><th>Status</th><th>Actions</th></tr></thead><tbody>' +
        appts.map(a => '<tr><td>' + Clinic.esc(Clinic.fmtDateTime(a.startTime)) + '</td><td>' + Clinic.esc(a.doctorName) +
          '<div class="small text-muted">' + Clinic.esc(a.doctorSpecialty) + '</div></td><td>' + Clinic.esc(a.reason || '') +
          '</td><td>' + Clinic.statusBadge(a.status) + '</td><td>' +
          (a.status === 'SCHEDULED' ? '<button class="btn btn-sm btn-outline-danger cancel-btn" data-id="' + a.id + '">Cancel</button>' : '') +
          '</td></tr>').join('') + '</tbody></table></div>';
    } catch (err) {
      listEl.innerHTML = '';
      Clinic.toast(Clinic.errorText(err), 'error');
    }
  }

  listEl.addEventListener('click', async (e) => {
    const btn = e.target.closest('.cancel-btn');
    if (!btn) return;
    if (!window.confirm('Cancel this appointment? This cannot be undone.')) return;
    try {
      await Clinic.api('/api/appointments/' + btn.dataset.id, { method: 'DELETE' });
      Clinic.toast('Appointment cancelled');
      load();
    } catch (err) {
      Clinic.toast(Clinic.errorText(err), 'error');
    }
  });
  load();
}
