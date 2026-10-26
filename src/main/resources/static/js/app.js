/* Shared helpers for every page: JWT storage, API calls, navigation, toasts. */
const Clinic = (() => {
  const KEY = 'clinic.auth';
  const HOME = { ADMIN: '/admin/dashboard', DOCTOR: '/doctor/dashboard', PATIENT: '/patient/dashboard' };
  const LOGIN = { ADMIN: '/admin/login', DOCTOR: '/doctor/login', PATIENT: '/patient/login' };
  const NAV = {
    ADMIN: [['Dashboard', '/admin/dashboard'], ['Doctors', '/admin/doctors'], ['Add doctor', '/admin/doctors/new']],
    DOCTOR: [['Dashboard', '/doctor/dashboard'], ['Appointments', '/doctor/appointments'], ['Prescriptions', '/prescriptions']],
    PATIENT: [['Dashboard', '/patient/dashboard'], ['Find doctors', '/patient/doctors'], ['My appointments', '/patient/appointments'], ['Prescriptions', '/prescriptions']]
  };

  // The token lives in sessionStorage: it disappears when the browser tab is closed.
  function getAuth() {
    try { return JSON.parse(sessionStorage.getItem(KEY)); } catch (e) { return null; }
  }
  function setAuth(auth) { sessionStorage.setItem(KEY, JSON.stringify(auth)); }
  function clearAuth() { sessionStorage.removeItem(KEY); }

  class ApiError extends Error {
    constructor(status, message, fieldErrors) {
      super(message);
      this.status = status;
      this.fieldErrors = fieldErrors || null;
    }
  }

  async function api(path, opts = {}) {
    const headers = { Accept: 'application/json' };
    if (opts.body !== undefined) headers['Content-Type'] = 'application/json';
    const auth = getAuth();
    if (auth && !opts.anonymous) headers.Authorization = 'Bearer ' + auth.token;
    let res;
    try {
      res = await fetch(path, {
        method: opts.method || 'GET',
        headers,
        body: opts.body !== undefined ? JSON.stringify(opts.body) : undefined
      });
    } catch (e) {
      throw new ApiError(0, 'Cannot reach the server. Is the application running?');
    }
    if (res.status === 204) return null;
    let data = null;
    const text = await res.text();
    if (text) { try { data = JSON.parse(text); } catch (e) { data = null; } }
    if (!res.ok) {
      if (res.status === 401 && auth && !opts.anonymous) {
        clearAuth();
        window.location.href = LOGIN[auth.role] || '/';
      }
      throw new ApiError(res.status, (data && data.message) || ('Request failed (' + res.status + ')'),
        data && data.fieldErrors);
    }
    return data;
  }

  /** Call at the top of each protected page. Returns the auth info or null (and redirects). */
  function requireRole(...roles) {
    let auth = getAuth();
    if (auth && auth.expiresAt && new Date(auth.expiresAt) < new Date()) { clearAuth(); auth = null; }
    if (!auth) { window.location.href = LOGIN[roles[0]] || '/'; return null; }
    if (!roles.includes(auth.role)) { window.location.href = '/access-denied'; return null; }
    renderNav(auth);
    return auth;
  }

  function renderNav(auth) {
    const links = document.getElementById('navLinks');
    if (links && auth) {
      const path = window.location.pathname;
      links.innerHTML = NAV[auth.role].map(([label, href]) =>
        '<li class="nav-item"><a class="nav-link' + (path === href ? ' active' : '') + '" href="' + href + '">' + esc(label) + '</a></li>').join('');
    }
    const user = document.getElementById('navUser');
    if (user && auth) user.textContent = auth.name + ' (' + auth.role.toLowerCase() + ')';
    const btn = document.getElementById('logoutBtn');
    if (btn && auth) { btn.classList.remove('d-none'); btn.onclick = logout; }
  }

  async function logout() {
    try { await api('/api/auth/logout', { method: 'POST' }); } catch (e) { /* ignore */ }
    clearAuth();
    window.location.href = '/';
  }

  function esc(value) {
    return String(value === null || value === undefined ? '' : value)
      .replace(/&/g, '&amp;').replace(/</g, '&lt;').replace(/>/g, '&gt;')
      .replace(/"/g, '&quot;').replace(/'/g, '&#39;');
  }

  function fmtDateTime(iso) {
    if (!iso) return '';
    return new Date(iso).toLocaleString([], { dateStyle: 'medium', timeStyle: 'short' });
  }
  function fmtTime(t) { return t ? String(t).substring(0, 5) : ''; }

  const BADGE = { SCHEDULED: 'primary', COMPLETED: 'success', CANCELLED: 'secondary', NO_SHOW: 'warning text-dark' };
  function statusBadge(status) {
    return '<span class="badge text-bg-' + (BADGE[status] || 'light') + '">' + esc(status) + '</span>';
  }

  function toast(message, type = 'success') {
    let area = document.getElementById('toastArea');
    if (!area) {
      area = document.createElement('div');
      area.id = 'toastArea';
      area.className = 'toast-container position-fixed top-0 end-0 p-3';
      document.body.appendChild(area);
    }
    const el = document.createElement('div');
    el.className = 'toast align-items-center text-bg-' + (type === 'error' ? 'danger' : 'success') + ' border-0';
    el.setAttribute('role', 'alert');
    el.setAttribute('aria-live', 'assertive');
    el.innerHTML = '<div class="d-flex"><div class="toast-body"></div>' +
      '<button type="button" class="btn-close btn-close-white me-2 m-auto" data-bs-dismiss="toast" aria-label="Close"></button></div>';
    el.querySelector('.toast-body').textContent = message;
    area.appendChild(el);
    const t = new bootstrap.Toast(el, { delay: 5000 });
    el.addEventListener('hidden.bs.toast', () => el.remove());
    t.show();
  }

  /** Human readable text for an ApiError, including field-level validation messages. */
  function errorText(err) {
    if (!(err instanceof ApiError)) return 'Something went wrong.';
    let msg = err.message;
    if (err.fieldErrors) msg += ': ' + Object.values(err.fieldErrors).join('; ');
    return msg;
  }

  function showAlert(el, message, type = 'danger') {
    if (!el) return;
    el.className = 'alert alert-' + type;
    el.textContent = message;
    el.classList.remove('d-none');
  }
  function hideAlert(el) { if (el) el.classList.add('d-none'); }

  function setBusy(btn, busy, label) {
    if (!btn) return;
    if (busy) {
      btn.dataset.label = btn.innerHTML;
      btn.disabled = true;
      btn.innerHTML = '<span class="spinner-border spinner-border-sm me-1" aria-hidden="true"></span>' + esc(label || 'Please wait');
    } else {
      btn.disabled = false;
      if (btn.dataset.label) btn.innerHTML = btn.dataset.label;
    }
  }

  function empty(message) { return '<div class="empty-state">' + esc(message) + '</div>'; }
  function loading() { return '<div class="empty-state"><span class="spinner-border spinner-border-sm me-2" aria-hidden="true"></span>Loading...</div>'; }

  const params = () => new URLSearchParams(window.location.search);

  document.addEventListener('DOMContentLoaded', () => {
    // pages that are not protected (index, login) still show the logout button if signed in
    const auth = getAuth();
    if (auth && !document.body.dataset.protected) renderNav(auth);
  });

  return { HOME, LOGIN, getAuth, setAuth, clearAuth, api, requireRole, logout, esc, fmtDateTime, fmtTime,
    statusBadge, toast, errorText, showAlert, hideAlert, setBusy, empty, loading, params, ApiError };
})();
