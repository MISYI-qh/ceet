'use strict';
// Ceet desktop: proses utama. Satu jendela, tab = WebContentsView, antarmuka = dua view HTML (shell + overlay).

const { app, BaseWindow, WebContentsView, ipcMain, session, protocol, Menu, shell, nativeTheme, dialog, clipboard } = require('electron');
const fs = require('fs');
const path = require('path');
const { Blocker, cleanUrl, registrableDomain } = require('./blocker');
const { EngineBridge, findBinary } = require('./engine');
const { Store, ENGINES } = require('./store');

const CHROME_H = 88; // 40 strip tab + 48 bilah alat
const FIND_H = 44;
const UI = path.join(__dirname, '..', 'ui');
const PRELOAD = (n) => path.join(__dirname, n);

protocol.registerSchemesAsPrivileged([
  { scheme: 'ceet', privileges: { standard: true, secure: true, supportFetchAPI: true, allowServiceWorkers: false } },
]);

let win, shellView, overlayView, store, engine, blocker;
const tabs = [];
let activeId = null;
let nextTabId = 1;
let overlayState = { open: false, type: null };
let findOpen = false;
const closedTabs = [];
const downloads = [];
let pushTimer = null;

// Setiap tab mencatat statistik pelacak sendiri. Direset saat navigasi utama.
function tabById(id) { return tabs.find((t) => t.id === id); }
function activeTab() { return tabById(activeId); }
function tabByContents(wc) { return tabs.find((t) => t.view.webContents === wc || t.view.webContents.id === wc); }

function resourcePath(name) {
  const packaged = path.join(process.resourcesPath || '', name);
  if (app.isPackaged && fs.existsSync(packaged)) return packaged;
  return path.join(__dirname, '..', '..', 'data', name);
}

// ---------- alamat internal ceet:// ----------
function internalUrl(route, params) {
  const q = params ? '?' + new URLSearchParams(params).toString() : '';
  return 'ceet://app/' + route + q;
}
function isInternal(url) { return typeof url === 'string' && url.startsWith('ceet://'); }

function registerProtocol() {
  protocol.handle('ceet', async (req) => {
    const u = new URL(req.url);
    let rel;
    if (u.hostname === 'app') rel = 'pages/app.html';
    else if (u.hostname === 'ui') rel = u.pathname.replace(/^\/+/, '');
    else return new Response('tidak ditemukan', { status: 404 });
    const file = path.normalize(path.join(UI, rel));
    if (!file.startsWith(UI)) return new Response('terlarang', { status: 403 });
    try {
      const body = await fs.promises.readFile(file);
      const ext = path.extname(file);
      const type = { '.html': 'text/html', '.js': 'text/javascript', '.css': 'text/css', '.svg': 'image/svg+xml', '.png': 'image/png' }[ext] || 'application/octet-stream';
      return new Response(body, { headers: { 'content-type': type + '; charset=utf-8', 'content-security-policy': "default-src 'self' ceet:; img-src 'self' ceet: data: https: http:; style-src 'self' ceet: 'unsafe-inline'; script-src 'self' ceet:" } });
    } catch {
      return new Response('tidak ditemukan', { status: 404 });
    }
  });
}

// ---------- perlindungan privasi pada sesi ----------
function protectSession(ses, isPrivate) {
  if (ses.__ceetProtected) return;
  ses.__ceetProtected = true;
  const s = () => store.settings;

  ses.webRequest.onBeforeRequest((d, cb) => {
    try {
      const wcTab = d.webContentsId ? tabs.find((t) => t.view.webContents.id === d.webContentsId) : null;
      let url;
      try { url = new URL(d.url); } catch { return cb({}); }
      if (url.protocol !== 'http:' && url.protocol !== 'https:' && url.protocol !== 'ws:' && url.protocol !== 'wss:') return cb({});

      if (d.resourceType === 'mainFrame') {
        const c = cleanUrl(d.url);
        const upgraded = s().upgradeHttps && c.upgraded;
        const stripped = s().stripParams && c.removed.length > 0;
        if (upgraded || stripped) {
          const target = cleanUrl2(d.url, upgraded, stripped);
          if (target !== d.url) {
            if (upgraded) store.bump('httpsUpgraded');
            if (stripped) store.bump('paramsStripped', c.removed.length);
            if (wcTab) { if (upgraded) wcTab.upgraded = true; if (stripped) wcTab.stripped = (wcTab.stripped || 0) + c.removed.length; }
            return cb({ redirectURL: target });
          }
        }
        return cb({});
      }

      if (s().blockTrackers && wcTab) {
        const pageHost = hostOf(wcTab.url) || hostOf(d.referrer);
        const r = blocker.check(url.hostname, pageHost);
        if (r.block) {
          wcTab.blocked += 1;
          if (!wcTab.trackers.includes(r.rule)) wcTab.trackers.push(r.rule);
          store.bump('trackersBlocked');
          schedulePush();
          return cb({ cancel: true });
        }
      }
      cb({});
    } catch {
      cb({});
    }
  });

  ses.webRequest.onBeforeSendHeaders((d, cb) => {
    const h = { ...d.requestHeaders };
    if (s().sendGpc) { h['Sec-GPC'] = '1'; h.DNT = '1'; }
    if (s().trimReferrer && h.Referer) {
      try {
        const ref = new URL(h.Referer);
        const req = new URL(d.url);
        if (registrableDomain(ref.hostname) !== registrableDomain(req.hostname)) h.Referer = ref.origin + '/';
      } catch { delete h.Referer; }
    }
    cb({ requestHeaders: h });
  });

  ses.setPermissionRequestHandler((wc, permission, cb) => {
    // Bawaan: tolak semua kecuali yang aman. Pengguna dapat mengaktifkan pertanyaan izin di Pengaturan.
    const safe = ['fullscreen', 'clipboard-sanitized-write', 'pointerLock', 'window-management'].includes(permission);
    if (safe) return cb(true);
    if (!s().askPermissions) return cb(false);
    const t = tabByContents(wc);
    const origin = t ? hostOf(t.url) : 'situs ini';
    dialog.showMessageBox({
      type: 'question', buttons: ['Izinkan sekali', 'Tolak'], defaultId: 1, cancelId: 1,
      message: `${origin} meminta izin: ${permission}`, detail: 'Izin tidak diingat setelah tab ditutup.',
    }).then((r) => cb(r.response === 0)).catch(() => cb(false));
  });
  ses.setPermissionCheckHandler((_wc, permission) => ['fullscreen', 'clipboard-sanitized-write'].includes(permission));

  ses.on('will-download', (_e, item) => {
    const entry = { id: 'd' + Date.now().toString(36), name: item.getFilename(), url: item.getURL(), total: item.getTotalBytes(), received: 0, state: 'berjalan', path: '' };
    downloads.unshift(entry);
    item.on('updated', () => { entry.received = item.getReceivedBytes(); entry.total = item.getTotalBytes(); entry.path = item.getSavePath(); schedulePush(); });
    item.once('done', (_ev, state) => { entry.state = state === 'completed' ? 'selesai' : 'gagal'; entry.path = item.getSavePath(); schedulePush(); });
  });
  void isPrivate;
}

