'use strict';
// Penyimpanan lokal berbasis berkas JSON. Semua data tetap di perangkat. Tulis atomik dengan jeda singkat.

const fs = require('fs');
const path = require('path');

const DEFAULTS = {
  settings: {
    theme: 'system',
    searchEngine: 'ddg',
    blockTrackers: true,
    upgradeHttps: true,
    stripParams: true,
    sendGpc: true,
    trimReferrer: true,
    phishingCheck: true,
    indexContent: true,
    saveHistory: true,
    restoreSession: true,
    askPermissions: false,
  },
  bookmarks: [],
  history: [],
  session: { tabs: [], active: 0 },
  stats: { trackersBlocked: 0, paramsStripped: 0, httpsUpgraded: 0, phishingStopped: 0, since: 0 },
};

const ENGINES = {
  ddg: { name: 'DuckDuckGo', url: 'https://duckduckgo.com/?q={q}' },
  brave: { name: 'Brave Search', url: 'https://search.brave.com/search?q={q}' },
  startpage: { name: 'Startpage', url: 'https://www.startpage.com/do/search?q={q}' },
  ecosia: { name: 'Ecosia', url: 'https://www.ecosia.org/search?q={q}' },
  google: { name: 'Google', url: 'https://www.google.com/search?q={q}' },
  bing: { name: 'Bing', url: 'https://www.bing.com/search?q={q}' },
};

const HISTORY_MAX = 5000;

class Store {
  constructor(dir) {
    this.dir = dir;
    this.data = JSON.parse(JSON.stringify(DEFAULTS));
    this.timers = new Map();
    fs.mkdirSync(dir, { recursive: true });
    for (const key of Object.keys(DEFAULTS)) this._load(key);
    if (!this.data.stats.since) this.data.stats.since = Date.now();
  }

  _file(key) { return path.join(this.dir, key + '.json'); }

  _load(key) {
    try {
      const raw = JSON.parse(fs.readFileSync(this._file(key), 'utf8'));
      const def = DEFAULTS[key];
      if (Array.isArray(def)) { if (Array.isArray(raw)) this.data[key] = raw; }
      else if (raw && typeof raw === 'object') this.data[key] = { ...def, ...raw };
    } catch { /* berkas belum ada atau rusak: pakai bawaan */ }
  }

  _save(key) {
    clearTimeout(this.timers.get(key));
    this.timers.set(key, setTimeout(() => this.flush(key), 400));
  }

  flush(key) {
    const keys = key ? [key] : Object.keys(DEFAULTS);
    for (const k of keys) {
      clearTimeout(this.timers.get(k));
      this.timers.delete(k);
      const file = this._file(k);
      const tmp = file + '.tmp';
      try {
        fs.writeFileSync(tmp, JSON.stringify(this.data[k]));
        fs.renameSync(tmp, file);
      } catch { /* disk penuh atau terkunci: coba lagi pada tulis berikutnya */ }
    }
  }

  get settings() { return this.data.settings; }

  setSetting(key, value) {
    if (!(key in DEFAULTS.settings)) return false;
    if (typeof value !== typeof DEFAULTS.settings[key]) return false;
    if (key === 'searchEngine' && !ENGINES[value]) return false;
    if (key === 'theme' && !['system', 'light', 'dark'].includes(value)) return false;
    this.data.settings[key] = value;
    this._save('settings');
    return true;
  }

  searchTemplate() {
    return (ENGINES[this.data.settings.searchEngine] || ENGINES.ddg).url;
  }

  // ---- penanda ----
  isBookmarked(url) { return this.data.bookmarks.some((b) => b.url === url); }

  toggleBookmark(url, title) {
    const i = this.data.bookmarks.findIndex((b) => b.url === url);
    if (i >= 0) this.data.bookmarks.splice(i, 1);
    else this.data.bookmarks.unshift({ id: 'b' + Date.now().toString(36), url, title: title || url, added: Date.now() });
    this._save('bookmarks');
    return i < 0;
  }

  removeBookmark(id) {
    const i = this.data.bookmarks.findIndex((b) => b.id === id);
    if (i >= 0) this.data.bookmarks.splice(i, 1);
    this._save('bookmarks');
    return i >= 0;
  }

  // ---- riwayat ----
  addHistory(url, title) {
    const h = this.data.history;
    const now = Date.now();
    const i = h.findIndex((e) => e.url === url);
    let entry;
    if (i >= 0) {
      entry = h.splice(i, 1)[0];
      entry.visits += 1;
      entry.last = now;
      if (title) entry.title = title;
    } else {
      entry = { id: 'h' + now.toString(36) + Math.random().toString(36).slice(2, 5), url, title: title || url, visits: 1, last: now };
    }
    h.unshift(entry);
    if (h.length > HISTORY_MAX) h.length = HISTORY_MAX;
    this._save('history');
    return entry;
  }

  updateHistoryTitle(url, title) {
    const e = this.data.history.find((x) => x.url === url);
    if (e && title) { e.title = title; this._save('history'); }
    return e || null;
  }

  removeHistory(id) {
    const i = this.data.history.findIndex((e) => e.id === id);
    const e = i >= 0 ? this.data.history.splice(i, 1)[0] : null;
    this._save('history');
    return e;
  }

  clearHistory() {
    const ids = this.data.history.map((e) => e.id);
    this.data.history = [];
    this._save('history');
    return ids;
  }

  // ---- statistik privasi ----
  bump(stat, n = 1) {
    if (stat in this.data.stats && typeof this.data.stats[stat] === 'number') {
      this.data.stats[stat] += n;
      this._save('stats');
    }
  }

  resetStats() {
    this.data.stats = { ...DEFAULTS.stats, since: Date.now() };
    this._save('stats');
  }

  // ---- sesi ----
  saveSession(tabs, active) {
    this.data.session = { tabs: tabs.slice(0, 60), active };
    this._save('session');
  }
}

module.exports = { Store, ENGINES, DEFAULTS };
