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
    closeAllSessionsBtn: document.getElementById('op-close-all-sessions-btn'),

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
    cfgBackupDir: document.getElementById('op-cfg-backup-dir'),
    cfgBackupReplicationDir: document.getElementById('op-cfg-backup-replication-dir'),
    cfgBackupReplicationEnabled: document.getElementById('op-cfg-backup-replication-enabled'),
    cfgReplicationStatus: document.getElementById('op-cfg-replication-status'),
    verifyBackupDir: document.getElementById('op-verify-backup-dir'),
    verifyReplicationDir: document.getElementById('op-verify-replication-dir'),
    backupDirStatus: document.getElementById('op-backup-dir-status'),
    uploadBackupBtn: document.getElementById('op-upload-backup-btn'),
    uploadBackupInput: document.getElementById('op-upload-backup-input'),

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
    integrationGuide: document.getElementById('op-integration-guide'),
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

    if (els.closeAllSessionsBtn) {
      els.closeAllSessionsBtn.disabled = sessions.length === 0;
      els.closeAllSessionsBtn.style.opacity = sessions.length === 0 ? '0.5' : '1';
      els.closeAllSessionsBtn.style.cursor = sessions.length === 0 ? 'not-allowed' : 'pointer';
    }

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

  if (els.closeAllSessionsBtn) {
    els.closeAllSessionsBtn.addEventListener('click', async function () {
      if (!window.confirm('¿Desconectar inmediatamente a TODOS los usuarios y forzar el cierre de todas las conexiones activas en OnlyOffice?')) return;
      try {
        const res = await api('/api/admin/editor-sessions/close-all', { method: 'POST' });
        showToast(res.message || 'Todas las sesiones activas han sido cerradas.');
        loadEditorSessions();
        loadOnlyOfficeStatus();
      } catch (err) {
        showToast(err.message, true);
      }
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

  /**
   * Styled confirmation dialog, built on the modal classes the panel already uses.
   *
   * Restoring a backup is the most far-reaching action in this console, so it gets a real dialog
   * rather than window.confirm: the warning needs room to explain what will and will not change.
   */
  function showConfirm(opts) {
    const overlay = document.createElement('div');
    overlay.className = 'op-modal-overlay';
    overlay.innerHTML =
      '<div class="op-modal op-modal--confirm">' +
        '<div class="op-confirm__icon">' +
          '<svg width="34" height="34" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2" stroke-linecap="round" stroke-linejoin="round">' +
          '<path d="m10.29 3.86-8.49 14.7A1 1 0 0 0 2.67 20h16.66a1 1 0 0 0 .87-1.5L11.71 3.86a1 1 0 0 0-1.72 0z"/>' +
          '<line x1="12" y1="9" x2="12" y2="13"/><line x1="12" y1="17" x2="12.01" y2="17"/></svg>' +
        '</div>' +
        '<h3></h3>' +
        '<p class="op-confirm__desc"></p>' +
        '<div class="op-modal__actions">' +
          '<button type="button" class="op-btn op-btn--ghost" data-confirm-cancel>Cancelar</button>' +
          '<button type="button" class="op-btn op-btn--danger" data-confirm-ok></button>' +
        '</div>' +
      '</div>';
    overlay.querySelector('h3').textContent = opts.title || 'Confirmar';
    overlay.querySelector('.op-confirm__desc').textContent = opts.message || '';
    const okBtn = overlay.querySelector('[data-confirm-ok]');
    okBtn.textContent = opts.confirmLabel || 'Continuar';

    function close() {
      document.removeEventListener('keydown', onKey);
      overlay.remove();
    }
    function onKey(e) { if (e.key === 'Escape') close(); }

    overlay.querySelector('[data-confirm-cancel]').addEventListener('click', close);
    overlay.addEventListener('click', function (e) { if (e.target === overlay) close(); });
    document.addEventListener('keydown', onKey);
    okBtn.addEventListener('click', function () {
      close();
      if (typeof opts.onConfirm === 'function') opts.onConfirm();
    });

    document.body.appendChild(overlay);
    okBtn.focus();
  }

  /** Runs a restore and reports the per-table outcome the backend returns. */
  async function runRestore(fileName) {
    showToast('Restaurando "' + fileName + '"... esto puede tardar según el tamaño del paquete.');
    try {
      const body = await api('/api/admin/backups/' + encodeURIComponent(fileName) + '/restore', { method: 'POST' });
      const d = body.data || {};
      const created = (d.filesCreated || 0) + (d.foldersCreated || 0);
      const updated = (d.filesUpdated || 0) + (d.foldersUpdated || 0);
      let msg = 'Restauración completada: ' + created + ' creados, ' + updated + ' actualizados, ' +
                (d.binariesRestored || 0) + ' binarios.';
      if (d.warnings && d.warnings.length) {
        msg += ' ' + d.warnings.length + ' advertencia(s) — revisa los logs.';
      }
      showToast(msg);
      loadBackups();
    } catch (err) {
      showToast('No se pudo restaurar: ' + err.message, true);
    }
  }

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
              '<button type="button" class="op-btn op-btn--sm op-btn--ghost" data-download-backup="' + escapeHtml(b.fileName) + '">Descargar ZIP</button>' +
              '<button type="button" class="op-btn op-btn--sm op-btn--warning" data-restore-backup="' + escapeHtml(b.fileName) + '">Restaurar</button>' +
              '<button type="button" class="op-btn op-btn--sm op-btn--danger" data-delete-backup="' + escapeHtml(b.fileName) + '">Eliminar</button>' +
            '</div>' +
          '</td>' +
          '</tr>';
      }).join('');

      // La descarga va por fetch y no por un <a href>: el endpoint exige ROLE_ADMIN y un enlace
      // nativo viaja sin la cabecera Basic, así que Spring devolvía 401 y Chrome cancelaba la
      // descarga con "el archivo no estaba disponible en el sitio", sin rastro del motivo real.
      els.backupsTbody.querySelectorAll('[data-download-backup]').forEach(function (btn) {
        btn.addEventListener('click', async function () {
          const fn = btn.dataset.downloadBackup;
          const label = btn.textContent;
          btn.disabled = true;
          btn.textContent = 'Descargando…';
          let url = null;
          try {
            const res = await api('/api/admin/backups/' + encodeURIComponent(fn) + '/download');
            const blob = await res.blob();
            url = URL.createObjectURL(blob);
            const a = document.createElement('a');
            a.href = url;
            a.download = fn;
            document.body.appendChild(a);
            a.click();
            a.remove();
          } catch (err) {
            showToast('No se pudo descargar el respaldo: ' + err.message, true);
          } finally {
            // El Object URL se revoca con un respiro: revocarlo en el mismo tick puede
            // adelantarse a que el navegador levante el blob y deja la descarga vacía.
            if (url) setTimeout(function () { URL.revokeObjectURL(url); }, 10000);
            btn.disabled = false;
            btn.textContent = label;
          }
        });
      });

      els.backupsTbody.querySelectorAll('[data-restore-backup]').forEach(function (btn) {
        btn.addEventListener('click', function () {
          const fn = btn.dataset.restoreBackup;
          showConfirm({
            title: 'Restaurar copia de seguridad',
            message: '¡Atención! Restaurar este respaldo reintegrará los metadatos y binarios al ' +
                     'estado de esa fecha. Los registros que ya existan se actualizarán y los que ' +
                     'falten se recrearán; nada de lo creado después se elimina. ¿Deseas continuar?',
            confirmLabel: 'Restaurar ahora',
            onConfirm: function () { runRestore(fn); },
          });
        });
      });

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
      els.cfgBackupDir.value = cfg.backupDirectory || '';
      els.cfgBackupReplicationDir.value = cfg.replicationDirectory || '';
      els.cfgBackupReplicationEnabled.checked = !!cfg.replicationEnabled;

      // Reachability is reported by the backend so the operator sees a broken network
      // share here instead of discovering it when a backup silently fails to replicate.
      if (cfg.replicationDirectory) {
        els.cfgReplicationStatus.textContent = cfg.replicationReachable
          ? 'Ruta accesible y escribible.'
          : 'ATENCION: la ruta no responde o no permite escritura. El respaldo local se conserva igual.';
      } else {
        els.cfgReplicationStatus.textContent =
          'Segunda copia fuera del servidor, por si el disco local falla.';
      }
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

  els.uploadBackupBtn.addEventListener('click', function () {
    els.uploadBackupInput.click();
  });

  els.uploadBackupInput.addEventListener('change', function () {
    const file = els.uploadBackupInput.files && els.uploadBackupInput.files[0];
    if (!file) return;
    // Reset immediately so picking the same file twice fires the event again.
    els.uploadBackupInput.value = '';
    showConfirm({
      title: 'Subir y restaurar respaldo externo',
      message: '¡Atención! Se cargará "' + file.name + '" y se reintegrarán sus metadatos y ' +
               'binarios. Los registros existentes se actualizarán y los faltantes se recrearán. ' +
               '¿Deseas continuar?',
      confirmLabel: 'Subir y restaurar',
      onConfirm: function () { uploadAndRestore(file); },
    });
  });

  async function uploadAndRestore(file) {
    showToast('Subiendo "' + file.name + '"...');
    try {
      const form = new FormData();
      form.append('file', file);
      // No Content-Type header on purpose: the browser sets the multipart boundary itself.
      const res = await fetch('/api/admin/backups/upload-and-restore', {
        method: 'POST',
        headers: { Authorization: state.authHeader },
        body: form,
      });
      const body = await res.json();
      if (!res.ok) throw new Error((body && body.message) || ('Error del servidor (' + res.status + ')'));
      const d = body.data || {};
      showToast('Respaldo externo restaurado: ' + ((d.filesCreated || 0) + (d.filesUpdated || 0)) +
                ' archivos, ' + (d.binariesRestored || 0) + ' binarios.');
      loadBackups();
    } catch (err) {
      showToast('No se pudo restaurar el respaldo externo: ' + err.message, true);
    }
  }

  /**
   * Comprueba una ruta contra el sistema de archivos del servidor antes de guardarla.
   * Sin esto, un error de tipeo solo se descubre cuando un respaldo deja de generarse.
   */
  async function verifyPath(input, statusEl, btn) {
    const path = input.value.trim();
    if (!path) {
      statusEl.textContent = 'Escribí una ruta para verificarla.';
      statusEl.className = 'op-field__hint is-error';
      return;
    }
    const original = btn.textContent;
    btn.disabled = true;
    btn.textContent = 'Verificando...';
    try {
      const body = await api('/api/admin/backups/config/validate-path', {
        method: 'POST',
        body: JSON.stringify({ path: path }),
      });
      const d = body.data || {};
      statusEl.textContent = d.message || (d.valid ? 'Ruta accesible.' : 'Ruta no accesible.');
      statusEl.className = 'op-field__hint ' + (d.valid ? 'is-ok' : 'is-error');
      if (d.valid && d.path) input.value = d.path;
    } catch (err) {
      statusEl.textContent = err.message;
      statusEl.className = 'op-field__hint is-error';
    } finally {
      btn.disabled = false;
      btn.textContent = original;
    }
  }

  if (els.verifyBackupDir) {
    els.verifyBackupDir.addEventListener('click', function () {
      verifyPath(els.cfgBackupDir, els.backupDirStatus, els.verifyBackupDir);
    });
  }
  if (els.verifyReplicationDir) {
    els.verifyReplicationDir.addEventListener('click', function () {
      verifyPath(els.cfgBackupReplicationDir, els.cfgReplicationStatus, els.verifyReplicationDir);
    });
  }

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
      backupDirectory: els.cfgBackupDir.value.trim(),
      replicationDirectory: els.cfgBackupReplicationDir.value.trim(),
      replicationEnabled: els.cfgBackupReplicationEnabled.checked,
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

        let appsBadges = '';
        if (u.consumingApps && u.consumingApps.length > 0) {
          appsBadges = '<div style="display:flex;flex-wrap:wrap;gap:4px;">' +
            u.consumingApps.map(function (appName) {
              return '<span class="op-badge op-badge--info">' + escapeHtml(appName) + '</span>';
            }).join('') +
            '</div>';
        } else {
          appsBadges = '<span class="op-badge op-badge--info">' + escapeHtml(u.apiKeyName || '—') + '</span>';
        }

        return '<tr>' +
          '<td><div style="display:flex;align-items:center;gap:10px;"><div class="op-avatar" style="width:28px;height:28px;font-size:12px;">' + initial + '</div><strong>' + name + '</strong></div></td>' +
          '<td><code>' + escapeHtml(u.userId) + '</code></td>' +
          '<td>' + appsBadges + '</td>' +
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
    if (!id) return;
    try {
      els.deleteKeyConfirm.disabled = true;
      await api('/api/admin/api-keys/' + id, { method: 'DELETE' });
      closeDeleteKeyModal();
      showToast('API Key eliminada permanentemente.');
      loadApiKeys();
    } catch (err) {
      showToast(err.message, true);
    } finally {
      els.deleteKeyConfirm.disabled = false;
    }
  });

  function buildIntegrationSnippet(apiKey, framework) {
    var origin = window.location.origin;

    var guides = {
      jsp: [
        '<div class="op-integration-guide__title">',
        '  <svg width="15" height="15" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2"><path d="M12 2v20M17 5H9.5a3.5 3.5 0 0 0 0 7h5a3.5 3.5 0 0 1 0 7H6"/></svg>',
        '  <span>Guía de Implementación: JSP / Java Nativo (NetBeans / Servlets / Web.xml)</span>',
        '</div>',
        '<ul class="op-integration-guide__list">',
        '  <li><strong>1. Configuración:</strong> Agregar los <code>&lt;context-param&gt;</code> en <code>Web Pages/WEB-INF/web.xml</code> (o en <code>Source Packages/config.properties</code>).</li>',
        '  <li><strong>2. Backend:</strong> Crear la clase en <code>Source Packages -> Metodos -> OfficePlatformService.java</code>. Cero dependencias externas (usa <code>HttpURLConnection</code> nativo).</li>',
        '  <li><strong>3. Frontend:</strong> Incrustar en <code>Web Pages -> EditorOffice.jsp</code> (o en tu JSP de vista). El contenedor ya incluye estilos responsivos (85vh).</li>',
        '</ul>'
      ].join(''),

      laravel: [
        '<div class="op-integration-guide__title">',
        '  <svg width="15" height="15" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2"><rect x="2" y="3" width="20" height="14" rx="2"/><line x1="8" y1="21" x2="16" y2="21"/><line x1="12" y1="17" x2="12" y2="21"/></svg>',
        '  <span>Guía de Implementación: Laravel / PHP</span>',
        '</div>',
        '<ul class="op-integration-guide__list">',
        '  <li><strong>1. Configuración:</strong> Añadir las 2 variables al final de tu archivo <code>.env</code> en la raíz del proyecto Laravel.</li>',
        '  <li><strong>2. Backend:</strong> Crear el controlador en <code>app/Http/Controllers/OfficeController.php</code> y registrar la ruta en <code>routes/web.php</code>.</li>',
        '  <li><strong>3. Frontend:</strong> Crear la vista Blade en <code>resources/views/office/editor.blade.php</code> con el contenedor y script del widget.</li>',
        '</ul>'
      ].join(''),

      node: [
        '<div class="op-integration-guide__title">',
        '  <svg width="15" height="15" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2"><circle cx="12" cy="12" r="10"/><path d="m4.93 4.93 4.24 4.24"/></svg>',
        '  <span>Guía de Implementación: Node.js (Express / Next.js / NestJS)</span>',
        '</div>',
        '<ul class="op-integration-guide__list">',
        '  <li><strong>1. Configuración:</strong> Añadir variables al archivo <code>.env</code> en la raíz del proyecto Node.js.</li>',
        '  <li><strong>2. Backend:</strong> Agregar la ruta en <code>routes/office.js</code> (o en tu <code>server.js</code>) con llamada <code>fetch</code> a <code>/api/auth/resolve</code>.</li>',
        '  <li><strong>3. Frontend:</strong> Incrustar el contenedor y script en tu plantilla (EJS/Pug) o componente frontend.</li>',
        '</ul>'
      ].join(''),

      python: [
        '<div class="op-integration-guide__title">',
        '  <svg width="15" height="15" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2"><path d="m10 15 5 3-5 3v-6Z"/></svg>',
        '  <span>Guía de Implementación: Python (Django / FastAPI / Flask)</span>',
        '</div>',
        '<ul class="op-integration-guide__list">',
        '  <li><strong>1. Configuración:</strong> Añadir variables en tu archivo <code>.env</code> o variables de entorno del servidor.</li>',
        '  <li><strong>2. Backend:</strong> Añadir la función en <code>views.py</code> (Django) o ruta en <code>main.py</code> (FastAPI/Flask) usando <code>requests.post</code>.</li>',
        '  <li><strong>3. Frontend:</strong> Ubicar la plantilla en <code>templates/editor.html</code> con las etiquetas de contexto.</li>',
        '</ul>'
      ].join(''),

      dotnet: [
        '<div class="op-integration-guide__title">',
        '  <svg width="15" height="15" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2"><path d="m18 16 4-4-4-4"/><path d="m6 8-4 4 4 4"/><path d="m14.5 4-5 16"/></svg>',
        '  <span>Guía de Implementación: .NET / C# (ASP.NET Core)</span>',
        '</div>',
        '<ul class="op-integration-guide__list">',
        '  <li><strong>1. Configuración:</strong> Añadir la sección <code>OfficePlatform</code> en <code>appsettings.json</code>.</li>',
        '  <li><strong>2. Backend:</strong> Crear el controlador en <code>Controllers/OfficeController.cs</code> con la acción <code>Editor()</code>.</li>',
        '  <li><strong>3. Frontend:</strong> Crear la vista Razor en <code>Views/Office/Editor.cshtml</code>.</li>',
        '</ul>'
      ].join(''),

      direct: [
        '<div class="op-integration-guide__title">',
        '  <svg width="15" height="15" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2"><circle cx="12" cy="12" r="10"/><path d="m8 12 2 2 4-4"/></svg>',
        '  <span>Modo Frontend Directo (Zero-Backend)</span>',
        '</div>',
        '<ul class="op-integration-guide__list">',
        '  <li><strong>Ubicación:</strong> Pega este bloque directamente en cualquier archivo HTML, JSP o PHP de tu aplicación.</li>',
        '  <li><strong>Funcionamiento:</strong> No requiere backend ni llamadas HTTP en tu servidor; el widget valida la API Key directamente en el navegador.</li>',
        '</ul>'
      ].join(''),

      generic: [
        '<div class="op-integration-guide__title">',
        '  <svg width="15" height="15" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2"><polyline points="4 17 10 11 4 5"/><line x1="12" y1="19" x2="20" y2="19"/></svg>',
        '  <span>Integración REST Genérica (cURL / HTTP)</span>',
        '</div>',
        '<ul class="op-integration-guide__list">',
        '  <li><strong>Endpoint:</strong> <code>POST /api/auth/resolve</code> con Header <code>X-Api-Key</code>.</li>',
        '  <li><strong>Payload:</strong> <code>{"cedula": "...", "nombre": "..."}</code>. Extrae <code>widgetToken</code> del JSON retornado.</li>',
        '</ul>'
      ].join(''),
    };

    var envs = {
      jsp: [
        '<!-- ========================================================================= -->',
        '<!-- OPCIÓN A (ESTÁNDAR JAVA WEB / NETBEANS): web.xml                           -->',
        '<!-- UBICACIÓN: Web Pages -> WEB-INF -> web.xml (dentro de <web-app> ... </web-app>) -->',
        '<!-- ========================================================================= -->',
        '<context-param>',
        '    <description>URL del Servidor Office Platform</description>',
        '    <param-name>OFFICE_PLATFORM_URL</param-name>',
        '    <param-value>' + origin + '</param-value>',
        '</context-param>',
        '<context-param>',
        '    <description>API Key de Integracion de Office Platform</description>',
        '    <param-name>OFFICE_PLATFORM_API_KEY</param-name>',
        '    <param-value>' + apiKey + '</param-value>',
        '</context-param>',
        '',
        '# =========================================================================',
        '# OPCIÓN B (.properties): Source Packages -> config.properties',
        '# =========================================================================',
        '# OFFICE_PLATFORM_URL=' + origin,
        '# OFFICE_PLATFORM_API_KEY=' + apiKey,
      ].join('\n'),

      laravel: [
        '# =========================================================================',
        '# UBICACIÓN: Archivo .env en la raíz de tu proyecto Laravel',
        '# =========================================================================',
        'OFFICE_PLATFORM_URL=' + origin,
        'OFFICE_PLATFORM_API_KEY=' + apiKey,
      ].join('\n'),

      node: [
        '# =========================================================================',
        '# UBICACIÓN: Archivo .env en la raíz de tu proyecto Node.js',
        '# =========================================================================',
        'OFFICE_PLATFORM_URL=' + origin,
        'OFFICE_PLATFORM_API_KEY=' + apiKey,
      ].join('\n'),

      python: [
        '# =========================================================================',
        '# UBICACIÓN: Archivo .env en la raíz de tu proyecto Python',
        '# =========================================================================',
        'OFFICE_PLATFORM_URL=' + origin,
        'OFFICE_PLATFORM_API_KEY=' + apiKey,
      ].join('\n'),

      dotnet: [
        '// =========================================================================',
        '// UBICACIÓN: appsettings.json',
        '// =========================================================================',
        '{',
        '  "OfficePlatform": {',
        '    "Url": "' + origin + '",',
        '    "ApiKey": "' + apiKey + '"',
        '  }',
        '}',
      ].join('\n'),

      direct: [
        '# Modo Zero-Backend:',
        '# No requiere archivo .env ni variables de entorno.',
        '# La API Key se coloca directamente en el atributo data-api-key del script en el Frontend.',
      ].join('\n'),

      generic: [
        'OFFICE_PLATFORM_URL=' + origin,
        'OFFICE_PLATFORM_API_KEY=' + apiKey,
      ].join('\n'),
    };

    var backends = {
      jsp: [
        '// =========================================================================================',
        '// UBICACIÓN EN NETBEANS: Source Packages -> Metodos -> OfficePlatformService.java',
        '// Cero dependencias externas: Compatible con Java 7, 8, 11, 17, 21 (Nativo HttpURLConnection)',
        '// =========================================================================================',
        'package Metodos;',
        '',
        'import java.io.BufferedReader;',
        'import java.io.InputStream;',
        'import java.io.InputStreamReader;',
        'import java.io.OutputStream;',
        'import java.net.HttpURLConnection;',
        'import java.net.URL;',
        'import java.util.Properties;',
        '',
        'public class OfficePlatformService {',
        '    public static String SERVER_URL = "' + origin + '";',
        '    private static String API_KEY = "' + apiKey + '";',
        '',
        '    static {',
        '        try (InputStream is = OfficePlatformService.class.getClassLoader().getResourceAsStream("config.properties")) {',
        '            if (is != null) {',
        '                Properties prop = new Properties();',
        '                prop.load(is);',
        '                if (prop.getProperty("OFFICE_PLATFORM_URL") != null) SERVER_URL = prop.getProperty("OFFICE_PLATFORM_URL").trim();',
        '                if (prop.getProperty("OFFICE_PLATFORM_API_KEY") != null) API_KEY = prop.getProperty("OFFICE_PLATFORM_API_KEY").trim();',
        '            }',
        '        } catch (Exception e) {',
        '            // Si no existe config.properties, conserva las URLs por defecto',
        '        }',
        '    }',
        '',
        '    /**',
        '     * Inicializa o sobreescribe la configuracion (util si se lee desde web.xml en un Servlet o JSP).',
        '     */',
        '    public static void init(String serverUrl, String apiKey) {',
        '        if (serverUrl != null && !serverUrl.trim().isEmpty()) SERVER_URL = serverUrl.trim();',
        '        if (apiKey != null && !apiKey.trim().isEmpty()) API_KEY = apiKey.trim();',
        '    }',
        '',
        '    /**',
        '     * Resuelve el token seguro (JWT) para el usuario activo.',
        '     * @param cedula Identificador único (Cédula, documento o username)',
        '     * @param nombre Nombre visible para autoría y comentarios',
        '     * @return widgetToken firmado para incrustar en el front',
        '     */',
        '    public static String obtenerToken(String cedula, String nombre) {',
        '        if (cedula == null || cedula.trim().isEmpty()) cedula = "usr_anonimo";',
        '        if (nombre == null || nombre.trim().isEmpty()) nombre = "Usuario " + cedula;',
        '        try {',
        '            URL url = new URL(SERVER_URL + "/api/auth/resolve");',
        '            HttpURLConnection conn = (HttpURLConnection) url.openConnection();',
        '            conn.setRequestMethod("POST");',
        '            conn.setRequestProperty("Content-Type", "application/json; charset=UTF-8");',
        '            conn.setRequestProperty("X-Api-Key", API_KEY);',
        '            conn.setConnectTimeout(4000);',
        '            conn.setReadTimeout(6000);',
        '            conn.setDoOutput(true);',
        '',
        "            String safeCedula = cedula.replace('\"', ' ');",
        "            String safeNombre = nombre.replace('\"', ' ');",
        '            String jsonBody = "{\\"cedula\\":\\"" + safeCedula + "\\",\\"nombre\\":\\"" + safeNombre + "\\"}";',
        '            try (OutputStream os = conn.getOutputStream()) {',
        '                os.write(jsonBody.getBytes("UTF-8"));',
        '            }',
        '',
        '            if (conn.getResponseCode() >= 200 && conn.getResponseCode() < 300) {',
        '                try (BufferedReader br = new BufferedReader(new InputStreamReader(conn.getInputStream(), "UTF-8"))) {',
        '                    StringBuilder sb = new StringBuilder();',
        '                    String line;',
        '                    while ((line = br.readLine()) != null) sb.append(line);',
        '                    String res = sb.toString();',
        '                    int idx = res.indexOf("\\"widgetToken\\":\\"");',
        '                    if (idx != -1) {',
        '                        int start = idx + 15;',
        '                        int end = res.indexOf("\\"", start);',
        '                        return res.substring(start, end);',
        '                    }',
        '                }',
        '            }',
        '        } catch (Exception ex) {',
        '            System.err.println("[OfficePlatformService] Error al obtener token: " + ex.getMessage());',
        '        }',
        '        return "";',
        '    }',
        '}',
        '',
        '// =========================================================================================',
        '// CÓMO CONSUMIRLO DESDE UN SERVLET (Source Packages -> Servlets -> TuServlet.java):',
        '// -----------------------------------------------------------------------------------------',
        '// // Si usas web.xml, puedes inicializar los parametros desde ServletContext:',
        '// String ctxUrl = getServletContext().getInitParameter("OFFICE_PLATFORM_URL");',
        '// String ctxKey = getServletContext().getInitParameter("OFFICE_PLATFORM_API_KEY");',
        '// Metodos.OfficePlatformService.init(ctxUrl, ctxKey);',
        '//',
        '// String cedula = (String) session.getAttribute("Documento");',
        '// String nombre = (String) session.getAttribute("Usuario");',
        '// String token  = Metodos.OfficePlatformService.obtenerToken(cedula, nombre);',
        '// request.setAttribute("widgetToken", token);',
        '// request.setAttribute("serverUrl", Metodos.OfficePlatformService.SERVER_URL);',
        '// request.getRequestDispatcher("Reporte.jsp").forward(request, response);',
        '// =========================================================================================',
      ].join('\n'),

      laravel: [
        '<?php',
        '// =========================================================================',
        '// UBICACIÓN: app/Http/Controllers/OfficeController.php',
        '// =========================================================================',
        'namespace App\\Http\\Controllers;',
        '',
        'use Illuminate\\Http\\Request;',
        'use Illuminate\\Support\\Facades\\Http;',
        '',
        'class OfficeController extends Controller',
        '{',
        '    public function index(Request $request)',
        '    {',
        '        // 1. Identificar usuario autenticado',
        '        $user = auth()->user();',
        '        $cedula = $user->cedula ?? $user->documento ?? $user->id ?? "usr_anonimo";',
        '        $nombre = $user->name ?? $user->nombreCompleto ?? ("Usuario " . $cedula);',
        '',
        '        $serverUrl = env("OFFICE_PLATFORM_URL", "' + origin + '");',
        '        $apiKey    = env("OFFICE_PLATFORM_API_KEY", "' + apiKey + '");',
        '',
        '        // 2. Solicitar Widget Token seguro',
        '        $widgetToken = "";',
        '        try {',
        '            $response = Http::withHeaders([',
        '                "X-Api-Key"    => $apiKey,',
        '                "Content-Type" => "application/json",',
        '            ])->timeout(6)->post($serverUrl . "/api/auth/resolve", [',
        '                "cedula" => (string) $cedula,',
        '                "nombre" => (string) $nombre,',
        '            ]);',
        '            if ($response->successful()) {',
        '                $widgetToken = $response->json("data.widgetToken") ?? "";',
        '            }',
        '        } catch (\\Exception $e) {',
        '            \\Log::error("[OfficePlatform] Error: " . $e->getMessage());',
        '        }',
        '',
        '        return view("office.editor", [',
        '            "widgetToken" => $widgetToken,',
        '            "serverUrl"   => $serverUrl,',
        '        ]);',
        '    }',
        '}',
        '',
        '// =========================================================================',
        '// REGISTRAR RUTA EN routes/web.php:',
        '// Route::middleware(["auth"])->get("/editor-documentos", [App\\Http\\Controllers\\OfficeController::class, "index"])->name("office.editor");',
        '// =========================================================================',
      ].join('\n'),

      node: [
        '// =========================================================================',
        '// UBICACIÓN: routes/office.js (o dentro de server.js)',
        '// =========================================================================',
        'const express = require("express");',
        'const router = express.Router();',
        '',
        'router.get("/editor-documentos", async (req, res) => {',
        '  const serverUrl = process.env.OFFICE_PLATFORM_URL || "' + origin + '";',
        '  const apiKey    = process.env.OFFICE_PLATFORM_API_KEY || "' + apiKey + '";',
        '',
        '  // 1. Identificar usuario desde la sesión o JWT activo',
        '  const user = req.user || req.session?.user || {};',
        '  const cedula = user.cedula || user.documento || user.id || "usr_anonimo";',
        '  const nombre = user.name || user.nombreCompleto || ("Usuario " + cedula);',
        '',
        '  let widgetToken = "";',
        '  try {',
        '    const response = await fetch(`${serverUrl}/api/auth/resolve`, {',
        '      method: "POST",',
        '      headers: {',
        '        "Content-Type": "application/json",',
        '        "X-Api-Key": apiKey,',
        '      },',
        '      body: JSON.stringify({ cedula: String(cedula), nombre: String(nombre) }),',
        '    });',
        '    const data = await response.json();',
        '    if (data?.data?.widgetToken) {',
        '      widgetToken = data.data.widgetToken;',
        '    }',
        '  } catch (err) {',
        '    console.error("[OfficePlatform] Error resolviendo token:", err.message);',
        '  }',
        '',
        '  // 2. Renderizar plantilla (o responder JSON si es API REST para React/Vue/Angular)',
        '  res.render("editor", { widgetToken, serverUrl });',
        '});',
        '',
        'module.exports = router;',
      ].join('\n'),

      python: [
        '# =========================================================================',
        '# UBICACIÓN: views.py (Django) o routes.py (FastAPI / Flask)',
        '# =========================================================================',
        'import os',
        'import requests',
        'from django.shortcuts import render',
        '',
        'def office_editor_view(request):',
        '    server_url = os.getenv("OFFICE_PLATFORM_URL", "' + origin + '")',
        '    api_key    = os.getenv("OFFICE_PLATFORM_API_KEY", "' + apiKey + '")',
        '',
        '    # 1. Identificar usuario de la sesión activa',
        '    user = request.user if hasattr(request, "user") and request.user.is_authenticated else None',
        '    cedula = getattr(user, "cedula", None) or getattr(user, "username", "usr_anonimo")',
        '    nombre = getattr(user, "get_full_name", lambda: None)() or getattr(user, "first_name", None) or f"Usuario {cedula}"',
        '',
        '    widget_token = ""',
        '    try:',
        '        res = requests.post(',
        '            f"{server_url}/api/auth/resolve",',
        '            headers={"X-Api-Key": api_key, "Content-Type": "application/json"},',
        '            json={"cedula": str(cedula), "nombre": str(nombre)},',
        '            timeout=6,',
        '        )',
        '        if res.status_code == 200:',
        '            widget_token = res.json().get("data", {}).get("widgetToken", "")',
        '    except Exception as e:',
        '        print(f"[OfficePlatform] Error obteniendo token: {e}")',
        '',
        '    return render(request, "editor.html", {',
        '        "widget_token": widget_token,',
        '        "server_url": server_url,',
        '    })',
      ].join('\n'),

      dotnet: [
        '// =========================================================================',
        '// UBICACIÓN: Controllers/OfficeController.cs',
        '// =========================================================================',
        'using System;',
        'using System.Net.Http;',
        'using System.Text;',
        'using System.Text.Json;',
        'using System.Threading.Tasks;',
        'using Microsoft.AspNetCore.Mvc;',
        'using Microsoft.Extensions.Configuration;',
        '',
        'namespace TuProyecto.Controllers',
        '{',
        '    public class OfficeController : Controller',
        '    {',
        '        private readonly IConfiguration _config;',
        '        private static readonly HttpClient _httpClient = new HttpClient();',
        '',
        '        public OfficeController(IConfiguration config)',
        '        {',
        '            _config = config;',
        '        }',
        '',
        '        [HttpGet("editor-documentos")]',
        '        public async Task<IActionResult> Editor()',
        '        {',
        '            string serverUrl = _config["OfficePlatform:Url"] ?? "' + origin + '";',
        '            string apiKey    = _config["OfficePlatform:ApiKey"] ?? "' + apiKey + '";',
        '',
        '            string cedula = User?.Identity?.Name ?? "12345678";',
        '            string nombre = User?.Identity?.Name ?? "Usuario " + cedula;',
        '',
        '            string widgetToken = "";',
        '            try',
        '            {',
        '                var payload = new { cedula = cedula, nombre = nombre };',
        '                using var request = new HttpRequestMessage(HttpMethod.Post, $"{serverUrl}/api/auth/resolve");',
        '                request.Headers.Add("X-Api-Key", apiKey);',
        '                request.Content = new StringContent(JsonSerializer.Serialize(payload), Encoding.UTF8, "application/json");',
        '',
        '                using var response = await _httpClient.SendAsync(request);',
        '                if (response.IsSuccessStatusCode)',
        '                {',
        '                    var json = await response.Content.ReadAsStringAsync();',
        '                    using var doc = JsonDocument.Parse(json);',
        '                    widgetToken = doc.RootElement.GetProperty("data").GetProperty("widgetToken").GetString() ?? "";',
        '                }',
        '            }',
        '            catch (Exception ex)',
        '            {',
        '                Console.WriteLine($"[OfficePlatform] Error: {ex.Message}");',
        '            }',
        '',
        '            ViewBag.WidgetToken = widgetToken;',
        '            ViewBag.ServerUrl = serverUrl;',
        '            return View();',
        '        }',
        '    }',
        '}',
      ].join('\n'),

      direct: [
        '<!-- ========================================================================= -->',
        '<!-- MODO ZERO-BACKEND: No requiere código de servidor ni controladores.        -->',
        '<!-- Solo copia y pega el código de la pestaña "Frontend" en tu HTML o JSP.    -->',
        '<!-- ========================================================================= -->',
      ].join('\n'),

      generic: [
        '# Solicitud HTTP estándar cURL para obtener el token desde la terminal o Postman:',
        'curl -X POST "' + origin + '/api/auth/resolve" \\',
        '  -H "Content-Type: application/json" \\',
        '  -H "X-Api-Key: ' + apiKey + '" \\',
        '  -d \'{"cedula":"12345678","nombre":"Usuario Demo"}\'',
        '',
        '# Respuesta esperada:',
        '# {',
        '#   "success": true,',
        '#   "data": {',
        '#     "widgetToken": "eyJhbGciOi...",',
        '#     "expiresIn": 28800',
        '#   }',
        '# }',
      ].join('\n'),
    };

    var frontends = {
      jsp: [
        '<%--',
        '  UBICACIÓN EN NETBEANS: Web Pages -> EditorOffice.jsp (o en tu JSP de vista)',
        '--%>',
        '<%@ page contentType="text/html;charset=UTF-8" language="java" %>',
        '<%@ page import="Metodos.OfficePlatformService" %>',
        '<%',
        '  // Si configuraste las variables en web.xml, se cargan de application (ServletContext):',
        '  String ctxUrl = application.getInitParameter("OFFICE_PLATFORM_URL");',
        '  String ctxKey = application.getInitParameter("OFFICE_PLATFORM_API_KEY");',
        '  if (ctxUrl != null && ctxKey != null) {',
        '    OfficePlatformService.init(ctxUrl, ctxKey);',
        '  }',
        '',
        '  String token = (String) request.getAttribute("widgetToken");',
        '  String server = (String) request.getAttribute("serverUrl");',
        '  if (token == null || token.isEmpty()) {',
        '    String cedula = session.getAttribute("Documento") != null ? session.getAttribute("Documento").toString() : "12345678";',
        '    String nombre = session.getAttribute("Usuario") != null ? session.getAttribute("Usuario").toString() : "Usuario Sistema";',
        '    token = OfficePlatformService.obtenerToken(cedula, nombre);',
        '    server = OfficePlatformService.SERVER_URL;',
        '  }',
        '%>',
        '',
        '<!-- Contenedor del Editor y Gestor Documental (Estilos responsivos listos) -->',
        '<div id="office-platform" style="width: 100%; height: 85vh; border: 1px solid #cbd5e1; border-radius: 8px; overflow: hidden; background: #ffffff;"></div>',
        '',
        '<!-- Script de integración de Office Platform -->',
        '<script',
        '  src="<%= server %>/office-platform-widget.js"',
        '  data-container="office-platform"',
        '  data-server="<%= server %>"',
        '  data-token="<%= token %>">',
        '</script>',
      ].join('\n'),
      laravel: [
        '{{-- UBICACIÓN: resources/views/office/editor.blade.php --}}',
        '<!DOCTYPE html>',
        '<html lang="es">',
        '<head>',
        '    <meta charset="UTF-8">',
        '    <title>Editor de Documentos</title>',
        '</head>',
        '<body style="margin: 15px; font-family: sans-serif;">',
        '    <h2>Gestor y Editor Documental</h2>',
        '',
        '    <!-- Contenedor del Editor -->',
        '    <div id="office-platform" style="width: 100%; height: 85vh; border: 1px solid #cbd5e1; border-radius: 8px; overflow: hidden; background: #ffffff;"></div>',
        '',
        '    <!-- Script del Widget de Office Platform -->',
        '    <script',
        '        src="{{ $serverUrl }}/office-platform-widget.js"',
        '        data-container="office-platform"',
        '        data-server="{{ $serverUrl }}"',
        '        data-token="{{ $widgetToken }}">',
        '    </script>',
        '</body>',
        '</html>',
      ].join('\n'),

      node: [
        '<!-- UBICACIÓN: views/editor.ejs (o tu plantilla HTML) -->',
        '<!DOCTYPE html>',
        '<html lang="es">',
        '<head>',
        '  <meta charset="UTF-8">',
        '  <title>Editor de Documentos</title>',
        '</head>',
        '<body style="margin: 15px; font-family: sans-serif;">',
        '  <div id="office-platform" style="width: 100%; height: 85vh; border: 1px solid #cbd5e1; border-radius: 8px; overflow: hidden; background: #ffffff;"></div>',
        '',
        '  <script',
        '    src="<%= serverUrl %>/office-platform-widget.js"',
        '    data-container="office-platform"',
        '    data-server="<%= serverUrl %>"',
        '    data-token="<%= widgetToken %>">',
        '  </script>',
        '</body>',
        '</html>',
      ].join('\n'),

      python: [
        '<!-- UBICACIÓN: templates/editor.html -->',
        '<!DOCTYPE html>',
        '<html lang="es">',
        '<head>',
        '  <meta charset="UTF-8">',
        '  <title>Editor de Documentos</title>',
        '</head>',
        '<body style="margin: 15px; font-family: sans-serif;">',
        '  <div id="office-platform" style="width: 100%; height: 85vh; border: 1px solid #cbd5e1; border-radius: 8px; overflow: hidden; background: #ffffff;"></div>',
        '',
        '  <script',
        '    src="{{ server_url }}/office-platform-widget.js"',
        '    data-container="office-platform"',
        '    data-server="{{ server_url }}"',
        '    data-token="{{ widget_token }}">',
        '  </script>',
        '</body>',
        '</html>',
      ].join('\n'),

      dotnet: [
        '@* UBICACIÓN: Views/Office/Editor.cshtml *@',
        '@{',
        '    Layout = null;',
        '}',
        '<!DOCTYPE html>',
        '<html lang="es">',
        '<head>',
        '    <meta charset="UTF-8">',
        '    <title>Editor de Documentos</title>',
        '</head>',
        '<body style="margin: 15px; font-family: sans-serif;">',
        '    <div id="office-platform" style="width: 100%; height: 85vh; border: 1px solid #cbd5e1; border-radius: 8px; overflow: hidden; background: #ffffff;"></div>',
        '',
        '    <script',
        '        src="@ViewBag.ServerUrl/office-platform-widget.js"',
        '        data-container="office-platform"',
        '        data-server="@ViewBag.ServerUrl"',
        '        data-token="@ViewBag.WidgetToken">',
        '    </script>',
        '</body>',
        '</html>',
      ].join('\n'),

      direct: [
        '<!-- UBICACIÓN: En cualquier vista HTML, JSP o PHP de tu aplicación -->',
        '<div id="office-platform" style="width: 100%; height: 85vh; border: 1px solid #cbd5e1; border-radius: 8px; overflow: hidden; background: #ffffff;"></div>',
        '',
        '<script',
        '  src="' + origin + '/office-platform-widget.js"',
        '  data-api-key="' + apiKey + '"',
        '  data-container="office-platform"',
        '  data-server="' + origin + '"',
        '  data-user-id="12345678"',
        '  data-user-name="Usuario Demo">',
        '</script>',
      ].join('\n'),

      generic: [
        '<!-- UBICACIÓN: En cualquier archivo HTML con tu token generado -->',
        '<div id="office-platform" style="width: 100%; height: 85vh; border: 1px solid #cbd5e1; border-radius: 8px; overflow: hidden; background: #ffffff;"></div>',
        '',
        '<script',
        '  src="' + origin + '/office-platform-widget.js"',
        '  data-container="office-platform"',
        '  data-server="' + origin + '"',
        '  data-token="TOKEN_OBTENIDO_DEL_BACKEND">',
        '</script>',
      ].join('\n'),
    };

    return {
      guide: guides[framework] || guides.generic,
      env: envs[framework] || envs.generic,
      backend: backends[framework] || backends.generic,
      frontend: frontends[framework] || frontends.generic,
    };
  }

  function renderIntegrationCode(apiKey) {
    var snippet = buildIntegrationSnippet(apiKey, els.frameworkSelect.value);
    if (els.integrationGuide) {
      els.integrationGuide.innerHTML = snippet.guide;
    }
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