function cleanUrl2(raw, upgrade, strip) {
  const c = cleanUrl(raw);
  if (upgrade && strip) return c.url;
  if (upgrade) { const u = new URL(raw); u.protocol = 'https:'; if (u.port === '80') u.port = ''; return u.toString(); }
  // hanya buang parameter: pakai hasil bersih tetapi pertahankan skema asli
  const out = new URL(c.url);
  out.protocol = new URL(raw).protocol;
  return out.toString();
}

function hostOf(u) { try { return new URL(u).hostname; } catch { return ''; } }

// ---------- tab ----------
function createTab(url, opts = {}) {
  const priv = !!opts.private;
  const view = new WebContentsView({
    webPreferences: {
      preload: PRELOAD('preload-page.js'),
      partition: priv ? 'ceet-private' : 'persist:ceet',
      contextIsolation: true, sandbox: true, nodeIntegration: false, spellcheck: true, webSecurity: true,
    },
  });
  const tab = {
    id: nextTabId++, view, url: '', title: 'Tab baru', favicon: '', loading: false, private: priv, pinned: !!opts.pinned,
    blocked: 0, trackers: [], upgraded: false, stripped: 0, risk: 'aman', riskReasons: [], zoom: 0,
  };
  tabs.push(tab);
  protectSession(view.webContents.session, priv);
  wireTab(tab);
  win.contentView.addChildView(view);
  view.setVisible(false);
  activate(tab.id);
  const target = url || internalUrl('home');
  navigate(tab, target);
  raiseOverlay();
  schedulePush();
  return tab;
}

