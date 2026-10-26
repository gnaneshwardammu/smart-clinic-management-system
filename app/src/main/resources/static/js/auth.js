/* Login and registration pages. */
document.addEventListener('DOMContentLoaded', () => {
  const page = document.body.dataset.page;
  if (page === 'login') initLogin();
  if (page === 'register') initRegister();
});

function initLogin() {
  const form = document.getElementById('loginForm');
  const role = form.dataset.role;
  const alertBox = document.getElementById('formAlert');
  const existing = Clinic.getAuth();
  if (existing && existing.role === role) { window.location.href = Clinic.HOME[role]; return; }
  if (Clinic.params().get('registered')) {
    Clinic.showAlert(alertBox, 'Account created. Please sign in.', 'success');
  }
  form.addEventListener('submit', async (e) => {
    e.preventDefault();
    Clinic.hideAlert(alertBox);
    if (!form.checkValidity()) { form.classList.add('was-validated'); return; }
    const btn = form.querySelector('button[type=submit]');
    Clinic.setBusy(btn, true, 'Signing in');
    try {
      const resp = await Clinic.api('/api/auth/login', {
        method: 'POST', anonymous: true,
        body: { email: form.elements.email.value.trim(), password: form.elements.password.value, role }
      });
      Clinic.setAuth(resp);
      window.location.href = Clinic.HOME[role];
    } catch (err) {
      Clinic.showAlert(alertBox, Clinic.errorText(err));
      Clinic.setBusy(btn, false);
    }
  });
}

function initRegister() {
  const form = document.getElementById('registerForm');
  const alertBox = document.getElementById('formAlert');
  form.addEventListener('submit', async (e) => {
    e.preventDefault();
    Clinic.hideAlert(alertBox);
    if (!form.checkValidity()) { form.classList.add('was-validated'); return; }
    const f = form.elements;
    const btn = form.querySelector('button[type=submit]');
    Clinic.setBusy(btn, true, 'Creating account');
    try {
      await Clinic.api('/api/auth/register', {
        method: 'POST', anonymous: true,
        body: {
          name: f.name.value.trim(), email: f.email.value.trim(), password: f.password.value,
          phone: f.phone.value.trim() || null, dateOfBirth: f.dateOfBirth.value || null,
          address: f.address.value.trim() || null
        }
      });
      window.location.href = '/patient/login?registered=1';
    } catch (err) {
      Clinic.showAlert(alertBox, Clinic.errorText(err));
      Clinic.setBusy(btn, false);
    }
  });
}
