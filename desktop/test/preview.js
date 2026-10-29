'use strict';
// Alat pratinjau pengembang: merender UI Ceet di Chromium dengan data tiruan, lalu menyimpan tangkapan layar.
// Pakai: node test/preview.js <folder-keluaran>
const fs = require('fs');
const os = require('os');
const path = require('path');
const { chromium } = require('playwright');

const out = process.argv[2] || path.join(os.tmpdir(), 'ceet-preview');
fs.mkdirSync(out, { recursive: true });
const src = path.join(__dirname, '..', 'ui');
const tmp = fs.mkdtempSync(path.join(os.tmpdir(), 'ceet-ui-'));
fs.cpSync(src, tmp, { recursive: true });
const appHtml = path.join(tmp, 'pages', 'app.html');
fs.writeFileSync(appHtml, fs.readFileSync(appHtml, 'utf8').replace(/ceet:\/\/ui\/pages\//g, './').replace(/ceet:\/\/ui\//g, '../'));

const NOW = Date.now();
const state = (extra = {}) => ({
  platform: 'linux', dark: false, theme: 'system', settings: { blockTrackers: true }, engineAvailable: true, engineInfo: { version: '0.1.0', blocklist: 174 },
  activeId: 2, findOpen: false, closedCount: 1,
  tabs: [
    { id: 1, title: 'Resep nasi goreng kampung', url: 'https://masak.id/nasi-goreng', favicon: '', loading: false, private: false, pinned: true, blocked: 0 },
    { id: 2, title: 'Kompas.com: Berita Terkini', url: 'https://www.kompas.com/', favicon: '', loading: false, private: false, pinned: false, blocked: 7 },
    { id: 3, title: 'Belajar Rust untuk pemula', url: 'https://belajar.dev/rust', favicon: '', loading: true, private: false, pinned: false, blocked: 2 },
    { id: 4, title: 'Tab baru', url: '', internal: true, private: true, pinned: false, blocked: 0 },
  ],
  nav: { url: 'https://www.kompas.com/', secure: true, internal: false, loading: false, canBack: true, canForward: false, bookmarked: true, private: false, blocked: 7, trackers: ['google-analytics.com', 'doubleclick.net', 'connect.facebook.net', 'hotjar.com'], upgraded: true, stripped: 2, risk: 'aman', riskReasons: [], host: 'www.kompas.com' },
  stats: { trackersBlocked: 1284, paramsStripped: 96, httpsUpgraded: 211, phishingStopped: 3, since: NOW - 40 * 864e5 },
  downloads: [], ...extra,
});

const mock = (st, theme) => `
(() => {
  const st = ${JSON.stringify(st)};
  st.theme = ${JSON.stringify(theme)};
  const noop = () => {};
  const ok = async () => true;
  const h = [
    { id: 'h1', url: 'https://masak.id/nasi-goreng', title: 'Resep nasi goreng kampung', visits: 6, last: ${NOW} - 3e5 },
    { id: 'h2', url: 'https://www.kompas.com/', title: 'Kompas.com: Berita Terkini', visits: 21, last: ${NOW} - 36e5 },
    { id: 'h3', url: 'https://belajar.dev/rust', title: 'Belajar Rust untuk pemula', visits: 9, last: ${NOW} - 9e7, why: ['mirip makna'] },
    { id: 'h4', url: 'https://id.wikipedia.org/wiki/Kopi', title: 'Kopi - Wikipedia bahasa Indonesia', visits: 2, last: ${NOW} - 3e8 },
  ];
  window.ceet = {
    state: async () => st, onState: noop, onFocusOmnibox: noop, onFind: noop, onFindResult: noop, onOverlay: (fn) => { window.__overlay = fn; }, onOverlayClosed: noop,
    onSuggestItems: noop, onSuggestSelect: (fn) => { window.__select = fn; },
    newTab: ok, closeTab: ok, activateTab: ok, tabMenu: ok, reopenTab: ok, go: ok, back: ok, forward: ok, reload: ok, home: ok, omniboxInput: ok, suggestSelect: ok,
    closeOverlay: ok, openOverlay: ok, toggleBookmark: ok, findOpen: ok, findQuery: ok, zoom: ok, devtools: ok, copyUrl: ok, openUrl: ok, openUrlNew: ok, showDownload: ok, setSetting: ok, menuAction: ok,
  };
  window.ceetPage = {
    settings: async () => ({ settings: { theme: ${JSON.stringify(theme)}, searchEngine: 'ddg', blockTrackers: true, upgradeHttps: true, stripParams: true, sendGpc: true, trimReferrer: true, phishingCheck: true, indexContent: true, saveHistory: true, restoreSession: true, askPermissions: false },
      engines: { ddg: 'DuckDuckGo', brave: 'Brave Search', startpage: 'Startpage', ecosia: 'Ecosia', google: 'Google', bing: 'Bing' }, engine: { version: '0.1.0', blocklist: 174 }, engineAvailable: true, stats: st.stats }),
    setSetting: ok, history: async (q) => ({ items: q ? h.slice(2, 3) : h, semantic: !!q }), historyRemove: ok, historyClear: ok, bookmarks: async () => [{ id: 'b1', url: 'https://www.kompas.com/', title: 'Kompas.com: Berita Terkini', added: ${NOW} - 864e5 }],
    downloads: async () => [{ id: 'd1', name: 'laporan-2026.pdf', state: 'selesai', received: 2400000, total: 2400000 }, { id: 'd2', name: 'foto-liburan.zip', state: 'berjalan', received: 41e6, total: 120e6 }],
    showDownload: ok, resetStats: ok, navigate: ok, proceed: ok, search: ok, topSites: async () => [
      { host: 'kompas.com', url: 'https://kompas.com/', title: 'Kompas', visits: 21 }, { host: 'masak.id', url: 'https://masak.id/', title: 'Masak', visits: 6 },
      { host: 'belajar.dev', url: 'https://belajar.dev/', title: 'Belajar', visits: 9 }, { host: 'id.wikipedia.org', url: 'https://id.wikipedia.org/', title: 'Wikipedia', visits: 4 },
      { host: 'github.com', url: 'https://github.com/', title: 'GitHub', visits: 30 }, { host: 'detik.com', url: 'https://detik.com/', title: 'Detik', visits: 3 } ],
  };
})();`;

(async () => {
  const browser = await chromium.launch();
  const shots = [];
  for (const theme of ['light', 'dark']) {
    const scheme = theme;
    const ctx = await browser.newContext({ viewport: { width: 1180, height: 720 }, colorScheme: scheme, deviceScaleFactor: 1 });
    const errors = [];
    const open = async (file, route, init) => {
      const page = await ctx.newPage();
      page.on('pageerror', (e) => errors.push(file + ': ' + e.message));
      page.on('console', (m) => { if (m.type() === 'error') errors.push(file + ' console: ' + m.text()); });
      await page.addInitScript(mock(state(), theme));
      if (route) await page.addInitScript(`window.__CEET_ROUTE__ = ${JSON.stringify(route)};`);
      await page.goto('file://' + path.join(tmp, file));
      await page.waitForTimeout(250);
      if (init) { await init(page); await page.waitForTimeout(300); }
      return page;
    };
    const snap = async (page, name) => { const f = path.join(out, `${name}-${theme}.png`); await page.screenshot({ path: f }); shots.push(f); await page.close(); };

    let p = await open('index.html'); await p.evaluate(() => { document.body.style.background = getComputedStyle(document.documentElement).getPropertyValue('--bg'); }); await snap(p, 'shell');

    p = await open('overlay.html', null, async (pg) => {
      await pg.evaluate((s) => { document.body.style.background = getComputedStyle(document.documentElement).getPropertyValue('--bg'); window.__overlay({ type: 'suggest', rect: { x: 150, y: 46, w: 700, h: 36 }, state: s, data: { q: 'kopi', items: [
        { kind: 'search', title: 'kopi', sub: 'Cari di DuckDuckGo', value: 'kopi', icon: 'search' },
        { kind: 'history', title: 'Kopi - Wikipedia bahasa Indonesia', sub: 'id.wikipedia.org/wiki/Kopi', value: 'x', icon: 'clock', why: 'mirip makna' },
        { kind: 'bookmark', title: 'Kopi Kenangan', sub: 'kopikenangan.com', value: 'y', icon: 'star' }] } }); window.__select(1); }, state());
    }); await snap(p, 'overlay-suggest');

    p = await open('overlay.html', null, async (pg) => { await pg.evaluate((s) => { document.body.style.background = getComputedStyle(document.documentElement).getPropertyValue('--bg'); window.__overlay({ type: 'shield', rect: { x: 160, y: 50, w: 30, h: 30 }, state: s }); }, state()); }); await snap(p, 'overlay-shield');
    p = await open('overlay.html', null, async (pg) => { await pg.evaluate((s) => { document.body.style.background = getComputedStyle(document.documentElement).getPropertyValue('--bg'); window.__overlay({ type: 'menu', rect: { x: 1130, y: 50, w: 34, h: 34 }, state: s }); }, state({ dark: theme === 'dark' })); }); await snap(p, 'overlay-menu');
    p = await open('overlay.html', null, async (pg) => { await pg.evaluate((s) => { document.body.style.background = getComputedStyle(document.documentElement).getPropertyValue('--bg'); window.__overlay({ type: 'reader', state: s, data: { title: 'Kopi Indonesia dikenal di seluruh dunia', url: 'https://x.id', minutes: 3, keywords: ['kopi', 'petani', 'ekspor'], summary: ['Kopi Indonesia dikenal di seluruh dunia karena ragam cita rasanya.', 'Ekspor kopi Indonesia meningkat setiap tahun karena permintaan global yang kuat.'], body: 'Kopi Indonesia dikenal di seluruh dunia karena ragam cita rasanya. Kopi Gayo dari Aceh punya aroma rempah.\n\nPetani kopi di Toraja menanam kopi di dataran tinggi yang sejuk dan lembap.' } }); }, state()); }); await snap(p, 'overlay-reader');

    for (const r of ['home', 'history', 'bookmarks', 'downloads', 'settings', 'privacy', 'warning', 'error']) {
      const pg = await ctx.newPage();
      pg.on('pageerror', (e) => errors.push(r + ': ' + e.message));
      await pg.addInitScript(mock(state(), theme));
      await pg.addInitScript(`window.__CEET_ROUTE__ = ${JSON.stringify(r)};`);
      await pg.goto('file://' + appHtml + (r === 'warning' ? '?url=https%3A%2F%2Fpaypa1.com%2Flogin&reasons=Ejaan%20hampir%20sama%20dengan%20%22paypal%22|Kata%20pancingan%20di%20alamat' : r === 'error' ? '?url=https%3A%2F%2Fcontoh.id&code=-105' : ''));
      await pg.waitForTimeout(250);
      await snap(pg, 'page-' + r);
    }
    if (errors.length) console.log('GALAT', theme, errors);
    await ctx.close();
  }
  await browser.close();
  console.log(shots.length + ' tangkapan layar di ' + out);
})();