function wireTab(tab) {
  const wc = tab.view.webContents;
  const upd = () => schedulePush();

  wc.setWindowOpenHandler(({ url }) => {
    if (/^https?:/i.test(url)) createTab(url, { private: tab.private });
    return { action: 'deny' };
  });

  wc.on('page-title-updated', (_e, title) => {
    tab.title = title || tab.title;
    if (!tab.private && store.settings.saveHistory && tab.url && !isInternal(tab.url)) {
      const e = store.updateHistoryTitle(tab.url, title);
      if (e) engine.indexAdd({ docid: e.id, url: e.url, title: e.title, body: '', visits: e.visits, last_visit: Math.floor(e.last / 1000) });
    }
    upd();
  });
  wc.on('page-favicon-updated', (_e, favs) => { tab.favicon = favs && favs[0] ? favs[0] : ''; upd(); });
  wc.on('did-start-loading', () => { tab.loading = true; upd(); });
  wc.on('did-stop-loading', () => { tab.loading = false; upd(); });

  wc.on('did-start-navigation', (d) => {
    // Electron 33: argumen pertama berupa detail. Hanya navigasi utama yang mereset hitungan.
    const details = d && d.url !== undefined ? d : null;
    if (details && details.isMainFrame && !details.isSameDocument) {
      tab.blocked = 0; tab.trackers = []; tab.upgraded = false; tab.stripped = 0;
      tab.risk = 'aman'; tab.riskReasons = [];
    }
  });

  const onNav = (url) => {
    tab.url = url;
    if (!tab.private && store.settings.saveHistory && !isInternal(url) && /^https?:/i.test(url)) {
      const e = store.addHistory(url, tab.title);
      engine.indexAdd({ docid: e.id, url: e.url, title: e.title, body: '', visits: e.visits, last_visit: Math.floor(e.last / 1000) });
    }
    upd();
  };
  wc.on('did-navigate', (_e, url) => onNav(url));
  wc.on('did-navigate-in-page', (_e, url, isMain) => { if (isMain) { tab.url = url; upd(); } });

  wc.on('did-finish-load', async () => {
    if (tab.private || isInternal(tab.url) || !store.settings.indexContent || !store.settings.saveHistory) return;
    try {
      const text = await wc.executeJavaScript('(document.body && document.body.innerText || "").slice(0, 20000)', true);
      const e = store.data.history.find((x) => x.url === tab.url);
      if (e && text) engine.indexAdd({ docid: e.id, url: e.url, title: e.title, body: text, visits: e.visits, last_visit: Math.floor(e.last / 1000) });
    } catch { /* halaman ditutup sebelum selesai */ }
  });

  wc.on('will-navigate', (e, url) => {
    if (isInternal(url)) return;
    if (!/^https?:/i.test(url)) {
      e.preventDefault();
      if (/^(mailto|tel):/i.test(url)) shell.openExternal(url);
    }
  });

  wc.on('did-fail-load', (_e, code, desc, url, isMain) => {
    if (!isMain || code === -3) return; // -3 = dibatalkan
    tab.title = 'Gagal dimuat';
    wc.loadURL(internalUrl('error', { url, code: String(code), desc }));
  });

  wc.on('context-menu', (_e, p) => {
    const items = [];
    if (p.linkURL) {
      items.push({ label: 'Buka tautan di tab baru', click: () => createTab(p.linkURL, { private: tab.private }) });
      items.push({ label: 'Salin alamat tautan', click: () => clipboard.writeText(cleanUrl(p.linkURL).url) });
      items.push({ type: 'separator' });
    }
    if (p.selectionText) {
      items.push({ label: 'Salin', role: 'copy' });
      const q = p.selectionText.slice(0, 120);
      items.push({ label: `Cari "${q.length > 24 ? q.slice(0, 24) + '…' : q}"`, click: () => createTab(store.searchTemplate().replace('{q}', encodeURIComponent(q)), { private: tab.private }) });
      items.push({ type: 'separator' });
    }
    if (p.isEditable) {
      items.push({ role: 'cut', label: 'Potong' }, { role: 'copy', label: 'Salin' }, { role: 'paste', label: 'Tempel' }, { type: 'separator' });
    }
    if (p.mediaType === 'image' && p.srcURL) items.push({ label: 'Salin alamat gambar', click: () => clipboard.writeText(p.srcURL) });
    items.push(
      { label: 'Kembali', enabled: canGoBack(wc), click: () => wc.navigationHistory.goBack() },
      { label: 'Maju', enabled: canGoForward(wc), click: () => wc.navigationHistory.goForward() },
      { label: 'Muat ulang', click: () => wc.reload() },
      { type: 'separator' },
      { label: 'Periksa elemen', click: () => wc.inspectElement(p.x, p.y) },
    );
    Menu.buildFromTemplate(items).popup({ window: undefined });
  });

  wc.on('before-input-event', (e, input) => {
    if (input.type !== 'keyDown') return;
    if (handleShortcut(input)) e.preventDefault();
  });
  wc.on('found-in-page', (_e, r) => send('find:result', { active: r.activeMatchOrdinal, total: r.matches }));
}

function canGoBack(wc) { return wc.navigationHistory ? wc.navigationHistory.canGoBack() : wc.canGoBack(); }
function canGoForward(wc) { return wc.navigationHistory ? wc.navigationHistory.canGoForward() : wc.canGoForward(); }

async function navigate(tab, url) {
  const wc = tab.view.webContents;
  if (isInternal(url)) { wc.loadURL(url).catch(() => {}); return; }
  if (store.settings.phishingCheck && /^https?:/i.test(url)) {
    const a = await engine.phishing(url);
    tab.risk = a.risk; tab.riskReasons = a.reasons || [];
    if (a.risk === 'berbahaya') {
      store.bump('phishingStopped');
      wc.loadURL(internalUrl('warning', { url, score: String(a.score), reasons: (a.reasons || []).join('|') })).catch(() => {});
      return;
    }
  }
  wc.loadURL(url).catch(() => {});
}

function activate(id) {
  const tab = tabById(id);
  if (!tab) return;
  activeId = id;
  for (const t of tabs) t.view.setVisible(t.id === id);
  layout();
  if (!isInternal(tab.url) || tab.url.endsWith('/home')) { /* fokus ditangani shell */ }
  tab.view.webContents.focus();
  schedulePush();
}

function closeTab(id) {
  const i = tabs.findIndex((t) => t.id === id);
  if (i < 0) return;
  const tab = tabs[i];
  if (!tab.private && tab.url && !isInternal(tab.url)) { closedTabs.unshift({ url: tab.url, title: tab.title }); closedTabs.length = Math.min(closedTabs.length, 20); }
  win.contentView.removeChildView(tab.view);
  tab.view.webContents.close();
  tabs.splice(i, 1);
  if (!tabs.length) { createTab(); return; }
  if (activeId === id) activate(tabs[Math.min(i, tabs.length - 1)].id);
  persistSession();
  schedulePush();
}

