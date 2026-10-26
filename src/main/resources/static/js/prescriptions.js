/* Prescriptions page: patients read, doctors create/edit. */
document.addEventListener('DOMContentLoaded', () => {
  if (document.body.dataset.page === 'prescriptions') initPrescriptions();
});

function medicineRow(m = {}) {
  const v = (x) => Clinic.esc(x || '');
  return '<div class="row g-2 mb-2 medicine-row">' +
    '<div class="col-md-3"><input class="form-control" name="mName" placeholder="Medicine" aria-label="Medicine name" maxlength="100" required value="' + v(m.name) + '"></div>' +
    '<div class="col-md-3"><input class="form-control" name="mDosage" placeholder="Dosage (e.g. 500 mg)" aria-label="Dosage" maxlength="100" required value="' + v(m.dosage) + '"></div>' +
    '<div class="col-md-2"><input class="form-control" name="mFrequency" placeholder="Frequency" aria-label="Frequency" maxlength="100" required value="' + v(m.frequency) + '"></div>' +
    '<div class="col-md-3"><input class="form-control" name="mDuration" placeholder="Duration" aria-label="Duration" maxlength="100" required value="' + v(m.duration) + '"></div>' +
    '<div class="col-md-1"><button type="button" class="btn btn-outline-danger w-100 remove-med" aria-label="Remove medicine">&times;</button></div></div>';
}

function prescriptionCard(p, canEdit) {
  const meds = (p.medicines || []).map(m => '<tr><td>' + Clinic.esc(m.name) + '</td><td>' + Clinic.esc(m.dosage) + '</td><td>' +
    Clinic.esc(m.frequency) + '</td><td>' + Clinic.esc(m.duration) + '</td></tr>').join('');
  return '<div class="card mb-3"><div class="card-body"><div class="d-flex justify-content-between flex-wrap gap-2 mb-2">' +
    '<div><strong>Appointment #' + Clinic.esc(p.appointmentId) + '</strong> &middot; Patient #' + Clinic.esc(p.patientId) + ' &middot; Doctor #' + Clinic.esc(p.doctorId) +
    '</div><span class="text-muted small">' + Clinic.esc(Clinic.fmtDateTime(p.createdAt)) + '</span></div>' +
    '<div class="table-responsive"><table class="table table-sm"><thead><tr><th>Medicine</th><th>Dosage</th><th>Frequency</th><th>Duration</th></tr></thead><tbody>' +
    meds + '</tbody></table></div>' + (p.instructions ? '<p class="mb-2"><strong>Instructions:</strong> ' + Clinic.esc(p.instructions) + '</p>' : '') +
    (canEdit ? '<button class="btn btn-sm btn-outline-primary edit-rx" data-id="' + Clinic.esc(p.id) + '">Edit</button>' : '') + '</div></div>';
}

async function initPrescriptions() {
  const auth = Clinic.requireRole('PATIENT', 'DOCTOR');
  if (!auth) return;
  const listEl = document.getElementById('rxList');
  const formCard = document.getElementById('rxFormCard');
  let items = [];

  async function load() {
    listEl.innerHTML = Clinic.loading();
    try {
      items = auth.role === 'PATIENT'
        ? await Clinic.api('/api/prescriptions/patient/' + auth.userId)
        : await Clinic.api('/api/prescriptions/doctor/my');
      listEl.innerHTML = items.length === 0 ? Clinic.empty('No prescriptions yet.') :
        items.map(p => prescriptionCard(p, auth.role === 'DOCTOR')).join('');
    } catch (err) {
      listEl.innerHTML = '';
      Clinic.toast(Clinic.errorText(err), 'error');
    }
  }

  if (auth.role === 'PATIENT') { load(); return; }

  // ----- doctor: create / edit form -----
  formCard.classList.remove('d-none');
  const form = document.getElementById('rxForm');
  const medsEl = document.getElementById('medicines');
  const alertBox = document.getElementById('formAlert');
  let editingId = null;
  medsEl.innerHTML = medicineRow();

  const appointmentId = Clinic.params().get('appointmentId');
  if (appointmentId) form.elements.appointmentId.value = appointmentId;

  document.getElementById('addMed').addEventListener('click', () => medsEl.insertAdjacentHTML('beforeend', medicineRow()));
  medsEl.addEventListener('click', (e) => {
    if (e.target.closest('.remove-med') && medsEl.querySelectorAll('.medicine-row').length > 1) {
      e.target.closest('.medicine-row').remove();
    }
  });

  listEl.addEventListener('click', (e) => {
    const btn = e.target.closest('.edit-rx');
    if (!btn) return;
    const p = items.find(x => x.id === btn.dataset.id);
    if (!p) return;
    editingId = p.id;
    form.elements.appointmentId.value = p.appointmentId;
    form.elements.appointmentId.readOnly = true;
    form.elements.instructions.value = p.instructions || '';
    medsEl.innerHTML = (p.medicines || []).map(medicineRow).join('');
    document.getElementById('rxFormTitle').textContent = 'Edit prescription';
    formCard.scrollIntoView({ behavior: 'smooth' });
  });

  form.addEventListener('submit', async (e) => {
    e.preventDefault();
    Clinic.hideAlert(alertBox);
    if (!form.checkValidity()) { form.classList.add('was-validated'); return; }
    const medicines = [...medsEl.querySelectorAll('.medicine-row')].map(row => ({
      name: row.querySelector('[name=mName]').value.trim(), dosage: row.querySelector('[name=mDosage]').value.trim(),
      frequency: row.querySelector('[name=mFrequency]').value.trim(), duration: row.querySelector('[name=mDuration]').value.trim() }));
    const instructions = form.elements.instructions.value.trim() || null;
    const btn = form.querySelector('button[type=submit]');
    Clinic.setBusy(btn, true, 'Saving');
    try {
      if (editingId) {
        await Clinic.api('/api/prescriptions/' + editingId, { method: 'PUT', body: { medicines, instructions } });
      } else {
        await Clinic.api('/api/prescriptions', { method: 'POST',
          body: { appointmentId: Number(form.elements.appointmentId.value), medicines, instructions } });
      }
      Clinic.toast('Prescription saved');
      editingId = null;
      form.reset();
      form.classList.remove('was-validated');
      form.elements.appointmentId.readOnly = false;
      medsEl.innerHTML = medicineRow();
      document.getElementById('rxFormTitle').textContent = 'New prescription';
      load();
    } catch (err) {
      Clinic.showAlert(alertBox, Clinic.errorText(err));
    }
    Clinic.setBusy(btn, false);
  });
  load();
}
