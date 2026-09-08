/* Office Platform — Portal web
 *
 * Login por cédula y montaje del gestor documental a pantalla completa.
 * El token nunca se guarda en localStorage: vive solo en memoria, así que cerrar
 * la pestaña termina la sesión. Es una decisión deliberada — el portal se usa en
 * equipos compartidos de planta, donde un token persistido es una sesión abierta
 * para el siguiente que se siente.
 */
(function () {
  'use strict';

  var SERVER = window.location.origin;

  var state = { cedula: null, displayName: null, token: null };

  var els = {
    login: document.getElementById('op-portal-login'),
    loginForm: document.getElementById('op-portal-form'),
    cedula: document.getElementById('op-portal-cedula'),
    password: document.getElementById('op-portal-password'),
    loginError: document.getElementById('op-portal-error'),
    loginSubmit: document.getElementById('op-portal-submit'),

    change: document.getElementById('op-portal-change'),
    changeForm: document.getElementById('op-portal-change-form'),
    newPass: document.getElementById('op-portal-new'),
    newPass2: document.getElementById('op-portal-new2'),
    rules: document.getElementById('op-portal-rules'),
    changeError: document.getElementById('op-portal-change-error'),
    changeSubmit: document.getElementById('op-portal-change-submit'),

    app: document.getElementById('op-portal-app'),
    username: document.getElementById('op-portal-username'),
    logout: document.getElementById('op-portal-logout'),
    widget: document.getElementById('office-platform'),
  };

  // ── Utilidades ────────────────────────────────────────────────────────────

  function showError(el, message) {
    el.textContent = message;
    el.hidden = false;
  }

  function clearError(el) {
    el.textContent = '';
    el.hidden = true;
  }

  function busy(btn, isBusy, busyText) {
    btn.disabled = isBusy;
    var span = btn.querySelector('span');
    if (!span) return;
    if (isBusy) {
      btn.dataset.label = span.textContent;
      span.textContent = busyText;
    } else if (btn.dataset.label) {
      span.textContent = btn.dataset.label;
    }
  }

  async function post(path, body) {
    var res = await fetch(SERVER + path, {
      method: 'POST',
      headers: { 'Content-Type': 'application/json' },
      body: JSON.stringify(body),
    });
    var json = null;
    try { json = await res.json(); } catch (_) {}
    if (!res.ok) {
      throw new Error((json && json.message) || ('Error del servidor (' + res.status + ')'));
    }
    return json;
  }

  // ── Login ─────────────────────────────────────────────────────────────────

  els.loginForm.addEventListener('submit', async function (e) {
    e.preventDefault();
    clearError(els.loginError);

    var cedula = els.cedula.value.trim();
    var password = els.password.value;
    if (!cedula || !password) {
      showError(els.loginError, 'Completá la cédula y la contraseña.');
      return;
    }

    busy(els.loginSubmit, true, 'Verificando...');
    try {
      var body = await post('/api/portal/auth/login', { cedula: cedula, password: password });
      var data = body.data || {};
      state.cedula = data.cedula || cedula;
      state.displayName = data.displayName || cedula;

      if (data.mustChangePassword) {
        // Sin token: el backend no emite sesión hasta que la clave provisional cambie.
        goToChangePassword();
      } else {
        state.token = data.token;
        startApp();
      }
    } catch (err) {
      showError(els.loginError, err.message);
    } finally {
      busy(els.loginSubmit, false);
    }
  });

  // ── Cambio obligatorio de contraseña ──────────────────────────────────────

  function goToChangePassword() {
    els.login.hidden = true;
    els.change.hidden = false;
    els.newPass.focus();
  }

  /** Evalúa las reglas y las marca en vivo. Devuelve true si todas se cumplen. */
  function evaluateRules() {
    var p1 = els.newPass.value;
    var p2 = els.newPass2.value;
    var checks = {
      len: p1.length >= 8,
      mix: /[a-zA-Z]/.test(p1) && /[0-9]/.test(p1),
      notdefault: p1 !== '2026',
      match: p1.length > 0 && p1 === p2,
    };
    Object.keys(checks).forEach(function (key) {
      var li = els.rules.querySelector('[data-rule="' + key + '"]');
      if (li) li.classList.toggle('is-ok', checks[key]);
    });
    return checks.len && checks.mix && checks.notdefault && checks.match;
  }

  els.newPass.addEventListener('input', evaluateRules);
  els.newPass2.addEventListener('input', evaluateRules);

  els.changeForm.addEventListener('submit', async function (e) {
    e.preventDefault();
    clearError(els.changeError);

    if (!evaluateRules()) {
      showError(els.changeError, 'Revisá que la contraseña cumpla las cuatro condiciones.');
      return;
    }

    busy(els.changeSubmit, true, 'Guardando...');
    try {
      var body = await post('/api/portal/auth/change-password', {
        cedula: state.cedula,
        currentPassword: els.password.value,
        newPassword: els.newPass.value,
      });
      var data = body.data || {};
      state.token = data.token;
      state.displayName = data.displayName || state.displayName;
      startApp();
    } catch (err) {
      showError(els.changeError, err.message);
    } finally {
      busy(els.changeSubmit, false);
    }
  });

  // ── Gestor documental ─────────────────────────────────────────────────────

  function startApp() {
    els.login.hidden = true;
    els.change.hidden = true;
    els.app.hidden = false;
    els.username.textContent = state.displayName;

    // El widget se configura por atributos del script, así que se inyecta recién
    // acá, cuando ya tenemos el token. Cargarlo antes lo dejaría sin identidad.
    var script = document.createElement('script');
    script.src = '/office-platform-widget.js';
    script.setAttribute('data-token', state.token);
    script.setAttribute('data-container', 'office-platform');
    script.setAttribute('data-server', SERVER);
    script.setAttribute('data-user-id', state.cedula);
    script.setAttribute('data-user-name', state.displayName);
    document.body.appendChild(script);
  }

  els.logout.addEventListener('click', function () {
    // Recarga completa a propósito: destruye el widget, cualquier sesión de editor
    // abierta y el token en memoria, sin dejar nada del usuario anterior.
    window.location.reload();
  });

  els.cedula.focus();
})();