function persistSession() {
  const list = tabs.filter((t) => !t.private && t.url).map((t) => ({ url: t.url, title: t.title, pinned: t.pinned }));
  const idx = Math.max(0, tabs.filter((t) => !t.private && t.url).findIndex((t) => t.id === activeId));
  store.saveSession(list, idx);
}

// ---------- tata letak ----------
function layout() {
  if (!win || win.isDestroyed()) return;
  const b = win.getContentBounds();
  const w = b.width; const h = b.height;
  shellView.setBounds({ x: 0, y: 0, width: w, height: h });
  const top = CHROME_H + (findOpen ? FIND_H : 0);
  for (const t of tabs) t.view.setBounds({ x: 0, y: top, width: w, height: Math.max(0, h - top) });
  if (overlayState.open) overlayView.setBounds({ x: 0, y: 0, width: w, height: h });
  else overlayView.setBounds({ x: 0, y: 0, width: 0, height: 0 });
}

function raiseOverlay() {
  win.contentView.addChildView(overlayView);
  layout();
}

// ---------- komunikasi ke antarmuka ----------
function send(channel, payload) {
  for (const v of [shellView, overlayView]) {
    if (v && !v.webContents.isDestroyed()) v.webContents.send(channel, payload);
  }
}

function snapshot() {
  const a = activeTab();
  const wc = a && a.view.webContents;
  return {
    platform: process.platform,
    dark: nativeTheme.shouldUseDarkColors,
    theme: store.settings.theme,
    settings: store.settings,
    engineAvailable: engine.available,
    engineInfo: engine.info,
    activeId,
    findOpen,
    tabs: tabs.map((t) => ({
      id: t.id, title: t.title, url: isInternal(t.url) ? '' : t.url, internal: isInternal(t.url), favicon: t.favicon,
      loading: t.loading, private: t.private, pinned: t.pinned, blocked: t.blocked,
    })),
    nav: a ? {
      url: isInternal(a.url) ? '' : a.url, secure: /^https:/i.test(a.url), internal: isInternal(a.url), loading: a.loading,
      canBack: canGoBack(wc), canForward: canGoForward(wc), bookmarked: store.isBookmarked(a.url), private: a.private,
      blocked: a.blocked, trackers: a.trackers, upgraded: a.upgraded, stripped: a.stripped, risk: a.risk, riskReasons: a.riskReasons,
      host: hostOf(a.url),
    } : null,
    stats: store.data.stats,
    downloads: downloads.slice(0, 20),
    closedCount: closedTabs.length,
  };
}

function schedulePush() {
  if (pushTimer) return;
  pushTimer = setTimeout(() => {
    pushTimer = null;
    if (!win || win.isDestroyed()) return;
    send('state', snapshot());
  }, 40);
}

function applyTheme() {
  const t = store.settings.theme;
  nativeTheme.themeSource = t;
  const dark = nativeTheme.shouldUseDarkColors;
  if (win && !win.isDestroyed()) {
    try { win.setTitleBarOverlay({ color: '#00000000', symbolColor: dark ? '#EAF0FB' : '#0B1A30', height: 40 }); } catch { /* tidak didukung */ }
  }
}

// ---------- overlay (saran, perisai, menu, pembaca) ----------
function openOverlay(type, data, rect) {
  if (overlayState.open && overlayState.type === type && type !== 'reader') { closeOverlay(); return; }
  overlayState = { open: true, type };
  raiseOverlay();
  overlayView.webContents.send('overlay:show', { type, data, rect, state: snapshot() });
  overlayView.webContents.focus();
}
function closeOverlay() {
  if (!overlayState.open) return;
  overlayState = { open: false, type: null };
  layout();
  const a = activeTab();
  if (a) a.view.webContents.focus();
  send('overlay:closed');
}

// ---------- pintasan keyboard ----------
function handleShortcut(input) {
  const mod = process.platform === 'darwin' ? input.meta : input.control;
  const k = input.key.toLowerCase();
  const a = activeTab();
  if (mod && !input.shift && !input.alt) {
    if (k === 't') { createTab(); shellView.webContents.send('focus:omnibox'); return true; }
    if (k === 'w') { if (a) closeTab(a.id); return true; }
    if (k === 'l') { shellView.webContents.focus(); shellView.webContents.send('focus:omnibox'); return true; }
    if (k === 'r') { if (a) a.view.webContents.reload(); return true; }
    if (k === 'd') { toggleBookmark(); return true; }
    if (k === 'f') { toggleFind(true); return true; }
    if (k === 'k') { shellView.webContents.focus(); shellView.webContents.send('focus:omnibox', '> '); return true; }
    if (k === 'h') { createTab(internalUrl('history')); return true; }
    if (k === 'j') { createTab(internalUrl('downloads')); return true; }
    if (k === 'tab') { cycle(1); return true; }
    if (k === '=' || k === '+') { zoom(0.5); return true; }
    if (k === '-') { zoom(-0.5); return true; }
    if (k === '0') { zoomReset(); return true; }
    if (/^[1-9]$/.test(k)) { const t = tabs[k === '9' ? tabs.length - 1 : Number(k) - 1]; if (t) activate(t.id); return true; }
  }
  if (mod && input.shift && !input.alt) {
    if (k === 't') { reopenClosed(); return true; }
    if (k === 'n') { createTab(undefined, { private: true }); shellView.webContents.send('focus:omnibox'); return true; }
    if (k === 'r') { if (a) a.view.webContents.reloadIgnoringCache(); return true; }
    if (k === 'tab') { cycle(-1); return true; }
    if (k === 'i') { if (a) a.view.webContents.toggleDevTools(); return true; }
  }
  if (input.alt && !mod) {
    if (k === 'arrowleft' && a && canGoBack(a.view.webContents)) { a.view.webContents.navigationHistory.goBack(); return true; }
    if (k === 'arrowright' && a && canGoForward(a.view.webContents)) { a.view.webContents.navigationHistory.goForward(); return true; }
  }
  if (input.key === 'Escape') {
    if (overlayState.open) { closeOverlay(); return true; }
    if (findOpen) { toggleFind(false); return true; }
  }
  if (input.key === 'F5') { if (a) a.view.webContents.reload(); return true; }
  if (input.key === 'F12') { if (a) a.view.webContents.toggleDevTools(); return true; }
  return false;
}

