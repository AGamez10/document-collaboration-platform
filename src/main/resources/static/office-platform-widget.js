/**
 * Office Platform Widget
 * Self-contained file-manager widget, zero external dependencies.
 * Include via <script src="office-platform-widget.js" data-api-key="..." data-container="..." data-server="..." data-user-id="..." data-user-name="..."></script>
 * data-user-id/data-user-name are optional and identify the current end user to OnlyOffice's
 * collaborative editor (author name shown to other concurrent editors); they default to the
 * API key's own id/name when omitted.
 *
 * ── Public API ────────────────────────────────────────────────────────────
 * Once the script has loaded, a global `window.OfficePlatform` is available so the
 * host page can open the OnlyOffice editor directly, without navigating the manager:
 *
 *   OfficePlatform.openEditor({ type: 'document' })      // create + open a blank .docx
 *   OfficePlatform.openEditor({ type: 'spreadsheet' })   // create + open a blank .xlsx
 *   OfficePlatform.openEditor({ type: 'presentation' })  // create + open a blank .pptx
 *   OfficePlatform.openEditor({ fileId: 123 })           // open an existing file by id
 *
 * Returns a Promise resolving with the file ({ fileId, originalFileName, ... }) for the
 * create flows, or ({ fileId }) for the open-by-id flow. New documents are created honoring
 * the active scope (private/shared) and the current user's token, saved in the manager, and
 * shown in a fullscreen editor overlay. Example consumer button:
 *
 *   <button onclick="OfficePlatform.openEditor({ type: 'document' })">Nuevo documento</button>
 */
