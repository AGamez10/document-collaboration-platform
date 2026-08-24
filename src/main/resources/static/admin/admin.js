(function () {
  'use strict';

  const AUTH_STORAGE_KEY = 'op-admin-auth';
  const THEME_STORAGE_KEY = 'op-admin-theme';
  const ACTIVITY_PAGE_SIZE = 20;
  const SESSIONS_REFRESH_INTERVAL_MS = 30000;

  const els = {
    root: document.getElementById('op-admin-root'),
    login: document.getElementById('op-login'),
    loginForm: document.getElementById('op-login-form'),
    loginUsername: document.getElementById('op-login-username'),
    loginPassword: document.getElementById('op-login-password'),
    loginError: document.getElementById('op-login-error'),
    app: document.getElementById('op-app'),
    viewTitle: document.getElementById('op-view-title'),
    viewSubtitle: document.getElementById('op-view-subtitle'),
    adminUser: document.getElementById('op-admin-user'),
    adminAvatar: document.getElementById('op-admin-avatar'),
    toast: document.getElementById('op-toast'),
    themeToggle: document.getElementById('op-theme-toggle'),
    iconTheme: document.getElementById('op-icon-theme'),
    logout: document.getElementById('op-logout'),

    // Dashboard
    dashboardCards: document.getElementById('op-dashboard-cards'),
    activityChart: document.getElementById('op-activity-chart'),
    quickStats: document.getElementById('op-quick-stats'),
    recentActivity: document.getElementById('op-recent-activity'),
    recentEmpty: document.getElementById('op-recent-empty'),
    ooStatusCard: document.getElementById('op-oo-status-card'),
    sessionsTbody: document.getElementById('op-sessions-tbody'),
    sessionsEmpty: document.getElementById('op-sessions-empty'),

    // Files
    filesTbody: document.getElementById('op-files-tbody'),
    filesEmpty: document.getElementById('op-files-empty'),
    filesBtnActive: document.getElementById('files-btn-active'),
    filesBtnTrash: document.getElementById('files-btn-trash'),
    filesHint: document.getElementById('op-files-hint'),

    // Backups
    createBackupBtn: document.getElementById('op-create-backup-btn'),
    backupConfigBtn: document.getElementById('op-backup-config-btn'),
    backupDiskFree: document.getElementById('op-backup-disk-free'),
    backupDiskTotal: document.getElementById('op-backup-disk-total'),
    backupCount: document.getElementById('op-backup-count'),
    backupDirSub: document.getElementById('op-backup-dir-sub'),
    backupSchedulerStatus: document.getElementById('op-backup-scheduler-status'),
    backupSchedulerDetail: document.getElementById('op-backup-scheduler-detail'),
    backupsTbody: document.getElementById('op-backups-tbody'),
    backupsEmpty: document.getElementById('op-backups-empty'),
    backupModal: document.getElementById('op-backup-modal'),
    backupModalCancel: document.getElementById('op-backup-modal-cancel'),
    backupModalSave: document.getElementById('op-backup-modal-save'),
    cfgBackupEnabled: document.getElementById('op-cfg-backup-enabled'),
    cfgBackupInterval: document.getElementById('op-cfg-backup-interval'),
    cfgBackupMax: document.getElementById('op-cfg-backup-max'),

    // Users
    usersTbody: document.getElementById('op-users-tbody'),
    usersEmpty: document.getElementById('op-users-empty'),
    usersSearch: document.getElementById('op-users-search'),
    usersFilterApp: document.getElementById('op-users-filter-app'),
    usersFilterBtn: document.getElementById('op-users-filter-btn'),
    usersClearBtn: document.getElementById('op-users-clear-btn'),

    // API Keys
    apiKeysTbody: document.getElementById('op-api-keys-tbody'),
    apiKeysEmpty: document.getElementById('op-api-keys-empty'),
    createKeyBtn: document.getElementById('op-create-key-btn'),
    keyModal: document.getElementById('op-key-modal'),
    keyModalForm: document.getElementById('op-key-modal-form'),
    keyModalResult: document.getElementById('op-key-modal-result'),
    newKeyName: document.getElementById('op-new-key-name'),
    keyModalCancel: document.getElementById('op-key-modal-cancel'),
    keyModalCreate: document.getElementById('op-key-modal-create'),
    newKeyValue: document.getElementById('op-new-key-value'),
    newKeyCopy: document.getElementById('op-new-key-copy'),
    keyModalClose: document.getElementById('op-key-modal-close'),
    keyModalX: document.getElementById('op-key-modal-x'),
    deleteKeyModal: document.getElementById('op-delete-key-modal'),
    deleteKeyModalX: document.getElementById('op-delete-key-modal-x'),
    deleteKeyName: document.getElementById('op-delete-key-name'),
    deleteKeyCancel: document.getElementById('op-delete-key-cancel'),
    deleteKeyConfirm: document.getElementById('op-delete-key-confirm'),
    backupModal: document.getElementById('op-backup-modal'),
    backupModalX: document.getElementById('op-backup-modal-x'),
    frameworkSelect: document.getElementById('op-framework-select'),
    envCode: document.getElementById('op-env-code'),
    backendCode: document.getElementById('op-backend-code'),
    frontendCode: document.getElementById('op-frontend-code'),
    copyEnvBtn: document.getElementById('op-copy-env-btn'),
    copyBackendBtn: document.getElementById('op-copy-backend-btn'),
    copyFrontendBtn: document.getElementById('op-copy-frontend-btn'),

    // Activity Log
    activityTbody: document.getElementById('op-activity-tbody'),
    activityEmpty: document.getElementById('op-activity-empty'),
    activityPagination: document.getElementById('op-activity-pagination'),
    filterDateFrom: document.getElementById('op-filter-date-from'),
    filterDateTo: document.getElementById('op-filter-date-to'),
    filterAction: document.getElementById('op-filter-action'),
    filterUserId: document.getElementById('op-filter-user-id'),
    filterApply: document.getElementById('op-filter-apply'),
    filterClear: document.getElementById('op-filter-clear'),
  };

  const state = {
    authHeader: null,
    username: null,
    currentView: 'dashboard',
    activityPage: 0,
    activityTotalPages: 0,
    chart: null,
    filesTrashed: 'false',
    sessionsRefreshTimer: null,
    cachedApiKeys: [],
  };

  // ── Theme Management ──────────────────────────────────────────────────
  function applyTheme(theme) {
    if (theme === 'light') {
      document.documentElement.setAttribute('data-op-theme', 'light');
      if (els.iconTheme) {
        els.iconTheme.innerHTML = '<circle cx="12" cy="12" r="5"/><line x1="12" y1="1" x2="12" y2="3"/><line x1="12" y1="21" x2="12" y2="23"/><line x1="4.22" y1="4.22" x2="5.64" y2="5.64"/><line x1="18.36" y1="18.36" x2="19.78" y2="19.78"/><line x1="1" y1="12" x2="3" y2="12"/><line x1="21" y1="12" x2="23" y2="12"/><line x1="4.22" y1="19.78" x2="5.64" y2="18.36"/><line x1="18.36" y1="5.64" x2="19.78" y2="4.22"/>';
      }
    } else {
      document.documentElement.setAttribute('data-op-theme', 'dark');
      if (els.iconTheme) {
        els.iconTheme.innerHTML = '<path d="M21 12.79A9 9 0 1 1 11.21 3 7 7 0 0 0 21 12.79z"/>';
      }
    }
  }

  function initTheme() {
    const saved = localStorage.getItem(THEME_STORAGE_KEY) || 'dark';
    applyTheme(saved);
    if (els.themeToggle) {
      els.themeToggle.addEventListener('click', function () {
        const current = document.documentElement.getAttribute('data-op-theme') === 'light' ? 'light' : 'dark';
        const next = current === 'light' ? 'dark' : 'light';
        localStorage.setItem(THEME_STORAGE_KEY, next);
        applyTheme(next);
        if (state.currentView === 'dashboard' && state.chart) {
          loadDashboard();
        }
      });
    }
  }

  // ── Toast System ──────────────────────────────────────────────────────
  let toastTimer = null;
  function showToast(message, isError) {
    els.toast.textContent = message;
    els.toast.classList.toggle('is-error', !!isError);
    els.toast.hidden = false;
    clearTimeout(toastTimer);
    toastTimer = setTimeout(function () { els.toast.hidden = true; }, 4000);
  }

  // ── API Fetch Helper ──────────────────────────────────────────────────
  function buildAuthHeader(username, password) {
    return 'Basic ' + btoa(username + ':' + password);
  }

  async function api(path, options) {
    options = options || {};
    const headers = Object.assign({}, options.headers, { Authorization: state.authHeader });
    if (options.body && typeof options.body === 'string') {
      headers['Content-Type'] = 'application/json';
    }

    const res = await fetch(path, Object.assign({}, options, { headers }));

    if (res.status === 401) {
      doLogout();
      throw new Error('Sesión expirada. Por favor ingresa nuevamente.');
    }

    if (res.headers.get('content-type') && res.headers.get('content-type').includes('application/json')) {
      const body = await res.json();
      if (!res.ok) {
        throw new Error((body && body.message) || ('Error del servidor (' + res.status + ')'));
      }
      return body;
    }

    if (!res.ok) {
      throw new Error('Error en la solicitud (' + res.status + ')');
    }
    return res;
  }

  function doLogout() {
    stopSessionsAutoRefresh();
    state.authHeader = null;
    state.username = null;
    sessionStorage.removeItem(AUTH_STORAGE_KEY);
    const loginEl = document.getElementById('op-login');
    const appEl = document.getElementById('op-app');
    if (appEl) {
      appEl.setAttribute('hidden', '');
      appEl.style.setProperty('display', 'none', 'important');
    }
    if (loginEl) {
      loginEl.removeAttribute('hidden');
      loginEl.style.setProperty('display', 'flex', 'important');
    }
    document.body.classList.remove('is-authenticated');
    document.body.classList.add('not-authenticated');
    if (els.loginPassword) els.loginPassword.value = '';
  }

  async function tryLogin(username, password) {
    const authHeader = buildAuthHeader(username, password);
    const res = await fetch('/api/admin/dashboard', { headers: { Authorization: authHeader } });
    if (res.status === 401) {
      throw new Error('Usuario o contraseña incorrectos.');
    }
    if (!res.ok) {
      throw new Error('No se pudo conectar con el servidor (código ' + res.status + ').');
    }
    state.authHeader = authHeader;
    state.username = username;
    sessionStorage.setItem(AUTH_STORAGE_KEY, JSON.stringify({ authHeader, username }));
    return res.json();
  }

  function enterApp() {
    const loginEl = document.getElementById('op-login');
    const appEl = document.getElementById('op-app');
    if (loginEl) {
      loginEl.setAttribute('hidden', '');
      loginEl.style.setProperty('display', 'none', 'important');
    }
    if (appEl) {
      appEl.removeAttribute('hidden');
      appEl.style.setProperty('display', 'flex', 'important');
    }
    document.body.classList.add('is-authenticated');
    document.body.classList.remove('not-authenticated');

    if (els.adminUser) els.adminUser.textContent = state.username;
    if (els.adminAvatar) {
      els.adminAvatar.textContent = (state.username || 'A').charAt(0).toUpperCase();
    }
    loadView('dashboard');
  }

  function restoreSession() {
    const raw = sessionStorage.getItem(AUTH_STORAGE_KEY);
    if (!raw) return false;
    try {
      const parsed = JSON.parse(raw);
      state.authHeader = parsed.authHeader;
      state.username = parsed.username;
      return true;
    } catch (e) {
      return false;
    }
  }

  els.loginForm.addEventListener('submit', async function (evt) {
    evt.preventDefault();
    els.loginError.hidden = true;
    const username = els.loginUsername.value.trim();
    const password = els.loginPassword.value;
    try {
      await tryLogin(username, password);
      enterApp();
    } catch (err) {
      els.loginError.textContent = err.message;
      els.loginError.hidden = false;
    }
  });

  els.logout.addEventListener('click', doLogout);

  // ── Navigation & Views ────────────────────────────────────────────────
  const VIEW_CONFIG = {
    dashboard: { title: 'Dashboard General', subtitle: 'Monitoreo en tiempo real, almacenamiento y sesiones activas' },
    files: { title: 'Archivos & Papelera de Reciclaje', subtitle: 'Gestión integral de archivos, restauración y purga física' },
    backups: { title: 'Copias de Seguridad (Backups)', subtitle: 'Respaldo físico en disco local, compresión ZIP y programador' },
    users: { title: 'Usuarios & Aplicaciones Consumidoras', subtitle: 'Directorio de usuarios por proyecto y asignación de roles' },
    'api-keys': { title: 'API Keys de Integración', subtitle: 'Gestión de credenciales para aplicativos backend y snippets' },
    activity: { title: 'Trazabilidad & Auditoría', subtitle: 'Registro detallado de acciones, documentos y direcciones IP' },
  };

  document.querySelectorAll('.op-nav__item').forEach(function (btn) {
    btn.addEventListener('click', function () { loadView(btn.dataset.view); });
  });

  function loadView(view) {
    state.currentView = view;
    document.querySelectorAll('.op-nav__item').forEach(function (btn) {
      btn.classList.toggle('is-active', btn.dataset.view === view);
    });
    document.querySelectorAll('.op-view').forEach(function (section) {
      section.hidden = true;
      section.style.display = 'none';
    });

    const targetSection = document.getElementById('op-view-' + view);
    if (targetSection) {
      targetSection.hidden = false;
      targetSection.style.display = 'block';
    }

    const conf = VIEW_CONFIG[view] || { title: view, subtitle: '' };
    els.viewTitle.textContent = conf.title;
    els.viewSubtitle.textContent = conf.subtitle;

    stopSessionsAutoRefresh();
    if (view === 'dashboard') {
      loadDashboard();
      loadOnlyOfficeStatus();
      loadEditorSessions();
      startSessionsAutoRefresh();
    } else if (view === 'files') {
      loadFiles();
    } else if (view === 'backups') {
      loadBackups();
    } else if (view === 'users') {
      loadUsers();
    } else if (view === 'api-keys') {
      loadApiKeys();
    } else if (view === 'activity') {
      loadActivity();
    }
  }

  function startSessionsAutoRefresh() {
    stopSessionsAutoRefresh();
    state.sessionsRefreshTimer = setInterval(function () {
      if (state.currentView === 'dashboard') {
        loadOnlyOfficeStatus();
        loadEditorSessions();
        loadRecentActivity();
      }
    }, SESSIONS_REFRESH_INTERVAL_MS);
  }

  function stopSessionsAutoRefresh() {
    if (state.sessionsRefreshTimer) {
      clearInterval(state.sessionsRefreshTimer);
      state.sessionsRefreshTimer = null;
    }
  }

  // ── Formatters & Helpers ──────────────────────────────────────────────
  function formatBytes(bytes) {
    if (!bytes || bytes <= 0) return '0 B';
    const units = ['B', 'KB', 'MB', 'GB', 'TB'];
    let value = bytes;
    let i = 0;
    while (value >= 1024 && i < units.length - 1) { value /= 1024; i++; }
    return value.toFixed(value >= 10 || i === 0 ? 0 : 1) + ' ' + units[i];
  }

  function parseUtc(iso) {
    if (!iso) return null;
    const hasZone = /[Zz]$|[+-]\d\d:?\d\d$/.test(iso);
    return new Date(hasZone ? iso : iso + 'Z');
  }

  function formatRelativeTime(iso) {
    if (!iso) return '—';
    const parsed = parseUtc(iso);
    const then = parsed ? parsed.getTime() : NaN;
    if (isNaN(then)) return '—';
    const diffMin = Math.max(0, Math.round((Date.now() - then) / 60000));
    if (diffMin < 1) return 'Hace un momento';
    if (diffMin < 60) return 'Hace ' + diffMin + ' min';
    const diffH = Math.round(diffMin / 60);
    if (diffH < 24) return 'Hace ' + diffH + ' h';
    const diffD = Math.round(diffH / 24);
    return 'Hace ' + diffD + ' d';
  }

  function formatDate(iso) {
    if (!iso) return '—';
    const d = parseUtc(iso);
    if (!d || isNaN(d.getTime())) return iso;
    return d.toLocaleString('es-CO', {
      timeZone: 'America/Bogota',
      year: 'numeric',
      month: '2-digit',
      day: '2-digit',
      hour: '2-digit',
      minute: '2-digit',
      second: '2-digit',
      hour12: true,
    });
  }

  function escapeHtml(str) {
    if (str === null || str === undefined) return '';
    return String(str)
      .replace(/&/g, '&amp;')
      .replace(/</g, '&lt;')
      .replace(/>/g, '&gt;')
      .replace(/"/g, '&quot;')
      .replace(/'/g, '&#39;');
  }

  // ── 1. DASHBOARD ──────────────────────────────────────────────────────
  async function loadDashboard() {
    els.dashboardCards.innerHTML = '<p class="op-empty">Cargando métricas...</p>';
    try {
      const body = await api('/api/admin/dashboard');
      const d = body.data;
      els.dashboardCards.innerHTML = [
        card('Archivos Totales', d.totalFiles, 'Documentos en plataforma'),
        card('Aplicaciones Conectadas', d.activeApiKeys, 'API Keys activas'),
        storageCard(d.storageUsedBytes, d.storageLimit),
        card('Nuevos Hoy', d.filesCreatedToday, 'Archivos creados hoy'),
        card('Esta Semana', d.filesCreatedThisWeek, 'Archivos en 7 días'),
        card('Este Mes', d.filesCreatedThisMonth, 'Archivos en 30 días'),
      ].join('');
      renderChart(d.activityLast30Days || []);
      renderTopApiKeys(d.topApiKeys || []);
    } catch (err) {
      showToast(err.message, true);
    }
    loadRecentActivity();
  }

  function card(label, value, sub) {
    return '<div class="op-card">' +
      '<div class="op-card__label">' + label + '</div>' +
      '<div class="op-card__value">' + value + '</div>' +
      (sub ? '<div class="op-card__sub">' + sub + '</div>' : '') +
      '</div>';
  }

  function storageCard(usedBytes, limitBytes) {
    const pct = limitBytes ? Math.max(0, Math.min(100, Math.round((usedBytes / limitBytes) * 100))) : 0;
    const level = pct > 85 ? 'danger' : (pct >= 60 ? 'warning' : 'brand');
    return '<div class="op-card">' +
      '<div class="op-card__label">Almacenamiento Usado</div>' +
      '<div class="op-card__value">' + formatBytes(usedBytes) + '</div>' +
      '<div class="op-card__sub">Límite: ' + formatBytes(limitBytes) + ' (' + pct + '%)</div>' +
      '<div class="op-oo-bar"><div class="op-oo-bar__fill op-oo-bar__fill--' + level + '" style="width:' + pct + '%"></div></div>' +
      '</div>';
  }

  function renderTopApiKeys(list) {
    if (!els.quickStats) return;
    if (!list || list.length === 0) {
      els.quickStats.innerHTML = '<li class="op-empty">Sin datos registrados.</li>';
      return;
    }
    const total = list.reduce(function (sum, k) { return sum + k.storageUsed; }, 0) || 1;
    els.quickStats.innerHTML = list.map(function (k) {
      const pct = Math.round((k.storageUsed / total) * 100);
      return '<li class="op-bar-item">' +
        '<div class="op-bar-item__hd"><span>' + escapeHtml(k.name) + '</span><span>' + formatBytes(k.storageUsed) + ' (' + pct + '%)</span></div>' +
        '<div class="op-oo-bar"><div class="op-oo-bar__fill op-oo-bar__fill--brand" style="width:' + pct + '%"></div></div>' +
        '</li>';
    }).join('');
  }

  const ACTION_ICONS = {
    UPLOAD: '⬆️', DOWNLOAD: '⬇️', DELETE: '🗑️', RESTORE: '♻️', PURGE: '💥',
    RENAME: '✏️', EDITOR_OPEN: '📂', EDITOR_SAVE: '💾', CREATE_BLANK: '📄',
    CREATE_FOLDER: '📁', RENAME_FOLDER: '✏️', DELETE_FOLDER: '🗑️',
    MOVE_FILE: '➡️', MOVE_FOLDER: '➡️', DOWNLOAD_FOLDER: '⬇️',
    SHARE: '🔗', UNSHARE: '🔒', REVOKE_SHARE: '🔒',
  };
  const ACTION_VERBS = {
    UPLOAD: 'subió', DOWNLOAD: 'descargó', DELETE: 'envió a papelera', RESTORE: 'restauró',
    PURGE: 'purgó físicamente', RENAME: 'renombró', EDITOR_OPEN: 'abrió', EDITOR_SAVE: 'guardó cambios en',
    CREATE_BLANK: 'creó', CREATE_FOLDER: 'creó carpeta', RENAME_FOLDER: 'renombró carpeta',
    DELETE_FOLDER: 'eliminó carpeta', MOVE_FILE: 'movió', MOVE_FOLDER: 'movió carpeta', DOWNLOAD_FOLDER: 'descargó carpeta',
    SHARE: 'compartió', UNSHARE: 'dejó de compartir', REVOKE_SHARE: 'revocó acceso a',
  };

  async function loadRecentActivity() {
    if (!els.recentActivity) return;
    try {
      const body = await api('/api/admin/activity-log?size=8');
      renderRecentActivity((body.data && body.data.content) || []);
    } catch (err) {}
  }

  function renderRecentActivity(items) {
    els.recentEmpty.hidden = items.length > 0;
    els.recentActivity.innerHTML = items.map(function (a) {
      let who = a.userName;
      if (!who || who === 'Usuario' || who === 'Alguien' || who.trim() === '—') {
        if (a.userId) {
          who = 'Usuario (' + a.userId + ')';
        } else if (a.apiKeyName) {
          who = a.apiKeyName;
        } else {
          who = 'Sistema / Aplicativo';
        }
      }
      who = escapeHtml(who);
      const verb = ACTION_VERBS[a.action] || String(a.action || '').toLowerCase();
      const icon = ACTION_ICONS[a.action] || '•';
      const what = a.fileName ? ' ' + escapeHtml(a.fileName) : (a.folderName ? ' 📁 ' + escapeHtml(a.folderName) : '');

      let detailHtml = '';
      if (a.details && a.details.trim().length > 0) {
        detailHtml = '<div style="margin-top:3px;"><span class="op-badge op-badge--info" style="font-size:11px; font-weight:500; padding:2px 8px; display:inline-flex; align-items:center; gap:4px;"><svg width="12" height="12" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2" stroke-linecap="round" stroke-linejoin="round"><circle cx="12" cy="12" r="10"/><line x1="12" y1="16" x2="12" y2="12"/><line x1="12" y1="8" x2="12.01" y2="8"/></svg>' + escapeHtml(a.details) + '</span></div>';
      }

      let appBadge = a.apiKeyName ? ' • <span style="font-weight:600; color:var(--op-color-primary);" title="Aplicación">📦 ' + escapeHtml(a.apiKeyName) + '</span>' : '';

      return '<li class="op-recent-item">' +
        '<span class="op-recent-item__icon">' + icon + '</span>' +
        '<div class="op-recent-item__body">' +
          '<span class="op-recent-item__text"><strong>' + who + '</strong> ' + verb + ' <em>' + what + '</em></span>' +
          detailHtml +
          '<span class="op-recent-item__time">' + formatRelativeTime(a.timestamp) + appBadge + ' • IP: <code>' + escapeHtml(a.ipAddress || 'local') + '</code></span>' +
        '</div>' +
        '</li>';
    }).join('');
  }

  async function loadOnlyOfficeStatus() {
    try {
      const body = await api('/api/admin/onlyoffice/status');
      renderOnlyOfficeCard(body.data);
    } catch (err) {}
  }

  function renderOnlyOfficeCard(d) {
    if (!els.ooStatusCard) return;
    const pct = Math.max(0, Math.min(100, d.usage));
    const level = pct > 80 ? 'danger' : (pct >= 60 ? 'warning' : 'success');
    els.ooStatusCard.innerHTML =
      '<div class="op-panel op-card--oo">' +
        '<div class="op-card__label"><span class="op-live-indicator" style="margin-right:6px;"></span> Conexiones OnlyOffice Server</div>' +
        '<div class="op-card__value">' + d.activeConnections + ' / ' + d.maxConnections + ' activas</div>' +
        '<div class="op-card__sub">' + pct + '% de la capacidad de concurrencia ocupada</div>' +
        '<div class="op-oo-bar"><div class="op-oo-bar__fill op-oo-bar__fill--' + level + '" style="width:' + pct + '%"></div></div>' +
      '</div>';
  }

  async function loadEditorSessions() {
    try {
      const body = await api('/api/admin/editor-sessions?active=true');
      renderEditorSessions(body.data || []);
    } catch (err) {}
  }

  function renderEditorSessions(sessions) {
    if (!els.sessionsTbody) return;
    els.sessionsEmpty.hidden = sessions.length > 0;
    els.sessionsTbody.innerHTML = sessions.map(function (s) {
      const userDisplay = escapeHtml(s.userName || (s.userId ? 'ID ' + s.userId : 'Anónimo'));
      return '<tr>' +
        '<td><strong>' + escapeHtml(s.fileName) + '</strong></td>' +
        '<td><div style="display:flex;align-items:center;gap:8px;"><div class="op-avatar" style="width:26px;height:26px;font-size:11px;">' + userDisplay.charAt(0).toUpperCase() + '</div><span>' + userDisplay + '</span></div></td>' +
        '<td><span class="op-badge op-badge--info">' + escapeHtml(s.apiKeyName || 'Aplicativo') + '</span></td>' +
        '<td>' + formatRelativeTime(s.openedAt) + '</td>' +
        '<td style="text-align:right;"><button type="button" class="op-btn op-btn--sm op-btn--danger" data-session-id="' + s.id + '">Forzar Cierre</button></td>' +
        '</tr>';
    }).join('');

    els.sessionsTbody.querySelectorAll('[data-session-id]').forEach(function (btn) {
      btn.addEventListener('click', async function () {
        if (!window.confirm('¿Desconectar inmediatamente a este usuario del editor? Se cerrará la sesión de OnlyOffice.')) return;
        try {
          await api('/api/admin/editor-sessions/' + btn.dataset.sessionId + '/close', { method: 'POST' });
          showToast('Usuario desconectado y sesión cerrada exitosamente.');
          loadEditorSessions();
          loadOnlyOfficeStatus();
        } catch (err) {
          showToast(err.message, true);
        }
      });
    });
  }

  function renderChart(daily) {
    if (typeof Chart === 'undefined' || !els.activityChart) return;
    const labels = daily.map(function (d) { return d.date; });
    const counts = daily.map(function (d) { return d.count; });
    const isDark = document.documentElement.getAttribute('data-op-theme') !== 'light';
    const primary = '#4f46e5';
    const textColor = isDark ? '#9499a8' : '#64748b';
    const gridColor = isDark ? 'rgba(255,255,255,0.06)' : 'rgba(0,0,0,0.06)';

    const ctx = els.activityChart.getContext('2d');
    const gradient = ctx.createLinearGradient(0, 0, 0, 220);
    gradient.addColorStop(0, 'rgba(79, 70, 229, 0.4)');
    gradient.addColorStop(1, 'rgba(79, 70, 229, 0.0)');

    if (state.chart) state.chart.destroy();
    state.chart = new Chart(els.activityChart, {
      type: 'line',
      data: {
        labels: labels,
        datasets: [{
          label: 'Operaciones',
          data: counts,
          borderColor: primary,
          backgroundColor: gradient,
          borderWidth: 2.5,
          tension: 0.35,
          fill: true,
          pointRadius: 0,
          pointHoverRadius: 5,
          pointHoverBackgroundColor: primary,
        }],
      },
      options: {
        responsive: true,
        maintainAspectRatio: false,
        plugins: {
          legend: { display: false },
          tooltip: {
            callbacks: {
              label: function (item) { return item.parsed.y + ' operaciones'; },
            },
          },
        },
        scales: {
          x: { ticks: { color: textColor, font: { size: 11 } }, grid: { display: false } },
          y: { ticks: { color: textColor, precision: 0, font: { size: 11 } }, grid: { color: gridColor }, beginAtZero: true },
        },
      },
    });
  }

  // ── 2. FILES & TRASH ──────────────────────────────────────────────────
  async function loadFiles() {
    els.filesEmpty.hidden = true;
    els.filesTbody.innerHTML = '<tr><td colspan="7" class="op-empty">Cargando archivos...</td></tr>';
    try {
      const trashed = state.filesTrashed;
      const isTrashedView = trashed === 'true';
      els.filesHint.textContent = isTrashedView
        ? 'Archivos en papelera de reciclaje. Puedes restaurar un archivo a su ubicación original o purgarlo definitivamente del almacenamiento MinIO.'
        : 'Listado de archivos activos almacenados en la plataforma.';

      const body = await api('/api/admin/files?trashed=' + trashed);
      const files = body.data || [];
      els.filesEmpty.hidden = files.length > 0;

      els.filesTbody.innerHTML = files.map(function (f) {
        return '<tr>' +
          '<td><strong>' + escapeHtml(f.originalFileName) + '</strong></td>' +
          '<td>' + escapeHtml(f.folderPath || 'Raíz') + '</td>' +
          '<td><span class="op-badge op-badge--muted">' + escapeHtml(f.mimeType || 'Documento') + '</span></td>' +
          '<td>' + formatBytes(f.size) + '</td>' +
          '<td><span class="op-badge op-badge--info">' + escapeHtml(f.apiKeyName || '—') + '</span></td>' +
          '<td>' + formatDate(f.updatedAt) + '</td>' +
          '<td style="text-align:right;">' +
            (isTrashedView
              ? '<div style="display:flex;gap:6px;justify-content:flex-end;">' +
                  '<button type="button" class="op-btn op-btn--sm op-btn--success" data-restore-id="' + f.id + '" data-file-name="' + escapeHtml(f.originalFileName) + '">Restaurar</button>' +
                  '<button type="button" class="op-btn op-btn--sm op-btn--danger" data-purge-id="' + f.id + '" data-file-name="' + escapeHtml(f.originalFileName) + '">Purgar</button>' +
                '</div>'
              : '<span class="op-badge op-badge--success">Activo</span>') +
          '</td>' +
          '</tr>';
      }).join('');

      // Listeners for Restore
      els.filesTbody.querySelectorAll('[data-restore-id]').forEach(function (btn) {
        btn.addEventListener('click', async function () {
          const id = btn.dataset.restoreId;
          const name = btn.dataset.fileName;
          if (!window.confirm('¿Restaurar el archivo "' + name + '" a su ubicación original?')) return;
          try {
            await api('/api/admin/files/' + id + '/restore', { method: 'POST' });
            showToast('Archivo "' + name + '" restaurado con éxito.');
            loadFiles();
          } catch (err) {
            showToast(err.message, true);
          }
        });
      });

      // Listeners for Purge
      els.filesTbody.querySelectorAll('[data-purge-id]').forEach(function (btn) {
        btn.addEventListener('click', async function () {
          const id = btn.dataset.purgeId;
          const name = btn.dataset.fileName;
          if (!window.confirm('¡Atención! Esto eliminará el archivo "' + name + '" de forma PERMANENTE e IRREVERSIBLE tanto de MinIO como de la base de datos. ¿Continuar?')) return;
          try {
            await api('/api/admin/files/' + id + '/purge', { method: 'DELETE' });
            showToast('Archivo purgado permanentemente.');
            loadFiles();
          } catch (err) {
            showToast(err.message, true);
          }
        });
      });

    } catch (err) {
      showToast(err.message, true);
    }
  }

  [els.filesBtnActive, els.filesBtnTrash].forEach(function (btn) {
    btn.addEventListener('click', function () {
      if (btn.classList.contains('is-active')) return;
      state.filesTrashed = btn.dataset.trashed;
      els.filesBtnActive.classList.toggle('is-active', btn === els.filesBtnActive);
      els.filesBtnTrash.classList.toggle('is-active', btn === els.filesBtnTrash);
      loadFiles();
    });
  });

  // ── 3. COPIAS DE SEGURIDAD (BACKUPS) ──────────────────────────────────
  async function loadBackups() {
    loadBackupConfig();
    els.backupsEmpty.hidden = true;
    els.backupsTbody.innerHTML = '<tr><td colspan="5" class="op-empty">Cargando copias de seguridad...</td></tr>';
    try {
      const body = await api('/api/admin/backups');
      const backups = body.data || [];
      els.backupsEmpty.hidden = backups.length > 0;
      els.backupCount.textContent = backups.length;

      els.backupsTbody.innerHTML = backups.map(function (b) {
        const isAuto = b.type === 'AUTOMATICO';
        const typeBadge = isAuto
          ? '<span class="op-badge op-badge--info">Automático</span>'
          : '<span class="op-badge op-badge--success">Manual</span>';

        return '<tr>' +
          '<td><div style="display:flex;align-items:center;gap:8px;"><code>' + escapeHtml(b.fileName) + '</code></div></td>' +
          '<td>' + typeBadge + '</td>' +
          '<td>' + escapeHtml(b.formattedSize) + '</td>' +
          '<td>' + formatDate(b.createdAt) + '</td>' +
          '<td style="text-align:right;">' +
            '<div style="display:flex;gap:6px;justify-content:flex-end;">' +
              '<a href="/api/admin/backups/' + encodeURIComponent(b.fileName) + '/download" class="op-btn op-btn--sm op-btn--ghost" download>Descargar ZIP</a>' +
              '<button type="button" class="op-btn op-btn--sm op-btn--danger" data-delete-backup="' + escapeHtml(b.fileName) + '">Eliminar</button>' +
            '</div>' +
          '</td>' +
          '</tr>';
      }).join('');

      els.backupsTbody.querySelectorAll('[data-delete-backup]').forEach(function (btn) {
        btn.addEventListener('click', async function () {
          const fn = btn.dataset.deleteBackup;
          if (!window.confirm('¿Eliminar el archivo de copia de seguridad "' + fn + '" del disco?')) return;
          try {
            await api('/api/admin/backups/' + encodeURIComponent(fn), { method: 'DELETE' });
            showToast('Backup eliminado del disco.');
            loadBackups();
          } catch (err) {
            showToast(err.message, true);
          }
        });
      });

    } catch (err) {
      showToast(err.message, true);
    }
  }

  async function loadBackupConfig() {
    try {
      const body = await api('/api/admin/backups/config');
      const cfg = body.data;
      els.backupDiskFree.textContent = cfg.formattedDiskFreeSpace || '—';
      els.backupDiskTotal.textContent = 'Libre de ' + (cfg.formattedDiskTotalSpace || '—') + ' en disco físico';
      els.backupDirSub.textContent = 'Ruta: ' + (cfg.backupDirectory || './backups');

      if (cfg.enabled) {
        els.backupSchedulerStatus.textContent = 'Activo (cada ' + cfg.intervalHours + 'h)';
        els.backupSchedulerDetail.textContent = 'Retiene hasta ' + cfg.maxRetainedBackups + ' copias automáticas';
      } else {
        els.backupSchedulerStatus.textContent = 'Deshabilitado';
        els.backupSchedulerDetail.textContent = 'Solo backups manuales bajo demanda';
      }

      els.cfgBackupEnabled.checked = cfg.enabled;
      els.cfgBackupInterval.value = cfg.intervalHours;
      els.cfgBackupMax.value = cfg.maxRetainedBackups;
    } catch (err) {}
  }

  els.createBackupBtn.addEventListener('click', async function () {
    const originalContent = els.createBackupBtn.innerHTML;
    els.createBackupBtn.disabled = true;
    els.createBackupBtn.innerHTML = '<span>Generando snapshot ZIP...</span>';
    try {
      const body = await api('/api/admin/backups/create?type=MANUAL', { method: 'POST' });
      showToast('¡Copia de seguridad creada con éxito! Archivo: ' + body.data.fileName);
      loadBackups();
    } catch (err) {
      showToast(err.message, true);
    } finally {
      els.createBackupBtn.disabled = false;
      els.createBackupBtn.innerHTML = originalContent;
    }
  });

  els.backupConfigBtn.addEventListener('click', function () {
    els.backupModal.hidden = false;
  });
  els.backupModalCancel.addEventListener('click', function () {
    els.backupModal.hidden = true;
  });
  els.backupModalSave.addEventListener('click', async function () {
    const payload = {
      enabled: els.cfgBackupEnabled.checked,
      intervalHours: parseInt(els.cfgBackupInterval.value, 10) || 24,
      maxRetainedBackups: parseInt(els.cfgBackupMax.value, 10) || 10,
    };
    try {
      await api('/api/admin/backups/config', {
        method: 'PUT',
        body: JSON.stringify(payload),
      });
      els.backupModal.hidden = true;
      showToast('Configuración de copias de seguridad actualizada.');
      loadBackupConfig();
    } catch (err) {
      showToast(err.message, true);
    }
  });

  // ── 4. USUARIOS & APLICACIONES ─────────────────────────────────────────
  async function loadUsers() {
    els.usersEmpty.hidden = true;
    els.usersTbody.innerHTML = '<tr><td colspan="7" class="op-empty">Cargando directorio de usuarios...</td></tr>';
    try {
      // Load apps for filter dropdown if not yet populated
      if (state.cachedApiKeys.length === 0) {
        const keysRes = await api('/api/admin/api-keys');
        state.cachedApiKeys = keysRes.data || [];
        els.usersFilterApp.innerHTML = '<option value="">Todas las aplicaciones</option>' +
          state.cachedApiKeys.map(function (k) {
            return '<option value="' + k.id + '">' + escapeHtml(k.name) + '</option>';
          }).join('');
      }

      const search = (els.usersSearch.value || '').trim();
      const appId = els.usersFilterApp.value;
      let q = '/api/admin/users?';
      if (appId) q += 'apiKeyId=' + encodeURIComponent(appId) + '&';
      if (search) q += 'search=' + encodeURIComponent(search) + '&';

      const body = await api(q);
      const users = body.data || [];
      els.usersEmpty.hidden = users.length > 0;

      els.usersTbody.innerHTML = users.map(function (u) {
        const isAdmin = u.role === 'admin';
        const roleBadge = isAdmin
          ? '<span class="op-badge op-badge--warning">Admin Proyecto</span>'
          : '<span class="op-badge op-badge--muted">Usuario</span>';
        const targetRole = isAdmin ? 'user' : 'admin';
        const roleBtnLabel = isAdmin ? 'Cambiar a Usuario' : 'Promover a Admin';
        const name = escapeHtml(u.displayName || 'Sin nombre registrado');
        const initial = name.charAt(0).toUpperCase();

        return '<tr>' +
          '<td><div style="display:flex;align-items:center;gap:10px;"><div class="op-avatar" style="width:28px;height:28px;font-size:12px;">' + initial + '</div><strong>' + name + '</strong></div></td>' +
          '<td><code>' + escapeHtml(u.userId) + '</code></td>' +
          '<td><span class="op-badge op-badge--info">' + escapeHtml(u.apiKeyName || '—') + '</span></td>' +
          '<td>' + roleBadge + '</td>' +
          '<td>' + formatDate(u.firstSeenAt) + '</td>' +
          '<td>' + formatRelativeTime(u.lastSeenAt) + '</td>' +
          '<td style="text-align:right;"><button type="button" class="op-btn op-btn--sm op-btn--ghost" data-user-id="' + u.id + '" data-role="' + targetRole + '">' + roleBtnLabel + '</button></td>' +
          '</tr>';
      }).join('');

      els.usersTbody.querySelectorAll('[data-user-id]').forEach(function (btn) {
        btn.addEventListener('click', async function () {
          const id = btn.dataset.userId;
          const role = btn.dataset.role;
          try {
            await api('/api/admin/users/' + id + '/role', {
              method: 'PATCH',
              body: JSON.stringify({ role: role }),
            });
            showToast('Rol de usuario actualizado a: ' + role);
            loadUsers();
          } catch (err) {
            showToast(err.message, true);
          }
        });
      });

    } catch (err) {
      showToast(err.message, true);
    }
  }

  els.usersFilterBtn.addEventListener('click', loadUsers);
  els.usersClearBtn.addEventListener('click', function () {
    els.usersSearch.value = '';
    els.usersFilterApp.value = '';
    loadUsers();
  });

  // ── 5. API KEYS ───────────────────────────────────────────────────────
  async function loadApiKeys() {
    els.apiKeysEmpty.hidden = true;
    els.apiKeysTbody.innerHTML = '<tr><td colspan="6" class="op-empty">Cargando llaves de API...</td></tr>';
    try {
      const body = await api('/api/admin/api-keys');
      const keys = body.data || [];
      state.cachedApiKeys = keys;
      els.apiKeysEmpty.hidden = keys.length > 0;

      els.apiKeysTbody.innerHTML = keys.map(function (k) {
        const badge = k.active
          ? '<span class="op-badge op-badge--success">Activa</span>'
          : '<span class="op-badge op-badge--muted">Inactiva</span>';

        return '<tr>' +
          '<td><strong>' + escapeHtml(k.name) + '</strong></td>' +
          '<td><code>' + escapeHtml(k.maskedKey) + '</code></td>' +
          '<td>' + badge + '</td>' +
          '<td>' + formatDate(k.createdAt) + '</td>' +
          '<td><label class="op-switch">' +
            '<input type="checkbox" data-key-id="' + k.id + '" ' + (k.active ? 'checked' : '') + ' />' +
            '<span class="op-switch__track"></span>' +
          '</label></td>' +
          '<td style="text-align:right;"><button type="button" class="op-btn op-btn--sm op-btn--danger" data-delete-key-id="' + k.id + '" data-delete-key-name="' + escapeHtml(k.name) + '">Eliminar</button></td>' +
          '</tr>';
      }).join('');

      els.apiKeysTbody.querySelectorAll('input[type="checkbox"]').forEach(function (input) {
        input.addEventListener('change', async function () {
          const id = input.dataset.keyId;
          try {
            await api('/api/admin/api-keys/' + id, {
              method: 'PATCH',
              body: JSON.stringify({ active: input.checked }),
            });
            showToast('Estado de la API key actualizado.');
          } catch (err) {
            input.checked = !input.checked;
            showToast(err.message, true);
          }
        });
      });

      els.apiKeysTbody.querySelectorAll('[data-delete-key-id]').forEach(function (btn) {
        btn.addEventListener('click', function () {
          openDeleteKeyModal(btn.dataset.deleteKeyId, btn.dataset.deleteKeyName);
        });
      });
    } catch (err) {
      showToast(err.message, true);
    }
  }

  function closeKeyModal() {
    els.keyModal.hidden = true;
    loadApiKeys();
  }

  els.createKeyBtn.addEventListener('click', function () {
    els.newKeyName.value = '';
    els.keyModalForm.hidden = false;
    els.keyModalResult.hidden = true;
    els.keyModal.hidden = false;
  });
  els.keyModalCancel.addEventListener('click', function () { els.keyModal.hidden = true; });
  els.keyModalClose.addEventListener('click', closeKeyModal);
  if (els.keyModalX) els.keyModalX.addEventListener('click', closeKeyModal);

  els.keyModalCreate.addEventListener('click', async function () {
    const name = els.newKeyName.value.trim();
    if (!name) { showToast('Ingresa un nombre para la aplicación.', true); return; }
    try {
      const body = await api('/api/admin/api-keys', {
        method: 'POST',
        body: JSON.stringify({ name: name }),
      });
      els.newKeyValue.textContent = body.data.apiKey;
      els.keyModalForm.hidden = true;
      els.keyModalResult.hidden = false;
      els.frameworkSelect.value = 'jsp';
      renderIntegrationCode(body.data.apiKey);
    } catch (err) {
      showToast(err.message, true);
    }
  });
  els.newKeyCopy.addEventListener('click', function () {
    navigator.clipboard.writeText(els.newKeyValue.textContent).then(function () {
      showToast('API Key copiada al portapapeles.');
    });
  });

  function openDeleteKeyModal(id, name) {
    els.deleteKeyName.textContent = name;
    els.deleteKeyConfirm.dataset.keyId = id;
    els.deleteKeyModal.hidden = false;
  }
  function closeDeleteKeyModal() { els.deleteKeyModal.hidden = true; }
  els.deleteKeyCancel.addEventListener('click', closeDeleteKeyModal);
  if (els.deleteKeyModalX) els.deleteKeyModalX.addEventListener('click', closeDeleteKeyModal);
  if (els.backupModalX) els.backupModalX.addEventListener('click', function () {
    if (els.backupModal) els.backupModal.hidden = true;
  });

  // Cerrar modales haciendo clic fuera en el fondo (backdrop)
  [els.keyModal, els.deleteKeyModal, els.backupModal].forEach(function (modal) {
    if (modal) {
      modal.addEventListener('click', function (e) {
        if (e.target === modal) {
          modal.hidden = true;
          if (modal === els.keyModal) loadApiKeys();
        }
      });
    }
  });

  // Cerrar modales con la tecla Escape
  document.addEventListener('keydown', function (e) {
    if (e.key === 'Escape') {
      if (els.keyModal && !els.keyModal.hidden) closeKeyModal();
      if (els.deleteKeyModal && !els.deleteKeyModal.hidden) closeDeleteKeyModal();
      if (els.backupModal && !els.backupModal.hidden) els.backupModal.hidden = true;
    }
  });

  els.deleteKeyConfirm.addEventListener('click', async function () {
    const id = els.deleteKeyConfirm.dataset.keyId;
    try {
      await api('/api/admin/api-keys/' + id, { method: 'DELETE' });
      closeDeleteKeyModal();
      showToast('API Key eliminada permanentemente.');
      loadApiKeys();
    } catch (err) {
      showToast(err.message, true);
    }
  });

  // Integration Snippets
  function frontendBlock(serverExpr, tokenExpr) {
    return [
      '<div id="office-platform"></div>',
      '<script',
      '  src="' + serverExpr + '/office-platform-widget.js"',
      '  data-container="office-platform"',
      '  data-server="' + serverExpr + '"',
      '  data-token="' + tokenExpr + '">',
      '</script>',
    ].join('\n');
  }

  function buildIntegrationSnippet(apiKey, framework) {
    var origin = window.location.origin;
    var env = 'OFFICE_PLATFORM_API_KEY=' + apiKey + '\nOFFICE_PLATFORM_URL=' + origin;

    var backends = {
      jsp: [
        '<%',
        '  // 1. Identificador de usuario (Cédula, documento, ID de base de datos o username)',
        '  Object docObj = session.getAttribute("Documento"); // o "cedula", "idUsuario", "username"',
        '  Object usrObj = session.getAttribute("Usuario");   // o "nombreCompleto"',
        '',
        '  String cedula = (docObj != null && !docObj.toString().trim().isEmpty()) ',
        '      ? docObj.toString().trim() ',
        '      : "usr_" + (session.getAttribute("Id_usuario") != null ? session.getAttribute("Id_usuario") : session.getId());',
        '',
        '  String nombre = (usrObj != null && !usrObj.toString().trim().isEmpty()) ',
        '      ? usrObj.toString().trim() ',
        '      : "Usuario " + cedula;',
        '',
        '  // 2. Generar Widget Token hacia Office Platform (Zero Dependencias)',
        '  String apiKey = "' + apiKey + '";',
        '  String serverUrl = "' + origin + '";',
        '',
        '  String widgetToken = "";',
        '  try {',
        '    java.net.URL url = new java.net.URL(serverUrl + "/api/auth/resolve");',
        '    java.net.HttpURLConnection conn = (java.net.HttpURLConnection) url.openConnection();',
        '    conn.setRequestMethod("POST");',
        '    conn.setRequestProperty("Content-Type", "application/json");',
        '    conn.setRequestProperty("X-Api-Key", apiKey);',
        '    conn.setDoOutput(true);',
        '',
        '    String jsonBody = "{\\"cedula\\":\\"" + cedula + "\\",\\"nombre\\":\\"" + nombre + "\\"}";',
        '    try (java.io.OutputStream os = conn.getOutputStream()) { os.write(jsonBody.getBytes("UTF-8")); }',
        '',
        '    try (java.io.BufferedReader br = new java.io.BufferedReader(new java.io.InputStreamReader(conn.getInputStream(), "UTF-8"))) {',
        '      StringBuilder sb = new StringBuilder();',
        '      String line;',
        '      while ((line = br.readLine()) != null) { sb.append(line); }',
        '      String res = sb.toString();',
        '      int idx = res.indexOf("\\"widgetToken\\":\\"");',
        '      if (idx != -1) {',
        '        int start = idx + 15;',
        '        int end = res.indexOf("\\"", start);',
        '        widgetToken = res.substring(start, end);',
        '      }',
        '    }',
        '  } catch (Exception ex) {',
        '    // Fallback silencioso en caso de contingencia de red',
        '  }',
        '  request.setAttribute("widgetToken", widgetToken);',
        '  request.setAttribute("serverUrl", serverUrl);',
        '%>',
      ].join('\n'),

      laravel: [
        '// En tu Controlador de Laravel / PHP',
        'use Illuminate\\Support\\Facades\\Http;',
        '',
        '$response = Http::withHeaders([',
        '    \'X-Api-Key\' => env(\'OFFICE_PLATFORM_API_KEY\', \'' + apiKey + '\'),',
        '])->post(env(\'OFFICE_PLATFORM_URL\', \'' + origin + '\') . \'/api/auth/resolve\', [',
        '    // Identificador único (cédula, documento, id o username):',
        '    \'cedula\' => auth()->user()->cedula ?? auth()->user()->documento ?? auth()->user()->id,',
        '    // Nombre visible para colaboración y trazabilidad:',
        '    \'nombre\' => auth()->user()->name ?? auth()->user()->nombreCompleto ?? \'Usuario\',',
        ']);',
        '',
        '$widgetToken = $response->json()[\'data\'][\'widgetToken\'];',
        '',
        'return view(\'tu-vista\', [',
        '    \'widgetToken\' => $widgetToken,',
        '    \'serverUrl\'   => env(\'OFFICE_PLATFORM_URL\', \'' + origin + '\'),',
        ']);',
      ].join('\n'),

      node: [
        '// En tu ruta de Express / Node.js / Next.js',
        'app.get(\'/gestor-archivos\', async (req, res) => {',
        '  const response = await fetch(`${process.env.OFFICE_PLATFORM_URL || \'' + origin + '\'}/api/auth/resolve`, {',
        '    method: \'POST\',',
        '    headers: {',
        '      \'Content-Type\': \'application/json\',',
        '      \'X-Api-Key\': process.env.OFFICE_PLATFORM_API_KEY || \'' + apiKey + '\',',
        '    },',
        '    body: JSON.stringify({',
        '      // Identificador único del usuario:',
        '      cedula: req.user.cedula || req.user.documento || req.user.id || req.user.username,',
        '      // Nombre visible en el editor:',
        '      nombre: req.user.name || req.user.nombreCompleto || \'Usuario\',',
        '    }),',
        '  });',
        '  const { data } = await response.json();',
        '  res.render(\'gestor-archivos\', { widgetToken: data.widgetToken, serverUrl: \'' + origin + '\' });',
        '});',
      ].join('\n'),

      python: [
        '# En tu vista de Django / FastAPI / Flask',
        'import os, requests',
        '',
        'def obtener_widget_token(usuario):',
        '    resp = requests.post(',
        '        f"{os.getenv(\'OFFICE_PLATFORM_URL\', \'' + origin + '\')}/api/auth/resolve",',
        '        headers={"X-Api-Key": os.getenv("OFFICE_PLATFORM_API_KEY", "' + apiKey + '")},',
        '        json={',
        '            # Identificador único:',
        '            "cedula": str(getattr(usuario, "cedula", None) or getattr(usuario, "id", None) or usuario.username),',
        '            # Nombre visible:',
        '            "nombre": getattr(usuario, "get_full_name", lambda: usuario.username)() or "Usuario",',
        '        }',
        '    )',
        '    return resp.json()["data"]["widgetToken"]',
      ].join('\n'),

      dotnet: [
        '// En tu Controller de ASP.NET Core / C#',
        'var client = new HttpClient();',
        'client.DefaultRequestHeaders.Add("X-Api-Key", Environment.GetEnvironmentVariable("OFFICE_PLATFORM_API_KEY") ?? "' + apiKey + '");',
        '',
        'var payload = new {',
        '    cedula = user.Cedula ?? user.Documento ?? user.Id.ToString(),',
        '    nombre = user.NombreCompleto ?? user.Name ?? "Usuario"',
        '};',
        '',
        'var content = new StringContent(JsonSerializer.Serialize(payload), Encoding.UTF8, "application/json");',
        'var response = await client.PostAsync($"' + origin + '/api/auth/resolve", content);',
        'var json = await response.Content.ReadAsStringAsync();',
        '',
        'ViewBag.WidgetToken = JsonDocument.Parse(json).RootElement.GetProperty("data").GetProperty("widgetToken").GetString();',
        'ViewBag.ServerUrl = "' + origin + '";',
      ].join('\n'),

      direct: [
        '<!-- OPCIÓN ZERO-BACKEND (Frontend directo) -->',
        '<!-- No necesitas llamadas HTTP en tu servidor: pasa los datos de usuario directamente al script -->',
        '<div id="office-platform"></div>',
        '<script',
        '  src="' + origin + '/office-platform-widget.js"',
        '  data-api-key="' + apiKey + '"',
        '  data-container="office-platform"',
        '  data-server="' + origin + '"',
        '  data-user-id="${usuario.cedula}"',
        '  data-user-name="${usuario.nombre}">',
        '</script>',
      ].join('\n'),

      generic: [
        '# Solicitud HTTP estándar cURL para obtener el token',
        'curl -X POST "' + origin + '/api/auth/resolve" \\',
        '  -H "Content-Type: application/json" \\',
        '  -H "X-Api-Key: ' + apiKey + '" \\',
        '  -d \'{"cedula":"12345678","nombre":"Usuario Demo"}\'',
      ].join('\n'),
    };

    var frontends = {
      jsp: frontendBlock('${serverUrl}', '${widgetToken}'),
      laravel: frontendBlock('{{ $serverUrl }}', '{{ $widgetToken }}'),
      node: frontendBlock('<%= serverUrl %>', '<%= widgetToken %>'),
      python: frontendBlock('{{ server_url }}', '{{ widget_token }}'),
      dotnet: frontendBlock('@ViewBag.ServerUrl', '@ViewBag.WidgetToken'),
      direct: '<!-- El script directo de arriba ya incluye la inicialización completa. Listo para usar. -->',
      generic: frontendBlock(origin, 'TOKEN_GENERADO'),
    };

    return {
      env: env,
      backend: backends[framework] || backends.generic,
      frontend: frontends[framework] || frontends.generic,
    };
  }

  function renderIntegrationCode(apiKey) {
    var snippet = buildIntegrationSnippet(apiKey, els.frameworkSelect.value);
    els.envCode.textContent = snippet.env;
    els.backendCode.textContent = snippet.backend;
    els.frontendCode.textContent = snippet.frontend;
  }

  els.frameworkSelect.addEventListener('change', function () {
    renderIntegrationCode(els.newKeyValue.textContent);
  });

  function copyText(text, label) {
    navigator.clipboard.writeText(text).then(function () {
      showToast(label + ' copiado al portapapeles.');
    });
  }
  els.copyEnvBtn.addEventListener('click', function () { copyText(els.envCode.textContent, '.env'); });
  els.copyBackendBtn.addEventListener('click', function () { copyText(els.backendCode.textContent, 'Código Backend'); });
  els.copyFrontendBtn.addEventListener('click', function () { copyText(els.frontendCode.textContent, 'Código Frontend'); });

  // ── 6. ACTIVITY LOG ───────────────────────────────────────────────────
  function buildActivityQuery(page) {
    const params = new URLSearchParams();
    params.set('page', page);
    params.set('size', ACTIVITY_PAGE_SIZE);
    if (els.filterDateFrom.value) params.set('dateFrom', els.filterDateFrom.value);
    if (els.filterDateTo.value) params.set('dateTo', els.filterDateTo.value);
    if (els.filterAction.value) params.set('action', els.filterAction.value);
    if (els.filterUserId.value.trim()) params.set('userId', els.filterUserId.value.trim());
    return params.toString();
  }

  async function loadActivity(page) {
    page = page || 0;
    els.activityEmpty.hidden = true;
    els.activityTbody.innerHTML = '<tr><td colspan="6" class="op-empty">Cargando registro de auditoría...</td></tr>';
    try {
      const body = await api('/api/admin/activity-log?' + buildActivityQuery(page));
      const paged = body.data;
      state.activityPage = paged.page;
      state.activityTotalPages = paged.totalPages;

      els.activityEmpty.hidden = paged.content.length > 0;
      els.activityTbody.innerHTML = paged.content.map(function (a) {
        let userDisplay = a.userName;
        if (!userDisplay || userDisplay === 'Usuario' || userDisplay === 'Alguien' || userDisplay.trim() === '—') {
          if (a.userId) {
            userDisplay = 'Usuario (' + a.userId + ')';
          } else if (a.apiKeyName) {
            userDisplay = a.apiKeyName;
          } else {
            userDisplay = 'Sistema / Aplicativo';
          }
        }
        userDisplay = escapeHtml(userDisplay);
        const icon = ACTION_ICONS[a.action] || '•';

        let appHtml = a.apiKeyName ? '<div style="font-size:11px; color:var(--op-color-primary); font-weight:600; margin-top:2px;">📦 ' + escapeHtml(a.apiKeyName) + '</div>' : '';

        let resourceHtml = a.fileName
          ? '<strong>' + escapeHtml(a.fileName) + '</strong>'
          : (a.folderName ? '<span style="color:var(--op-color-primary);">📁 ' + escapeHtml(a.folderName) + '</span>' : '<span style="color:var(--admin-text-muted);">—</span>');

        let detailsHtml = a.details
          ? '<span class="op-badge op-badge--info" style="font-size:11.5px; font-weight:500; white-space:normal; text-align:left;">' + escapeHtml(a.details) + '</span>'
          : '<span style="color:var(--admin-text-dim);">—</span>';

        return '<tr>' +
          '<td>' + formatDate(a.timestamp) + '</td>' +
          '<td><span class="op-badge op-badge--info">' + icon + ' ' + escapeHtml(a.action) + '</span></td>' +
          '<td>' +
            '<div style="display:flex;align-items:center;gap:8px;">' +
              '<div class="op-avatar" style="width:26px;height:26px;font-size:11px;flex-shrink:0;">' + userDisplay.charAt(0).toUpperCase() + '</div>' +
              '<div>' +
                '<strong>' + userDisplay + '</strong>' +
                appHtml +
              '</div>' +
            '</div>' +
          '</td>' +
          '<td>' + resourceHtml + '</td>' +
          '<td>' + detailsHtml + '</td>' +
          '<td><code>' + escapeHtml(a.ipAddress || '—') + '</code></td>' +
          '</tr>';
      }).join('');

      renderActivityPagination();
    } catch (err) {
      showToast(err.message, true);
    }
  }

  function renderActivityPagination() {
    if (state.activityTotalPages <= 1) {
      els.activityPagination.innerHTML = '';
      return;
    }
    els.activityPagination.innerHTML =
      '<button type="button" class="op-btn op-btn--sm" id="op-activity-prev" ' +
        (state.activityPage <= 0 ? 'disabled' : '') + '>← Anterior</button>' +
      '<span>Página ' + (state.activityPage + 1) + ' de ' + state.activityTotalPages + '</span>' +
      '<button type="button" class="op-btn op-btn--sm" id="op-activity-next" ' +
        (state.activityPage >= state.activityTotalPages - 1 ? 'disabled' : '') + '>Siguiente →</button>';

    const prev = document.getElementById('op-activity-prev');
    const next = document.getElementById('op-activity-next');
    if (prev) prev.addEventListener('click', function () { loadActivity(state.activityPage - 1); });
    if (next) next.addEventListener('click', function () { loadActivity(state.activityPage + 1); });
  }

  els.filterApply.addEventListener('click', function () { loadActivity(0); });
  els.filterClear.addEventListener('click', function () {
    els.filterDateFrom.value = '';
    els.filterDateTo.value = '';
    els.filterAction.value = '';
    els.filterUserId.value = '';
    loadActivity(0);
  });

  // ── Initialization ────────────────────────────────────────────────────
  initTheme();
  if (restoreSession()) {
    enterApp();
  }
})();