function cycle(dir) {
  if (tabs.length < 2) return;
  const i = tabs.findIndex((t) => t.id === activeId);
  activate(tabs[(i + dir + tabs.length) % tabs.length].id);
}
function reopenClosed() {
  const c = closedTabs.shift();
  if (c) createTab(c.url);
}
function zoom(delta) {
  const a = activeTab(); if (!a) return;
  a.zoom = Math.max(-3, Math.min(5, a.zoom + delta));
  a.view.webContents.setZoomLevel(a.zoom);
}
function zoomReset() { const a = activeTab(); if (a) { a.zoom = 0; a.view.webContents.setZoomLevel(0); } }
function toggleBookmark() {
  const a = activeTab();
  if (!a || !/^https?:/i.test(a.url)) return;
  store.toggleBookmark(a.url, a.title);
  schedulePush();
}
function toggleFind(open) {
  findOpen = open === undefined ? !findOpen : open;
  if (!findOpen) { const a = activeTab(); if (a) a.view.webContents.stopFindInPage('clearSelection'); }
  layout();
  shellView.webContents.send('find:toggle', findOpen);
  schedulePush();
}

// ---------- IPC ----------
function fromShell(e) { return shellView && e.sender === shellView.webContents; }
function fromOverlay(e) { return overlayView && e.sender === overlayView.webContents; }
function fromUi(e) { return fromShell(e) || fromOverlay(e); }
function fromInternalPage(e) { return typeof e.senderFrame?.url === 'string' && e.senderFrame.url.startsWith('ceet://'); }