(function () {
  'use strict';

  // ── Config from script tag ──────────────────────────────────────────────
  const scriptTag = document.currentScript || (function () {
    const scripts = document.getElementsByTagName('script');
    return scripts[scripts.length - 1];
  })();

  const API_KEY = scriptTag.getAttribute('data-api-key') || '';
  const CONTAINER = scriptTag.getAttribute('data-container');
  const SERVER = (scriptTag.getAttribute('data-server') || '').replace(/\/$/, '');

  // Port and address of the OnlyOffice Document Server as the browser reaches it. Only used when
  // the backend sends no usable value: the script tag for the editor API must always be an
  // absolute URL, never a path relative to the consuming application.
  const OO_DEFAULT_PORT = '8081';
  const OO_FALLBACK_URL = 'http://localhost:' + OO_DEFAULT_PORT;
  const USER_ID = scriptTag.getAttribute('data-user-id') || '';
  const USER_NAME = scriptTag.getAttribute('data-user-name') || '';
  const WIDGET_TOKEN = scriptTag.getAttribute('data-token') || '';

  if ((!API_KEY && !WIDGET_TOKEN) || !CONTAINER || !SERVER) {
    console.error('[OfficePlatform] Missing required attributes: (data-api-key or data-token), data-container, data-server');
    return;
  }

  const ALLOWED_EXTENSIONS = ['docx', 'doc', 'xlsx', 'xls', 'pptx', 'ppt', 'odt', 'ods', 'odp', 'pdf'];
  const ACCEPT_ATTR = ALLOWED_EXTENSIONS.map(function (e) { return '.' + e; }).join(',');

  // ── CSS ─────────────────────────────────────────────────────────────────
  // Theme variables — override any of these on the container element (or via
  // a page-level rule targeting the container's id/class) to customize the
  // widget's look without touching this stylesheet:
  //
  //   --op-accent           Primary accent (buttons, active tab, links)
  //   --op-accent-hover     Primary hover state
  //   --op-accent-text      Text color on top of the accent color
  //   --op-color-accent-2   Secondary accent (gradients, highlights)
  //   --op-bg               Main content background
  //   --op-bg-card          Card / panel / toolbar background
  //   --op-bg-hover         Secondary surface (hovers, tracks, illustrations)
  //   --op-border           Border color
  //   --op-text             Primary text color
  //   --op-text-secondary   Secondary / muted text color
  //   --op-danger           Destructive action color
  //   --op-danger-hover     Destructive hover state
  //   --op-success          Success state color
  //   --op-color-warning    Warning state color
  //   --op-color-info       Info state color
  //   --op-shadow           Default card/panel shadow
  //   --op-shadow-lg        Elevated shadow (modals, menus, hover states)
  //   --op-radius-sm/md/lg  Border radius scale
  //   --op-space-*          Spacing scale
  //   --op-transition       Shared interactive transition duration
  const CSS = `
    .op-widget,
    .op-modal-backdrop,
    .op-context-menu,
    .op-toast-stack,
    .op-editor-overlay {
      --op-accent: #4361ee;
      --op-accent-hover: #5472f5;
      --op-accent-text: #ffffff;
      --op-color-accent-2: #8b5cf6;
      --op-bg: #f8f9fa;
      --op-bg-card: #ffffff;
      --op-bg-hover: #f0f2f5;
      --op-border: #dadce0;
      --op-text: #1a1d27;
      --op-text-secondary: #5f6368;
      --op-danger: #e74c3c;
      --op-danger-hover: #d63a2a;
      --op-success: #2ecc71;
      --op-color-warning: #f59e0b;
      --op-color-info: #3b82f6;
      --op-shadow: 0 2px 8px rgba(0,0,0,0.08);
      --op-shadow-lg: 0 8px 24px rgba(0,0,0,0.12);
      --op-radius-sm: 6px;
      --op-radius-md: 10px;
      --op-radius-lg: 16px;
      --op-space-1: 4px;
      --op-space-2: 8px;
      --op-space-3: 12px;
      --op-space-4: 16px;
      --op-space-5: 24px;
      --op-transition: 150ms;
    }

    .op-widget {
      font-family: 'Inter', system-ui, -apple-system, 'Segoe UI', Roboto, sans-serif;
      font-size: 14px;
      color: var(--op-text);
      background: var(--op-bg);
      border: 1px solid var(--op-border);
      border-radius: var(--op-radius-lg);
      overflow: hidden;
      position: relative;
      display: flex;
      width: 100%;
      height: 100%;
      min-height: 560px;
      box-sizing: border-box;
    }
    @media (prefers-color-scheme: dark) {
      .op-widget,
      .op-modal-backdrop,
      .op-context-menu,
      .op-toast-stack,
      .op-editor-overlay {
        --op-accent: #5b7cfa;
        --op-accent-hover: #7691fb;
        --op-accent-text: #0d0d14;
        --op-color-accent-2: #a78bfa;
        --op-bg: #0f1117;
        --op-bg-card: #1a1d27;
        --op-bg-hover: #252830;
        --op-border: #2a2d37;
        --op-text: #e4e6eb;
        --op-text-secondary: #9aa0a6;
        --op-danger: #ff6b6b;
        --op-danger-hover: #ff8787;
        --op-success: #51cf66;
        --op-color-warning: #ffa94d;
        --op-color-info: #4dabf7;
        --op-shadow: 0 2px 8px rgba(0,0,0,0.3);
        --op-shadow-lg: 0 8px 24px rgba(0,0,0,0.5);
      }
    }
    /* Manual theme override (data-op-theme="light"|"dark" on .op-widget) always
       wins over the prefers-color-scheme media query above: an attribute
       selector has higher specificity than a plain-class one, regardless of
       which rule appears first or which media query currently matches. */
    .op-widget[data-op-theme="light"],
    body:has(.op-widget[data-op-theme="light"]) .op-modal-backdrop,
    body:has(.op-widget[data-op-theme="light"]) .op-context-menu,
    body:has(.op-widget[data-op-theme="light"]) .op-toast-stack,
    body:has(.op-widget[data-op-theme="light"]) .op-editor-overlay {
      --op-accent: #4361ee;
      --op-accent-hover: #5472f5;
      --op-accent-text: #ffffff;
      --op-color-accent-2: #8b5cf6;
      --op-bg: #f8f9fa;
      --op-bg-card: #ffffff;
      --op-bg-hover: #f0f2f5;
      --op-border: #dadce0;
      --op-text: #1a1d27;
      --op-text-secondary: #5f6368;
      --op-danger: #e74c3c;
      --op-danger-hover: #d63a2a;
      --op-success: #2ecc71;
      --op-color-warning: #f59e0b;
      --op-color-info: #3b82f6;
      --op-shadow: 0 2px 8px rgba(0,0,0,0.08);
      --op-shadow-lg: 0 8px 24px rgba(0,0,0,0.12);
    }
    .op-widget[data-op-theme="dark"],
    body:has(.op-widget[data-op-theme="dark"]) .op-modal-backdrop,
    body:has(.op-widget[data-op-theme="dark"]) .op-context-menu,
    body:has(.op-widget[data-op-theme="dark"]) .op-toast-stack,
    body:has(.op-widget[data-op-theme="dark"]) .op-editor-overlay {
      --op-accent: #5b7cfa;
      --op-accent-hover: #7691fb;
      --op-accent-text: #0d0d14;
      --op-color-accent-2: #a78bfa;
      --op-bg: #0f1117;
      --op-bg-card: #1a1d27;
      --op-bg-hover: #252830;
      --op-border: #2a2d37;
      --op-text: #e4e6eb;
      --op-text-secondary: #9aa0a6;
      --op-danger: #ff6b6b;
      --op-danger-hover: #ff8787;
      --op-success: #51cf66;
      --op-color-warning: #ffa94d;
      --op-color-info: #4dabf7;
      --op-shadow: 0 2px 8px rgba(0,0,0,0.3);
      --op-shadow-lg: 0 8px 24px rgba(0,0,0,0.5);
    }
    .op-widget *, .op-widget *::before, .op-widget *::after { box-sizing: border-box; }
    /* Any element toggled via the "hidden" DOM property/attribute must stay
       hidden regardless of its own display rule (buttons, bars, etc. below
       all set an explicit display value, which otherwise beats the UA
       [hidden] default in the cascade). */
    .op-widget [hidden] { display: none !important; }
    .op-widget button, .op-widget input, .op-widget select {
      font-family: inherit;
      font-size: inherit;
      color: inherit;
    }
    .op-widget svg { display: block; flex-shrink: 0; }
    .op-icon { width: 15px; height: 15px; }

    /* ── Main ────────────────────────────────────────────────────────── */
    .op-main { flex: 1; display: flex; flex-direction: column; min-width: 0; background: var(--op-bg); }

    /* ── Section tabs (replaces sidebar — compact, embeddable) ────────── */
    .op-tabs {
      display: flex;
      align-items: center;
      gap: 2px;
      padding: var(--op-space-2) var(--op-space-3) 0;
      background: var(--op-bg-card);
      border-bottom: 1px solid var(--op-border);
    }
    .op-tab {
      display: inline-flex;
      align-items: center;
      gap: 6px;
      border: none;
      background: transparent;
      padding: 9px 14px;
      font-weight: 600;
      font-size: 13px;
      color: var(--op-text-secondary);
      cursor: pointer;
      border-radius: var(--op-radius-sm) var(--op-radius-sm) 0 0;
      border-bottom: 2px solid transparent;
      transition: color var(--op-transition), border-color var(--op-transition), background var(--op-transition);
    }
    .op-tab:hover { color: var(--op-text); background: var(--op-bg-hover); }
    .op-tab.op-active { color: var(--op-accent); border-bottom-color: var(--op-accent); }

    /* ── Toolbar ─────────────────────────────────────────────────────── */
    .op-toolbar {
      display: flex;
      align-items: center;
      gap: var(--op-space-3);
      padding: var(--op-space-3) var(--op-space-4);
      background: var(--op-bg-card);
      border-bottom: 1px solid var(--op-border);
      flex-wrap: wrap;
    }
    .op-search-wrap { position: relative; flex: 1; min-width: 160px; }
    .op-search-icon {
      position: absolute;
      left: 10px;
      top: 50%;
      transform: translateY(-50%);
      color: var(--op-text-secondary);
      pointer-events: none;
    }
    .op-search {
      width: 100%;
      padding: 8px 32px 8px 32px;
      border: 1px solid var(--op-border);
      border-radius: var(--op-radius-md);
      background: var(--op-bg);
      color: var(--op-text);
      transition: border-color var(--op-transition), box-shadow var(--op-transition);
    }
    .op-search::placeholder { color: var(--op-text-secondary); }
    .op-search:focus {
      outline: none;
      border-color: var(--op-accent);
      box-shadow: 0 0 0 3px color-mix(in srgb, var(--op-accent) 20%, transparent);
    }
    /* Aviso de inactividad: flota sobre el editor sin taparlo. */
    .op-idle-warn {
      position: absolute; left: 50%; bottom: 24px; transform: translateX(-50%);
      z-index: 40; display: flex; align-items: center; gap: 14px;
      background: #1f2937; color: #fff; border: 1px solid rgba(255,255,255,.14);
      border-radius: 10px; padding: 12px 16px; font-size: 13px;
      box-shadow: 0 10px 30px rgba(0,0,0,.35); max-width: 92vw;
    }
    .op-idle-warn-btn {
      appearance: none; border: 0; border-radius: 7px; cursor: pointer;
      background: var(--op-accent); color: #fff;
      font-size: 12.5px; font-weight: 600; padding: 7px 14px; white-space: nowrap;
    }
    .op-idle-warn-btn:hover { filter: brightness(1.1); }
    .op-idle-suspended {
      display: flex; flex-direction: column; align-items: center; justify-content: center;
      gap: 14px; height: 100%; padding: 40px 24px; text-align: center;
      background: var(--op-bg); color: var(--op-text);
    }
    .op-idle-suspended h3 { margin: 0; font-size: 18px; font-weight: 700; }
    .op-idle-suspended p {
      margin: 0; font-size: 13.5px; color: var(--op-text-dim);
      line-height: 1.6; max-width: 420px;
    }

    .op-withme-when {
      font-size: 11px; color: var(--op-text-dim); margin-top: 2px;
    }
    .op-withme-project {
      display: inline-block; margin-top: 6px; padding: 2px 8px;
      background: var(--op-bg-soft); border: 1px solid var(--op-border);
      border-radius: 999px; font-size: 10.5px; font-weight: 600;
      color: var(--op-text-dim); max-width: 100%;
      overflow: hidden; text-overflow: ellipsis; white-space: nowrap;
    }
    .op-withme-note {
      margin-top: 8px; padding: 6px 9px;
      background: var(--op-bg-soft); border-left: 3px solid var(--op-accent);
      border-radius: 0 var(--op-radius-sm) var(--op-radius-sm) 0;
      font-size: 11.5px; font-style: italic; color: var(--op-text-dim);
      line-height: 1.45; text-align: left;
      display: -webkit-box; -webkit-line-clamp: 2; -webkit-box-orient: vertical;
      overflow: hidden;
    }

    .op-share-tag-active {
      display: inline-block; margin-left: 7px; padding: 1px 7px;
      background: rgba(34, 197, 94, .14); color: #22c55e;
      border: 1px solid rgba(34, 197, 94, .32); border-radius: 999px;
      font-size: 9.5px; font-weight: 700; letter-spacing: .3px;
      text-transform: uppercase; vertical-align: middle;
    }
    .op-share-result-empty {
      padding: 12px 13px; font-size: 12px; color: var(--op-text-dim); text-align: center;
    }

    .op-share-notes { display: block; margin-bottom: 10px; }
    .op-share-notes > span {
      display: block; margin-bottom: 5px;
      font-size: 11.5px; font-weight: 600; color: var(--op-text-dim);
    }
    .op-share-notes textarea {
      width: 100%; padding: 8px 10px; resize: vertical;
      background: var(--op-bg-soft); color: var(--op-text);
      border: 1px solid var(--op-border); border-radius: var(--op-radius-sm);
      font-family: inherit; font-size: 12.5px; line-height: 1.5;
    }
    .op-share-notes textarea:focus {
      outline: none; border-color: var(--op-accent);
    }
    .op-share-perm-note {
      display: block; margin-top: 3px;
      font-size: 11.5px; font-style: italic; color: var(--op-text-dim);
      line-height: 1.45;
      /* Una nota larga no debe empujar la fila: se recorta y queda en el title. */
      overflow: hidden; text-overflow: ellipsis; white-space: nowrap; max-width: 260px;
    }

    /* El contenedor del botón necesita posición relativa para anclar el popover
       y el badge; sin eso ambos se anclan al viewport. */
    /* La toolbar ancla el popover: sin posición explícita el panel se posiciona
       contra un ancestro cualquiera y termina fuera de lugar. */
    .op-toolbar { position: relative; }
    .op-notifications-btn {
      position: relative; width: 32px; height: 32px; flex: 0 0 auto;
      border: 1px solid var(--op-border); border-radius: 50%;
      background: transparent; color: var(--op-text-dim); cursor: pointer;
      display: flex; align-items: center; justify-content: center;
      transition: all .15s ease;
    }
    .op-notifications-btn svg { width: 16px; height: 16px; }
    .op-notifications-btn:hover {
      background: var(--op-accent); border-color: var(--op-accent); color: #fff;
    }
    .op-notif-badge {
      position: absolute; top: -4px; right: -4px; min-width: 17px; height: 17px;
      padding: 0 4px; border-radius: 999px;
      background: #ef4444; color: #fff; border: 2px solid var(--op-bg-card);
      font-size: 9.5px; font-weight: 700; line-height: 13px; text-align: center;
    }
    .op-notif-panel {
      position: absolute; top: calc(100% + 8px); right: 0; z-index: 60;
      width: 330px; max-width: 92vw;
      background: var(--op-bg-card); border: 1px solid var(--op-border);
      border-radius: var(--op-radius-md);
      box-shadow: 0 16px 40px rgba(0,0,0,.28);
      overflow: hidden;
    }
    .op-notif-head {
      display: flex; align-items: center; justify-content: space-between; gap: 10px;
      padding: 11px 13px; border-bottom: 1px solid var(--op-border);
      font-size: 13px; font-weight: 700;
    }
    .op-notif-markall {
      appearance: none; border: 0; background: transparent; cursor: pointer;
      color: var(--op-accent); font-family: inherit; font-size: 11px; font-weight: 600;
      padding: 0; white-space: nowrap;
    }
    .op-notif-markall:hover { text-decoration: underline; }
    .op-notif-list { max-height: 340px; overflow-y: auto; }
    .op-notif-item {
      display: flex; gap: 10px; padding: 11px 13px;
      border-bottom: 1px solid var(--op-border);
    }
    .op-notif-item.is-unread { background: var(--op-bg-soft); cursor: pointer; }
    .op-notif-item.is-unread:hover { background: var(--op-bg-hover); }
    /* La barra lateral marca lo no leído sin depender solo del color de fondo. */
    .op-notif-item.is-unread { border-left: 3px solid var(--op-accent); padding-left: 10px; }
    .op-notif-icon { flex: 0 0 auto; color: var(--op-text-dim); padding-top: 1px; }
    .op-notif-icon svg { width: 15px; height: 15px; }
    .op-notif-body { min-width: 0; }
    .op-notif-msg { font-size: 12.5px; line-height: 1.45; color: var(--op-text); }
    .op-notif-when { margin-top: 3px; font-size: 11px; color: var(--op-text-dim); }
    .op-notif-empty {
      padding: 26px 16px; text-align: center;
      font-size: 12.5px; color: var(--op-text-dim);
    }

    .op-macros-help-btn {
      width: 32px; height: 32px; flex: 0 0 auto;
      border: 1px solid var(--op-border); border-radius: 50%;
      background: transparent; color: var(--op-text-dim);
      font-size: 15px; font-weight: 700; line-height: 1; cursor: pointer;
      display: flex; align-items: center; justify-content: center;
      transition: all .15s ease;
    }
    .op-macros-help-btn:hover {
      background: var(--op-accent); border-color: var(--op-accent);
      color: #fff; transform: scale(1.06);
    }
    .op-modal--macros { max-width: 780px; width: 94vw; }
    .op-mh-tabs { display: flex; gap: 4px; margin-top: 12px; border-bottom: 1px solid var(--op-border); }
    .op-mh-tab {
      appearance: none; border: 0; background: transparent; cursor: pointer;
      padding: 9px 14px; font-size: 13px; font-weight: 600;
      color: var(--op-text-dim); border-bottom: 2px solid transparent;
      margin-bottom: -1px; transition: all .15s ease;
    }
    .op-mh-tab:hover { color: var(--op-text); }
    .op-mh-tab.op-active { color: var(--op-accent); border-bottom-color: var(--op-accent); }
    .op-mh-panel { padding-top: 4px; }
    .op-mh-intro { font-size: 13px; color: var(--op-text-dim); line-height: 1.55; margin: 0 0 12px; }
    /* Alto acotado: el prompt es largo y sin esto el modal se sale de la pantalla. */
    .op-mh-code {
      background: var(--op-bg-soft); border: 1px solid var(--op-border);
      border-radius: 8px; padding: 12px 14px; margin: 0 0 12px;
      font-family: 'JetBrains Mono', ui-monospace, Consolas, monospace;
      font-size: 11.5px; line-height: 1.55; color: var(--op-text);
      max-height: 280px; overflow: auto; white-space: pre-wrap; word-break: break-word;
    }
    .op-mh-copy { width: 100%; justify-content: center; }
    .op-mh-warn { font-size: 12px; color: var(--op-text-dim); margin: 12px 0 0; line-height: 1.5; }
    .op-mh-table { width: 100%; border-collapse: collapse; font-size: 12px; }
    .op-mh-table th {
      text-align: left; padding: 8px 10px; font-size: 11px; text-transform: uppercase;
      letter-spacing: .5px; color: var(--op-text-dim); border-bottom: 1px solid var(--op-border);
    }
    .op-mh-table td { padding: 8px 10px; border-bottom: 1px solid var(--op-border); vertical-align: top; }
    .op-mh-vba { color: var(--op-text-dim); font-family: 'JetBrains Mono', ui-monospace, Consolas, monospace; }
    .op-mh-js { color: var(--op-text); font-family: 'JetBrains Mono', ui-monospace, Consolas, monospace; }
    .op-mh-mini {
      appearance: none; border: 1px solid var(--op-border); border-radius: 6px;
      background: transparent; color: var(--op-text-dim);
      font-size: 11px; padding: 4px 9px; cursor: pointer; white-space: nowrap;
    }
    .op-mh-mini:hover { background: var(--op-accent); border-color: var(--op-accent); color: #fff; }
    .op-mh-mini:disabled { opacity: .6; cursor: default; }

    .op-search-clear {
      position: absolute;
      right: 6px;
      top: 50%;
      transform: translateY(-50%);
      display: flex;
      align-items: center;
      justify-content: center;
      padding: 2px;
      border: none;
      background: transparent;
      color: var(--op-text-secondary);
      cursor: pointer;
      border-radius: var(--op-radius-sm);
    }
    .op-search-clear svg { width: 16px; height: 16px; }
    .op-search-clear:hover { background: var(--op-bg-hover); color: var(--op-text); }

    /* ── Share modal + permission indicators ── */
    .op-share-tabs { display: flex; gap: 6px; margin-bottom: var(--op-space-3); }
    .op-share-tab {
      flex: 1; display: flex; align-items: center; justify-content: center; gap: 6px;
      padding: 8px; border: 1px solid var(--op-border); border-radius: var(--op-radius-md);
      background: var(--op-bg); color: var(--op-text-secondary); cursor: pointer;
    }
    .op-share-tab svg { width: 16px; height: 16px; }
    .op-share-tab.op-active { background: var(--op-accent); color: #fff; border-color: var(--op-accent); }
    .op-share-panel > * { margin-bottom: var(--op-space-2); }
    .op-share-level {
      width: 100%; padding: 8px; border: 1px solid var(--op-border);
      border-radius: var(--op-radius-md); background: var(--op-bg); color: var(--op-text);
    }
    .op-share-results {
      position: absolute; left: 0; right: 0; top: 100%; z-index: 5; margin-top: 2px;
      background: var(--op-bg-card); border: 1px solid var(--op-border);
      border-radius: var(--op-radius-md); max-height: 180px; overflow-y: auto;
      box-shadow: 0 6px 18px rgba(0,0,0,.18);
    }
    .op-share-result {
      display: flex; flex-direction: column; align-items: flex-start; width: 100%;
      padding: 8px 10px; border: none; background: transparent; color: var(--op-text);
      cursor: pointer; text-align: left;
    }
    .op-share-result:hover { background: var(--op-bg-hover); }
    .op-share-result span { color: var(--op-text-secondary); font-size: .85em; }
    .op-share-chip {
      display: flex; align-items: center; justify-content: space-between; gap: 8px;
      padding: 6px 10px; background: var(--op-bg-hover); border-radius: var(--op-radius-md);
    }
    .op-share-chip-clear, .op-share-perm-del {
      border: none; background: transparent; color: var(--op-text-secondary); cursor: pointer;
      display: flex; padding: 4px; border-radius: var(--op-radius-sm);
    }
    .op-share-chip-clear svg, .op-share-perm-del svg { width: 14px; height: 14px; }
    .op-share-chip-clear:hover, .op-share-perm-del:hover { background: var(--op-bg); color: var(--op-text); }
    .op-share-perms-title {
      margin-top: var(--op-space-3); padding-top: var(--op-space-3); font-weight: 600;
      color: var(--op-text); border-top: 1px solid var(--op-border);
    }
    .op-share-perms { display: flex; flex-direction: column; gap: 6px; margin-top: var(--op-space-2); max-height: 160px; overflow-y: auto; }
    .op-share-perms-empty { color: var(--op-text-secondary); font-size: .9em; padding: 6px 0; }
    .op-share-perm-row {
      display: flex; align-items: center; justify-content: space-between; gap: 8px;
      padding: 6px 10px; border: 1px solid var(--op-border); border-radius: var(--op-radius-md);
    }
    .op-share-perm-info { display: flex; flex-direction: column; }
    .op-share-perm-info span { color: var(--op-text-secondary); font-size: .85em; }
    .op-restricted {
      display: inline-flex; align-items: center; gap: 4px; margin-top: 4px;
      color: var(--op-accent); font-size: .78em;
    }
    .op-restricted svg { width: 12px; height: 12px; }
    /* ── "Compartidos conmigo" sub-tabs ── */
    .op-subtabs { display: flex; gap: 6px; padding: var(--op-space-2) var(--op-space-3) 0; }
    .op-subtab {
      padding: 6px 12px; border: none; border-bottom: 2px solid transparent;
      background: transparent; color: var(--op-text-secondary); cursor: pointer; font-size: .9em;
    }
    .op-subtab.op-active { color: var(--op-accent); border-bottom-color: var(--op-accent); }
    .op-withme-from { color: var(--op-text-secondary); font-size: .8em; margin-top: 4px; }
    .op-type-filter, .op-sort-field {
      padding: 7px 8px;
      border: 1px solid var(--op-border);
      border-radius: var(--op-radius-md);
      background: var(--op-bg-card);
      color: var(--op-text);
      cursor: pointer;
    }
    .op-sort-wrap { display: flex; gap: 4px; align-items: center; }
    .op-sort-dir {
      display: inline-flex;
      align-items: center;
      justify-content: center;
      border: 1px solid var(--op-border);
      background: var(--op-bg);
      border-radius: var(--op-radius-md);
      padding: 7px 9px;
      cursor: pointer;
      color: var(--op-text);
      transition: background var(--op-transition);
    }
    .op-sort-dir:hover { background: var(--op-bg-hover); }
    .op-view-toggle { display: flex; border: 1px solid var(--op-border); border-radius: var(--op-radius-md); overflow: hidden; }
    .op-view-btn {
      display: inline-flex;
      align-items: center;
      justify-content: center;
      border: none;
      background: var(--op-bg);
      color: var(--op-text-secondary);
      padding: 7px 10px;
      cursor: pointer;
      transition: background var(--op-transition), color var(--op-transition);
    }
    .op-view-btn.op-active { background: var(--op-accent); color: var(--op-accent-text); }
    .op-view-btn:not(:last-child) { border-right: 1px solid var(--op-border); }
    .op-theme-toggle,
    .op-refresh-btn {
      display: inline-flex;
      align-items: center;
      justify-content: center;
      border: 1px solid var(--op-border);
      background: var(--op-bg);
      color: var(--op-text);
      border-radius: 50%;
      width: 32px;
      height: 32px;
      cursor: pointer;
      transition: background var(--op-transition);
    }
    .op-theme-toggle svg,
    .op-refresh-btn svg { width: 18px; height: 18px; }
    .op-theme-toggle:hover,
    .op-refresh-btn:hover { background: var(--op-bg-hover); }

    /* ── Action bar ──────────────────────────────────────────────────── */
    .op-actionbar {
      display: flex;
      align-items: center;
      gap: var(--op-space-3);
      padding: var(--op-space-3) var(--op-space-4);
      flex-wrap: wrap;
      border-bottom: 1px solid var(--op-border);
    }
    .op-btn-primary, .op-btn-secondary, .op-btn-danger {
      display: inline-flex;
      align-items: center;
      gap: 7px;
      padding: 8px 16px;
      border-radius: var(--op-radius-md);
      font-weight: 600;
      font-size: 13px;
      cursor: pointer;
      border: 1px solid transparent;
      transition: background var(--op-transition), border-color var(--op-transition), opacity var(--op-transition), transform var(--op-transition);
    }
    .op-btn-primary:active, .op-btn-secondary:active, .op-btn-danger:active { transform: scale(0.97); }
    .op-btn-primary { background: var(--op-accent); color: var(--op-accent-text); }
    .op-btn-primary:hover { background: var(--op-accent-hover); }
    .op-btn-secondary { background: var(--op-bg-card); color: var(--op-text); border-color: var(--op-border); }
    .op-btn-secondary:hover { background: var(--op-bg-hover); }
    .op-btn-secondary.op-active { background: var(--op-accent); color: var(--op-accent-text); border-color: var(--op-accent); }
    .op-btn-danger { background: var(--op-danger); color: #fff; }
    .op-btn-danger:hover { background: var(--op-danger-hover); }
    .op-btn-primary:disabled, .op-btn-secondary:disabled, .op-btn-danger:disabled { opacity: 0.55; cursor: not-allowed; }
    .op-upload-label { display: inline-flex; cursor: pointer; }
    .op-upload-progress {
      display: flex;
      align-items: center;
      gap: var(--op-space-2);
      font-size: 12px;
      color: var(--op-text-secondary);
      flex: 1;
      min-width: 160px;
    }
    .op-upload-progress-track {
      flex: 1;
      height: 6px;
      background: var(--op-bg-hover);
      border-radius: 3px;
      overflow: hidden;
    }
    .op-upload-progress-bar { height: 100%; background: var(--op-accent); transition: width 120ms linear; }

    /* ── Breadcrumb ──────────────────────────────────────────────────── */
    .op-breadcrumb {
      display: flex;
      align-items: center;
      gap: 4px;
      padding: 8px var(--op-space-4);
      font-size: 13px;
      color: var(--op-text-secondary);
      flex-wrap: wrap;
      border-bottom: 1px solid var(--op-border);
    }
    .op-breadcrumb-item {
      display: inline-flex;
      align-items: center;
      gap: 5px;
      border: none;
      background: transparent;
      cursor: pointer;
      color: var(--op-text-secondary);
      padding: 3px 7px;
      border-radius: var(--op-radius-sm);
      transition: background var(--op-transition), color var(--op-transition);
    }
    .op-breadcrumb-item:hover { background: var(--op-bg-hover); color: var(--op-accent); }
    .op-breadcrumb-current { color: var(--op-text); font-weight: 600; cursor: default; }
    .op-breadcrumb-current:hover { background: transparent; }
    .op-breadcrumb-sep { opacity: 0.5; }
    .op-drop-target {
      outline: 2px dashed var(--op-accent);
      outline-offset: -2px;
      background: color-mix(in srgb, var(--op-accent) 12%, var(--op-bg-card)) !important;
    }

    /* ── Batch bar ───────────────────────────────────────────────────── */
    .op-batchbar {
      display: flex;
      align-items: center;
      gap: var(--op-space-3);
      padding: var(--op-space-2) var(--op-space-4);
      background: var(--op-accent);
      color: #fff;
      border-bottom: 1px solid var(--op-border);
      flex-wrap: wrap;
    }
    .op-batch-selectall { display: flex; align-items: center; gap: 6px; font-size: 14px; color: #fff; cursor: pointer; }
    .op-batch-count { font-size: 14px; font-weight: 600; color: #fff; }
    .op-batch-actions { display: flex; gap: var(--op-space-2); margin-left: auto; flex-wrap: wrap; }
    .op-batchbar .op-btn-secondary,
    .op-batchbar .op-btn-danger {
      background: rgba(255,255,255,0.2);
      color: #fff;
      border-color: transparent;
      font-size: 14px;
    }
    .op-batchbar .op-btn-secondary:hover,
    .op-batchbar .op-btn-danger:hover {
      background: rgba(255,255,255,0.32);
    }
    .op-batch-cancel {
      display: inline-flex;
      border: none;
      background: transparent;
      cursor: pointer;
      opacity: 0.85;
      padding: 4px;
      border-radius: var(--op-radius-sm);
      color: #fff;
    }
    .op-batch-cancel:hover { opacity: 1; background: rgba(255,255,255,0.2); }

    /* ── Content ─────────────────────────────────────────────────────── */
    .op-content { flex: 1; overflow-y: auto; padding: var(--op-space-4); }

    .op-grid {
      display: grid;
      grid-template-columns: repeat(auto-fill, minmax(152px, 1fr));
      gap: var(--op-space-4);
    }
    .op-card {
      position: relative;
      background: var(--op-bg-card);
      border: 1px solid var(--op-border);
      border-radius: var(--op-radius-md);
      padding: var(--op-space-3);
      cursor: pointer;
      transition: box-shadow var(--op-transition), border-color var(--op-transition), transform var(--op-transition), background var(--op-transition);
    }
    .op-card:hover {
      box-shadow: var(--op-shadow-lg);
      border-color: var(--op-accent);
      background: var(--op-bg-hover);
      transform: translateY(-2px);
    }
    .op-card.op-selected { border-color: var(--op-accent); background: color-mix(in srgb, var(--op-accent) 8%, var(--op-bg-card)); }
    .op-card-check {
      position: absolute;
      top: 8px;
      left: 8px;
      opacity: 0;
      transition: opacity var(--op-transition);
      z-index: 2;
    }
    .op-card:hover .op-card-check, .op-card.op-selected .op-card-check, .op-widget.op-selection-mode .op-card-check { opacity: 1; }
    .op-card-menu-btn {
      position: absolute;
      top: 6px;
      right: 6px;
      display: inline-flex;
      align-items: center;
      justify-content: center;
      border: none;
      background: transparent;
      color: var(--op-text-secondary);
      cursor: pointer;
      padding: 5px;
      border-radius: var(--op-radius-sm);
      opacity: 0;
      transition: opacity var(--op-transition), background var(--op-transition), color var(--op-transition);
    }
    .op-card:hover .op-card-menu-btn, .op-card-menu-btn:focus, .op-card-menu-btn.op-menu-open { opacity: 1; }
    .op-card-menu-btn:hover { background: var(--op-bg-hover); color: var(--op-text); }
    .op-card-icon { width: 100%; height: 68px; display: flex; align-items: center; justify-content: center; margin-bottom: var(--op-space-2); }
    .op-card-icon svg { width: 52px; height: 52px; }
    .op-card-name {
      color: var(--op-text);
      font-weight: 500;
      font-size: 13px;
      white-space: nowrap;
      overflow: hidden;
      text-overflow: ellipsis;
      text-align: center;
    }
    .op-card-meta { font-size: 11px; color: var(--op-text-secondary); text-align: center; margin-top: 3px; }

    .op-table-wrap { overflow-x: auto; }
    .op-table { width: 100%; border-collapse: collapse; background: var(--op-bg-card); border-radius: var(--op-radius-md); overflow: hidden; }
    .op-table th {
      text-align: left;
      font-size: 12px;
      color: var(--op-text-secondary);
      padding: 10px 12px;
      border-bottom: 1px solid var(--op-border);
      cursor: default;
      user-select: none;
      white-space: nowrap;
    }
    .op-table th[data-sort] { cursor: pointer; }
    .op-table th.op-th-active { color: var(--op-accent); }
    .op-table td { padding: 8px 12px; border-bottom: 1px solid var(--op-border); font-size: 13px; }
    .op-row { cursor: pointer; transition: background var(--op-transition); }
    .op-row:hover { background: var(--op-bg-hover); }
    .op-row.op-selected { background: color-mix(in srgb, var(--op-accent) 10%, var(--op-bg-card)); }
    .op-td-name { max-width: 320px; overflow: hidden; text-overflow: ellipsis; white-space: nowrap; font-weight: 500; }
    .op-icon-sm svg { width: 22px; height: 22px; display: block; }
    .op-menu-btn {
      display: inline-flex;
      align-items: center;
      justify-content: center;
      border: none;
      background: transparent;
      color: var(--op-text-secondary);
      cursor: pointer;
      padding: 5px 7px;
      border-radius: var(--op-radius-sm);
    }
    .op-menu-btn:hover { background: var(--op-bg-hover); color: var(--op-text); }

    /* ── Empty / loading / error states ─────────────────────────────── */
    .op-empty-state, .op-loading-state {
      display: flex;
      flex-direction: column;
      align-items: center;
      justify-content: center;
      text-align: center;
      padding: var(--op-space-5) var(--op-space-4);
      color: var(--op-text-secondary);
      min-height: 280px;
      gap: var(--op-space-3);
    }
    .op-empty-illustration { opacity: 0.9; }
    .op-empty-text { color: var(--op-text); font-size: 16px; max-width: 320px; margin: 0; }
    .op-empty-actions { display: flex; gap: var(--op-space-2); flex-wrap: wrap; justify-content: center; }
    .op-spinner {
      width: 28px;
      height: 28px;
      border-radius: 50%;
      border: 3px solid var(--op-border);
      border-top-color: var(--op-accent);
      animation: op-spin 0.8s linear infinite;
    }
    @keyframes op-spin { to { transform: rotate(360deg); } }

    /* ── New Dropdown ── */
    .op-new-dropdown-wrapper {
      position: relative;
      display: inline-block;
    }

    .op-new-dropdown {
      position: absolute !important;
      top: calc(100% + 6px) !important;
      left: 0 !important;
      background: var(--op-bg-card) !important;
      opacity: 1 !important;
      border: 1px solid var(--op-border);
      border-radius: 10px;
      box-shadow: 0 12px 32px rgba(0,0,0,0.25);
      z-index: 999998 !important;
      min-width: 220px;
      padding: 8px 0;
      overflow: hidden;
    }

    .op-new-dropdown-item {
      display: flex;
      align-items: center;
      gap: 12px;
      padding: 12px 18px;
      font-size: 14px;
      font-weight: 500;
      color: var(--op-text) !important;
      background: transparent;
      cursor: pointer;
      white-space: nowrap;
    }

    .op-new-dropdown-item:hover {
      background: var(--op-bg-hover) !important;
    }

    .op-new-dropdown-item svg,
    .op-new-dropdown-item img {
      width: 20px;
      height: 20px;
      flex-shrink: 0;
    }

    /* ── Context menu (also backs the "Nuevo documento" dropdown) ──────── */
    .op-context-menu {
      position: fixed;
      z-index: 99999;
      background: var(--op-bg-card);
      color: var(--op-text);
      border: 1px solid var(--op-border);
      border-radius: 8px;
      box-shadow: var(--op-shadow-lg);
      min-width: 200px;
      padding: 6px 0;
      overflow: hidden;
    }
    .op-context-item {
      display: flex;
      align-items: center;
      gap: 10px;
      width: 100%;
      text-align: left;
      padding: 10px 16px;
      border: none;
      background: transparent;
      cursor: pointer;
      font-size: 14px;
      color: var(--op-text);
      transition: background 0.1s;
    }
    .op-context-item svg { width: 20px; height: 20px; }
    .op-context-item:hover { background: var(--op-bg-hover); }
    .op-context-item-danger {
      color: var(--op-danger);
      margin-top: 4px;
      padding-top: 14px;
      border-top: 1px solid var(--op-border);
    }
    .op-context-sep { height: 1px; background: var(--op-border); margin: 4px 6px; }

    /* ── Modal ───────────────────────────────────────────────────────── */
    .op-modal-backdrop {
      position: fixed !important;
      inset: 0 !important;
      background: rgba(0, 0, 0, 0.65) !important;
      backdrop-filter: blur(2px);
      display: flex !important;
      align-items: center !important;
      justify-content: center !important;
      z-index: 999999 !important;
      opacity: 1 !important;
    }
    .op-modal-card {
      background: var(--op-bg-card) !important;
      opacity: 1 !important;
      isolation: isolate;
      position: relative;
      z-index: 1000000 !important;
      border-radius: 16px;
      padding: 32px;
      min-width: 420px;
      box-shadow: 0 24px 80px rgba(0,0,0,0.5);
      border: 1px solid var(--op-border);
    }
    .op-modal {
      display: flex;
      flex-direction: column;
      background: var(--op-bg-card) !important;
      color: var(--op-text);
      border-radius: 16px;
      min-width: 420px;
      max-width: 520px;
      width: 100%;
      max-height: 85vh;
      box-shadow: 0 24px 80px rgba(0,0,0,0.5);
      font-family: 'Inter', system-ui, -apple-system, 'Segoe UI', Roboto, sans-serif;
      overflow: hidden;
      opacity: 1 !important;
      isolation: isolate;
      position: relative;
      z-index: 1000000 !important;
      border: 1px solid var(--op-border);
    }
    .op-modal-hd { padding: 32px 32px 0; flex-shrink: 0; }
    .op-modal-title { margin: 0 0 16px; font-size: 18px; font-weight: 600; }
    .op-modal-message { margin: 0 0 var(--op-space-4); color: var(--op-text-secondary); font-size: 14px; line-height: 1.5; }
    .op-modal-body { padding: 0 32px; overflow-y: auto; flex: 1 1 auto; min-height: 0; }
    .op-modal-input {
      width: 100%;
      padding: 12px;
      border: 2px solid var(--op-border);
      border-radius: 8px;
      font-size: 15px;
      background: var(--op-bg);
      color: var(--op-text);
      outline: none;
      box-sizing: border-box;
      margin-bottom: 20px;
    }
    .op-modal-input::placeholder { color: var(--op-text-secondary); }
    .op-modal-input:focus { border-color: var(--op-accent); }
    .op-modal-ft {
      display: flex;
      justify-content: flex-end;
      gap: 12px;
      padding: 16px 32px 32px;
      flex-shrink: 0;
    }
    .op-modal-ft .op-btn-secondary,
    .op-modal-ft .op-btn-primary,
    .op-modal-ft .op-btn-danger {
      padding: 10px 24px;
      font-size: 14px;
    }
    .op-modal-ft .op-btn-primary,
    .op-modal-ft .op-btn-danger {
      font-weight: 500;
    }

    /* ── Folder picker (move-to modal) ─────────────────────────────────── */
    .op-picker-path {
      display: flex;
      align-items: center;
      gap: 4px;
      flex-wrap: wrap;
      font-size: 12.5px;
      color: var(--op-text-secondary);
      padding-bottom: var(--op-space-3);
      margin-bottom: var(--op-space-3);
      border-bottom: 1px solid var(--op-border);
    }
    .op-picker-path button {
      border: none;
      background: transparent;
      color: var(--op-text-secondary);
      cursor: pointer;
      padding: 2px 5px;
      border-radius: var(--op-radius-sm);
    }
    .op-picker-path button:hover { background: var(--op-bg-hover); color: var(--op-text); }
    .op-picker-path .op-picker-current { color: var(--op-text); font-weight: 600; cursor: default; }
    .op-picker-list { list-style: none; margin: 0; padding: 0; min-height: 60px; }
    .op-picker-row {
      display: flex;
      align-items: center;
      gap: var(--op-space-2);
      width: 100%;
      border: none;
      background: transparent;
      text-align: left;
      padding: 9px 8px;
      border-radius: var(--op-radius-sm);
      cursor: pointer;
      font-size: 13.5px;
      color: var(--op-text);
    }
    .op-picker-row:hover { background: var(--op-bg-hover); }
    .op-picker-row svg { width: 20px; height: 20px; flex-shrink: 0; }
    .op-picker-empty { padding: var(--op-space-3) 0; color: var(--op-text-secondary); font-size: 13px; }

    /* ── Toasts ──────────────────────────────────────────────────────── */
    .op-toast-stack {
      position: fixed;
      top: 16px;
      right: 16px;
      z-index: 10010;
      display: flex;
      flex-direction: column;
      gap: 8px;
      max-width: 320px;
    }
    .op-toast {
      padding: 12px 20px;
      border-radius: 8px;
      font-size: 14px;
      font-weight: 500;
      color: #fff;
      box-shadow: 0 4px 12px rgba(0,0,0,0.15);
      opacity: 0;
      transform: translateX(16px);
      transition: opacity var(--op-transition), transform var(--op-transition);
      cursor: pointer;
    }
    .op-toast-show { opacity: 1; transform: translateX(0); }
    .op-toast-success { background: var(--op-success); }
    .op-toast-error { background: var(--op-danger); }
    .op-toast-info { background: var(--op-accent); }

    /* ── Editor overlay ──────────────────────────────────────────────── */
    .op-editor-overlay {
      position: fixed;
      inset: 0;
      background: rgba(0,0,0,0.6);
      z-index: 100000;
      display: flex;
      align-items: center;
      justify-content: center;
      padding: 3vh 3vw;
      box-sizing: border-box;
    }
    .op-editor-modal {
      position: relative;
      width: 94vw;
      height: 94vh;
      max-width: 1400px;
      background: var(--op-bg-card);
      border-radius: 12px;
      box-shadow: 0 24px 80px rgba(0,0,0,0.5);
      display: flex;
      flex-direction: column;
      overflow: hidden;
    }
    /* Maximizado: el modal ocupa la ventana entera. Se hace por clase y no solo con la
       Fullscreen API porque el navegador puede bloquearla (iframe sin allow="fullscreen",
       o gesto no considerado de usuario), y en ese caso el usuario igual debe poder trabajar
       a pantalla completa. La API nativa se intenta encima, como mejora. */
    .op-editor-overlay.is-maximized { padding: 0 !important; margin: 0 !important; }
    .op-editor-modal.is-maximized {
      width: 100vw !important;
      height: 100vh !important;
      max-width: 100% !important;
      max-height: 100% !important;
      border-radius: 0 !important;
      border: none !important;
      margin: 0 !important;
      box-shadow: none !important;
    }
    /* El contenedor del editor y su iframe siguen al modal: sin esto el iframe conserva
       su alto anterior y queda una franja vacía abajo. */
    .op-editor-modal.is-maximized .op-editor-body,
    .op-editor-modal.is-maximized #op-editor-container { height: 100%; }
    .op-editor-modal.is-maximized #op-editor-container iframe {
      width: 100% !important;
      height: 100% !important;
      border: 0 !important;
    }
    .op-editor-fullscreen {
      background: transparent; border: 0; cursor: pointer;
      color: var(--op-text-dim); padding: 6px; border-radius: 8px;
      display: flex; align-items: center; justify-content: center;
      transition: all .15s ease;
    }
    .op-editor-fullscreen svg { width: 18px; height: 18px; }
    .op-editor-fullscreen:hover { background: var(--op-bg-hover); color: var(--op-text); }

    .op-editor-header {
      display: flex;
      align-items: center;
      gap: var(--op-space-3);
      padding: 12px 20px;
      background: var(--op-bg-card);
      color: var(--op-text);
      border-bottom: 1px solid var(--op-border);
      flex-shrink: 0;
    }
    .op-editor-title { font-weight: 600; font-size: 15px; color: var(--op-text); overflow: hidden; text-overflow: ellipsis; white-space: nowrap; }
    .op-editor-status {
      font-size: 12px;
      padding: 3px 10px;
      border-radius: 999px;
      background: var(--op-bg-hover);
      color: var(--op-text-secondary);
      margin-left: auto;
      white-space: nowrap;
    }
    .op-editor-status[data-state="dirty"] { background: rgba(245,158,11,0.18); color: #f59e0b; }
    .op-editor-status[data-state="saved"] { background: rgba(34,197,94,0.18); color: #22c55e; }
    .op-editor-status[data-state="error"] { background: rgba(239,68,68,0.18); color: #ef4444; }
    .op-editor-collab {
      font-size: 12px;
      padding: 3px 10px;
      border-radius: 999px;
      background: rgba(99,102,241,0.2);
      color: var(--op-accent);
      white-space: nowrap;
    }
    .op-editor-header-actions { display: flex; gap: 10px; align-items: center; }
    .op-editor-close {
      display: inline-flex;
      background: none;
      border: none;
      color: var(--op-text-secondary);
      cursor: pointer;
      padding: 4px;
      border-radius: var(--op-radius-sm);
      transition: opacity var(--op-transition), background var(--op-transition), color var(--op-transition);
    }
    .op-editor-close svg { width: 20px; height: 20px; }
    .op-editor-close:hover { background: var(--op-bg-hover); color: var(--op-text); }
    .op-editor-body { flex: 1; position: relative; overflow: hidden; background: #fff; }
    #op-editor-container { width: 100%; height: 100%; }
    /* OnlyOffice injects an <iframe> into the container; force it to fill the modal body. */
    .op-editor-body #op-editor-container,
    .op-editor-body iframe { width: 100% !important; height: 100% !important; border: none; }
    .op-editor-loading {
      position: absolute;
      inset: 0;
      display: flex;
      align-items: center;
      justify-content: center;
      background: var(--op-bg);
      color: var(--op-text-secondary);
      font-size: 14px;
    }

    /* ── In-editor file manager (button + drawer) ─────────────────────── */
    .op-editor-filemanager-btn {
      background: var(--op-accent);
      color: #fff;
      border: none;
      border-radius: 8px;
      padding: 7px 14px;
      font-size: 13px;
      font-weight: 500;
      cursor: pointer;
      display: inline-flex;
      align-items: center;
      gap: 8px;
      white-space: nowrap;
    }
    .op-editor-filemanager-btn svg { width: 16px; height: 16px; }
    .op-editor-filemanager-btn:hover { filter: brightness(1.1); }
    .op-fm-drawer {
      position: absolute;
      top: 0; right: 0; bottom: 0;
      width: 380px;
      max-width: 90vw;
      background: var(--op-bg-card);
      color: var(--op-text);
      box-shadow: -4px 0 24px rgba(0,0,0,0.3);
      z-index: 20;
      transform: translateX(100%);
      transition: transform 0.25s ease;
      display: flex;
      flex-direction: column;
    }
    .op-fm-drawer.open { transform: translateX(0); }
    .op-fm-drawer-header {
      padding: 16px 20px;
      border-bottom: 1px solid var(--op-border);
      display: flex;
      justify-content: space-between;
      align-items: center;
      font-weight: 600;
      font-size: 16px;
    }
    .op-fm-drawer-close {
      background: none; border: none; cursor: pointer;
      color: var(--op-text-secondary); display: inline-flex; padding: 4px;
      border-radius: var(--op-radius-sm);
    }
    .op-fm-drawer-close svg { width: 18px; height: 18px; }
    .op-fm-drawer-close:hover { background: var(--op-bg-hover); color: var(--op-text); }
    .op-fm-drawer-body { flex: 1; overflow-y: auto; padding: 16px 20px; }
    .op-fm-placeholder { color: var(--op-text-secondary); font-size: 13px; padding: 12px 0; }

    /* ── Responsive ──────────────────────────────────────────────────── */
    @media (max-width: 640px) {
      .op-toolbar { padding: var(--op-space-2) var(--op-space-3); }
      .op-search-wrap { flex-basis: 100%; order: 1; }
      .op-type-filter, .op-sort-wrap, .op-view-toggle, .op-theme-toggle { order: 2; }
      .op-actionbar { padding: var(--op-space-2) var(--op-space-3); }
      .op-actionbar .op-btn-primary span:last-child,
      .op-actionbar .op-btn-secondary span:last-child { display: none; }
      .op-grid { grid-template-columns: repeat(auto-fill, minmax(120px, 1fr)); }
      .op-tabs { padding: var(--op-space-2) var(--op-space-2) 0; }
      .op-tab { padding: 8px 10px; font-size: 12.5px; }
    }
  `;

  // ── Icons (stroke-based, consistent set) ────────────────────────────────
  function icon(path, extra) {
    return '<svg class="op-icon" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2" ' +
      'stroke-linecap="round" stroke-linejoin="round" aria-hidden="true">' + path + '</svg>' + (extra || '');
  }
  const ICONS = {
    search: icon('<circle cx="11" cy="11" r="7"/><line x1="21" y1="21" x2="16.65" y2="16.65"/>'),
    sun: icon('<circle cx="12" cy="12" r="4"/><line x1="12" y1="2" x2="12" y2="4"/><line x1="12" y1="20" x2="12" y2="22"/><line x1="4.22" y1="4.22" x2="5.64" y2="5.64"/><line x1="18.36" y1="18.36" x2="19.78" y2="19.78"/><line x1="2" y1="12" x2="4" y2="12"/><line x1="20" y1="12" x2="22" y2="12"/><line x1="4.22" y1="19.78" x2="5.64" y2="18.36"/><line x1="18.36" y1="5.64" x2="19.78" y2="4.22"/>'),
    moon: icon('<path d="M21 12.79A9 9 0 1 1 11.21 3 7 7 0 0 0 21 12.79z"/>'),
    refresh: icon('<path d="M23 4v6h-6"/><path d="M20.49 15a9 9 0 1 1-2.12-9.36L23 10"/>'),
    grid: icon('<rect x="3" y="3" width="7" height="7"/><rect x="14" y="3" width="7" height="7"/><rect x="3" y="14" width="7" height="7"/><rect x="14" y="14" width="7" height="7"/>'),
    list: icon('<line x1="8" y1="6" x2="21" y2="6"/><line x1="8" y1="12" x2="21" y2="12"/><line x1="8" y1="18" x2="21" y2="18"/><line x1="3" y1="6" x2="3.01" y2="6"/><line x1="3" y1="12" x2="3.01" y2="12"/><line x1="3" y1="18" x2="3.01" y2="18"/>'),
    plus: icon('<line x1="12" y1="5" x2="12" y2="19"/><line x1="5" y1="12" x2="19" y2="12"/>'),
    folderPlus: icon('<path d="M22 19a2 2 0 0 1-2 2H4a2 2 0 0 1-2-2V5a2 2 0 0 1 2-2h5l2 3h9a2 2 0 0 1 2 2z"/><line x1="12" y1="11" x2="12" y2="17"/><line x1="9" y1="14" x2="15" y2="14"/>'),
    upload: icon('<path d="M21 15v4a2 2 0 0 1-2 2H5a2 2 0 0 1-2-2v-4"/><polyline points="17 8 12 3 7 8"/><line x1="12" y1="3" x2="12" y2="15"/>'),
    check: icon('<polyline points="9 11 12 14 22 4"/><path d="M21 12v7a2 2 0 0 1-2 2H5a2 2 0 0 1-2-2V5a2 2 0 0 1 2-2h11"/>'),
    home: icon('<path d="m3 9 9-7 9 7v11a2 2 0 0 1-2 2H5a2 2 0 0 1-2-2z"/><polyline points="9 22 9 12 15 12 15 22"/>'),
    chevron: icon('<polyline points="9 18 15 12 9 6"/>'),
    dots: icon('<circle cx="12" cy="5" r="1.5"/><circle cx="12" cy="12" r="1.5"/><circle cx="12" cy="19" r="1.5"/>'),
    close: icon('<line x1="18" y1="6" x2="6" y2="18"/><line x1="6" y1="6" x2="18" y2="18"/>'),
    download: icon('<path d="M21 15v4a2 2 0 0 1-2 2H5a2 2 0 0 1-2-2v-4"/><polyline points="7 10 12 15 17 10"/><line x1="12" y1="15" x2="12" y2="3"/>'),
    trash: icon('<polyline points="3 6 5 6 21 6"/><path d="M19 6l-1 14a2 2 0 0 1-2 2H8a2 2 0 0 1-2-2L5 6"/><path d="M10 11v6"/><path d="M14 11v6"/><path d="M9 6V4a1 1 0 0 1 1-1h4a1 1 0 0 1 1 1v2"/>'),
    restore: icon('<polyline points="1 4 1 10 7 10"/><path d="M3.51 15a9 9 0 1 0 2.13-9.36L1 10"/>'),
    rename: icon('<path d="M12 20h9"/><path d="M16.5 3.5a2.12 2.12 0 0 1 3 3L7 19l-4 1 1-4Z"/>'),
    move: icon('<polyline points="16 3 21 3 21 8"/><line x1="21" y1="3" x2="14" y2="10"/><path d="M19 13v6a2 2 0 0 1-2 2H5a2 2 0 0 1-2-2V7a2 2 0 0 1 2-2h6"/>'),
    maximize: icon('<polyline points="15 3 21 3 21 9"/><polyline points="9 21 3 21 3 15"/><line x1="21" y1="3" x2="14" y2="10"/><line x1="3" y1="21" x2="10" y2="14"/>'),
    minimize: icon('<polyline points="4 14 10 14 10 20"/><polyline points="20 10 14 10 14 4"/><line x1="14" y1="10" x2="21" y2="3"/><line x1="3" y1="21" x2="10" y2="14"/>'),
    bell: icon('<path d="M18 8A6 6 0 0 0 6 8c0 7-3 9-3 9h18s-3-2-3-9"/><path d="M13.73 21a2 2 0 0 1-3.46 0"/>'),
    open: icon('<path d="M18 13v6a2 2 0 0 1-2 2H5a2 2 0 0 1-2-2V8a2 2 0 0 1 2-2h6"/><polyline points="15 3 21 3 21 9"/><line x1="10" y1="14" x2="21" y2="3"/>'),
    folder: icon('<path d="M22 19a2 2 0 0 1-2 2H4a2 2 0 0 1-2-2V5a2 2 0 0 1 2-2h5l2 3h9a2 2 0 0 1 2 2z"/>'),
    share: icon('<circle cx="18" cy="5" r="3"/><circle cx="6" cy="12" r="3"/><circle cx="18" cy="19" r="3"/><line x1="8.59" y1="13.51" x2="15.42" y2="17.49"/><line x1="15.41" y1="6.51" x2="8.59" y2="10.49"/>'),
    lock: icon('<rect x="3" y="11" width="18" height="11" rx="2" ry="2"/><path d="M7 11V7a5 5 0 0 1 10 0v4"/>'),
    users: icon('<path d="M17 21v-2a4 4 0 0 0-4-4H5a4 4 0 0 0-4 4v2"/><circle cx="9" cy="7" r="4"/><path d="M23 21v-2a4 4 0 0 0-3-3.87"/><path d="M16 3.13a4 4 0 0 1 0 7.75"/>'),
  };

  // ── Small helpers ────────────────────────────────────────────────────────
  function formatBytes(bytes) {
    if (bytes < 1024) return bytes + ' B';
    if (bytes < 1024 * 1024) return (bytes / 1024).toFixed(1) + ' KB';
    return (bytes / (1024 * 1024)).toFixed(1) + ' MB';
  }

  function formatDate(iso) {
    if (!iso) return '';
    const d = new Date(iso.endsWith('Z') ? iso : iso + 'Z');
    if (isNaN(d.getTime())) return iso;
    return d.toLocaleString('es-CO', {
      timeZone: 'America/Bogota',
      day: '2-digit',
      month: '2-digit',
      year: 'numeric',
      hour: '2-digit',
      minute: '2-digit',
      second: '2-digit',
      hour12: true,
    });
  }

  function extensionOf(name) {
    const idx = (name || '').lastIndexOf('.');
    return idx >= 0 ? name.slice(idx + 1).toLowerCase() : '';
  }

  function getFileKind(file) {
    const mime = (file.mimeType || '').toLowerCase();
    const ext = extensionOf(file.originalFileName);
    if (mime.indexOf('wordprocessingml') >= 0 || mime.indexOf('msword') >= 0 || ['doc', 'docx', 'odt'].indexOf(ext) >= 0) return 'word';
    if (mime.indexOf('spreadsheetml') >= 0 || mime.indexOf('ms-excel') >= 0 || ['xls', 'xlsx', 'ods'].indexOf(ext) >= 0) return 'excel';
    if (mime.indexOf('presentationml') >= 0 || mime.indexOf('ms-powerpoint') >= 0 || ['ppt', 'pptx', 'odp'].indexOf(ext) >= 0) return 'powerpoint';
    if (mime.indexOf('pdf') >= 0 || ext === 'pdf') return 'pdf';
    return 'other';
  }

  function kindLabel(kind) {
    const labels = { word: 'Word', excel: 'Excel', powerpoint: 'PowerPoint', pdf: 'PDF', other: 'Otro' };
    return labels[kind] || 'Otro';
  }

  function isAllowedFile(file) {
    return ALLOWED_EXTENSIONS.indexOf(extensionOf(file.name)) >= 0;
  }

  function escapeHtml(str) {
    return String(str)
      .replace(/&/g, '&amp;')
      .replace(/</g, '&lt;')
      .replace(/>/g, '&gt;')
      .replace(/"/g, '&quot;');
  }

  // docx = blue W · xlsx = green X · pptx = orange P · pdf = red PDF · other = gray generic
  function fileIconSvg(kind) {
    const colors = { word: '#2b579a', excel: '#217346', powerpoint: '#d24726', pdf: '#d32f2f', other: '#868e96' };
    const labels = { word: 'W', excel: 'X', powerpoint: 'P', pdf: 'PDF', other: '' };
    const color = colors[kind] || colors.other;
    const label = labels[kind] || '';
    const fontSize = label.length > 1 ? 9 : 15;
    return (
      '<svg viewBox="0 0 48 48" xmlns="http://www.w3.org/2000/svg">' +
        '<path d="M10 2 H29 L38 11 V44 A2 2 0 0 1 36 46 H10 A2 2 0 0 1 8 44 V4 A2 2 0 0 1 10 2 Z" fill="' + color + '"/>' +
        '<path d="M29 2 L38 11 H31 A2 2 0 0 1 29 9 Z" fill="rgba(255,255,255,0.5)"/>' +
        (label
          ? '<text x="23" y="33" font-size="' + fontSize + '" font-family="system-ui, sans-serif" font-weight="700" fill="#fff" text-anchor="middle">' + label + '</text>'
          : '') +
      '</svg>'
    );
  }

  const FOLDER_ICON_SVG =
    '<svg viewBox="0 0 48 48" xmlns="http://www.w3.org/2000/svg">' +
      '<path d="M4 12 A2 2 0 0 1 6 10 H18 L22 14 H42 A2 2 0 0 1 44 16 V36 A2 2 0 0 1 42 38 H6 A2 2 0 0 1 4 36 Z" fill="#f0b429"/>' +
      '<path d="M4 16 H44 V36 A2 2 0 0 1 42 38 H6 A2 2 0 0 1 4 36 Z" fill="#ffd25a"/>' +
    '</svg>';

  const EMPTY_ILLUSTRATION_SVG =
    '<svg class="op-empty-illustration" viewBox="0 0 120 96" width="120" height="96" xmlns="http://www.w3.org/2000/svg" fill="none">' +
      '<rect x="10" y="24" width="100" height="62" rx="6" fill="var(--op-bg-hover)" stroke="var(--op-border)" stroke-width="2"/>' +
      '<path d="M10 30 L10 16 A6 6 0 0 1 16 10 L46 10 L54 20 L104 20 A6 6 0 0 1 110 26 L110 30 Z" fill="var(--op-bg-hover)" stroke="var(--op-border)" stroke-width="2"/>' +
      '<circle cx="60" cy="55" r="16" fill="none" stroke="var(--op-accent)" stroke-width="3"/>' +
      '<path d="M60 47 L60 63 M52 55 L68 55" stroke="var(--op-accent)" stroke-width="3" stroke-linecap="round"/>' +
    '</svg>';

  // ── ZIP writer (STORE method, no compression) ───────────────────────────
  const CRC_TABLE = (function () {
    const table = new Uint32Array(256);
    for (let n = 0; n < 256; n++) {
      let c = n;
      for (let k = 0; k < 8; k++) {
        c = (c & 1) ? (0xEDB88320 ^ (c >>> 1)) : (c >>> 1);
      }
      table[n] = c >>> 0;
    }
    return table;
  })();

  function crc32(bytes) {
    let crc = 0xFFFFFFFF;
    for (let i = 0; i < bytes.length; i++) {
      crc = (crc >>> 8) ^ CRC_TABLE[(crc ^ bytes[i]) & 0xFF];
    }
    return (crc ^ 0xFFFFFFFF) >>> 0;
  }

  function dosDateTime(date) {
    date = date || new Date();
    const time = ((date.getHours() & 0x1F) << 11) | ((date.getMinutes() & 0x3F) << 5) | ((Math.floor(date.getSeconds() / 2)) & 0x1F);
    const dosDate = (((date.getFullYear() - 1980) & 0x7F) << 9) | (((date.getMonth() + 1) & 0xF) << 5) | (date.getDate() & 0x1F);
    return { time: time, dosDate: dosDate };
  }

  function pushUint32LE(arr, value) {
    arr.push(value & 0xFF, (value >>> 8) & 0xFF, (value >>> 16) & 0xFF, (value >>> 24) & 0xFF);
  }
  function pushUint16LE(arr, value) {
    arr.push(value & 0xFF, (value >>> 8) & 0xFF);
  }

  function uniqueZipName(usedNames, name) {
    if (!usedNames[name]) { usedNames[name] = 1; return name; }
    const count = usedNames[name]++;
    const dot = name.lastIndexOf('.');
    return dot > 0 ? name.slice(0, dot) + ' (' + count + ')' + name.slice(dot) : name + ' (' + count + ')';
  }

  function createZip(entries) {
    // Un ZIP de cero entradas es un archivo de 22 bytes que el explorador abre vacío: el
    // usuario cree que perdió sus archivos. Mejor decírselo que entregarle eso.
    if (!entries || entries.length === 0) {
      toast('No hay archivos para descargar', 'error');
      return null;
    }
    const encoder = new TextEncoder();
    const localChunks = [];
    const centralChunks = [];
    let offset = 0;
    const dt = dosDateTime();

    entries.forEach(function (entry) {
      const nameBytes = encoder.encode(entry.name);
      const data = entry.data;
      const crc = crc32(data);
      const size = data.length;

      const localHeader = [];
      pushUint32LE(localHeader, 0x04034b50);
      pushUint16LE(localHeader, 20);
      pushUint16LE(localHeader, 0);
      pushUint16LE(localHeader, 0);
      pushUint16LE(localHeader, dt.time);
      pushUint16LE(localHeader, dt.dosDate);
      pushUint32LE(localHeader, crc);
      pushUint32LE(localHeader, size);
      pushUint32LE(localHeader, size);
      pushUint16LE(localHeader, nameBytes.length);
      pushUint16LE(localHeader, 0);
      const localHeaderBytes = new Uint8Array(localHeader);
      localChunks.push(localHeaderBytes, nameBytes, data);

      const centralHeader = [];
      pushUint32LE(centralHeader, 0x02014b50);
      pushUint16LE(centralHeader, 20);
      pushUint16LE(centralHeader, 20);
      pushUint16LE(centralHeader, 0);
      pushUint16LE(centralHeader, 0);
      pushUint16LE(centralHeader, dt.time);
      pushUint16LE(centralHeader, dt.dosDate);
      pushUint32LE(centralHeader, crc);
      pushUint32LE(centralHeader, size);
      pushUint32LE(centralHeader, size);
      pushUint16LE(centralHeader, nameBytes.length);
      pushUint16LE(centralHeader, 0);
      pushUint16LE(centralHeader, 0);
      pushUint16LE(centralHeader, 0);
      pushUint16LE(centralHeader, 0);
      // Atributos externos. En cero, algunos extractores tratan la entrada como sin permisos
      // y la reportan vacía o corrupta. 0x81A40000 son los bits de un archivo regular 0644
      // en el byte alto (Unix) que Windows, macOS y WinRAR interpretan correctamente.
      pushUint32LE(centralHeader, 0x81A40000);
      pushUint32LE(centralHeader, offset);
      const centralHeaderBytes = new Uint8Array(centralHeader);
      centralChunks.push(centralHeaderBytes, nameBytes);

      offset += localHeaderBytes.length + nameBytes.length + data.length;
    });

    const centralDirOffset = offset;
    let centralSize = 0;
    centralChunks.forEach(function (c) { centralSize += c.length; });

    const eocd = [];
    pushUint32LE(eocd, 0x06054b50);
    pushUint16LE(eocd, 0);
    pushUint16LE(eocd, 0);
    pushUint16LE(eocd, entries.length);
    pushUint16LE(eocd, entries.length);
    pushUint32LE(eocd, centralSize);
    pushUint32LE(eocd, centralDirOffset);
    pushUint16LE(eocd, 0);

    const allChunks = localChunks.concat(centralChunks, [new Uint8Array(eocd)]);
    return new Blob(allChunks, { type: 'application/zip' });
  }

  // ── Toasts ───────────────────────────────────────────────────────────────
  let _toastStack = null;
  function toast(msg, type) {
    if (!_toastStack) {
      _toastStack = document.createElement('div');
      _toastStack.className = 'op-toast-stack';
      document.body.appendChild(_toastStack);
    }
    const el = document.createElement('div');
    el.className = 'op-toast op-toast-' + (type || 'info');
    el.textContent = msg;
    _toastStack.appendChild(el);
    void el.offsetWidth;
    el.classList.add('op-toast-show');
    const remove = function () {
      el.classList.remove('op-toast-show');
      setTimeout(function () { el.remove(); }, 220);
    };
    const timer = setTimeout(remove, 3600);
    el.onclick = function () { clearTimeout(timer); remove(); };
  }

  // ── API layer ────────────────────────────────────────────────────────────
  /**
   * Appends userId/userName to a request path, preserving any query string it already has.
   * Values already present are left untouched so an explicit caller always wins.
   */
  function withIdentity(path) {
    const [base, query] = path.split('?');
    const params = new URLSearchParams(query || '');
    if (USER_ID && !params.has('userId')) params.set('userId', USER_ID);
    if (USER_NAME && !params.has('userName')) params.set('userName', USER_NAME);
    const qs = params.toString();
    return qs ? base + '?' + qs : base;
  }

  async function apiFetch(path, options) {
    options = options || {};
    const headers = Object.assign({}, options.headers || {});
    let token = WIDGET_TOKEN;
    if (!token) {
      const tag = document.querySelector('script[data-token]');
      if (tag) token = tag.getAttribute('data-token') || '';
    }
    if (token) {
      headers['Authorization'] = 'Bearer ' + token;
    } else {
      headers['X-Api-Key'] = API_KEY;
    }
    // With an API key there is no cédula inside the credential, so the backend saw every call as
    // anonymous and denied the author of a file its own delete with "no eres el propietario".
    // The identity given to the widget travels as query parameters, which every endpoint that
    // needs it already accepts. Not added on the Bearer path: the token already carries it.
    const url = (!token && (USER_ID || USER_NAME)) ? withIdentity(path) : path;
    const res = await fetch(SERVER + url, Object.assign({}, options, { headers: headers }));
    if (!res.ok) {
      let msg = 'HTTP ' + res.status;
      try {
        const j = await res.json();
        if (j && j.message) msg = j.message;
        if (j && j.errors && typeof j.errors === 'object') {
          const details = Object.keys(j.errors).map(function (k) { return j.errors[k]; }).join(', ');
          if (details) msg += ': ' + details;
        }
      } catch (_) {}
      throw new Error(msg);
    }
    return res;
  }

  function getCurrentScope() {
    return state.section === 'shared' ? 'shared' : 'private';
  }

  async function listFiles(folderId) {
    const scope = getCurrentScope();
    const query = '?scope=' + scope + (folderId ? ('&folderId=' + encodeURIComponent(folderId)) : '');
    const res = await apiFetch('/api/files' + query);
    const j = await res.json();
    return j.data || [];
  }

  async function listAllFiles() {
    const scope = getCurrentScope();
    const res = await apiFetch('/api/files/all?scope=' + scope);
    const j = await res.json();
    return j.data || [];
  }

  async function listFolders(parentId) {
    const scope = getCurrentScope();
    const query = '?scope=' + scope + (parentId ? ('&parentId=' + encodeURIComponent(parentId)) : '');
    const res = await apiFetch('/api/folders' + query);
    const j = await res.json();
    return j.data || [];
  }

  async function searchFilesApi(term) {
    const scope = getCurrentScope();
    const res = await apiFetch('/api/files/search?scope=' + scope + '&q=' + encodeURIComponent(term));
    const j = await res.json();
    return j.data || [];
  }

  async function searchFoldersApi(term) {
    const scope = getCurrentScope();
    const res = await apiFetch('/api/folders/search?scope=' + scope + '&q=' + encodeURIComponent(term));
    const j = await res.json();
    return j.data || [];
  }

  // ── Sharing / permissions API ────────────────────────────────────────────
  async function shareResourceApi(payload) {
    const res = await apiFetch('/api/share', {
      method: 'POST',
      headers: { 'Content-Type': 'application/json' },
      body: JSON.stringify(payload),
    });
    return (await res.json()).data;
  }

  async function listResourcePermissionsApi(resourceType, resourceId) {
    const res = await apiFetch('/api/share/' + resourceType + '/' + encodeURIComponent(resourceId));
    return (await res.json()).data || [];
  }

  async function revokePermissionApi(permissionId) {
    await apiFetch('/api/share/' + encodeURIComponent(permissionId), { method: 'DELETE' });
  }

  async function searchUsersApi(term) {
    const res = await apiFetch('/api/users/search?q=' + encodeURIComponent(term));
    return (await res.json()).data || [];
  }

  async function sharedWithMeApi() {
    const res = await apiFetch('/api/shared-with-me');
    return (await res.json()).data || [];
  }

  async function sharedWithProjectApi() {
    const res = await apiFetch('/api/shared-with-project');
    return (await res.json()).data || [];
  }

  async function listUsersApi() {
    const res = await apiFetch('/api/users');
    return (await res.json()).data || [];
  }

  async function listProjectsApi() {
    const res = await apiFetch('/api/projects');
    return (await res.json()).data || [];
  }

  async function searchProjectsApi(term) {
    const res = await apiFetch('/api/projects/search?q=' + encodeURIComponent(term));
    return (await res.json()).data || [];
  }

  async function createFolderApi(name, parentId) {
    const scope = getCurrentScope();
    const res = await apiFetch('/api/folders?scope=' + scope, {
      method: 'POST',
      headers: { 'Content-Type': 'application/json' },
      body: JSON.stringify({ name: name, parentId: parentId || null }),
    });
    const j = await res.json();
    return j.data;
  }

  async function renameFolderApi(id, name) {
    const res = await apiFetch('/api/folders/' + id + '/rename', {
      method: 'PATCH',
      headers: { 'Content-Type': 'application/json' },
      body: JSON.stringify({ name: name }),
    });
    const j = await res.json();
    return j.data;
  }

  async function deleteFolderApi(id) {
    await apiFetch('/api/folders/' + id, { method: 'DELETE' });
  }

  async function moveFileApi(fileId, folderId, scope) {
    const query = scope ? ('?scope=' + encodeURIComponent(scope)) : '';
    const res = await apiFetch('/api/files/' + fileId + '/move' + query, {
      method: 'PATCH',
      headers: { 'Content-Type': 'application/json' },
      body: JSON.stringify({ folderId: folderId || null }),
    });
    const j = await res.json();
    return j.data;
  }

  async function listTrash() {
    const scope = getCurrentScope();
    const res = await apiFetch('/api/files/trash?scope=' + scope);
    const j = await res.json();
    return j.data || [];
  }

  async function deleteFile(id) {
    await apiFetch('/api/files/' + id, { method: 'DELETE' });
  }

  async function restoreFile(id) {
    const res = await apiFetch('/api/files/' + id + '/restore', { method: 'POST' });
    const j = await res.json();
    return j.data;
  }

  async function purgeFile(id) {
    await apiFetch('/api/files/' + id + '/purge', { method: 'DELETE' });
  }

  async function renameFile(id, name) {
    const res = await apiFetch('/api/files/' + id + '/rename', {
      method: 'PATCH',
      headers: { 'Content-Type': 'application/json' },
      body: JSON.stringify({ originalFileName: name }),
    });
    const j = await res.json();
    return j.data;
  }

  async function getEditorConfig(id) {
    const params = new URLSearchParams();
    if (USER_ID) params.set('userId', USER_ID);
    if (USER_NAME) params.set('userName', USER_NAME);
    const query = params.toString();
    const res = await apiFetch('/api/editor/' + id + (query ? '?' + query : ''));
    const j = await res.json();
    return j.data;
  }

  async function createBlankFile(folderId) {
    const scope = getCurrentScope();
    const query = '?scope=' + scope + (folderId ? ('&folderId=' + encodeURIComponent(folderId)) : '');
    const res = await apiFetch('/api/files/new' + query, { method: 'POST' });
    const j = await res.json();
    return j.data;
  }

  async function createBlankSpreadsheet(folderId) {
    const scope = getCurrentScope();
    const query = '?scope=' + scope + (folderId ? ('&folderId=' + encodeURIComponent(folderId)) : '');
    const res = await apiFetch('/api/files/new/spreadsheet' + query, { method: 'POST' });
    const j = await res.json();
    return j.data;
  }

  async function createBlankPresentation(folderId) {
    const scope = getCurrentScope();
    const query = '?scope=' + scope + (folderId ? ('&folderId=' + encodeURIComponent(folderId)) : '');
    const res = await apiFetch('/api/files/new/presentation' + query, { method: 'POST' });
    const j = await res.json();
    return j.data;
  }

  async function moveFolderApi(id, parentId, scope) {
    const query = scope ? ('?scope=' + encodeURIComponent(scope)) : '';
    const res = await apiFetch('/api/folders/' + id + '/move' + query, {
      method: 'PATCH',
      headers: { 'Content-Type': 'application/json' },
      body: JSON.stringify({ parentId: parentId || null }),
    });
    const j = await res.json();
    return j.data;
  }

  async function downloadFileBytes(id) {
    const res = await apiFetch('/api/files/' + id + '/download');
    return res.arrayBuffer();
  }

  function triggerBlobDownload(blob, filename) {
    const url = URL.createObjectURL(blob);
    const a = document.createElement('a');
    a.href = url;
    a.download = filename;
    document.body.appendChild(a);
    a.click();
    a.remove();
    setTimeout(function () { URL.revokeObjectURL(url); }, 5000);
  }

  async function downloadSingle(id, filename) {
    try {
      const buf = await downloadFileBytes(id);
      triggerBlobDownload(new Blob([buf]), filename);
    } catch (err) {
      toast('Error al descargar: ' + err.message, 'error');
    }
  }

  async function downloadFolderZip(folder) {
    try {
      const res = await apiFetch('/api/folders/' + folder.id + '/download');
      const blob = await res.blob();
      triggerBlobDownload(blob, folder.name + '.zip');
    } catch (err) {
      toast('Error al descargar la carpeta: ' + err.message, 'error');
    }
  }

  function uploadFile(file, folderId, onProgress) {
    return new Promise(function (resolve, reject) {
      const boundary = '----OPWidgetBoundary' + Math.random().toString(36).slice(2);
      const meta = JSON.stringify({ originalFileName: file.name, description: '' });

      const reader = new FileReader();
      reader.onload = function (e) {
        const fileBytes = new Uint8Array(e.target.result);

        const enc = new TextEncoder();
        const partFile = enc.encode(
          '--' + boundary + '\r\n' +
          'Content-Disposition: form-data; name="file"; filename="' + file.name + '"\r\n' +
          'Content-Type: ' + (file.type || 'application/octet-stream') + '\r\n\r\n'
        );
        const partMeta = enc.encode(
          '\r\n--' + boundary + '\r\n' +
          'Content-Disposition: form-data; name="request"\r\n' +
          'Content-Type: application/json\r\n\r\n' +
          meta + '\r\n' +
          '--' + boundary + '--\r\n'
        );

        const body = new Uint8Array(partFile.length + fileBytes.length + partMeta.length);
        body.set(partFile, 0);
        body.set(fileBytes, partFile.length);
        body.set(partMeta, partFile.length + fileBytes.length);

        const scope = getCurrentScope();
        const query = '?scope=' + scope + (folderId ? ('&folderId=' + encodeURIComponent(folderId)) : '');
        const xhr = new XMLHttpRequest();
        xhr.open('POST', SERVER + '/api/files/upload' + query);
        if (WIDGET_TOKEN) {
          xhr.setRequestHeader('Authorization', 'Bearer ' + WIDGET_TOKEN);
        } else {
          xhr.setRequestHeader('X-Api-Key', API_KEY);
        }
        xhr.setRequestHeader('Content-Type', 'multipart/form-data; boundary=' + boundary);

        if (onProgress) {
          xhr.upload.onprogress = function (ev) {
            if (ev.lengthComputable) onProgress(ev.loaded / ev.total);
          };
        }

        xhr.onload = function () {
          if (xhr.status >= 200 && xhr.status < 300) {
            try { resolve(JSON.parse(xhr.responseText)); }
            catch (_) { resolve({}); }
          } else {
            let msg = 'HTTP ' + xhr.status;
            try { msg = JSON.parse(xhr.responseText).message || msg; } catch (_) {}
            reject(new Error(msg));
          }
        };
        xhr.onerror = function () { reject(new Error('Network error')); };
        xhr.send(body);
      };
      reader.onerror = function () { reject(new Error('Could not read file')); };
      reader.readAsArrayBuffer(file);
    });
  }

  function resolveDocType(fileType) {
    if (!fileType) return 'word';
    const t = fileType.toLowerCase();
    if (['xlsx', 'xls', 'ods', 'csv'].indexOf(t) >= 0) return 'cell';
    if (['pptx', 'ppt', 'odp'].indexOf(t) >= 0) return 'slide';
    return 'word';
  }

  // ── Editor overlay ───────────────────────────────────────────────────────
  let _editorOverlay = null;
  let _docsAPI = null;
  let _currentEditorFileId = null;
  let _currentEditorDocKey = null;
  let _editorDirty = false;

  function notifyEditorClosed(fileId, documentKey) {
    apiFetch('/api/editor/' + fileId + '/close?key=' + encodeURIComponent(documentKey), { method: 'POST' }).catch(function () {});
  }

  function closeEditor() {
    stopIdleWatch();
    if (_editorMaximized) {
      _editorMaximized = false;
      if (isNativeFullscreen() && document.exitFullscreen) {
        document.exitFullscreen().catch(function () {});
      }
    }
    if (_docsAPI) { try { _docsAPI.destroyEditor(); } catch (_) {} _docsAPI = null; }
    if (_editorOverlay) { _editorOverlay.remove(); _editorOverlay = null; }
    const prev = document.getElementById('op-oo-script');
    if (prev) prev.remove();
    if (_currentEditorFileId && _currentEditorDocKey) {
      notifyEditorClosed(_currentEditorFileId, _currentEditorDocKey);
    }
    _currentEditorFileId = null;
    _currentEditorDocKey = null;
    _editorDirty = false;
  }

  // Closes the editor, confirming first if there are unsaved local edits.

  // ── Pantalla completa del editor ──────────────────────────────────────────
  // Dos mecanismos que se complementan: la clase CSS garantiza el 100% de la ventana
  // siempre, y la Fullscreen API nativa se intenta encima para además ocultar la barra
  // del navegador. Si la API está bloqueada por política de permisos, la clase sola
  // deja al usuario trabajando a pantalla completa igual.
  //
  // Ninguno de los dos toca el iframe ni la instancia de DocsAPI: es un cambio de
  // tamaño, así que la sesión de OnlyOffice sigue viva y sin recargar el documento.

  let _editorMaximized = false;

  function isNativeFullscreen() {
    return !!(document.fullscreenElement || document.webkitFullscreenElement);
  }

  function updateFullscreenButton() {
    if (!_editorOverlay) return;
    const btn = _editorOverlay.querySelector('.op-editor-fullscreen');
    if (!btn) return;
    btn.innerHTML = _editorMaximized ? ICONS.minimize : ICONS.maximize;
    btn.title = _editorMaximized ? 'Restaurar tamaño' : 'Pantalla completa';
    btn.setAttribute('aria-label', btn.title);
  }

  function applyMaximizedState(on) {
    if (!_editorOverlay) return;
    const modal = _editorOverlay.querySelector('.op-editor-modal');
    _editorMaximized = on;
    _editorOverlay.classList.toggle('is-maximized', on);
    if (modal) modal.classList.toggle('is-maximized', on);
    updateFullscreenButton();
  }

  function toggleEditorFullscreen() {
    if (!_editorOverlay) return;

    if (_editorMaximized) {
      applyMaximizedState(false);
      if (isNativeFullscreen() && document.exitFullscreen) {
        document.exitFullscreen().catch(function () {});
      }
      return;
    }

    applyMaximizedState(true);
    // Se pide después de aplicar la clase: si el navegador la rechaza, el usuario ya
    // está a pantalla completa dentro de la ventana y no percibe ningún fallo.
    const el = _editorOverlay;
    const request = el.requestFullscreen || el.webkitRequestFullscreen;
    if (request) {
      try {
        const result = request.call(el);
        if (result && typeof result.catch === 'function') result.catch(function () {});
      } catch (_) {}
    }
  }

  // Salir de pantalla completa nativa (con ESC o con el gesto del navegador) debe dejar
  // el modal en su tamaño original, no maximizado dentro de una ventana normal.
  document.addEventListener('fullscreenchange', function () {
    if (!_editorOverlay) return;
    if (!isNativeFullscreen() && _editorMaximized) applyMaximizedState(false);
  });

  // ESC restaura el tamaño cuando el maximizado es solo por CSS (la Fullscreen API nativa
  // la maneja el propio navegador y dispara fullscreenchange). Deliberadamente NO cierra el
  // editor: ESC es un gesto de "salir de pantalla completa", y cerrar un documento con
  // cambios sin guardar por esa tecla sería destructivo.
  document.addEventListener('keydown', function (e) {
    if (e.key !== 'Escape') return;
    if (_editorOverlay && _editorMaximized && !isNativeFullscreen()) {
      e.stopPropagation();
      applyMaximizedState(false);
    }
  }, true);

  function attemptCloseEditor() {
    // ESC estando maximizado restaura primero. Cerrar el documento de una sería
    // destructivo para un gesto que el usuario asocia a "salir de pantalla completa".
    if (_editorMaximized && !isNativeFullscreen()) {
      applyMaximizedState(false);
      return;
    }
    if (_editorDirty && !window.confirm('Hay cambios sin guardar. ¿Cerrar el editor de todos modos?')) {
      return;
    }
    closeEditor();
  }

  // ── In-editor file manager: header button + slide-in drawer ──────────────
  // Lets the user insert/attach files from the manager while editing. The drawer is a child of the
  // editor modal, so closeEditor() (which removes the overlay) cleans it up automatically. The
  // trigger button already lives in the modal header (built in openEditor's HTML).
  // 2.1/2.2: button + drawer open/close shell. Upload/list/insert land in the next steps (3.x).
  function buildFileManagerUI(overlay) {
    const modal = overlay.querySelector('.op-editor-modal');
    const btn = overlay.querySelector('.op-editor-filemanager-btn');
    if (!modal || !btn) return;

    const drawer = document.createElement('div');
    drawer.className = 'op-fm-drawer';
    drawer.innerHTML =
      '<div class="op-fm-drawer-header">' +
        '<span>Insertar archivo</span>' +
        '<button type="button" class="op-fm-drawer-close" aria-label="Cerrar">' + ICONS.close + '</button>' +
      '</div>' +
      '<div class="op-fm-drawer-body">' +
        '<div class="op-fm-placeholder">Subir e insertar archivos — próximo paso (3.x).</div>' +
      '</div>';
    modal.appendChild(drawer);

    btn.onclick = function () { drawer.classList.toggle('open'); };
    drawer.querySelector('.op-fm-drawer-close').onclick = function () { drawer.classList.remove('open'); };
  }


  // ── Suspensión por inactividad ─────────────────────────────────────────────
  // OnlyOffice Community Edition admite 20 conexiones simultáneas. Una pestaña
  // olvidada retiene una de por vida, así que un puñado de ellas deja al resto de
  // la empresa sin poder abrir documentos. El backend tiene su propio reaper; esto
  // libera el cupo antes y, sobre todo, le explica al usuario qué pasó en vez de
  // dejarlo mirando un editor muerto.

  const IDLE_EVENTS = ['mousemove', 'mousedown', 'keydown', 'click', 'touchstart', 'wheel'];
  const IDLE_HEARTBEAT_MS = 120000;  // 2 min: avisa al backend que seguimos acá
  const IDLE_WARN_MS      = 540000;  // 9 min: aviso con cuenta regresiva
  const IDLE_SUSPEND_MS   = 600000;  // 10 min: se cierra, igual que el reaper

  let _idleLastActivity = 0;
  let _idleTimer = null;
  let _idleHeartbeatTimer = null;
  let _idleWarnEl = null;
  let _idleCountdown = null;
  let _idleSessionId = null;
  let _idleSuspended = false;

  /** Cualquier señal de vida del usuario reinicia el reloj y retira el aviso. */
  function markEditorActivity() {
    _idleLastActivity = Date.now();
    if (_idleWarnEl) dismissIdleWarning();
  }

  function startIdleWatch(sessionId) {
    stopIdleWatch();
    _idleSessionId = sessionId || null;
    _idleSuspended = false;
    _idleLastActivity = Date.now();

    IDLE_EVENTS.forEach(function (evt) {
      document.addEventListener(evt, markEditorActivity, true);
    });

    // Un solo temporizador de 5s en vez de uno por umbral: menos relojes que
    // desincronizar y el estado se deriva siempre del último instante de actividad.
    _idleTimer = setInterval(checkIdleState, 5000);

    _idleHeartbeatTimer = setInterval(function () {
      // Solo se late si hubo actividad reciente. Latir siempre mantendría viva
      // una pestaña abandonada, que es justamente lo que se quiere evitar.
      if (Date.now() - _idleLastActivity < IDLE_HEARTBEAT_MS) sendHeartbeat();
    }, IDLE_HEARTBEAT_MS);
  }

  function stopIdleWatch() {
    IDLE_EVENTS.forEach(function (evt) {
      document.removeEventListener(evt, markEditorActivity, true);
    });
    if (_idleTimer) { clearInterval(_idleTimer); _idleTimer = null; }
    if (_idleHeartbeatTimer) { clearInterval(_idleHeartbeatTimer); _idleHeartbeatTimer = null; }
    dismissIdleWarning();
    _idleSessionId = null;
  }

  async function sendHeartbeat() {
    if (!_idleSessionId) return;
    try {
      const res = await apiFetch('/api/editor/sessions/' + _idleSessionId + '/heartbeat', { method: 'POST' });
      const j = await res.json();
      // El backend responde false cuando la sesión ya fue cerrada por el reaper:
      // seguir latiendo sobre una sesión inexistente no aporta nada.
      if (j && j.data === false) _idleSessionId = null;
    } catch (_) {
      // Un heartbeat perdido no es motivo para molestar al usuario: el próximo
      // reintenta, y si el backend nunca se entera, el reaper cierra la sesión.
    }
  }

  function checkIdleState() {
    if (_idleSuspended || !_editorOverlay) return;
    const idleFor = Date.now() - _idleLastActivity;

    if (idleFor >= IDLE_SUSPEND_MS) {
      suspendEditorForInactivity();
    } else if (idleFor >= IDLE_WARN_MS && !_idleWarnEl) {
      showIdleWarning();
    }
  }

  /** Aviso con cuenta regresiva y una salida clara para el usuario. */
  function showIdleWarning() {
    if (_idleWarnEl || !_editorOverlay) return;

    const el = document.createElement('div');
    el.className = 'op-idle-warn';
    const text = document.createElement('span');
    const btn = document.createElement('button');
    btn.type = 'button';
    btn.className = 'op-idle-warn-btn';
    btn.textContent = 'Continuar editando';
    btn.onclick = markEditorActivity;
    el.appendChild(text);
    el.appendChild(btn);
    _editorOverlay.appendChild(el);
    _idleWarnEl = el;

    const tick = function () {
      const left = Math.max(0, Math.ceil((IDLE_SUSPEND_MS - (Date.now() - _idleLastActivity)) / 1000));
      text.textContent = 'Tu sesión se suspenderá en ' + left + ' segundos por inactividad para liberar recursos.';
      if (left <= 0) dismissIdleWarning();
    };
    tick();
    _idleCountdown = setInterval(tick, 1000);
  }

  function dismissIdleWarning() {
    if (_idleCountdown) { clearInterval(_idleCountdown); _idleCountdown = null; }
    if (_idleWarnEl && _idleWarnEl.parentNode) _idleWarnEl.parentNode.removeChild(_idleWarnEl);
    _idleWarnEl = null;
  }

  /**
   * Cierra el editor y deja en su lugar una pantalla que explica qué pasó.
   * Se destruye la instancia de DocsAPI a propósito: ocultarla mantendría viva la
   * conexión con Document Server, que es exactamente el recurso que se busca liberar.
   */
  function suspendEditorForInactivity() {
    if (_idleSuspended) return;
    _idleSuspended = true;
    dismissIdleWarning();

    const sessionId = _idleSessionId;
    stopIdleWatch();

    try {
      if (_docsAPI && typeof _docsAPI.destroyEditor === 'function') _docsAPI.destroyEditor();
    } catch (_) {}
    _docsAPI = null;

    if (sessionId) {
      // Aviso al backend para que libere el cupo ya, sin esperar al reaper.
      apiFetch('/api/editor/' + _currentEditorFileId + '/close', { method: 'POST' }).catch(function () {});
    }

    const host = _editorOverlay && _editorOverlay.querySelector('#op-editor-container');
    if (!host) return;
    host.innerHTML = '';

    const box = document.createElement('div');
    box.className = 'op-idle-suspended';
    const h = document.createElement('h3');
    h.textContent = 'Sesión pausada por inactividad';
    const p = document.createElement('p');
    p.textContent = 'Cerramos el documento para liberar una conexión de edición. '
      + 'Tus cambios quedaron guardados.';
    const resume = document.createElement('button');
    resume.type = 'button';
    resume.className = 'op-btn-primary';
    resume.textContent = 'Reanudar edición';
    resume.onclick = function () {
      const fileId = _currentEditorFileId;
      closeEditor();
      if (fileId) openEditor(fileId);
    };
    box.appendChild(h);
    box.appendChild(p);
    box.appendChild(resume);
    host.appendChild(box);
  }


  // ── Notificaciones ─────────────────────────────────────────────────────────
  // Quien comparte un documento no tiene forma de saber si alguien lo miró. Esto
  // le avisa. Es informativo: si el sondeo falla, el gestor sigue funcionando sin
  // molestar al usuario con errores que no puede resolver.

  // 15s en vez de 60: el usuario que acaba de compartir algo quiere ver la campana
  // encenderse mientras mira, no un minuto después.
  const NOTIF_POLL_MS = 15000;

  let _notifTimer = null;
  let _notifPanel = null;
  let _notifItems = [];
  let _notifUnread = 0;

  async function fetchNotificationsApi() {
    const res = await apiFetch('/api/notifications');
    return (await res.json()).data || { notifications: [], unreadCount: 0 };
  }

  async function markNotificationReadApi(id) {
    await apiFetch('/api/notifications/' + id + '/read', { method: 'POST' });
  }

  async function markAllNotificationsReadApi() {
    await apiFetch('/api/notifications/read-all', { method: 'POST' });
  }

  /** "hace 5 minutos", "hace 2 horas", "ayer"… Más legible que una fecha exacta. */
  function relativeTime(iso) {
    if (!iso) return '';
    const then = new Date(iso.endsWith('Z') ? iso : iso + 'Z');
    if (isNaN(then.getTime())) return '';
    const seconds = Math.floor((Date.now() - then.getTime()) / 1000);
    if (seconds < 60) return 'hace un momento';
    const minutes = Math.floor(seconds / 60);
    if (minutes < 60) return 'hace ' + minutes + (minutes === 1 ? ' minuto' : ' minutos');
    const hours = Math.floor(minutes / 60);
    if (hours < 24) return 'hace ' + hours + (hours === 1 ? ' hora' : ' horas');
    const days = Math.floor(hours / 24);
    if (days === 1) return 'ayer';
    if (days < 30) return 'hace ' + days + ' días';
    return formatDate(iso);
  }

  function updateNotifBadge() {
    const btn = _container && _container.querySelector('.op-notifications-btn');
    if (!btn) return;
    let badge = btn.querySelector('.op-notif-badge');
    if (_notifUnread > 0) {
      if (!badge) {
        badge = document.createElement('span');
        badge.className = 'op-notif-badge';
        btn.appendChild(badge);
      }
      badge.textContent = _notifUnread > 99 ? '99+' : String(_notifUnread);
    } else if (badge) {
      badge.remove();
    }
  }

  async function refreshNotifications(rerender) {
    try {
      const data = await fetchNotificationsApi();
      console.log('[OP-NOTIF] Notificaciones recibidas:', data);
      _notifItems = data.notifications || [];
      _notifUnread = data.unreadCount || 0;
      updateNotifBadge();
      if (rerender && _notifPanel) renderNotifList();
    } catch (_) {
      // Silencio deliberado: es información secundaria y el usuario no puede
      // hacer nada al respecto. El próximo sondeo reintenta.
    }
  }

  function startNotificationPolling() {
    refreshNotifications(false);
    if (_notifTimer) clearInterval(_notifTimer);
    _notifTimer = setInterval(function () { refreshNotifications(true); }, NOTIF_POLL_MS);

    // Volver a la pestaña es la señal más fuerte de que el usuario quiere ver el estado
    // actual: consultar en ese momento evita la espera hasta el próximo sondeo.
    window.addEventListener('focus', function () { refreshNotifications(true); });
  }

  function closeNotifPanel() {
    if (_notifPanel && _notifPanel.parentNode) _notifPanel.parentNode.removeChild(_notifPanel);
    _notifPanel = null;
    document.removeEventListener('mousedown', onNotifOutsideClick, true);
  }

  function onNotifOutsideClick(e) {
    if (!_notifPanel) return;
    const btn = _container && _container.querySelector('.op-notifications-btn');
    if (_notifPanel.contains(e.target) || (btn && btn.contains(e.target))) return;
    closeNotifPanel();
  }

  function renderNotifList() {
    if (!_notifPanel) return;
    const list = _notifPanel.querySelector('.op-notif-list');
    if (!list) return;
    list.innerHTML = '';

    if (_notifItems.length === 0) {
      const empty = document.createElement('div');
      empty.className = 'op-notif-empty';
      empty.textContent = 'No tienes notificaciones pendientes';
      list.appendChild(empty);
      return;
    }

    _notifItems.forEach(function (n) {
      const item = document.createElement('div');
      item.className = 'op-notif-item' + (n.read ? '' : ' is-unread');

      const icon = document.createElement('div');
      icon.className = 'op-notif-icon';
      icon.innerHTML = ICONS.bell;

      const body = document.createElement('div');
      body.className = 'op-notif-body';
      const msg = document.createElement('div');
      msg.className = 'op-notif-msg';
      msg.textContent = n.message;
      const when = document.createElement('div');
      when.className = 'op-notif-when';
      when.textContent = relativeTime(n.createdAt);
      body.appendChild(msg);
      body.appendChild(when);

      item.appendChild(icon);
      item.appendChild(body);

      if (!n.read) {
        item.onclick = function () {
          markNotificationReadApi(n.id).then(function () {
            n.read = true;
            _notifUnread = Math.max(0, _notifUnread - 1);
            updateNotifBadge();
            renderNotifList();
          }).catch(function () {});
        };
      }
      list.appendChild(item);
    });
  }

  function toggleNotifPanel() {
    if (_notifPanel) { closeNotifPanel(); return; }

    const panel = document.createElement('div');
    panel.className = 'op-notif-panel';

    const head = document.createElement('div');
    head.className = 'op-notif-head';
    const title = document.createElement('span');
    title.textContent = 'Notificaciones';
    const markAll = document.createElement('button');
    markAll.type = 'button';
    markAll.className = 'op-notif-markall';
    markAll.textContent = 'Marcar todas como leídas';
    markAll.onclick = function () {
      markAllNotificationsReadApi().then(function () {
        _notifItems.forEach(function (n) { n.read = true; });
        _notifUnread = 0;
        updateNotifBadge();
        renderNotifList();
      }).catch(function () { toast('No se pudieron marcar como leídas', 'error'); });
    };
    head.appendChild(title);
    head.appendChild(markAll);

    const list = document.createElement('div');
    list.className = 'op-notif-list';

    panel.appendChild(head);
    panel.appendChild(list);

    const btn = _container.querySelector('.op-notifications-btn');
    (btn && btn.parentNode ? btn.parentNode : _container).appendChild(panel);
    _notifPanel = panel;

    renderNotifList();
    // Se refresca al abrir: el sondeo corre cada minuto y el usuario abre el panel
    // justamente porque quiere ver lo último.
    refreshNotifications(true);

    document.addEventListener('mousedown', onNotifOutsideClick, true);
  }

  function openEditor(fileId, fileName) {
    closeEditor();

    _editorOverlay = document.createElement('div');
    _editorOverlay.className = 'op-editor-overlay';
    _editorOverlay.innerHTML =
      '<div class="op-editor-modal">' +
        '<div class="op-editor-header">' +
          '<span class="op-editor-title">' + escapeHtml(fileName) + '</span>' +
          '<span class="op-editor-status" data-state="idle">Cargando…</span>' +
          '<span class="op-editor-collab" hidden></span>' +
          '<div class="op-editor-header-actions">' +
            '<button type="button" class="op-editor-filemanager-btn">' + ICONS.folder + '<span>Gestor de archivos</span></button>' +
            '<button type="button" class="op-editor-fullscreen" aria-label="Pantalla completa" title="Pantalla completa">' + ICONS.maximize + '</button>' +
            '<button type="button" class="op-editor-close" aria-label="Cerrar">' + ICONS.close + '</button>' +
          '</div>' +
        '</div>' +
        '<div class="op-editor-body">' +
          '<div id="op-editor-container"></div>' +
          '<div class="op-editor-loading">Cargando editor…</div>' +
        '</div>' +
      '</div>';

    document.body.appendChild(_editorOverlay);
    _editorOverlay.querySelector('.op-editor-close').onclick = attemptCloseEditor;
    _editorOverlay.querySelector('.op-editor-fullscreen').onclick = toggleEditorFullscreen;
    // Click on the dark backdrop (outside the modal) also closes, with the same unsaved-changes guard.
    _editorOverlay.onclick = function (e) { if (e.target === _editorOverlay) attemptCloseEditor(); };
    buildFileManagerUI(_editorOverlay);
    const statusEl = _editorOverlay.querySelector('.op-editor-status');
    const collabEl = _editorOverlay.querySelector('.op-editor-collab');

    getEditorConfig(fileId).then(function (cfg) {
      if (!cfg || !_editorOverlay) return;

      _currentEditorFileId = fileId;
      _currentEditorDocKey = cfg.document && cfg.document.key;
      if (collabEl && cfg.activeSessionsCount > 1) {
        collabEl.textContent = cfg.activeSessionsCount + ' personas editando este documento';
        collabEl.hidden = false;
      }

      const loading = _editorOverlay.querySelector('.op-editor-loading');
      // cfg.documentServer is the browser-reachable public URL of the OnlyOffice document server
      // (e.g. http://localhost:8081). The internal Docker hostname is never sent to the browser.
      let docServer = (cfg.documentServer || '').trim();

      // Defence in depth. If the backend sends nothing usable, an empty value here would build
      // the relative path '/web-apps/...', which the browser resolves against the CONSUMING
      // application's own host — a 404 on every open. A relative path is never acceptable for
      // this script: it must be an absolute URL pointing at Document Server.
      if (!/^https?:\/\//i.test(docServer)) {
        let base = '';
        try {
          // Same host as the platform, on the Document Server port.
          const platform = new URL(SERVER || window.location.origin, window.location.href);
          platform.port = OO_DEFAULT_PORT;
          platform.pathname = '';
          platform.search = '';
          platform.hash = '';
          base = platform.toString().replace(/\/$/, '');
        } catch (_) {}
        docServer = base || OO_FALLBACK_URL;
      }

      try {
        const dsUrl = new URL(docServer);
        if (dsUrl.hostname === 'localhost' || dsUrl.hostname === '127.0.0.1') {
          // A loopback address is meaningless to a browser on another machine: rewrite it to the
          // host this page was actually served from.
          let refHost = '';
          if (SERVER) {
            try { refHost = new URL(SERVER, window.location.href).hostname; } catch (_) {}
          }
          if (!refHost && window.location && window.location.hostname) {
            refHost = window.location.hostname;
          }
          if (refHost && refHost !== 'localhost' && refHost !== '127.0.0.1') {
            dsUrl.hostname = refHost;
            docServer = dsUrl.toString().replace(/\/$/, '');
          }
        }
      } catch (_) {}

      const scriptSrc = docServer.replace(/\/$/, '') + '/web-apps/apps/api/documents/api.js';

      const script = document.createElement('script');
      script.id = 'op-oo-script';
      script.src = scriptSrc;
      script.onload = function () {
        if (!_editorOverlay) return;
        try {
          // editorConfig/token come straight from the backend (signed JWT payload).
          // Never rebuild them client-side: the callbackUrl inside must resolve from
          // the OnlyOffice Document Server container, not from the browser's URL.
          _docsAPI = new window.DocsAPI.DocEditor('op-editor-container', {
            document: cfg.document,
            documentType: resolveDocType(cfg.document && cfg.document.fileType),
            token: cfg.token,
            editorConfig: cfg.editorConfig,
            events: {
              onDocumentReady: function () {
                if (loading) loading.remove();
                // El reloj arranca recién acá: antes de que el documento cargue no hay
                // sesión de edición que valga la pena vigilar.
                startIdleWatch(cfg.sessionId);
                _editorDirty = false;
                if (statusEl) { statusEl.textContent = 'Guardado'; statusEl.setAttribute('data-state', 'saved'); }
              },
              onDocumentStateChange: function (event) {
                _editorDirty = !!(event && event.data === true);
                if (!statusEl) return;
                if (_editorDirty) {
                  statusEl.textContent = 'Cambios sin guardar';
                  statusEl.setAttribute('data-state', 'dirty');
                } else {
                  statusEl.textContent = 'Guardado';
                  statusEl.setAttribute('data-state', 'saved');
                }
              },
              onError: function (event) {
                if (statusEl) { statusEl.textContent = 'Error al guardar'; statusEl.setAttribute('data-state', 'error'); }
                const detail = event && event.data ? (event.data.errorDescription || String(event.data)) : 'Error del editor';
                toast('Error del editor: ' + detail, 'error');
              },
            },
          });
        } catch (err) {
          console.error('[OfficePlatform] Editor init error:', err);
          toast('Error al inicializar el editor', 'error');
        }
      };
      script.onerror = function () {
        toast('No se pudo cargar el editor OnlyOffice', 'error');
        if (loading) loading.textContent = 'Error al cargar el editor.';
      };
      document.head.appendChild(script);
    }).catch(function (err) {
      toast('Error al obtener configuración del editor: ' + err.message, 'error');
      closeEditor();
    });
  }

  // ── Modal shell ──────────────────────────────────────────────────────────
  // Header/body/footer are separate flex regions so the footer's action
  // buttons are always visible (pinned, non-scrolling) no matter how tall
  // the body content gets or how short the viewport is.
  let _modalEl = null;
  function closeModal() {
    if (_modalEl) { _modalEl.remove(); _modalEl = null; }
  }

  function buildModalShell(title) {
    closeModal();
    const backdrop = document.createElement('div');
    backdrop.className = 'op-modal-backdrop';
    const box = document.createElement('div');
    box.className = 'op-modal';

    const hd = document.createElement('div');
    hd.className = 'op-modal-hd';
    const titleEl = document.createElement('h4');
    titleEl.className = 'op-modal-title';
    titleEl.textContent = title || '';
    hd.appendChild(titleEl);
    box.appendChild(hd);

    const body = document.createElement('div');
    body.className = 'op-modal-body';
    box.appendChild(body);

    const ft = document.createElement('div');
    ft.className = 'op-modal-ft';
    box.appendChild(ft);

    backdrop.appendChild(box);
    backdrop.onclick = function (e) { if (e.target === backdrop) closeModal(); };
    document.body.appendChild(backdrop);
    _modalEl = backdrop;

    return { backdrop: backdrop, box: box, hd: hd, body: body, ft: ft };
  }


  // ── Ayuda de macros ────────────────────────────────────────────────────────
  // Los analistas que llegan desde Excel VBA necesitan la traducción en el momento
  // de escribir, no en un documento aparte. Todo esto es aditivo: no toca la
  // autenticación por token, la subida de archivos ni el visor.

  const MACRO_PROMPT = [
    'Sos experto en la API de macros de OnlyOffice Document Server (Api de hojas de cálculo).',
    '',
    'Convertí la macro de Excel VBA que te paso al final a JavaScript de OnlyOffice,',
    'siguiendo estas reglas sin excepción:',
    '',
    '1. Envolvé todo el código en (function () { ... })();',
    '2. Usá Api.GetActiveSheet() para obtener la hoja activa.',
    '3. Celdas por letra: sheet.GetRange("A1"). Por número: sheet.GetRangeByNumber(fila, columna),',
    '   recordando que empiezan en 0, no en 1.',
    '4. Leer es .GetValue(), escribir es .SetValue(valor).',
    '5. Colores: Api.CreateColorFromRGB(r, g, b). Relleno .SetFillColor(...), letra .SetFontColor(...).',
    '6. Formato: .SetBold(true), .SetFontSize(n), .SetFontName("..."), .SetAlignHorizontal("center"),',
    '   .SetNumberFormat("#,##0.00").',
    '7. Bordes: .SetBorders("Bottom", "Thin", Api.CreateColorFromRGB(r, g, b)).',
    '8. MsgBox se convierte en Api.ShowAlert(...).',
    '9. Ancho de columna con .SetColumnWidth(n); no existe AutoFit.',
    '10. Comentá cada bloque en castellano, explicando qué hace en términos de negocio.',
    '11. No inventes funciones que no existan en la API de OnlyOffice. Si algo de VBA no tiene',
    '    equivalente, decilo en un comentario y proponé la alternativa más cercana.',
    '',
    'Devolveme únicamente el código final listo para pegar, y debajo una lista breve de las',
    'diferencias de comportamiento que debería revisar.',
    '',
    'Esta es mi macro de VBA:',
    '',
    '[PEGÁ ACÁ TU MACRO]',
  ].join('\n');

  const MACRO_EQUIV = [
    ['Set ws = ActiveSheet', 'let sheet = Api.GetActiveSheet();'],
    ['Range("A1").Value = "X"', 'sheet.GetRange("A1").SetValue("X");'],
    ['x = Range("A1").Value', 'let x = sheet.GetRange("A1").GetValue();'],
    ['Cells(f, c).Value', 'sheet.GetRangeByNumber(f - 1, c - 1).GetValue();'],
    ['.Font.Bold = True', '.SetBold(true);'],
    ['.Interior.Color = RGB(r,g,b)', '.SetFillColor(Api.CreateColorFromRGB(r, g, b));'],
    ['.Font.Color = RGB(r,g,b)', '.SetFontColor(Api.CreateColorFromRGB(r, g, b));'],
    ['.NumberFormat = "#,##0.00"', '.SetNumberFormat("#,##0.00");'],
    ['For i = 2 To 100 ... Next', 'for (let i = 2; i <= 100; i++) { ... }'],
    ['MsgBox "Listo"', 'Api.ShowAlert("Listo");'],
  ];

  /** Copia texto al portapapeles y confirma en el propio botón. */
  function copyToClipboard(text, btn) {
    const done = function () {
      const original = btn.textContent;
      btn.textContent = 'Copiado';
      btn.disabled = true;
      setTimeout(function () { btn.textContent = original; btn.disabled = false; }, 1600);
    };
    if (navigator.clipboard && navigator.clipboard.writeText) {
      navigator.clipboard.writeText(text).then(done).catch(function () { fallbackCopy(text, done); });
    } else {
      // Sin contexto seguro (http://) la Clipboard API no existe: hay camino alternativo.
      fallbackCopy(text, done);
    }
  }

  function fallbackCopy(text, done) {
    const ta = document.createElement('textarea');
    ta.value = text;
    ta.style.position = 'fixed';
    ta.style.opacity = '0';
    document.body.appendChild(ta);
    ta.select();
    try { document.execCommand('copy'); done(); } catch (_) {}
    document.body.removeChild(ta);
  }

  function showMacrosHelp() {
    const shell = buildModalShell('Ayuda de macros');
    shell.box.classList.add('op-modal--macros');

    const tabs = document.createElement('div');
    tabs.className = 'op-mh-tabs';
    const tabIA = document.createElement('button');
    tabIA.type = 'button';
    tabIA.className = 'op-mh-tab op-active';
    tabIA.textContent = 'Conversor con IA';
    const tabEq = document.createElement('button');
    tabEq.type = 'button';
    tabEq.className = 'op-mh-tab';
    tabEq.textContent = 'Equivalencias rápidas';
    tabs.appendChild(tabIA);
    tabs.appendChild(tabEq);
    shell.hd.appendChild(tabs);

    // Panel 1: el prompt para la IA
    const panelIA = document.createElement('div');
    panelIA.className = 'op-mh-panel';
    const introIA = document.createElement('p');
    introIA.className = 'op-mh-intro';
    introIA.textContent = 'Copiá este texto, pegalo en ChatGPT o Claude, y debajo pegá tu macro de Excel. '
      + 'Te devuelve el código listo para el editor de macros.';
    panelIA.appendChild(introIA);

    const pre = document.createElement('pre');
    pre.className = 'op-mh-code';
    pre.textContent = MACRO_PROMPT;
    panelIA.appendChild(pre);

    const copyPrompt = document.createElement('button');
    copyPrompt.type = 'button';
    copyPrompt.className = 'op-btn-primary op-mh-copy';
    copyPrompt.textContent = 'Copiar prompt al portapapeles';
    copyPrompt.onclick = function () { copyToClipboard(MACRO_PROMPT, copyPrompt); };
    panelIA.appendChild(copyPrompt);

    const warn = document.createElement('p');
    warn.className = 'op-mh-warn';
    warn.textContent = 'Probá siempre el resultado sobre una copia antes de usarlo en producción.';
    panelIA.appendChild(warn);

    // Panel 2: tabla de equivalencias
    const panelEq = document.createElement('div');
    panelEq.className = 'op-mh-panel';
    panelEq.hidden = true;
    const introEq = document.createElement('p');
    introEq.className = 'op-mh-intro';
    introEq.textContent = 'Lo mismo que ya hacías, escrito distinto. Copiá el equivalente con un clic.';
    panelEq.appendChild(introEq);

    const table = document.createElement('table');
    table.className = 'op-mh-table';
    const thead = document.createElement('thead');
    const htr = document.createElement('tr');
    ['Excel VBA', 'OnlyOffice JavaScript', ''].forEach(function (h) {
      const th = document.createElement('th');
      th.textContent = h;
      htr.appendChild(th);
    });
    thead.appendChild(htr);
    table.appendChild(thead);

    const tbody = document.createElement('tbody');
    MACRO_EQUIV.forEach(function (pair) {
      const tr = document.createElement('tr');
      const tdV = document.createElement('td');
      tdV.className = 'op-mh-vba';
      tdV.textContent = pair[0];
      const tdJ = document.createElement('td');
      tdJ.className = 'op-mh-js';
      tdJ.textContent = pair[1];
      const tdB = document.createElement('td');
      const b = document.createElement('button');
      b.type = 'button';
      b.className = 'op-mh-mini';
      b.textContent = 'Copiar';
      b.onclick = function () { copyToClipboard(pair[1], b); };
      tdB.appendChild(b);
      tr.appendChild(tdV);
      tr.appendChild(tdJ);
      tr.appendChild(tdB);
      tbody.appendChild(tr);
    });
    table.appendChild(tbody);
    panelEq.appendChild(table);

    const hint = document.createElement('p');
    hint.className = 'op-mh-warn';
    hint.textContent = 'GetRangeByNumber cuenta desde 0: la celda A1 es (0, 0). '
      + 'Es el error más común al convertir una macro.';
    panelEq.appendChild(hint);

    shell.body.appendChild(panelIA);
    shell.body.appendChild(panelEq);

    function activate(isIA) {
      tabIA.classList.toggle('op-active', isIA);
      tabEq.classList.toggle('op-active', !isIA);
      panelIA.hidden = !isIA;
      panelEq.hidden = isIA;
    }
    tabIA.onclick = function () { activate(true); };
    tabEq.onclick = function () { activate(false); };

    const close = document.createElement('button');
    close.type = 'button';
    close.className = 'op-btn-secondary';
    close.textContent = 'Cerrar';
    close.onclick = closeModal;
    shell.ft.appendChild(close);
  }

  function showModal(opts) {
    const shell = buildModalShell(opts.title);

    if (opts.message) {
      const msg = document.createElement('p');
      msg.className = 'op-modal-message';
      msg.textContent = opts.message;
      shell.hd.appendChild(msg);
    }

    let inputEl = null;
    if (opts.inputValue !== undefined) {
      inputEl = document.createElement('input');
      inputEl.type = 'text';
      inputEl.className = 'op-modal-input';
      inputEl.value = opts.inputValue;
      inputEl.maxLength = 255;
      inputEl.style.marginBottom = '0';
      inputEl.style.marginTop = 'var(--op-space-1)';
      shell.body.appendChild(inputEl);
      shell.body.style.paddingBottom = 'var(--op-space-4)';
    }

    const cancelBtn = document.createElement('button');
    cancelBtn.type = 'button';
    cancelBtn.className = 'op-btn-secondary';
    cancelBtn.textContent = opts.cancelLabel || 'Cancelar';
    cancelBtn.onclick = closeModal;
    const confirmBtn = document.createElement('button');
    confirmBtn.type = 'button';
    confirmBtn.className = opts.danger ? 'op-btn-danger' : 'op-btn-primary';
    confirmBtn.textContent = opts.confirmLabel || 'Confirmar';
    confirmBtn.onclick = function () {
      const value = inputEl ? inputEl.value : undefined;
      closeModal();
      opts.onConfirm(value);
    };
    shell.ft.appendChild(cancelBtn);
    shell.ft.appendChild(confirmBtn);

    if (inputEl) {
      inputEl.focus();
      inputEl.select();
      inputEl.onkeydown = function (e) { if (e.key === 'Enter') confirmBtn.click(); };
    } else {
      confirmBtn.focus();
    }
  }

  // ── Share modal (granular permissions) ──────────────────────────────────
  const SHARE_LEVELS = [
    { value: 'VIEW', label: 'Ver' },
    { value: 'DOWNLOAD', label: 'Descargar' },
    { value: 'EDIT', label: 'Editar' },
  ];

  function shareLevelLabel(value) {
    const found = SHARE_LEVELS.filter(function (l) { return l.value === value; })[0];
    return found ? found.label : value;
  }

  function buildLevelSelect() {
    const select = document.createElement('select');
    select.className = 'op-share-level';
    SHARE_LEVELS.forEach(function (lvl) {
      const opt = document.createElement('option');
      opt.value = lvl.value;
      opt.textContent = lvl.label;
      select.appendChild(opt);
    });
    return select;
  }

  function openShareModal(resourceType, resourceId, resourceName) {
    const shell = buildModalShell('Compartir "' + resourceName + '"');

    // Target-type tabs: Usuario | Proyecto
    const tabBar = document.createElement('div');
    tabBar.className = 'op-share-tabs';
    const tabUser = document.createElement('button');
    tabUser.type = 'button';
    tabUser.className = 'op-share-tab op-active';
    tabUser.innerHTML = ICONS.users + '<span>Usuario</span>';
    const tabProject = document.createElement('button');
    tabProject.type = 'button';
    tabProject.className = 'op-share-tab';
    tabProject.innerHTML = ICONS.share + '<span>Proyecto</span>';
    tabBar.appendChild(tabUser);
    tabBar.appendChild(tabProject);
    shell.body.appendChild(tabBar);

    // ── User panel ──
    const userPanel = document.createElement('div');
    userPanel.className = 'op-share-panel';

    const userSearchWrap = document.createElement('div');
    userSearchWrap.style.position = 'relative';
    const userInput = document.createElement('input');
    userInput.type = 'text';
    userInput.className = 'op-modal-input';
    userInput.placeholder = 'Buscar usuario por nombre…';
    const userResults = document.createElement('div');
    userResults.className = 'op-share-results';
    userResults.hidden = true;
    userSearchWrap.appendChild(userInput);
    userSearchWrap.appendChild(userResults);
    userPanel.appendChild(userSearchWrap);

    let selectedUser = null;
    const selectedChip = document.createElement('div');
    selectedChip.className = 'op-share-chip';
    selectedChip.hidden = true;
    userPanel.appendChild(selectedChip);

    /** Campo de observaciones, idéntico en ambas pestañas. */
    function buildNotesField() {
      const wrap = document.createElement('label');
      wrap.className = 'op-share-notes';
      const caption = document.createElement('span');
      caption.textContent = 'Observaciones (opcional)';
      const area = document.createElement('textarea');
      area.rows = 2;
      area.maxLength = 500;
      area.placeholder = 'Ej: revisar antes del viernes, versión final para firma…';
      wrap.appendChild(caption);
      wrap.appendChild(area);
      return {
        wrap: wrap,
        value: function () {
          const v = area.value.trim();
          return v ? v : null;
        },
        clear: function () { area.value = ''; },
      };
    }

    function setSelectedUser(user) {
      selectedUser = user;
      if (!user) { selectedChip.hidden = true; return; }
      selectedChip.hidden = false;
      selectedChip.innerHTML = '';
      const label = document.createElement('span');
      label.textContent = (user.displayName || user.userId) + ' · ' + user.userId;
      const clear = document.createElement('button');
      clear.type = 'button';
      clear.className = 'op-share-chip-clear';
      clear.innerHTML = ICONS.close;
      clear.onclick = function () { setSelectedUser(null); userInput.value = ''; };
      selectedChip.appendChild(label);
      selectedChip.appendChild(clear);
      userInput.value = '';
      userResults.hidden = true;
    }

    function renderUserResults(users) {
      userResults.innerHTML = '';
      if (users.length === 0) { userResults.hidden = true; return; }
      users.forEach(function (user) {
        const item = document.createElement('button');
        item.type = 'button';
        item.className = 'op-share-result';
        item.innerHTML = '<strong>' + escapeHtml(user.displayName || user.userId) + '</strong><span>'
            + escapeHtml(user.userId) + '</span>';
        item.onclick = function () { setSelectedUser(user); };
        userResults.appendChild(item);
      });
      userResults.hidden = false;
    }

    userInput.onfocus = function () {
      if (!userInput.value.trim()) {
        listUsersApi().then(renderUserResults).catch(function () { userResults.hidden = true; });
      }
    };

    let userSearchTimer = null;
    userInput.oninput = function () {
      clearTimeout(userSearchTimer);
      const term = userInput.value.trim();
      if (!term) {
        listUsersApi().then(renderUserResults).catch(function () { userResults.hidden = true; });
        return;
      }
      userSearchTimer = setTimeout(function () {
        searchUsersApi(term).then(renderUserResults).catch(function () { userResults.hidden = true; });
      }, 350);
    };

    const userLevel = buildLevelSelect();
    userPanel.appendChild(userLevel);

    const userNotes = buildNotesField();
    userPanel.appendChild(userNotes.wrap);

    const userShareBtn = document.createElement('button');
    userShareBtn.type = 'button';
    userShareBtn.className = 'op-btn-primary';
    userShareBtn.style.width = '100%';
    userShareBtn.textContent = 'Compartir con usuario';
    userShareBtn.onclick = function () {
      if (!selectedUser) { toast('Elegí un usuario primero', 'error'); return; }
      userShareBtn.disabled = true;
      shareResourceApi({
        resourceType: resourceType,
        resourceId: resourceId,
        targetType: 'USER',
        targetUserId: selectedUser.userId,
        permissionLevel: userLevel.value,
        notes: userNotes.value(),
      }).then(function () {
        toast('Compartido con ' + (selectedUser.displayName || selectedUser.userId), 'success');
        setSelectedUser(null);
        userNotes.clear();
        reloadPerms();
      }).catch(function (err) {
        toast('Error al compartir: ' + err.message, 'error');
      }).finally(function () { userShareBtn.disabled = false; });
    };
    userPanel.appendChild(userShareBtn);

    // ── Project panel ──
    // Autocompletado en vez de un <select> con todos los proyectos: con veinte
    // aplicativos consumidores la lista desplegable se vuelve inmanejable, y el
    // usuario ya sabe el nombre del proyecto que busca.
    const projectPanel = document.createElement('div');
    projectPanel.className = 'op-share-panel';
    projectPanel.style.display = 'none';

    let selectedProject = null;

    const projectChip = document.createElement('div');
    projectChip.className = 'op-share-chip';
    projectChip.hidden = true;
    projectPanel.appendChild(projectChip);

    const projectInput = document.createElement('input');
    projectInput.type = 'text';
    projectInput.className = 'op-modal-input';
    projectInput.placeholder = 'Buscá el proyecto por nombre…';
    projectPanel.appendChild(projectInput);

    const projectResults = document.createElement('div');
    projectResults.className = 'op-share-results';
    projectResults.hidden = true;
    projectPanel.appendChild(projectResults);

    /** ProjectResponse expone projectName, no name. */
    function projectLabel(p) {
      return p.projectName || ('Proyecto #' + p.id);
    }

    function setSelectedProject(project) {
      selectedProject = project;
      if (!project) { projectChip.hidden = true; return; }
      projectChip.hidden = false;
      projectChip.innerHTML = '';
      const label = document.createElement('span');
      label.textContent = projectLabel(project) + ' · ID ' + project.id;
      const clear = document.createElement('button');
      clear.type = 'button';
      clear.className = 'op-share-chip-clear';
      clear.innerHTML = ICONS.close;
      clear.onclick = function () { setSelectedProject(null); projectInput.value = ''; };
      projectChip.appendChild(label);
      projectChip.appendChild(clear);
      projectInput.value = '';
      projectResults.hidden = true;
    }

    // Lista precargada: el desplegable debe poder mostrarse sin que el usuario
    // escriba nada, y el filtrado local responde sin esperar al servidor.
    let allProjects = null;

    function ensureProjects() {
      if (allProjects) return Promise.resolve(allProjects);
      return listProjectsApi().then(function (list) {
        allProjects = list || [];
        return allProjects;
      });
    }

    function renderProjectResults(projects) {
      projectResults.innerHTML = '';
      if (!projects || projects.length === 0) {
        const empty = document.createElement('div');
        empty.className = 'op-share-result-empty';
        empty.textContent = 'No hay proyectos que coincidan';
        projectResults.appendChild(empty);
        projectResults.hidden = false;
        return;
      }
      projects.forEach(function (p) {
        const item = document.createElement('button');
        item.type = 'button';
        item.className = 'op-share-result';
        const main = document.createElement('strong');
        main.textContent = projectLabel(p);
        // El tag separa los aplicativos reales de las API keys creadas para una
        // prueba y nunca usadas, que en la lista se ven exactamente igual.
        if (p.activeConsumer) {
          const tag = document.createElement('span');
          tag.className = 'op-share-tag-active';
          tag.textContent = 'Vinculado';
          main.appendChild(tag);
        }
        const sub = document.createElement('span');
        sub.textContent = 'ID ' + p.id;
        item.appendChild(main);
        item.appendChild(sub);
        item.onclick = function () { setSelectedProject(p); };
        projectResults.appendChild(item);
      });
      projectResults.hidden = false;
    }

    projectInput.onfocus = function () {
      if (!projectInput.value.trim()) {
        ensureProjects().then(renderProjectResults).catch(function () { projectResults.hidden = true; });
      }
    };

    let projectSearchTimer = null;
    function filterLocally(term) {
      const needle = term.toLowerCase();
      return (allProjects || []).filter(function (p) {
        return projectLabel(p).toLowerCase().indexOf(needle) !== -1;
      });
    }

    projectInput.oninput = function () {
      clearTimeout(projectSearchTimer);
      const term = projectInput.value.trim();
      if (!term) {
        ensureProjects().then(renderProjectResults).catch(function () { projectResults.hidden = true; });
        return;
      }
      // Respuesta inmediata sobre lo ya cargado, y luego el servidor afina. Esperar
      // 300ms para ver el primer resultado hace que el campo se sienta trabado.
      if (allProjects) renderProjectResults(filterLocally(term));

      projectSearchTimer = setTimeout(function () {
        searchProjectsApi(term)
          .then(function (found) {
            if (found && found.length > 0) renderProjectResults(found);
            else renderProjectResults(filterLocally(term));
          })
          .catch(function () { renderProjectResults(filterLocally(term)); });
      }, 300);
    };

    const projectLevel = buildLevelSelect();
    projectPanel.appendChild(projectLevel);

    const projectNotes = buildNotesField();
    projectPanel.appendChild(projectNotes.wrap);

    const projectShareBtn = document.createElement('button');
    projectShareBtn.type = 'button';
    projectShareBtn.className = 'op-btn-primary';
    projectShareBtn.style.width = '100%';
    projectShareBtn.textContent = 'Compartir con proyecto';
    projectShareBtn.onclick = function () {
      if (!selectedProject) { toast('Elegí un proyecto de la lista', 'error'); return; }
      projectShareBtn.disabled = true;
      shareResourceApi({
        resourceType: resourceType,
        resourceId: resourceId,
        targetType: 'PROJECT',
        targetApiKeyId: selectedProject.id,
        permissionLevel: projectLevel.value,
        notes: projectNotes.value(),
      }).then(function () {
        toast('Compartido con ' + projectLabel(selectedProject), 'success');
        setSelectedProject(null);
        projectNotes.clear();
        reloadPerms();
      }).catch(function (err) {
        toast('Error al compartir: ' + err.message, 'error');
      }).finally(function () { projectShareBtn.disabled = false; });
    };
    projectPanel.appendChild(projectShareBtn);

    shell.body.appendChild(userPanel);
    shell.body.appendChild(projectPanel);

    tabUser.onclick = function () {
      tabUser.classList.add('op-active');
      tabProject.classList.remove('op-active');
      userPanel.style.display = '';
      projectPanel.style.display = 'none';
    };
    tabProject.onclick = function () {
      tabProject.classList.add('op-active');
      tabUser.classList.remove('op-active');
      projectPanel.style.display = '';
      userPanel.style.display = 'none';
      // La lista se despliega al entrar a la pestaña: obligar a tipear para ver qué
      // proyectos existen es pedirle al usuario que adivine el nombre exacto.
      if (!selectedProject) {
        ensureProjects().then(renderProjectResults).catch(function () { projectResults.hidden = true; });
      }
    };

    // ── Current permissions ──
    const permsTitle = document.createElement('div');
    permsTitle.className = 'op-share-perms-title';
    permsTitle.textContent = 'Compartido con';
    shell.body.appendChild(permsTitle);
    const permsList = document.createElement('div');
    permsList.className = 'op-share-perms';
    shell.body.appendChild(permsList);

    function reloadPerms() {
      permsList.innerHTML = '<div class="op-share-perms-empty">Cargando…</div>';
      listResourcePermissionsApi(resourceType, resourceId).then(function (perms) {
        permsList.innerHTML = '';
        if (perms.length === 0) {
          permsList.innerHTML = '<div class="op-share-perms-empty">Sin permisos asignados: visible para todos en el proyecto.</div>';
          return;
        }
        perms.forEach(function (p) {
          const row = document.createElement('div');
          row.className = 'op-share-perm-row';
          const who = p.targetType === 'USER'
            ? ('Usuario ' + (p.targetUserId || ''))
            : ('Proyecto ' + (p.targetApiKeyId || ''));
          const info = document.createElement('span');
          info.className = 'op-share-perm-info';
          info.innerHTML = '<strong>' + escapeHtml(who) + '</strong>';
          // La observación explica POR QUÉ se compartió, que es lo que se olvida
          // primero. Mostrarla acá evita tener que reconstruirlo de memoria.
          if (p.notes) {
            const note = document.createElement('em');
            note.className = 'op-share-perm-note';
            note.textContent = p.notes;
            note.title = p.notes;
            info.appendChild(note);
          }

          const actionsWrap = document.createElement('div');
          actionsWrap.style.display = 'flex';
          actionsWrap.style.alignItems = 'center';
          actionsWrap.style.gap = '8px';

          const selectEl = document.createElement('select');
          selectEl.className = 'op-share-perm-select';
          selectEl.style.fontSize = '0.85em';
          selectEl.style.padding = '2px 4px';
          selectEl.style.borderRadius = 'var(--op-radius-sm)';
          selectEl.style.border = '1px solid var(--op-border)';
          selectEl.style.background = 'var(--op-bg-card)';
          selectEl.style.color = 'var(--op-text)';

          const optView = document.createElement('option');
          optView.value = 'VIEW';
          optView.textContent = 'Ver';
          optView.selected = p.permissionLevel === 'VIEW';
          selectEl.appendChild(optView);

          const optDownload = document.createElement('option');
          optDownload.value = 'DOWNLOAD';
          optDownload.textContent = 'Descargar';
          optDownload.selected = p.permissionLevel === 'DOWNLOAD';
          selectEl.appendChild(optDownload);

          const optEdit = document.createElement('option');
          optEdit.value = 'EDIT';
          optEdit.textContent = 'Editar';
          optEdit.selected = p.permissionLevel === 'EDIT';
          selectEl.appendChild(optEdit);

          selectEl.onchange = function () {
            selectEl.disabled = true;
            shareResourceApi({
              resourceType: p.resourceType,
              resourceId: p.resourceId,
              targetType: p.targetType,
              targetUserId: p.targetUserId,
              targetApiKeyId: p.targetApiKeyId,
              permissionLevel: selectEl.value
            }).then(function () {
              toast('Permiso actualizado', 'success');
              reloadPerms();
            }).catch(function (err) {
              toast('Error al actualizar: ' + err.message, 'error');
              selectEl.disabled = false;
              reloadPerms();
            });
          };

          const del = document.createElement('button');
          del.type = 'button';
          del.className = 'op-share-perm-del';
          del.title = 'Revocar';
          del.innerHTML = ICONS.trash;
          del.onclick = function () {
            del.disabled = true;
            revokePermissionApi(p.id).then(function () {
              reloadPerms();
            }).catch(function (err) {
              toast('Error al revocar: ' + err.message, 'error');
              del.disabled = false;
            });
          };

          actionsWrap.appendChild(selectEl);
          actionsWrap.appendChild(del);

          row.appendChild(info);
          row.appendChild(actionsWrap);
          permsList.appendChild(row);
        });
      }).catch(function (err) {
        permsList.innerHTML = '<div class="op-share-perms-empty">Error: ' + escapeHtml(err.message) + '</div>';
      });
    }
    reloadPerms();

    const closeBtn = document.createElement('button');
    closeBtn.type = 'button';
    closeBtn.className = 'op-btn-secondary';
    closeBtn.textContent = 'Cerrar';
    closeBtn.onclick = function () {
      closeModal();
      // Refresh the listing so the "restricted" indicator reflects any change.
      if (state.section === 'shared' && !state.searchActive) {
        if (state.sharedView === 'withme') loadSharedWithMe(); else loadFiles();
      }
    };
    shell.ft.appendChild(closeBtn);

    userInput.focus();
  }

  // ── Folder picker modal (for "Mover" file action) ───────────────────────
  // The backend only exposes GET /api/folders?parentId=... (direct children),
  // so the picker is a tiny self-contained navigator: it lists the current
  // level's sub-folders and lets the user drill down, mirroring the main
  // breadcrumb but scoped to this modal only.
  function showMovePicker(file) {
    const shell = buildModalShell('Mover "' + file.originalFileName + '"');
    shell.box.classList.add('op-modal--picker');

    let pickerScope = getCurrentScope();

    const scopeSelector = document.createElement('div');
    scopeSelector.className = 'op-picker-scope-selector';
    scopeSelector.style.display = 'flex';
    scopeSelector.style.gap = '8px';
    scopeSelector.style.marginBottom = 'var(--op-space-3)';

    const btnPrivate = document.createElement('button');
    btnPrivate.type = 'button';
    btnPrivate.className = 'op-btn-secondary op-picker-scope-btn';
    btnPrivate.textContent = 'Mis archivos';
    btnPrivate.onclick = function () {
      if (pickerScope === 'private') return;
      pickerScope = 'private';
      updateScopeButtons();
      path = [];
      targetId = null;
      navigate();
    };

    const btnShared = document.createElement('button');
    btnShared.type = 'button';
    btnShared.className = 'op-btn-secondary op-picker-scope-btn';
    btnShared.textContent = 'Compartidos';
    btnShared.onclick = function () {
      if (pickerScope === 'shared') return;
      pickerScope = 'shared';
      updateScopeButtons();
      path = [];
      targetId = null;
      navigate();
    };

    function updateScopeButtons() {
      btnPrivate.classList.toggle('op-btn-primary', pickerScope === 'private');
      btnPrivate.classList.toggle('op-btn-secondary', pickerScope !== 'private');
      btnShared.classList.toggle('op-btn-primary', pickerScope === 'shared');
      btnShared.classList.toggle('op-btn-secondary', pickerScope !== 'shared');
    }

    scopeSelector.appendChild(btnPrivate);
    scopeSelector.appendChild(btnShared);
    shell.body.appendChild(scopeSelector);
    updateScopeButtons();

    const pathBar = document.createElement('div');
    pathBar.className = 'op-picker-path';
    shell.body.appendChild(pathBar);

    const list = document.createElement('ul');
    list.className = 'op-picker-list';
    shell.body.appendChild(list);

    const cancelBtn = document.createElement('button');
    cancelBtn.type = 'button';
    cancelBtn.className = 'op-btn-secondary';
    cancelBtn.textContent = 'Cancelar';
    cancelBtn.onclick = closeModal;
    const moveBtn = document.createElement('button');
    moveBtn.type = 'button';
    moveBtn.className = 'op-btn-primary';
    moveBtn.textContent = 'Mover aquí';
    shell.ft.appendChild(cancelBtn);
    shell.ft.appendChild(moveBtn);

    let path = []; // [{id, name}] from root, exclusive of root itself
    let targetId = null; // null = root

    function renderPath() {
      pathBar.innerHTML = '';
      const rootBtn = document.createElement('button');
      rootBtn.type = 'button';
      rootBtn.textContent = 'Raíz';
      if (path.length === 0) rootBtn.className = 'op-picker-current';
      rootBtn.onclick = function () { path = []; targetId = null; navigate(); };
      pathBar.appendChild(rootBtn);
      path.forEach(function (crumb, idx) {
        const sep = document.createElement('span');
        sep.textContent = '›';
        pathBar.appendChild(sep);
        const btn = document.createElement('button');
        btn.type = 'button';
        btn.textContent = crumb.name;
        const isCurrent = idx === path.length - 1;
        if (isCurrent) btn.className = 'op-picker-current';
        btn.onclick = function () { path = path.slice(0, idx + 1); targetId = crumb.id; navigate(); };
        pathBar.appendChild(btn);
      });
      moveBtn.disabled = targetId === (file.folderId || null) && pickerScope === getCurrentScope();
    }

    function navigate() {
      renderPath();
      list.innerHTML = '<li class="op-picker-empty">Cargando…</li>';
      listFolders(targetId, pickerScope).then(function (folders) {
        list.innerHTML = '';
        const usable = folders.filter(function (f) { return f.id !== file.folderId || targetId !== (file.folderId || null); });
        if (usable.length === 0) {
          list.innerHTML = '<li class="op-picker-empty">No hay subcarpetas acá.</li>';
          return;
        }
        usable.forEach(function (folder) {
          const li = document.createElement('li');
          const btn = document.createElement('button');
          btn.type = 'button';
          btn.className = 'op-picker-row';
          btn.innerHTML = ICONS.folder + '<span>' + escapeHtml(folder.name) + '</span>';
          btn.onclick = function () { path = path.concat([{ id: folder.id, name: folder.name }]); targetId = folder.id; navigate(); };
          li.appendChild(btn);
          list.appendChild(li);
        });
      }).catch(function () {
        list.innerHTML = '<li class="op-picker-empty">No se pudieron cargar las carpetas.</li>';
      });
    }

    moveBtn.onclick = function () {
      closeModal();
      moveFileApi(file.id, targetId, pickerScope).then(function () {
        toast('Archivo movido', 'success');
        loadFiles();
      }).catch(function (err) {
        toast('Error al mover: ' + err.message, 'error');
      });
    };

    navigate();
  }

  // ── Folder picker (for folder "Mover" action) ───────────────────────────
  // The backend rejects moving a folder into itself or into one of its own
  // descendants (cycle guard in FolderServiceImpl); this picker doesn't
  // duplicate that check client-side, it just surfaces the backend's error.
  function showMoveFolderPicker(folder) {
    const shell = buildModalShell('Mover "' + folder.name + '"');
    shell.box.classList.add('op-modal--picker');

    let pickerScope = getCurrentScope();

    const scopeSelector = document.createElement('div');
    scopeSelector.className = 'op-picker-scope-selector';
    scopeSelector.style.display = 'flex';
    scopeSelector.style.gap = '8px';
    scopeSelector.style.marginBottom = 'var(--op-space-3)';

    const btnPrivate = document.createElement('button');
    btnPrivate.type = 'button';
    btnPrivate.className = 'op-btn-secondary op-picker-scope-btn';
    btnPrivate.textContent = 'Mis archivos';
    btnPrivate.onclick = function () {
      if (pickerScope === 'private') return;
      pickerScope = 'private';
      updateScopeButtons();
      path = [];
      targetId = null;
      navigate();
    };

    const btnShared = document.createElement('button');
    btnShared.type = 'button';
    btnShared.className = 'op-btn-secondary op-picker-scope-btn';
    btnShared.textContent = 'Compartidos';
    btnShared.onclick = function () {
      if (pickerScope === 'shared') return;
      pickerScope = 'shared';
      updateScopeButtons();
      path = [];
      targetId = null;
      navigate();
    };

    function updateScopeButtons() {
      btnPrivate.classList.toggle('op-btn-primary', pickerScope === 'private');
      btnPrivate.classList.toggle('op-btn-secondary', pickerScope !== 'private');
      btnShared.classList.toggle('op-btn-primary', pickerScope === 'shared');
      btnShared.classList.toggle('op-btn-secondary', pickerScope !== 'shared');
    }

    scopeSelector.appendChild(btnPrivate);
    scopeSelector.appendChild(btnShared);
    shell.body.appendChild(scopeSelector);
    updateScopeButtons();

    const pathBar = document.createElement('div');
    pathBar.className = 'op-picker-path';
    shell.body.appendChild(pathBar);

    const list = document.createElement('ul');
    list.className = 'op-picker-list';
    shell.body.appendChild(list);

    const cancelBtn = document.createElement('button');
    cancelBtn.type = 'button';
    cancelBtn.className = 'op-btn-secondary';
    cancelBtn.textContent = 'Cancelar';
    cancelBtn.onclick = closeModal;
    const moveBtn = document.createElement('button');
    moveBtn.type = 'button';
    moveBtn.className = 'op-btn-primary';
    moveBtn.textContent = 'Mover aquí';
    shell.ft.appendChild(cancelBtn);
    shell.ft.appendChild(moveBtn);

    let path = [];
    let targetId = null;

    function renderPath() {
      pathBar.innerHTML = '';
      const rootBtn = document.createElement('button');
      rootBtn.type = 'button';
      rootBtn.textContent = 'Raíz';
      if (path.length === 0) rootBtn.className = 'op-picker-current';
      rootBtn.onclick = function () { path = []; targetId = null; navigate(); };
      pathBar.appendChild(rootBtn);
      path.forEach(function (crumb, idx) {
        const sep = document.createElement('span');
        sep.textContent = '›';
        pathBar.appendChild(sep);
        const btn = document.createElement('button');
        btn.type = 'button';
        btn.textContent = crumb.name;
        const isCurrent = idx === path.length - 1;
        if (isCurrent) btn.className = 'op-picker-current';
        btn.onclick = function () { path = path.slice(0, idx + 1); targetId = crumb.id; navigate(); };
        pathBar.appendChild(btn);
      });
      moveBtn.disabled = (targetId === (folder.parentId || null) && pickerScope === getCurrentScope()) || targetId === folder.id;
    }

    function navigate() {
      renderPath();
      list.innerHTML = '<li class="op-picker-empty">Cargando…</li>';
      listFolders(targetId, pickerScope).then(function (folders) {
        list.innerHTML = '';
        const usable = folders.filter(function (f) { return f.id !== folder.id; });
        if (usable.length === 0) {
          list.innerHTML = '<li class="op-picker-empty">No hay subcarpetas acá.</li>';
          return;
        }
        usable.forEach(function (f) {
          const li = document.createElement('li');
          const btn = document.createElement('button');
          btn.type = 'button';
          btn.className = 'op-picker-row';
          btn.innerHTML = ICONS.folder + '<span>' + escapeHtml(f.name) + '</span>';
          btn.onclick = function () { path = path.concat([{ id: f.id, name: f.name }]); targetId = f.id; navigate(); };
          li.appendChild(btn);
          list.appendChild(li);
        });
      }).catch(function () {
        list.innerHTML = '<li class="op-picker-empty">No se pudieron cargar las carpetas.</li>';
      });
    }

    moveBtn.onclick = function () {
      closeModal();
      moveFolderApi(folder.id, targetId, pickerScope).then(function () {
        toast('Carpeta movida', 'success');
        loadFiles();
      }).catch(function (err) {
        toast('Error al mover: ' + err.message, 'error');
      });
    };

    navigate();
  }

  // ── Context menu ─────────────────────────────────────────────────────────
  let _menuEl = null;
  let _menuOpenBtn = null;
  function closeContextMenu() {
    if (_menuEl) { _menuEl.remove(); _menuEl = null; }
    if (_menuOpenBtn) { _menuOpenBtn.classList.remove('op-menu-open'); _menuOpenBtn = null; }
    document.removeEventListener('click', onDocClickForMenu, true);
  }

  function onDocClickForMenu(e) {
    if (_menuEl && !_menuEl.contains(e.target)) closeContextMenu();
  }

  function menuItem(label, iconSvg, danger, action) {
    return { label: label, icon: iconSvg, danger: !!danger, action: action };
  }

  function fileContextMenuItems(file, trashed) {
    if (trashed) {
      return [
        menuItem('Descargar', ICONS.download, false, function () { downloadSingle(file.id, file.originalFileName); }),
        menuItem('Restaurar', ICONS.restore, false, function () { handleRestore(file); }),
        menuItem('Eliminar permanentemente', ICONS.trash, true, function () { handlePurge(file); }),
      ];
    }
    const items = [
      menuItem('Abrir', ICONS.open, false, function () { openEditor(file.id, file.originalFileName); }),
      menuItem('Descargar', ICONS.download, false, function () { downloadSingle(file.id, file.originalFileName); }),
      menuItem('Renombrar', ICONS.rename, false, function () { handleRename(file); }),
      menuItem('Mover', ICONS.move, false, function () { showMovePicker(file); }),
    ];
    if (state.section === 'files' || state.section === 'shared') {
      items.push(menuItem('Compartir', ICONS.share, false, function () { openShareModal('FILE', file.id, file.originalFileName); }));
    }
    items.push(menuItem('Eliminar', ICONS.trash, true, function () { handleSoftDelete(file); }));
    return items;
  }

  function folderContextMenuItems(folder) {
    const items = [
      menuItem('Abrir', ICONS.open, false, function () { navigateToFolder(folder); }),
      menuItem('Renombrar', ICONS.rename, false, function () { handleRenameFolder(folder); }),
      menuItem('Mover', ICONS.move, false, function () { showMoveFolderPicker(folder); }),
      menuItem('Descargar como ZIP', ICONS.download, false, function () { downloadFolderZip(folder); }),
    ];
    if (state.section === 'files' || state.section === 'shared') {
      items.push(menuItem('Compartir', ICONS.share, false, function () { openShareModal('FOLDER', folder.id, folder.name); }));
    }
    items.push(menuItem('Eliminar', ICONS.trash, true, function () { handleDeleteFolder(folder); }));
    return items;
  }

  function showContextMenu(anchorEl, items) {
    closeContextMenu();
    _menuEl = document.createElement('div');
    const isNewDoc = anchorEl.classList.contains('op-new-btn');
    if (isNewDoc) {
      _menuEl.className = 'op-new-dropdown';
      items.forEach(function (item) {
        const btn = document.createElement('button');
        btn.type = 'button';
        btn.className = 'op-new-dropdown-item';
        btn.innerHTML = item.icon + '<span>' + escapeHtml(item.label) + '</span>';
        btn.onclick = function (e) {
          e.stopPropagation();
          closeContextMenu();
          item.action();
        };
        _menuEl.appendChild(btn);
      });
      anchorEl.parentNode.appendChild(_menuEl);
    } else {
      _menuEl.className = 'op-context-menu';
      items.forEach(function (item) {
        const btn = document.createElement('button');
        btn.type = 'button';
        btn.className = 'op-context-item' + (item.danger ? ' op-context-item-danger' : '');
        btn.innerHTML = item.icon + '<span>' + escapeHtml(item.label) + '</span>';
        btn.onclick = function (e) {
          e.stopPropagation();
          closeContextMenu();
          item.action();
        };
        _menuEl.appendChild(btn);
      });
      document.body.appendChild(_menuEl);
    }
    anchorEl.classList.add('op-menu-open');
    _menuOpenBtn = anchorEl;

    if (!isNewDoc) {
      const rect = anchorEl.getBoundingClientRect();
      const menuRect = _menuEl.getBoundingClientRect();
      let left = rect.right - menuRect.width;
      let top = rect.bottom + 4;
      if (left < 4) left = 4;
      if (left + menuRect.width > window.innerWidth - 4) left = window.innerWidth - menuRect.width - 4;
      if (top + menuRect.height > window.innerHeight - 4) top = rect.top - menuRect.height - 4;
      _menuEl.style.left = left + 'px';
      _menuEl.style.top = top + 'px';
    }

    setTimeout(function () { document.addEventListener('click', onDocClickForMenu, true); }, 0);
  }

  // ── State ────────────────────────────────────────────────────────────────
  const state = {
    section: 'files',       // 'files' | 'recent' | 'trash'
    search: '',
    typeFilter: 'all',       // 'all' | 'word' | 'excel' | 'powerpoint' | 'pdf'
    sortField: 'name',       // 'name' | 'date' | 'size'
    sortDir: 'asc',          // 'asc' | 'desc'
    viewMode: 'grid',        // 'grid' | 'list'
    theme: 'light',          // 'system' | 'light' | 'dark' — in-memory only, resets on reload. Default: light (white).
    selectionMode: false,
    selected: new Set(),
    files: null,             // active files cache (current folder), null = not loaded yet
    allFiles: null,          // flat cache across all folders, backs the 'recent' section
    trash: null,             // trashed files cache, null = not loaded yet
    folders: null,           // sub-folders of the current folder, null = not loaded yet
    filesError: false,
    allFilesError: false,
    trashError: false,
    foldersError: false,
    visibleFiles: [],        // last rendered (filtered/sorted) list, for select-all
    currentFolderId: null,   // null = root
    breadcrumb: [],          // [{id, name}, ...] path from root (exclusive) to current folder
    searchActive: false,     // true = showing backend search results instead of folder browse
    searchFiles: [],         // backend search results (files) for the current term + scope
    searchFolders: [],       // backend search results (folders) for the current term + scope
    searchError: false,
    sharedView: 'space',     // within 'shared' section: 'space' (project's shared) | 'withme' (shared-with-me)
    sharedWithMe: null,      // combined shared-with-me + shared-with-project resources, null = not loaded
    sharedWithMeError: false,
  };

  // ── DOM refs (assigned in init) ─────────────────────────────────────────
  let _container = null;
  let _root = null;
  let _content = null;
  let _actionbar = null;
  let _batchBar = null;
  let _batchCount = null;
  let _batchSelectAll = null;
  let _batchDownloadBtn = null;
  let _batchDeleteBtn = null;
  let _batchRestoreBtn = null;
  let _batchPurgeBtn = null;
  let _batchCancelBtn = null;
  let _searchInput = null;
  let _searchClearBtn = null;
  let _searchDebounce = null;
  let _typeFilterSelect = null;
  let _sortFieldSelect = null;
  let _sortDirBtn = null;
  let _viewButtons = null;
  let _themeToggleBtn = null;
  let _refreshBtn = null;
  let _newBtn = null;
  let _newFolderBtn = null;
  let _breadcrumbBar = null;
  let _uploadInput = null;
  let _selectToggleBtn = null;
  let _uploadProgress = null;
  let _uploadProgressLabel = null;
  let _uploadProgressBar = null;
  let _tabItems = null;
  let _subtabBar = null;
  let _subtabItems = null;

  // ── Data loading ─────────────────────────────────────────────────────────
  function loadFiles() {
    console.log('[OP-DEBUG] loadFiles START scope=', getCurrentScope(), 'section=', state.section, 'folderId=', state.currentFolderId);
    if (state.section === 'files' || state.section === 'shared' || state.section === 'recent') {
      _content.innerHTML = '';
      _content.appendChild(buildLoadingState());
    }

    const filesPromise = listFiles(state.currentFolderId).then(function (files) {
      console.log('[OP-DEBUG] files RESOLVED count=', files.length);
      state.files = files;
      state.filesError = false;
    }).catch(function (err) {
      console.log('[OP-DEBUG] files REJECTED', err && err.message);
      state.filesError = true;
    });

    const foldersPromise = listFolders(state.currentFolderId).then(function (folders) {
      console.log('[OP-DEBUG] folders RESOLVED count=', folders.length);
      state.folders = folders;
      state.foldersError = false;
    }).catch(function (err) {
      console.log('[OP-DEBUG] folders REJECTED', err && err.message);
      state.foldersError = true;
    });

    const allFilesPromise = listAllFiles().then(function (files) {
      console.log('[OP-DEBUG] allFiles RESOLVED count=', files.length);
      state.allFiles = files;
      state.allFilesError = false;
    }).catch(function (err) {
      console.log('[OP-DEBUG] allFiles REJECTED', err && err.message);
      state.allFilesError = true;
    });

    // Render the folder-browse view (files/shared) as soon as ITS OWN data is ready. It must not be
    // gated on allFilesPromise: that call (/api/files/all) only backs the 'recent' section. If it is
    // slow or stalls (observed on the shared scope), waiting on it leaves the browse view stuck on the
    // loading spinner even though files+folders already resolved — a manual re-render (e.g. the sort
    // toggle, which calls renderContent directly) would then appear to "fix" it.
    Promise.all([filesPromise, foldersPromise]).then(function () {
      console.log('[OP-DEBUG] browse promise SETTLED section=', state.section, 'files=', state.files && state.files.length, 'folders=', state.folders && state.folders.length);
      if (state.section === 'files' || state.section === 'shared') renderContent();
    });

    return Promise.all([filesPromise, foldersPromise, allFilesPromise]).then(function () {
      console.log('[OP-DEBUG] all-three promise SETTLED section=', state.section);
      if (state.section === 'recent') renderContent();
    });
  }

  function loadTrash() {
    if (state.section === 'trash') {
      state.trashError = false;
      _content.innerHTML = '';
      _content.appendChild(buildLoadingState());
    }
    return listTrash().then(function (files) {
      state.trash = files;
      state.trashError = false;
    }).catch(function () {
      state.trashError = true;
    }).then(function () {
      if (state.section === 'trash') renderContent();
    });
  }

  // ── "Compartidos conmigo" (shared-with-me + shared-with-project) ─────────
  function loadSharedWithMe() {
    state.sharedWithMeError = false;
    if (state.section === 'shared' && state.sharedView === 'withme') {
      _content.innerHTML = '';
      _content.appendChild(buildLoadingState());
    }
    return Promise.all([sharedWithMeApi(), sharedWithProjectApi()]).then(function (results) {
      const combined = results[0].concat(results[1]);
      const seen = {};
      state.sharedWithMe = combined.filter(function (r) {
        if (seen[r.permissionId]) return false;
        seen[r.permissionId] = true;
        return true;
      });
      state.sharedWithMeError = false;
    }).catch(function () {
      state.sharedWithMeError = true;
    }).then(function () {
      if (state.section === 'shared' && state.sharedView === 'withme') renderContent();
    });
  }

  function switchSharedView(view) {
    if (state.sharedView === view) return;
    state.sharedView = view;
    state.selected.clear();
    state.selectionMode = false;
    updateSelectToggleLabel();
    updateSubtabs();
    if (view === 'withme') {
      loadSharedWithMe();
    } else {
      loadFiles();
    }
  }

  function updateSubtabs() {
    const showSub = state.section === 'shared';
    if (_subtabBar) _subtabBar.hidden = !showSub;
    if (_subtabItems) {
      _subtabItems.forEach(function (btn) {
        btn.classList.toggle('op-active', btn.getAttribute('data-subview') === state.sharedView);
      });
    }
    // "Compartidos conmigo" is a read-only reference list at root: no upload / new-folder actions there.
    if (_actionbar) {
      _actionbar.hidden = state.section === 'trash' || (showSub && state.sharedView === 'withme' && state.currentFolderId === null);
    }
  }

  // ── Search (global, backend-driven) ──────────────────────────────────────
  // Backend search spans the whole scope (all folders), unlike the folder browse which is
  // limited to the current folder. Only available in the 'files' (private) and 'shared' spaces.
  function searchEnabledForSection() {
    return state.section === 'files' || state.section === 'shared';
  }

  function loadSearch() {
    state.searchError = false;
    _content.innerHTML = '';
    _content.appendChild(buildLoadingState());
    const term = state.search.trim();

    const filesPromise = searchFilesApi(term).then(function (files) {
      state.searchFiles = files;
    }).catch(function () {
      state.searchError = true;
    });

    const foldersPromise = searchFoldersApi(term).then(function (folders) {
      state.searchFolders = folders;
    }).catch(function () {
      state.searchError = true;
    });

    return Promise.all([filesPromise, foldersPromise]).then(function () {
      if (state.searchActive) renderContent();
    });
  }

  function enterSearch() {
    if (!searchEnabledForSection()) return;
    const term = state.search.trim();
    if (!term) { exitSearch(); return; }
    state.searchActive = true;
    state.selected.clear();
    loadSearch();
  }

  function exitSearch() {
    const wasActive = state.searchActive;
    state.searchActive = false;
    state.searchFiles = [];
    state.searchFolders = [];
    state.searchError = false;
    if (wasActive) {
      state.selected.clear();
      if (state.section === 'trash') loadTrash(); else loadFiles();
    }
  }

  function updateSearchClearBtn() {
    if (!_searchClearBtn) return;
    _searchClearBtn.hidden = _searchInput.value.length === 0;
  }

  // ── Action handlers ──────────────────────────────────────────────────────
  function handleRename(file) {
    showModal({
      title: 'Renombrar archivo',
      inputValue: file.originalFileName,
      confirmLabel: 'Renombrar',
      onConfirm: function (newName) {
        newName = (newName || '').trim();
        if (!newName) { toast('El nombre no puede estar vacío', 'error'); return; }
        if (newName.length > 255) { toast('El nombre no puede superar 255 caracteres', 'error'); return; }
        renameFile(file.id, newName).then(function () {
          toast('Archivo renombrado', 'success');
          loadFiles();
        }).catch(function (err) {
          toast('Error al renombrar: ' + err.message, 'error');
        });
      },
    });
  }

  function handleSoftDelete(file) {
    showModal({
      title: 'Mover a la papelera',
      message: 'Se moverá "' + file.originalFileName + '" a la papelera.',
      confirmLabel: 'Mover a la papelera',
      onConfirm: function () {
        deleteFile(file.id).then(function () {
          toast('Archivo movido a la papelera', 'success');
          state.selected.delete(file.id);
          loadFiles();
          if (state.trash !== null) loadTrash();
        }).catch(function (err) {
          toast('Error al eliminar: ' + err.message, 'error');
        });
      },
    });
  }

  function handleBatchDelete() {
    const ids = Array.from(state.selected);
    if (ids.length === 0) return;
    showModal({
      title: 'Mover a la papelera',
      message: 'Se moverá' + (ids.length === 1 ? '' : 'n') + ' ' + ids.length + ' archivo' + (ids.length === 1 ? '' : 's') + ' a la papelera.',
      confirmLabel: 'Mover a la papelera',
      onConfirm: function () {
        Promise.allSettled(ids.map(function (id) { return deleteFile(id); })).then(function (results) {
          const success = results.filter(function (r) { return r.status === 'fulfilled'; }).length;
          const failed = results.length - success;
          toast(
            failed === 0 ? success + ' archivo(s) movidos a la papelera' : success + ' movido(s), ' + failed + ' con error',
            failed === 0 ? 'success' : 'info'
          );
          state.selected.clear();
          loadFiles();
          if (state.trash !== null) loadTrash();
        });
      },
    });
  }

  function handleRestore(file) {
    restoreFile(file.id).then(function () {
      toast('Archivo restaurado', 'success');
      state.selected.delete(file.id);
      loadTrash();
      loadFiles();
    }).catch(function (err) {
      toast('Error al restaurar: ' + err.message, 'error');
    });
  }

  function handleBatchRestore() {
    const ids = Array.from(state.selected);
    if (ids.length === 0) return;
    Promise.allSettled(ids.map(function (id) { return restoreFile(id); })).then(function (results) {
      const success = results.filter(function (r) { return r.status === 'fulfilled'; }).length;
      const failed = results.length - success;
      toast(
        failed === 0 ? success + ' archivo(s) restaurados' : success + ' restaurado(s), ' + failed + ' con error',
        failed === 0 ? 'success' : 'info'
      );
      state.selected.clear();
      loadTrash();
      loadFiles();
    });
  }

  function handlePurge(file) {
    showModal({
      title: 'Eliminar permanentemente',
      message: 'Esta acción no se puede deshacer. "' + file.originalFileName + '" se eliminará permanentemente.',
      confirmLabel: 'Eliminar permanentemente',
      danger: true,
      onConfirm: function () {
        purgeFile(file.id).then(function () {
          toast('Archivo eliminado permanentemente', 'success');
          state.selected.delete(file.id);
          loadTrash();
        }).catch(function (err) {
          toast('Error al eliminar: ' + err.message, 'error');
        });
      },
    });
  }

  function handleBatchPurge() {
    const ids = Array.from(state.selected);
    if (ids.length === 0) return;
    showModal({
      title: 'Eliminar permanentemente',
      message: 'Esta acción no se puede deshacer. Se eliminarán permanentemente ' + ids.length + ' archivo(s).',
      confirmLabel: 'Eliminar permanentemente',
      danger: true,
      onConfirm: function () {
        Promise.allSettled(ids.map(function (id) { return purgeFile(id); })).then(function (results) {
          const success = results.filter(function (r) { return r.status === 'fulfilled'; }).length;
          const failed = results.length - success;
          toast(
            failed === 0 ? success + ' archivo(s) eliminados permanentemente' : success + ' eliminado(s), ' + failed + ' con error',
            failed === 0 ? 'success' : 'info'
          );
          state.selected.clear();
          loadTrash();
        });
      },
    });
  }

  function handleBatchDownload() {
    const ids = Array.from(state.selected);
    if (ids.length === 0) return;

    const byId = {};
    (state.files || []).forEach(function (f) { byId[f.id] = f; });
    (state.trash || []).forEach(function (f) { byId[f.id] = f; });

    // Una carpeta seleccionada no se puede pedir por /api/files/{id}: ese endpoint solo
    // conoce archivos y responde 404, lo que dejaba la descarga entera sin resultado.
    const folderById = {};
    (state.folders || []).forEach(function (f) { folderById[f.id] = f; });

    const folderIds = ids.filter(function (id) { return folderById[id]; });
    const fileIds = ids.filter(function (id) { return !folderById[id]; });

    if (folderIds.length === 1 && fileIds.length === 0) {
      // Caso simple: el backend ya arma el ZIP con toda la jerarquía.
      return downloadFolderZip(folderById[folderIds[0]]);
    }

    // Mezcla o varias carpetas: cada carpeta baja como su propio ZIP y los archiv0s
    // sueltos van juntos. Anidar ZIPs dentro de otro ZIP es peor para el usuario final.
    folderIds.forEach(function (id) { downloadFolderZip(folderById[id]); });

    if (fileIds.length > 0) {
      downloadMultiple(fileIds, byId);
    } else if (folderIds.length > 1) {
      toast('Descargando ' + folderIds.length + ' carpetas como ZIP separados', 'info');
    }
  }

  async function downloadMultiple(ids, byId) {
    if (ids.length === 1) {
      const f = byId[ids[0]];
      return downloadSingle(ids[0], f ? f.originalFileName : ('archivo-' + ids[0]));
    }
    toast('Preparando descarga de ' + ids.length + ' archivos…', 'info');
    const usedNames = Object.create(null);
    const entries = [];
    const results = await Promise.allSettled(ids.map(function (id) {
      return downloadFileBytes(id).then(function (buf) { return { id: id, buf: buf }; });
    }));
    let failed = 0;
    results.forEach(function (r, idx) {
      if (r.status === 'fulfilled') {
        const f = byId[ids[idx]];
        const baseName = f ? f.originalFileName : ('archivo-' + ids[idx]);
        entries.push({ name: uniqueZipName(usedNames, baseName), data: new Uint8Array(r.value.buf) });
      } else {
        failed++;
      }
    });
    if (entries.length === 0) {
      toast('No se pudo descargar ningún archivo', 'error');
      return;
    }
    const blob = createZip(entries);
    if (!blob) return;
    triggerBlobDownload(blob, 'archivos.zip');
    toast(
      failed === 0 ? 'Descarga lista (' + entries.length + ' archivos)' : entries.length + ' archivo(s) listos, ' + failed + ' con error',
      failed === 0 ? 'success' : 'info'
    );
  }

  // ── Selection ────────────────────────────────────────────────────────────
  function toggleSelect(id) {
    if (state.selected.has(id)) state.selected.delete(id);
    else { state.selected.add(id); state.selectionMode = true; }
    updateSelectToggleLabel();
    renderContent();
  }

  function toggleSelectAllVisible(files, checked) {
    files.forEach(function (f) {
      if (checked) state.selected.add(f.id);
      else state.selected.delete(f.id);
    });
    if (checked && files.length > 0) state.selectionMode = true;
    updateSelectToggleLabel();
    renderContent();
  }

  function updateSelectToggleLabel() {
    if (!_selectToggleBtn) return;
    _selectToggleBtn.innerHTML = state.selectionMode
      ? ICONS.close + '<span>Cancelar selección</span>'
      : ICONS.check + '<span>Seleccionar</span>';
    _selectToggleBtn.classList.toggle('op-active', state.selectionMode);
    if (_root) _root.classList.toggle('op-selection-mode', state.selectionMode);
  }

  // ── Filter / sort pipeline ───────────────────────────────────────────────
  function deriveRecent(files) {
    return files.slice().sort(function (a, b) {
      return new Date(b.updatedAt || b.createdAt || 0) - new Date(a.updatedAt || a.createdAt || 0);
    }).slice(0, 20);
  }

  // Name matching now happens server-side (see backend /search endpoints); this pipeline only
  // applies the type filter and sort on top of whatever list it receives (folder browse or search results).
  function applyFilters(list) {
    let out = list;
    if (state.typeFilter !== 'all') out = out.filter(function (f) { return getFileKind(f) === state.typeFilter; });
    return sortFiles(out, state.sortField, state.sortDir);
  }

  function applyFolderFilters(folders) {
    const out = (folders || []).slice();
    out.sort(function (a, b) { return (a.name || '').localeCompare(b.name || ''); });
    return out;
  }

  function sortFiles(files, field, dir) {
    const sorted = files.slice().sort(function (a, b) {
      let av, bv;
      if (field === 'name') {
        av = (a.originalFileName || '').toLowerCase();
        bv = (b.originalFileName || '').toLowerCase();
      } else if (field === 'size') {
        av = a.size || 0;
        bv = b.size || 0;
      } else {
        av = new Date(a.updatedAt || a.createdAt || 0).getTime();
        bv = new Date(b.updatedAt || b.createdAt || 0).getTime();
      }
      if (av < bv) return -1;
      if (av > bv) return 1;
      return 0;
    });
    if (dir === 'desc') sorted.reverse();
    return sorted;
  }

  // ── Rendering ────────────────────────────────────────────────────────────
  function buildLoadingState() {
    const wrap = document.createElement('div');
    wrap.className = 'op-loading-state';
    wrap.innerHTML = '<div class="op-spinner"></div><p>Cargando…</p>';
    return wrap;
  }

  function buildErrorState(retryFn) {
    const wrap = document.createElement('div');
    wrap.className = 'op-empty-state';
    wrap.innerHTML =
      '<p class="op-empty-text">No se pudo conectar con el servidor.</p>' +
      '<button type="button" class="op-btn-primary op-retry-btn">Reintentar</button>';
    wrap.querySelector('.op-retry-btn').onclick = function () { retryFn(); };
    return wrap;
  }

  function buildEmptyState() {
    const wrap = document.createElement('div');
    wrap.className = 'op-empty-state';
    wrap.innerHTML =
      EMPTY_ILLUSTRATION_SVG +
      '<p class="op-empty-text">Aún no tienes archivos. Crea uno nuevo o sube uno.</p>' +
      '<div class="op-empty-actions">' +
        '<button type="button" class="op-btn-primary op-empty-new">' + ICONS.plus + '<span>Nuevo documento</span>' + ICONS.chevron + '</button>' +
        '<button type="button" class="op-btn-secondary op-empty-new-folder">' + ICONS.folderPlus + '<span>Nueva carpeta</span></button>' +
        '<button type="button" class="op-btn-secondary op-empty-upload">' + ICONS.upload + '<span>Subir archivo</span></button>' +
      '</div>';
    const emptyNewBtn = wrap.querySelector('.op-empty-new');
    emptyNewBtn.onclick = function (e) { e.stopPropagation(); openNewDocumentMenu(emptyNewBtn); };
    wrap.querySelector('.op-empty-upload').onclick = triggerUpload;
    wrap.querySelector('.op-empty-new-folder').onclick = triggerNewFolder;
    return wrap;
  }

  function buildTrashEmptyState() {
    const wrap = document.createElement('div');
    wrap.className = 'op-empty-state';
    wrap.innerHTML = EMPTY_ILLUSTRATION_SVG + '<p class="op-empty-text">La papelera está vacía.</p>';
    return wrap;
  }

  function buildNoResultsState() {
    const wrap = document.createElement('div');
    wrap.className = 'op-empty-state';
    wrap.innerHTML = EMPTY_ILLUSTRATION_SVG + '<p class="op-empty-text">No encontramos archivos que coincidan con tu búsqueda.</p>';
    return wrap;
  }

  function buildCard(file, trashed) {
    const card = document.createElement('div');
    card.className = 'op-card' + (state.selected.has(file.id) ? ' op-selected' : '');
    card.setAttribute('data-id', file.id);
    card.title = file.originalFileName;

    const kind = getFileKind(file);

    const checkboxWrap = document.createElement('label');
    checkboxWrap.className = 'op-card-check';
    checkboxWrap.onclick = function (e) { e.stopPropagation(); };
    const checkbox = document.createElement('input');
    checkbox.type = 'checkbox';
    checkbox.checked = state.selected.has(file.id);
    checkbox.onchange = function () { toggleSelect(file.id); };
    checkboxWrap.appendChild(checkbox);
    card.appendChild(checkboxWrap);

    const menuBtn = document.createElement('button');
    menuBtn.type = 'button';
    menuBtn.className = 'op-card-menu-btn';
    menuBtn.setAttribute('aria-label', 'Más acciones');
    menuBtn.innerHTML = ICONS.dots;
    menuBtn.onclick = function (e) { e.stopPropagation(); showContextMenu(menuBtn, fileContextMenuItems(file, trashed)); };
    card.appendChild(menuBtn);

    const iconWrap = document.createElement('div');
    iconWrap.className = 'op-card-icon';
    iconWrap.innerHTML = fileIconSvg(kind);
    card.appendChild(iconWrap);

    const name = document.createElement('div');
    name.className = 'op-card-name';
    name.textContent = file.originalFileName;
    card.appendChild(name);

    const meta = document.createElement('div');
    meta.className = 'op-card-meta';
    let metaText = formatBytes(file.size || 0) + ' · ' + formatDate(file.updatedAt || file.createdAt);
    const fileModifier = file.updatedByName || file.createdByName;
    if (state.section === 'shared') {
      if (fileModifier) metaText += ' · Modificado por: ' + fileModifier;
    } else if (fileModifier) {
      metaText += ' por ' + fileModifier;
    }
    meta.textContent = metaText;
    card.appendChild(meta);

    if (file.restricted) {
      card.appendChild(buildRestrictedBadge());
    }

    card.onclick = function () {
      if (state.selectionMode) { toggleSelect(file.id); return; }
      if (!trashed) openEditor(file.id, file.originalFileName);
    };
    card.oncontextmenu = function (e) {
      e.preventDefault();
      showContextMenu(menuBtn, fileContextMenuItems(file, trashed));
    };

    if (!trashed) {
      card.setAttribute('draggable', 'true');
      card.addEventListener('dragstart', function (e) {
        e.dataTransfer.setData('text/op-file-id', String(file.id));
        e.dataTransfer.effectAllowed = 'move';
      });
    }

    return card;
  }

  function buildRestrictedBadge() {
    const badge = document.createElement('div');
    badge.className = 'op-restricted';
    // El icono de personas comunica mejor lo que realmente significa: el recurso tiene
    // destinatarios explícitos. Un candado sugería "bloqueado", que es lo contrario de
    // lo que ve el jefe que acaba de compartirlo.
    badge.title = 'Compartido con usuarios o proyectos específicos. '
      + 'Solo ellos, el creador y los administradores lo ven.';
    badge.innerHTML = ICONS.users + '<span>Compartido</span>';
    return badge;
  }

  function buildFolderCard(folder) {
    const card = document.createElement('div');
    card.className = 'op-card op-folder-card';
    card.setAttribute('data-folder-id', folder.id);
    card.title = folder.name;

    const menuBtn = document.createElement('button');
    menuBtn.type = 'button';
    menuBtn.className = 'op-card-menu-btn';
    menuBtn.setAttribute('aria-label', 'Más acciones');
    menuBtn.innerHTML = ICONS.dots;
    menuBtn.onclick = function (e) { e.stopPropagation(); showContextMenu(menuBtn, folderContextMenuItems(folder)); };
    card.appendChild(menuBtn);

    const iconWrap = document.createElement('div');
    iconWrap.className = 'op-card-icon';
    iconWrap.innerHTML = FOLDER_ICON_SVG;
    card.appendChild(iconWrap);

    const name = document.createElement('div');
    name.className = 'op-card-name';
    name.textContent = folder.name;
    card.appendChild(name);

    const meta = document.createElement('div');
    meta.className = 'op-card-meta';
    if (state.section === 'shared') {
      const modifier = folder.updatedByName || folder.createdByName;
      meta.textContent = 'Carpeta' + (modifier ? (' · Modificado por: ' + modifier) : '');
    } else {
      meta.textContent = 'Carpeta' + (folder.createdByName ? (' por ' + folder.createdByName) : '');
    }
    card.appendChild(meta);

    if (folder.restricted) {
      card.appendChild(buildRestrictedBadge());
    }

    card.onclick = function () { navigateToFolder(folder); };
    card.oncontextmenu = function (e) {
      e.preventDefault();
      showContextMenu(menuBtn, folderContextMenuItems(folder));
    };
    wireFolderDropTarget(card, folder.id);

    return card;
  }

  function buildGrid(files, trashed, folders) {
    const grid = document.createElement('div');
    grid.className = 'op-grid';
    (folders || []).forEach(function (folder) { grid.appendChild(buildFolderCard(folder)); });
    files.forEach(function (file) { grid.appendChild(buildCard(file, trashed)); });
    return grid;
  }

  function buildListRow(file, trashed) {
    const tr = document.createElement('tr');
    tr.className = 'op-row' + (state.selected.has(file.id) ? ' op-selected' : '');
    tr.setAttribute('data-id', file.id);

    const tdCheck = document.createElement('td');
    tdCheck.className = 'op-td-check';
    const checkbox = document.createElement('input');
    checkbox.type = 'checkbox';
    checkbox.checked = state.selected.has(file.id);
    checkbox.onclick = function (e) { e.stopPropagation(); toggleSelect(file.id); };
    tdCheck.appendChild(checkbox);
    tr.appendChild(tdCheck);

    const kind = getFileKind(file);
    const tdIcon = document.createElement('td');
    tdIcon.className = 'op-td-icon';
    tdIcon.innerHTML = '<span class="op-icon-sm">' + fileIconSvg(kind) + '</span>';
    tr.appendChild(tdIcon);

    const tdName = document.createElement('td');
    tdName.className = 'op-td-name';
    tdName.title = file.originalFileName;
    tdName.textContent = file.originalFileName;
    tr.appendChild(tdName);

    const tdType = document.createElement('td');
    tdType.textContent = kindLabel(kind);
    tr.appendChild(tdType);

    const tdSize = document.createElement('td');
    tdSize.textContent = formatBytes(file.size || 0);
    tr.appendChild(tdSize);

    const tdDate = document.createElement('td');
    let dateText = formatDate(file.updatedAt || file.createdAt);
    if (file.updatedByName) {
      dateText += ' (' + file.updatedByName + ')';
    } else if (file.createdByName) {
      dateText += ' (' + file.createdByName + ')';
    }
    tdDate.textContent = dateText;
    tr.appendChild(tdDate);

    const tdActions = document.createElement('td');
    tdActions.className = 'op-td-actions';
    const menuBtn = document.createElement('button');
    menuBtn.type = 'button';
    menuBtn.className = 'op-menu-btn';
    menuBtn.setAttribute('aria-label', 'Más acciones');
    menuBtn.innerHTML = ICONS.dots;
    menuBtn.onclick = function (e) { e.stopPropagation(); showContextMenu(menuBtn, fileContextMenuItems(file, trashed)); };
    tdActions.appendChild(menuBtn);
    tr.appendChild(tdActions);

    tr.onclick = function () {
      if (state.selectionMode) { toggleSelect(file.id); return; }
      if (!trashed) openEditor(file.id, file.originalFileName);
    };
    tr.oncontextmenu = function (e) {
      e.preventDefault();
      showContextMenu(menuBtn, fileContextMenuItems(file, trashed));
    };

    if (!trashed) {
      tr.setAttribute('draggable', 'true');
      tr.addEventListener('dragstart', function (e) {
        e.dataTransfer.setData('text/op-file-id', String(file.id));
        e.dataTransfer.effectAllowed = 'move';
      });
    }

    return tr;
  }

  function buildFolderListRow(folder) {
    const tr = document.createElement('tr');
    tr.className = 'op-row op-folder-row';
    tr.setAttribute('data-folder-id', folder.id);

    const tdCheck = document.createElement('td');
    tdCheck.className = 'op-td-check';
    tr.appendChild(tdCheck);

    const tdIcon = document.createElement('td');
    tdIcon.className = 'op-td-icon';
    tdIcon.innerHTML = '<span class="op-icon-sm">' + FOLDER_ICON_SVG + '</span>';
    tr.appendChild(tdIcon);

    const tdName = document.createElement('td');
    tdName.className = 'op-td-name';
    tdName.title = folder.name;
    tdName.textContent = folder.name;
    tr.appendChild(tdName);

    const tdType = document.createElement('td');
    tdType.textContent = 'Carpeta';
    tr.appendChild(tdType);

    const tdSize = document.createElement('td');
    tr.appendChild(tdSize);

    const tdDate = document.createElement('td');
    let folderDateText = formatDate(folder.updatedAt || folder.createdAt);
    const folderModifier = (state.section === 'shared' ? folder.updatedByName : null) || folder.createdByName;
    if (folderModifier) {
      folderDateText += ' (' + folderModifier + ')';
    }
    tdDate.textContent = folderDateText;
    tr.appendChild(tdDate);

    const tdActions = document.createElement('td');
    tdActions.className = 'op-td-actions';
    const menuBtn = document.createElement('button');
    menuBtn.type = 'button';
    menuBtn.className = 'op-menu-btn';
    menuBtn.setAttribute('aria-label', 'Más acciones');
    menuBtn.innerHTML = ICONS.dots;
    menuBtn.onclick = function (e) { e.stopPropagation(); showContextMenu(menuBtn, folderContextMenuItems(folder)); };
    tdActions.appendChild(menuBtn);
    tr.appendChild(tdActions);

    tr.onclick = function () { navigateToFolder(folder); };
    tr.oncontextmenu = function (e) {
      e.preventDefault();
      showContextMenu(menuBtn, folderContextMenuItems(folder));
    };
    wireFolderDropTarget(tr, folder.id);

    return tr;
  }

  function buildListTable(files, trashed, folders) {
    const wrap = document.createElement('div');
    wrap.className = 'op-table-wrap';
    const table = document.createElement('table');
    table.className = 'op-table';

    const thead = document.createElement('thead');
    thead.innerHTML =
      '<tr>' +
        '<th class="op-th-check"><input type="checkbox" class="op-select-all-inline"></th>' +
        '<th class="op-th-icon"></th>' +
        '<th data-sort="name">Nombre</th>' +
        '<th>Tipo</th>' +
        '<th data-sort="size">Tamaño</th>' +
        '<th data-sort="date">Modificado</th>' +
        '<th class="op-th-actions">Acciones</th>' +
      '</tr>';
    table.appendChild(thead);

    const tbody = document.createElement('tbody');
    (folders || []).forEach(function (folder) { tbody.appendChild(buildFolderListRow(folder)); });
    files.forEach(function (file) { tbody.appendChild(buildListRow(file, trashed)); });
    table.appendChild(tbody);
    wrap.appendChild(table);

    thead.querySelectorAll('[data-sort]').forEach(function (th) {
      const field = th.getAttribute('data-sort');
      if (field === state.sortField) {
        th.classList.add('op-th-active');
        th.textContent += state.sortDir === 'asc' ? ' ▲' : ' ▼';
      }
      th.onclick = function () {
        if (state.sortField === field) {
          state.sortDir = state.sortDir === 'asc' ? 'desc' : 'asc';
        } else {
          state.sortField = field;
          state.sortDir = 'asc';
        }
        if (_sortFieldSelect) _sortFieldSelect.value = state.sortField;
        if (_sortDirBtn) _sortDirBtn.innerHTML = state.sortDir === 'asc' ? ICONS.chevron.replace('viewBox="0 0 24 24"', 'viewBox="0 0 24 24" style="transform:rotate(-90deg)"') : ICONS.chevron.replace('viewBox="0 0 24 24"', 'viewBox="0 0 24 24" style="transform:rotate(90deg)"');
        renderContent();
      };
    });

    const selectAllInline = thead.querySelector('.op-select-all-inline');
    selectAllInline.checked = files.length > 0 && files.every(function (f) { return state.selected.has(f.id); });
    selectAllInline.onchange = function (e) { toggleSelectAllVisible(files, e.target.checked); };

    return wrap;
  }

  function renderList(base, trashed, folders) {
    const filteredFolders = applyFolderFilters(folders || []);

    if (base.length === 0 && filteredFolders.length === 0) {
      state.visibleFiles = [];
      _content.appendChild(trashed ? buildTrashEmptyState() : buildEmptyState());
      updateBatchBar();
      return;
    }
    const filtered = applyFilters(base);
    state.visibleFiles = filtered;
    if (filtered.length === 0 && filteredFolders.length === 0) {
      _content.appendChild(buildNoResultsState());
      updateBatchBar();
      return;
    }
    _content.appendChild(
      state.viewMode === 'grid'
        ? buildGrid(filtered, trashed, filteredFolders)
        : buildListTable(filtered, trashed, filteredFolders)
    );
    updateBatchBar();
  }

  function renderContent() {
    console.log('[OP-DEBUG] renderContent section=', state.section, 'searchActive=', state.searchActive, 'files=', state.files && state.files.length, 'folders=', state.folders && state.folders.length, 'filesError=', state.filesError, 'foldersError=', state.foldersError);
    closeContextMenu();
    _content.innerHTML = '';
    renderBreadcrumb();

    if (state.searchActive) {
      if (state.searchError) {
        state.visibleFiles = [];
        _content.appendChild(buildErrorState(loadSearch));
        updateBatchBar();
        return;
      }
      renderList(state.searchFiles || [], false, state.searchFolders || []);
      return;
    }

    if (state.section === 'shared' && state.sharedView === 'withme' && state.currentFolderId === null) {
      renderSharedWithMe();
      return;
    }

    if (state.section === 'trash') {
      if (state.trashError) {
        state.visibleFiles = [];
        _content.appendChild(buildErrorState(loadTrash));
        updateBatchBar();
        return;
      }
      if (state.trash === null) {
        state.visibleFiles = [];
        _content.appendChild(buildLoadingState());
        updateBatchBar();
        return;
      }
      renderList(state.trash, true, []);
      return;
    }

    if (state.section === 'recent') {
      if (state.allFilesError) {
        state.visibleFiles = [];
        _content.appendChild(buildErrorState(loadFiles));
        updateBatchBar();
        return;
      }
      if (state.allFiles === null) {
        state.visibleFiles = [];
        _content.appendChild(buildLoadingState());
        updateBatchBar();
        return;
      }
      renderList(deriveRecent(state.allFiles), false, []);
      return;
    }

    // 'files' section — folder-aware browsing
    if (state.filesError || state.foldersError) {
      state.visibleFiles = [];
      _content.appendChild(buildErrorState(loadFiles));
      updateBatchBar();
      return;
    }
    if (state.files === null || state.folders === null) {
      state.visibleFiles = [];
      _content.appendChild(buildLoadingState());
      updateBatchBar();
      return;
    }
    renderList(state.files, false, state.folders);
  }

  function renderSharedWithMe() {
    if (state.sharedWithMeError) {
      state.visibleFiles = [];
      _content.appendChild(buildErrorState(loadSharedWithMe));
      updateBatchBar();
      return;
    }
    if (state.sharedWithMe === null) {
      state.visibleFiles = [];
      _content.appendChild(buildLoadingState());
      updateBatchBar();
      return;
    }
    state.visibleFiles = [];
    if (state.sharedWithMe.length === 0) {
      const empty = document.createElement('div');
      empty.className = 'op-empty-state';
      empty.innerHTML = ICONS.share + '<p>Nadie compartió recursos con vos todavía.</p>';
      _content.appendChild(empty);
      updateBatchBar();
      return;
    }
    const grid = document.createElement('div');
    grid.className = 'op-grid';
    state.sharedWithMe.forEach(function (r) { grid.appendChild(buildSharedWithMeCard(r)); });
    _content.appendChild(grid);
    updateBatchBar();
  }

  function buildSharedWithMeCard(resource) {
    const card = document.createElement('div');
    card.className = 'op-card';
    card.title = resource.resourceName;

    const iconWrap = document.createElement('div');
    iconWrap.className = 'op-card-icon';
    iconWrap.innerHTML = resource.resourceType === 'FOLDER'
      ? FOLDER_ICON_SVG
      : fileIconSvg(getFileKind({ originalFileName: resource.resourceName, mimeType: resource.mimeType }));
    card.appendChild(iconWrap);

    const name = document.createElement('div');
    name.className = 'op-card-name';
    name.textContent = resource.resourceName;
    card.appendChild(name);

    const meta = document.createElement('div');
    meta.className = 'op-card-meta';
    meta.textContent = shareLevelLabel(resource.permissionLevel)
      + (resource.resourceType === 'FOLDER' ? ' · Carpeta' : '');
    card.appendChild(meta);

    const from = document.createElement('div');
    from.className = 'op-withme-from';
    from.textContent = 'Compartido por: ' + (resource.sharedByName || resource.sharedByUserId || '—');
    card.appendChild(from);

    // Cuándo llegó: sin esto, veinte recursos compartidos son indistinguibles entre sí.
    if (resource.createdAt) {
      const when = document.createElement('div');
      when.className = 'op-withme-when';
      when.textContent = 'Compartido: ' + formatDate(resource.createdAt);
      card.appendChild(when);
    }

    // De qué proyecto viene. Relevante en compartición cross-project, donde el
    // recurso pertenece a un aplicativo distinto del que estoy usando.
    if (resource.sourceProjectName) {
      const project = document.createElement('div');
      project.className = 'op-withme-project';
      project.textContent = resource.sourceProjectName;
      project.title = 'Proyecto de origen: ' + resource.sourceProjectName;
      card.appendChild(project);
    }

    // La observación de quien compartió. Se recorta a dos líneas y queda completa
    // en el title: una nota larga no debe deformar la grilla de tarjetas.
    if (resource.notes) {
      const note = document.createElement('div');
      note.className = 'op-withme-note';
      note.textContent = resource.notes;
      note.title = resource.notes;
      card.appendChild(note);
    }

    // Files open in the editor. Cross-project resources may not resolve (the editor is scoped to the
    // caller's project); that surfaces as a toast from openEditor, which is acceptable for now.
    if (resource.resourceType === 'FILE') {
      card.style.cursor = 'pointer';
      card.onclick = function () { openEditor(resource.resourceId, resource.resourceName); };
    } else if (resource.resourceType === 'FOLDER') {
      card.style.cursor = 'pointer';
      card.onclick = function () {
        navigateToFolder({ id: resource.resourceId, name: resource.resourceName });
      };
    }
    return card;
  }

  // ── Folder navigation ────────────────────────────────────────────────────
  function resetSelectionForNavigation() {
    state.selected.clear();
    state.selectionMode = false;
    updateSelectToggleLabel();
    state.files = null;
    state.folders = null;
  }

  function navigateToFolder(folder) {
    state.currentFolderId = folder.id;
    state.breadcrumb.push({ id: folder.id, name: folder.name });
    resetSelectionForNavigation();
    loadFiles();
  }

  function navigateToBreadcrumb(index) {
    if (index < 0) {
      state.currentFolderId = null;
      state.breadcrumb = [];
    } else {
      state.breadcrumb = state.breadcrumb.slice(0, index + 1);
      state.currentFolderId = state.breadcrumb[index].id;
    }
    resetSelectionForNavigation();
    loadFiles();
  }

  function renderBreadcrumb() {
    if (!_breadcrumbBar) return;
    // Search results span all folders, so the folder path is meaningless while a search is active.
    if (state.searchActive) { _breadcrumbBar.hidden = true; return; }
    // "Compartidos conmigo" is a flat reference list with no folder path when at the root.
    if (state.section === 'shared' && state.sharedView === 'withme' && state.currentFolderId === null) { _breadcrumbBar.hidden = true; return; }
    if (state.section !== 'files' && state.section !== 'shared') { _breadcrumbBar.hidden = true; return; }
    _breadcrumbBar.hidden = false;
    _breadcrumbBar.innerHTML = '';

    const rootBtn = document.createElement('button');
    rootBtn.type = 'button';
    rootBtn.className = 'op-breadcrumb-item' + (state.breadcrumb.length === 0 ? ' op-breadcrumb-current' : '');
    rootBtn.innerHTML = ICONS.home + '<span>Raíz</span>';
    rootBtn.onclick = function () { navigateToBreadcrumb(-1); };
    if (state.currentFolderId !== null) wireFolderDropTarget(rootBtn, null);
    _breadcrumbBar.appendChild(rootBtn);

    state.breadcrumb.forEach(function (crumb, idx) {
      const sep = document.createElement('span');
      sep.className = 'op-breadcrumb-sep';
      sep.textContent = '›';
      _breadcrumbBar.appendChild(sep);

      const isCurrent = idx === state.breadcrumb.length - 1;
      const btn = document.createElement('button');
      btn.type = 'button';
      btn.className = 'op-breadcrumb-item' + (isCurrent ? ' op-breadcrumb-current' : '');
      btn.textContent = crumb.name;
      btn.onclick = function () { navigateToBreadcrumb(idx); };
      if (!isCurrent) wireFolderDropTarget(btn, crumb.id);
      _breadcrumbBar.appendChild(btn);
    });
  }

  // ── Drag & drop (move files into folders) ───────────────────────────────
  function wireFolderDropTarget(el, destinationFolderId) {
    el.addEventListener('dragover', function (e) {
      e.preventDefault();
      el.classList.add('op-drop-target');
    });
    el.addEventListener('dragleave', function () {
      el.classList.remove('op-drop-target');
    });
    el.addEventListener('drop', function (e) {
      e.preventDefault();
      el.classList.remove('op-drop-target');
      const fileId = e.dataTransfer.getData('text/op-file-id');
      if (!fileId) return;
      handleMoveFile(Number(fileId), destinationFolderId);
    });
  }

  function handleMoveFile(fileId, folderId) {
    moveFileApi(fileId, folderId).then(function () {
      toast('Archivo movido', 'success');
      loadFiles();
    }).catch(function (err) {
      toast('Error al mover: ' + err.message, 'error');
    });
  }

  function triggerNewFolder() {
    showModal({
      title: 'Nueva carpeta',
      inputValue: '',
      confirmLabel: 'Crear',
      onConfirm: function (name) {
        name = (name || '').trim();
        if (!name) { toast('El nombre no puede estar vacío', 'error'); return; }
        if (name.length > 255) { toast('El nombre no puede superar 255 caracteres', 'error'); return; }
        createFolderApi(name, state.currentFolderId).then(function () {
          toast('Carpeta creada', 'success');
          loadFiles();
        }).catch(function (err) {
          toast('Error al crear la carpeta: ' + err.message, 'error');
        });
      },
    });
  }

  function handleRenameFolder(folder) {
    showModal({
      title: 'Renombrar carpeta',
      inputValue: folder.name,
      confirmLabel: 'Renombrar',
      onConfirm: function (newName) {
        newName = (newName || '').trim();
        if (!newName) { toast('El nombre no puede estar vacío', 'error'); return; }
        if (newName.length > 255) { toast('El nombre no puede superar 255 caracteres', 'error'); return; }
        renameFolderApi(folder.id, newName).then(function () {
          toast('Carpeta renombrada', 'success');
          loadFiles();
        }).catch(function (err) {
          toast('Error al renombrar la carpeta: ' + err.message, 'error');
        });
      },
    });
  }

  function handleDeleteFolder(folder) {
    showModal({
      title: 'Eliminar carpeta',
      message: 'Se eliminará "' + folder.name + '" y sus archivos se moverán a la papelera. Esta acción no se puede deshacer.',
      confirmLabel: 'Eliminar carpeta',
      danger: true,
      onConfirm: function () {
        deleteFolderApi(folder.id).then(function () {
          toast('Carpeta eliminada', 'success');
          loadFiles();
          if (state.trash !== null) loadTrash();
        }).catch(function (err) {
          toast('Error al eliminar la carpeta: ' + err.message, 'error');
        });
      },
    });
  }

  function updateBatchBar() {
    const count = state.selected.size;
    if (count === 0) { _batchBar.hidden = true; return; }
    _batchBar.hidden = false;
    _batchCount.textContent = count + (count === 1 ? ' seleccionado' : ' seleccionados');
    const visible = state.visibleFiles || [];
    _batchSelectAll.checked = visible.length > 0 && visible.every(function (f) { return state.selected.has(f.id); });
    const trashSection = state.section === 'trash';
    _batchDeleteBtn.hidden = trashSection;
    _batchRestoreBtn.hidden = !trashSection;
    _batchPurgeBtn.hidden = !trashSection;
  }

  // ── Shell ────────────────────────────────────────────────────────────────
  const SHELL_HTML =
    '<div class="op-widget">' +
      '<div class="op-main">' +
        '<div class="op-tabs" role="tablist" aria-label="Secciones">' +
          '<button type="button" class="op-tab op-active" data-section="files" role="tab">Mis archivos</button>' +
          '<button type="button" class="op-tab" data-section="shared" role="tab">Compartidos</button>' +
          '<button type="button" class="op-tab" data-section="recent" role="tab">Recientes</button>' +
          '<button type="button" class="op-tab" data-section="trash" role="tab">Papelera</button>' +
        '</div>' +
        '<div class="op-subtabs" hidden>' +
          '<button type="button" class="op-subtab op-active" data-subview="space">Del espacio</button>' +
          '<button type="button" class="op-subtab" data-subview="withme">Compartidos conmigo</button>' +
        '</div>' +
        '<header class="op-toolbar">' +
          '<div class="op-search-wrap">' +
            '<span class="op-search-icon">' + ICONS.search + '</span>' +
            '<input type="text" class="op-search" placeholder="Buscar archivos…" aria-label="Buscar archivos">' +
            '<button type="button" class="op-search-clear" aria-label="Limpiar búsqueda" title="Limpiar búsqueda" hidden>' + ICONS.close + '</button>' +
          '</div>' +
          '<select class="op-type-filter" aria-label="Filtrar por tipo">' +
            '<option value="all">Todos los tipos</option>' +
            '<option value="word">Word</option>' +
            '<option value="excel">Excel</option>' +
            '<option value="powerpoint">PowerPoint</option>' +
            '<option value="pdf">PDF</option>' +
          '</select>' +
          '<div class="op-sort-wrap">' +
            '<select class="op-sort-field" aria-label="Ordenar por">' +
              '<option value="name">Nombre</option>' +
              '<option value="date">Fecha</option>' +
              '<option value="size">Tamaño</option>' +
            '</select>' +
            '<button type="button" class="op-sort-dir" aria-label="Cambiar orden" title="Cambiar orden">' + ICONS.chevron + '</button>' +
          '</div>' +
          '<div class="op-view-toggle" role="group" aria-label="Cambiar vista">' +
            '<button type="button" class="op-view-btn op-active" data-mode="grid" aria-label="Vista de cuadrícula">' + ICONS.grid + '</button>' +
            '<button type="button" class="op-view-btn" data-mode="list" aria-label="Vista de lista">' + ICONS.list + '</button>' +
          '</div>' +
          '<button type="button" class="op-notifications-btn" aria-label="Notificaciones" title="Notificaciones">' + ICONS.bell + '</button>' +
          '<button type="button" class="op-macros-help-btn" aria-label="Ayuda de macros" title="Ayuda de macros: convertir desde Excel VBA">?</button>' +
          '<button type="button" class="op-refresh-btn" aria-label="Actualizar" title="Actualizar">' + ICONS.refresh + '</button>' +
          '<button type="button" class="op-theme-toggle" aria-label="Cambiar tema claro/oscuro" title="Cambiar tema">' + ICONS.moon + '</button>' +
        '</header>' +
        '<div class="op-actionbar">' +
          '<div class="op-new-dropdown-wrapper">' +
            '<button type="button" class="op-btn-primary op-new-btn">' + ICONS.plus + '<span>Nuevo documento</span>' + ICONS.chevron + '</button>' +
          '</div>' +
          '<button type="button" class="op-btn-secondary op-new-folder-btn">' + ICONS.folderPlus + '<span>Nueva carpeta</span></button>' +
          '<label class="op-upload-label">' +
            '<input type="file" class="op-upload-input" accept="' + ACCEPT_ATTR + '" hidden>' +
            '<span class="op-btn-secondary op-upload-btn">' + ICONS.upload + '<span>Subir archivo</span></span>' +
          '</label>' +
          '<button type="button" class="op-select-toggle-btn op-btn-secondary">' + ICONS.check + '<span>Seleccionar</span></button>' +
          '<div class="op-upload-progress" hidden>' +
            '<span class="op-upload-progress-label"></span>' +
            '<div class="op-upload-progress-track"><div class="op-upload-progress-bar" style="width:0%"></div></div>' +
          '</div>' +
        '</div>' +
        '<div class="op-breadcrumb" hidden></div>' +
        '<div class="op-batchbar" hidden>' +
          '<label class="op-batch-selectall"><input type="checkbox" class="op-batch-selectall-cb"><span>Seleccionar todo</span></label>' +
          '<span class="op-batch-count">0 seleccionados</span>' +
          '<div class="op-batch-actions">' +
            '<button type="button" class="op-btn-secondary op-batch-download">' + ICONS.download + '<span>Descargar</span></button>' +
            '<button type="button" class="op-btn-danger op-batch-delete">' + ICONS.trash + '<span>Eliminar</span></button>' +
            '<button type="button" class="op-btn-secondary op-batch-restore">' + ICONS.restore + '<span>Restaurar</span></button>' +
            '<button type="button" class="op-btn-danger op-batch-purge">' + ICONS.trash + '<span>Eliminar permanentemente</span></button>' +
          '</div>' +
          '<button type="button" class="op-batch-cancel" aria-label="Cancelar selección">' + ICONS.close + '</button>' +
        '</div>' +
        '<div class="op-content"></div>' +
      '</div>' +
    '</div>';

  // ── Actions shared between action bar and empty states ──────────────────
  const NEW_DOCUMENT_CREATORS = { word: createBlankFile, excel: createBlankSpreadsheet, powerpoint: createBlankPresentation };

  function triggerNewDocument(kind, anchorBtn) {
    const btn = anchorBtn || _newBtn;
    const creator = NEW_DOCUMENT_CREATORS[kind] || createBlankFile;
    btn.disabled = true;
    creator(state.currentFolderId).then(function (file) {
      btn.disabled = false;
      toast('Documento creado', 'success');
      return loadFiles().then(function () { openEditor(file.fileId, file.originalFileName); });
    }).catch(function (err) {
      btn.disabled = false;
      toast('Error al crear el documento: ' + err.message, 'error');
    });
  }

  function newDocumentMenuItems(anchorBtn) {
    return [
      menuItem('Documento de texto', fileIconSvg('word'), false, function () { triggerNewDocument('word', anchorBtn); }),
      menuItem('Hoja de cálculo', fileIconSvg('excel'), false, function () { triggerNewDocument('excel', anchorBtn); }),
      menuItem('Presentación', fileIconSvg('powerpoint'), false, function () { triggerNewDocument('powerpoint', anchorBtn); }),
    ];
  }

  function openNewDocumentMenu(anchorBtn) {
    showContextMenu(anchorBtn, newDocumentMenuItems(anchorBtn));
  }

  function triggerUpload() {
    _uploadInput.click();
  }

  function performUpload(file) {
    if (!isAllowedFile(file)) {
      toast('Tipo de archivo no permitido', 'error');
      return;
    }
    _uploadProgress.hidden = false;
    _uploadProgressLabel.textContent = 'Subiendo ' + file.name + '…';
    _uploadProgressBar.style.width = '0%';
    uploadFile(file, state.currentFolderId, function (pct) {
      _uploadProgressBar.style.width = Math.round(pct * 100) + '%';
    }).then(function () {
      _uploadProgress.hidden = true;
      toast('Archivo subido correctamente', 'success');
      loadFiles();
    }).catch(function (err) {
      _uploadProgress.hidden = true;
      toast('Error al subir: ' + err.message, 'error');
    });
  }

  // ── Navigation (section tabs) ────────────────────────────────────────────
  function updateTabActiveClasses() {
    _tabItems.forEach(function (btn) {
      btn.classList.toggle('op-active', btn.getAttribute('data-section') === state.section);
    });
  }

  function switchSection(section) {
    console.log('[OP-DEBUG] switchSection incoming=', section, 'current=', state.section);
    if (state.section === section) return;
    state.section = section;
    state.currentFolderId = null;
    state.search = '';
    _searchInput.value = '';
    state.searchActive = false;
    state.searchFiles = [];
    state.searchFolders = [];
    state.searchError = false;
    state.sharedView = 'space';
    state.sharedWithMe = null;
    state.sharedWithMeError = false;
    updateSearchClearBtn();
    state.selected.clear();
    state.selectionMode = false;
    updateSelectToggleLabel();
    updateTabActiveClasses();
    updateSubtabs();
    _actionbar.hidden = section === 'trash';
    if (section === 'trash') {
      loadTrash();
    } else {
      loadFiles();
    }
  }

  // ── Theme ────────────────────────────────────────────────────────────────
  function isDarkEffective() {
    if (state.theme === 'dark') return true;
    if (state.theme === 'light') return false;
    return !!(window.matchMedia && window.matchMedia('(prefers-color-scheme: dark)').matches);
  }

  function applyTheme() {
    if (state.theme === 'system') {
      _root.removeAttribute('data-op-theme');
    } else {
      _root.setAttribute('data-op-theme', state.theme);
    }
    const dark = isDarkEffective();
    _themeToggleBtn.innerHTML = dark ? ICONS.sun : ICONS.moon;
    _themeToggleBtn.setAttribute('aria-label', dark ? 'Cambiar a modo claro' : 'Cambiar a modo oscuro');
  }

  // ── Init ─────────────────────────────────────────────────────────────────
  function injectStyles() {
    if (!document.getElementById('op-styles')) {
      const style = document.createElement('style');
      style.id = 'op-styles';
      style.textContent = CSS;
      document.head.appendChild(style);
    }
  }

  function buildShell() {
    _container.innerHTML = SHELL_HTML;
    _root = _container.querySelector('.op-widget');
    _content = _container.querySelector('.op-content');
    _actionbar = _container.querySelector('.op-actionbar');
    _batchBar = _container.querySelector('.op-batchbar');
    _batchCount = _container.querySelector('.op-batch-count');
    _batchSelectAll = _container.querySelector('.op-batch-selectall-cb');
    _batchDownloadBtn = _container.querySelector('.op-batch-download');
    _batchDeleteBtn = _container.querySelector('.op-batch-delete');
    _batchRestoreBtn = _container.querySelector('.op-batch-restore');
    _batchPurgeBtn = _container.querySelector('.op-batch-purge');
    _batchCancelBtn = _container.querySelector('.op-batch-cancel');
    _searchInput = _container.querySelector('.op-search');
    _searchClearBtn = _container.querySelector('.op-search-clear');
    _typeFilterSelect = _container.querySelector('.op-type-filter');
    _sortFieldSelect = _container.querySelector('.op-sort-field');
    _sortDirBtn = _container.querySelector('.op-sort-dir');
    _viewButtons = _container.querySelectorAll('.op-view-btn');
    _themeToggleBtn = _container.querySelector('.op-theme-toggle');
    _refreshBtn = _container.querySelector('.op-refresh-btn');
    // Ayuda de macros: opcional a propósito. Si el botón no está en el DOM (una
    // integración con la barra recortada), el widget sigue funcionando igual.
    const macrosHelpBtn = _container.querySelector('.op-macros-help-btn');
    if (macrosHelpBtn) macrosHelpBtn.onclick = showMacrosHelp;

    const notifBtn = _container.querySelector('.op-notifications-btn');
    if (notifBtn) notifBtn.onclick = toggleNotifPanel;
    _newBtn = _container.querySelector('.op-new-btn');
    _newFolderBtn = _container.querySelector('.op-new-folder-btn');
    _breadcrumbBar = _container.querySelector('.op-breadcrumb');
    _uploadInput = _container.querySelector('.op-upload-input');
    _selectToggleBtn = _container.querySelector('.op-select-toggle-btn');
    _uploadProgress = _container.querySelector('.op-upload-progress');
    _uploadProgressLabel = _container.querySelector('.op-upload-progress-label');
    _uploadProgressBar = _container.querySelector('.op-upload-progress-bar');
    _tabItems = _container.querySelectorAll('.op-tab');
    _subtabBar = _container.querySelector('.op-subtabs');
    _subtabItems = _container.querySelectorAll('.op-subtab');
  }

  function wireEvents() {
    _tabItems.forEach(function (btn) {
      btn.onclick = function () { switchSection(btn.getAttribute('data-section')); };
    });

    _subtabItems.forEach(function (btn) {
      btn.onclick = function () { switchSharedView(btn.getAttribute('data-subview')); };
    });

    window.addEventListener('resize', function () { closeContextMenu(); });

    _searchInput.oninput = function () {
      state.search = _searchInput.value;
      updateSearchClearBtn();
      clearTimeout(_searchDebounce);
      if (!state.search.trim()) { exitSearch(); return; }
      _searchDebounce = setTimeout(enterSearch, 500);
    };
    _searchInput.onkeydown = function (e) {
      if (e.key === 'Enter') {
        clearTimeout(_searchDebounce);
        if (state.search.trim()) enterSearch(); else exitSearch();
      } else if (e.key === 'Escape') {
        clearTimeout(_searchDebounce);
        _searchInput.value = '';
        state.search = '';
        updateSearchClearBtn();
        exitSearch();
      }
    };
    _searchClearBtn.onclick = function () {
      clearTimeout(_searchDebounce);
      _searchInput.value = '';
      state.search = '';
      updateSearchClearBtn();
      exitSearch();
      _searchInput.focus();
    };
    _typeFilterSelect.onchange = function () { state.typeFilter = _typeFilterSelect.value; renderContent(); };
    _sortFieldSelect.onchange = function () { state.sortField = _sortFieldSelect.value; renderContent(); };
    _sortDirBtn.onclick = function () {
      state.sortDir = state.sortDir === 'asc' ? 'desc' : 'asc';
      renderContent();
    };
    _refreshBtn.onclick = function () {
      if (state.section === 'trash') {
        loadTrash();
      } else {
        loadFiles();
      }
      // Refrescar es un gesto de "mostrame lo último": incluye la campana.
      refreshNotifications(true);
    };
    _viewButtons.forEach(function (btn) {
      btn.onclick = function () {
        state.viewMode = btn.getAttribute('data-mode');
        _viewButtons.forEach(function (b) { b.classList.toggle('op-active', b === btn); });
        renderContent();
      };
    });

    _selectToggleBtn.onclick = function () {
      state.selectionMode = !state.selectionMode;
      if (!state.selectionMode) state.selected.clear();
      updateSelectToggleLabel();
      renderContent();
    };

    _batchCancelBtn.onclick = function () {
      state.selected.clear();
      state.selectionMode = false;
      updateSelectToggleLabel();
      renderContent();
    };

    _batchSelectAll.onchange = function () {
      toggleSelectAllVisible(state.visibleFiles || [], _batchSelectAll.checked);
    };

    _batchDownloadBtn.onclick = handleBatchDownload;
    _batchDeleteBtn.onclick = handleBatchDelete;
    _batchRestoreBtn.onclick = handleBatchRestore;
    _batchPurgeBtn.onclick = handleBatchPurge;

    _themeToggleBtn.onclick = function () {
      state.theme = isDarkEffective() ? 'light' : 'dark';
      applyTheme();
    };

    if (window.matchMedia) {
      const darkMedia = window.matchMedia('(prefers-color-scheme: dark)');
      const onSystemThemeChange = function () { if (state.theme === 'system') applyTheme(); };
      if (darkMedia.addEventListener) darkMedia.addEventListener('change', onSystemThemeChange);
      else if (darkMedia.addListener) darkMedia.addListener(onSystemThemeChange);
    }

    _newBtn.onclick = function (e) { e.stopPropagation(); openNewDocumentMenu(_newBtn); };
    _newFolderBtn.onclick = triggerNewFolder;
    _uploadInput.onchange = function () {
      const file = _uploadInput.files[0];
      if (!file) return;
      _uploadInput.value = '';
      performUpload(file);
    };

    _content.addEventListener('scroll', closeContextMenu);
    document.addEventListener('keydown', function (e) {
      if (e.key === 'Escape') { closeContextMenu(); closeModal(); }
    });
  }

  function init() {
    _container = document.getElementById(CONTAINER);
    if (!_container) {
      _container = document.createElement('div');
      _container.id = CONTAINER;
      _container.style.display = 'none';
      (document.body || document.documentElement).appendChild(_container);
    }

    injectStyles();
    buildShell();
    wireEvents();
    updateTabActiveClasses();
    updateSelectToggleLabel();
    applyTheme();
    loadFiles();
    startNotificationPolling();
  }

  // ── Public API (window.OfficePlatform) ───────────────────────────────────
  // Exposed synchronously with script execution so the host page can call it any time
  // after load. See the JSDoc at the top of this file for the contract.
  window.OfficePlatform = window.OfficePlatform || {};
  window.OfficePlatform.openEditor = function (options) {
    options = options || {};

    // Open an existing file directly.
    if (options.fileId != null) {
      openEditor(options.fileId, options.fileName || 'Documento');
      return Promise.resolve({ fileId: options.fileId });
    }

    // Create a blank document of the requested type, then open it.
    const creators = {
      document: createBlankFile,
      spreadsheet: createBlankSpreadsheet,
      presentation: createBlankPresentation,
    };
    const creator = creators[options.type];
    if (!creator) {
      const err = new Error("OfficePlatform.openEditor requiere { fileId } o { type: 'document' | 'spreadsheet' | 'presentation' }");
      console.error('[OfficePlatform]', err.message);
      return Promise.reject(err);
    }

    // Honors the active scope (resolved inside the creator via getCurrentScope) and, when the
    // manager sidebar is mounted, the folder the user is currently in.
    const folderId = state.currentFolderId || options.folderId || null;
    return creator(folderId).then(function (file) {
      openEditor(file.fileId, file.originalFileName);
      // Refresh the manager listing if it is mounted, so the new file shows there too.
      if (_container) {
        try { loadFiles(); } catch (e) { /* manager not ready; ignore */ }
      }
      return file;
    }).catch(function (err) {
      console.error('[OfficePlatform] openEditor error:', err);
      if (typeof toast === 'function') {
        try { toast('No se pudo abrir el editor: ' + err.message, 'error'); } catch (e) { /* ignore */ }
      }
      throw err;
    });
  };

  if (document.readyState === 'loading') {
    document.addEventListener('DOMContentLoaded', init);
  } else {
    init();
  }

})();