function registerIpc() {
  const h = (ch, fn, guard = fromUi) => ipcMain.handle(ch, (e, ...a) => { if (!guard(e)) throw new Error('tidak diizinkan'); return fn(...a); });

  h('state:get', () => snapshot());
  h('tab:new', (url, priv) => { createTab(url, { private: !!priv }); return true; });
  h('tab:close', (id) => closeTab(id));
  h('tab:activate', (id) => activate(id));
  h('tab:pin', (id) => { const t = tabById(id); if (t) { t.pinned = !t.pinned; schedulePush(); } });
  h('tab:duplicate', (id) => { const t = tabById(id); if (t && t.url) createTab(t.url, { private: t.private }); });
  h('tab:closeOthers', (id) => { for (const t of [...tabs]) if (t.id !== id && !t.pinned) closeTab(t.id); });
  h('tab:reopen', () => reopenClosed());
  h('tab:menu', (id) => {
    const t = tabById(id); if (!t) return;
    Menu.buildFromTemplate([
      { label: t.pinned ? 'Lepas sematan' : 'Sematkan tab', click: () => { t.pinned = !t.pinned; schedulePush(); } },
      { label: 'Gandakan', click: () => t.url && createTab(t.url, { private: t.private }) },
      { label: 'Muat ulang', click: () => t.view.webContents.reload() },
      { type: 'separator' },
      { label: 'Tutup tab lain', click: () => { for (const x of [...tabs]) if (x.id !== id && !x.pinned) closeTab(x.id); } },
      { label: 'Tutup tab', click: () => closeTab(id) },
    ]).popup();
  });

  h('nav:go', (input) => goInput(input));
  h('nav:back', () => { const a = activeTab(); if (a && canGoBack(a.view.webContents)) a.view.webContents.navigationHistory.goBack(); });
  h('nav:forward', () => { const a = activeTab(); if (a && canGoForward(a.view.webContents)) a.view.webContents.navigationHistory.goForward(); });
  h('nav:reload', () => { const a = activeTab(); if (a) (a.loading ? a.view.webContents.stop() : a.view.webContents.reload()); });
  h('nav:home', () => { const a = activeTab(); if (a) navigate(a, internalUrl('home')); });

  h('omnibox:input', async (text, rect) => {
    const q = String(text || '').trim();
    if (!q) { closeOverlay(); return; }
    const items = await buildSuggestions(q);
    if (!overlayState.open || overlayState.type !== 'suggest') overlayState = { open: true, type: 'suggest' };
    raiseOverlay();
    overlayView.webContents.send('overlay:show', { type: 'suggest', data: { q, items }, rect, state: snapshot() });
    shellView.webContents.send('suggest:items', items);
    shellView.webContents.focus();
  });
  h('suggest:select', (i) => overlayView.webContents.send('suggest:select', i));
  h('overlay:close', () => closeOverlay());
  h('overlay:open', (type, rect) => {
    if (type === 'reader') return openReader();
    openOverlay(type, undefined, rect);
  });
  h('setting:set', (k, v) => { const ok = store.setSetting(k, v); if (ok && k === 'theme') applyTheme(); schedulePush(); return ok; });
  h('menu:action', (name) => menuAction(name));

  h('bookmark:toggle', () => toggleBookmark());
  h('find:open', (open) => toggleFind(open));
  h('find:query', (q, forward = true, next = false) => {
    const a = activeTab(); if (!a) return;
    if (!q) { a.view.webContents.stopFindInPage('clearSelection'); send('find:result', { active: 0, total: 0 }); return; }
    a.view.webContents.findInPage(q, { forward, findNext: next });
  });
  h('zoom', (d) => (d === 0 ? zoomReset() : zoom(d)));
  h('devtools', () => { const a = activeTab(); if (a) a.view.webContents.toggleDevTools(); });
  h('page:copyUrl', () => { const a = activeTab(); if (a && !isInternal(a.url)) clipboard.writeText(cleanUrl(a.url).url); });
  h('download:show', (id) => { const d = downloads.find((x) => x.id === id); if (d && d.path) shell.showItemInFolder(d.path); });
  h('page:open', (url) => { const a = activeTab(); if (a) navigate(a, url); closeOverlay(); });
  h('page:openNew', (url) => { createTab(url); closeOverlay(); });
  h('app:window', (what) => {
    if (!win) return;
    if (what === 'fullscreen') win.setFullScreen(!win.isFullScreen());
  });

  // Halaman internal (ceet://) memakai API terbatas sendiri.
  const p = (ch, fn) => h(ch, fn, fromInternalPage);
  p('page:settings', () => ({ settings: store.settings, engines: Object.fromEntries(Object.entries(ENGINES).map(([k, v]) => [k, v.name])), engine: engine.info, engineAvailable: engine.available, stats: store.data.stats }));
  p('page:setSetting', (k, v) => { const ok = store.setSetting(k, v); if (ok && k === 'theme') applyTheme(); schedulePush(); return ok; });
  p('page:history', async (q) => {
    if (q && q.trim()) {
      const hits = await engine.search(q, 60);
      if (hits) {
        const byId = new Map(store.data.history.map((e) => [e.id, e]));
        return { items: hits.map((x) => ({ ...byId.get(x.id), why: x.why })).filter((x) => x.url), semantic: true };
      }
      const ql = q.toLowerCase();
      return { items: store.data.history.filter((e) => (e.title + ' ' + e.url).toLowerCase().includes(ql)).slice(0, 60), semantic: false };
    }
    return { items: store.data.history.slice(0, 300), semantic: false };
  });
  p('page:historyRemove', (id) => { const e = store.removeHistory(id); if (e) engine.indexRemove(e.id); return !!e; });
  p('page:historyClear', () => { for (const id of store.clearHistory()) engine.indexRemove(id); return true; });
  p('page:bookmarks', () => store.data.bookmarks);
  p('page:bookmarkRemove', (id) => { const ok = store.removeBookmark(id); schedulePush(); return ok; });
  p('page:downloads', () => downloads);
  p('page:resetStats', () => { store.resetStats(); schedulePush(); return true; });
  p('page:search', (q) => goInput(q));
  p('page:downloadShow', (id) => { const d = downloads.find((x) => x.id === id); if (d && d.path) shell.showItemInFolder(d.path); });
  p('page:navigate', (url) => { const a = activeTab(); if (a && /^(https?:|ceet:\/\/app\/)/i.test(url)) navigate(a, url); });
  p('page:proceed', (url) => { const a = activeTab(); if (a && /^https?:/i.test(url)) { a.risk = 'diizinkan'; a.view.webContents.loadURL(url).catch(() => {}); } });
  p('page:topSites', () => {
    const byHost = new Map();
    for (const e of store.data.history) {
      const h = hostOf(e.url); if (!h) continue;
      const cur = byHost.get(h) || { host: h, url: new URL(e.url).origin + '/', title: e.title, visits: 0 };
      cur.visits += e.visits; byHost.set(h, cur);
    }
    return [...byHost.values()].sort((a, b) => b.visits - a.visits).slice(0, 8);
  });
}

function menuAction(name) {
  closeOverlay();
  const open = (r) => createTab(internalUrl(r));
  switch (name) {
    case 'newTab': createTab(); shellView.webContents.send('focus:omnibox'); break;
    case 'privateTab': createTab(undefined, { private: true }); shellView.webContents.send('focus:omnibox'); break;
    case 'history': open('history'); break;
    case 'bookmarks': open('bookmarks'); break;
    case 'downloads': open('downloads'); break;
    case 'privacy': open('privacy'); break;
    case 'settings': open('settings'); break;
    case 'reader': openReader(); break;
    case 'find': toggleFind(true); break;
    case 'zoomIn': zoom(0.5); break;
    case 'zoomOut': zoom(-0.5); break;
    case 'zoomReset': zoomReset(); break;
    case 'fullscreen': win.setFullScreen(!win.isFullScreen()); break;
    case 'devtools': { const a = activeTab(); if (a) a.view.webContents.toggleDevTools(); break; }
    case 'reopen': reopenClosed(); break;
    case 'themeToggle': store.setSetting('theme', nativeTheme.shouldUseDarkColors ? 'light' : 'dark'); applyTheme(); schedulePush(); break;
    default: break;
  }
  return true;
}

async function goInput(input) {
  const a = activeTab() || createTab();
  const c = await engine.classify(input, store.searchTemplate());
  if (c.kind === 'command') return runCommand(c.value, c.arg);
  if (!c.target) return false;
  await navigate(a, c.target);
  closeOverlay();
  return true;
}

async function buildSuggestions(q) {
  const items = [];
  const c = await engine.classify(q, store.searchTemplate());
  if (c.kind === 'command') {
    for (const cmd of COMMANDS.filter((x) => x.name.startsWith(c.value))) items.push({ kind: 'command', title: cmd.title, sub: '> ' + cmd.name, value: '> ' + cmd.name, icon: 'command' });
    return items.slice(0, 8);
  }
  if (c.kind === 'url') items.push({ kind: 'url', title: c.value, sub: 'Buka alamat', value: c.target, icon: 'globe' });
  else items.push({ kind: 'search', title: c.value, sub: c.kind === 'question' ? 'Tanya lewat ' + engineName() : 'Cari di ' + engineName(), value: q, icon: 'search' });
  const sug = await engine.suggest(q, 5);
  const hits = sug && sug.length ? sug : (await engine.search(q, 5)) || [];
  const byId = new Map(store.data.history.map((e) => [e.id, e]));
  for (const hit of hits) {
    const e = byId.get(hit.id);
    if (e && !items.some((i) => i.value === e.url)) items.push({ kind: 'history', title: e.title || e.url, sub: e.url.replace(/^https?:\/\/(www\.)?/, ''), value: e.url, icon: 'clock', why: (hit.why || [])[0] });
  }
  for (const b of store.data.bookmarks) {
    if (items.length >= 8) break;
    if ((b.title + ' ' + b.url).toLowerCase().includes(q.toLowerCase()) && !items.some((i) => i.value === b.url)) items.push({ kind: 'bookmark', title: b.title, sub: b.url.replace(/^https?:\/\/(www\.)?/, ''), value: b.url, icon: 'star' });
  }
  return items.slice(0, 8);
}

function engineName() { return (ENGINES[store.settings.searchEngine] || ENGINES.ddg).name; }

const COMMANDS = [
  { name: 'tema gelap', title: 'Ganti ke tema gelap' },
  { name: 'tema terang', title: 'Ganti ke tema terang' },
  { name: 'tema sistem', title: 'Ikuti tema perangkat' },
  { name: 'privat', title: 'Buka tab privat' },
  { name: 'riwayat', title: 'Buka riwayat' },
  { name: 'penanda', title: 'Buka penanda' },
  { name: 'unduhan', title: 'Buka unduhan' },
  { name: 'pengaturan', title: 'Buka pengaturan' },
  { name: 'privasi', title: 'Buka dasbor privasi' },
  { name: 'ringkas', title: 'Ringkas halaman ini' },
  { name: 'baca', title: 'Mode baca' },
  { name: 'bersihkan', title: 'Hapus riwayat' },
];

function runCommand(name, arg) {
  const full = (name + ' ' + (arg || '')).trim();
  closeOverlay();
  if (full.startsWith('tema')) {
    const v = full.includes('gelap') ? 'dark' : full.includes('terang') ? 'light' : 'system';
    store.setSetting('theme', v); applyTheme(); schedulePush(); return true;
  }
  const open = (route) => { createTab(internalUrl(route)); return true; };
  switch (name) {
    case 'privat': createTab(undefined, { private: true }); return true;
    case 'riwayat': return open('history');
    case 'penanda': return open('bookmarks');
    case 'unduhan': return open('downloads');
    case 'pengaturan': return open('settings');
    case 'privasi': return open('privacy');
    case 'ringkas': case 'baca': openReader(); return true;
    case 'bersihkan': for (const id of store.clearHistory()) engine.indexRemove(id); schedulePush(); return true;
    default: return false;
  }
}

async function openReader() {
  const a = activeTab();
  if (!a || !/^https?:/i.test(a.url)) { openOverlay('reader', { error: 'Mode baca hanya untuk halaman web.' }); return; }
  openOverlay('reader', { loading: true, title: a.title });
  try {
    const html = await a.view.webContents.executeJavaScript('document.documentElement.outerHTML', true);
    const r = await engine.readable(html.slice(0, 2_000_000));
    if (!r) { overlayView.webContents.send('overlay:show', { type: 'reader', data: { error: 'Mesin ringkasan belum aktif di build ini.' }, state: snapshot() }); return; }
    const sm = r.body ? await engine.summarize(r.body, 4) : null;
    overlayView.webContents.send('overlay:show', {
      type: 'reader',
      data: { title: r.title || a.title, body: r.body, summary: sm ? sm.sentences : [], keywords: sm ? sm.keywords : [], minutes: sm ? sm.reading_minutes : 0, url: a.url, empty: !r.body },
      state: snapshot(),
    });
  } catch {
    overlayView.webContents.send('overlay:show', { type: 'reader', data: { error: 'Halaman ini tidak bisa dibaca.' }, state: snapshot() });
  }
}

// ---------- siklus hidup ----------
const gotLock = app.requestSingleInstanceLock();
if (!gotLock) app.quit();
app.on('second-instance', (_e, argv) => {
  const url = argv.find((a) => /^https?:\/\//i.test(a));
  if (win) { if (win.isMinimized()) win.restore(); win.focus(); }
  if (url) createTab(url);
});

app.whenReady().then(async () => {
  store = new Store(path.join(app.getPath('userData'), 'ceet-data'));
  blocker = new Blocker(fs.readFileSync(resourcePath('blocklist.txt'), 'utf8'));
  const bin = findBinary([path.join(process.resourcesPath || '', 'engine')]);
  engine = new EngineBridge(bin);
  await engine.start();
  // Bangun ulang indeks pencarian lokal dari riwayat tersimpan (isi halaman tidak disimpan di disk).
  if (engine.available) for (const e of store.data.history.slice(0, 2000)) engine.indexAdd({ docid: e.id, url: e.url, title: e.title, body: '', visits: e.visits, last_visit: Math.floor(e.last / 1000) });

  registerProtocol();
  protectSession(session.fromPartition('persist:ceet'), false);
  protectSession(session.fromPartition('ceet-private'), true);
  registerIpc();
  applyTheme();

  win = new BaseWindow({
    width: 1280, height: 820, minWidth: 720, minHeight: 480, show: false,
    title: 'Ceet', backgroundColor: nativeTheme.shouldUseDarkColors ? '#0A1222' : '#FBFAF8',
    titleBarStyle: 'hidden',
    titleBarOverlay: { color: '#00000000', symbolColor: nativeTheme.shouldUseDarkColors ? '#EAF0FB' : '#0B1A30', height: 40 },
  });

  const uiPrefs = (preload) => ({ preload: PRELOAD(preload), contextIsolation: true, sandbox: true, nodeIntegration: false });
  shellView = new WebContentsView({ webPreferences: uiPrefs('preload-shell.js') });
  overlayView = new WebContentsView({ webPreferences: uiPrefs('preload-shell.js') });
  overlayView.setBackgroundColor('#00000000');
  shellView.setBackgroundColor('#00000000');
  win.contentView.addChildView(shellView);
  win.contentView.addChildView(overlayView);
  for (const v of [shellView, overlayView]) {
    v.webContents.setWindowOpenHandler(() => ({ action: 'deny' }));
    v.webContents.on('will-navigate', (e) => e.preventDefault());
    v.webContents.on('before-input-event', (e, input) => { if (input.type === 'keyDown' && handleShortcut(input)) e.preventDefault(); });
  }
  shellView.webContents.loadFile(path.join(UI, 'index.html'));
  overlayView.webContents.loadFile(path.join(UI, 'overlay.html'));

  win.on('resize', layout);
  win.on('closed', () => { engine.stop(); store.flush(); });
  nativeTheme.on('updated', () => { applyTheme(); schedulePush(); });

  await new Promise((r) => shellView.webContents.once('did-finish-load', r));
  layout();

  const s = store.data.session;
  const urls = process.argv.filter((a) => /^https?:\/\//i.test(a));
  if (store.settings.restoreSession && s.tabs && s.tabs.length && !urls.length) {
    s.tabs.forEach((t, i) => createTab(t.url, { pinned: t.pinned }));
    const idx = Math.min(s.active || 0, tabs.length - 1);
    activate(tabs[idx].id);
  } else {
    createTab(urls[0]);
    urls.slice(1).forEach((u) => createTab(u));
  }
  win.show();
  schedulePush();

  Menu.setApplicationMenu(Menu.buildFromTemplate([
    ...(process.platform === 'darwin' ? [{ role: 'appMenu' }] : []),
    { label: 'Berkas', submenu: [{ label: 'Tab baru', accelerator: 'CmdOrCtrl+T', click: () => createTab() }, { label: 'Tab privat', accelerator: 'CmdOrCtrl+Shift+N', click: () => createTab(undefined, { private: true }) }, { type: 'separator' }, { role: 'quit', label: 'Keluar' }] },
    { label: 'Edit', submenu: [{ role: 'undo', label: 'Urungkan' }, { role: 'redo', label: 'Ulangi' }, { type: 'separator' }, { role: 'cut', label: 'Potong' }, { role: 'copy', label: 'Salin' }, { role: 'paste', label: 'Tempel' }, { role: 'selectAll', label: 'Pilih semua' }] },
    { label: 'Tampilan', submenu: [{ label: 'Layar penuh', role: 'togglefullscreen' }] },
  ]));
});

setInterval(() => { if (store && tabs.length) persistSession(); }, 15000);
app.on('before-quit', () => { if (store) { persistSession(); store.flush(); } });
app.on('window-all-closed', () => { if (process.platform !== 'darwin') app.quit(); });
