'use strict';
// Jembatan ke mesin Rust (ceet-engine) lewat stdin/stdout JSON per baris.
// Jika biner tidak ada, jatuh ke mode terbatas dan melaporkannya apa adanya. Tidak ada fitur palsu.

const { spawn } = require('child_process');
const fs = require('fs');
const path = require('path');
const { cleanUrl } = require('./blocker');

function findBinary(extraDirs = []) {
  const exe = process.platform === 'win32' ? 'ceet-engine.exe' : 'ceet-engine';
  const cands = [
    ...extraDirs.map((d) => path.join(d, exe)),
    path.join(__dirname, '..', '..', 'engine', 'target', 'release', exe),
  ];
  return cands.find((p) => fs.existsSync(p)) || null;
}

class EngineBridge {
  constructor(binaryPath) {
    this.bin = binaryPath;
    this.proc = null;
    this.nextId = 1;
    this.pending = new Map();
    this.buf = '';
    this.info = null;
    this.restarts = 0;
  }

  get available() { return !!this.proc; }

  async start() {
    if (!this.bin) return false;
    try {
      this.proc = spawn(this.bin, [], { stdio: ['pipe', 'pipe', 'ignore'], windowsHide: true });
    } catch {
      this.proc = null;
      return false;
    }
    this.proc.stdout.setEncoding('utf8');
    this.proc.stdout.on('data', (d) => this._onData(d));
    this.proc.on('error', () => this._onExit());
    this.proc.on('exit', () => this._onExit());
    try {
      this.info = await this.call({ cmd: 'ping' }, 3000);
      return true;
    } catch {
      this.stop();
      return false;
    }
  }

  _onData(chunk) {
    this.buf += chunk;
    let i;
    while ((i = this.buf.indexOf('\n')) >= 0) {
      const line = this.buf.slice(0, i);
      this.buf = this.buf.slice(i + 1);
      if (!line.trim()) continue;
      let msg;
      try { msg = JSON.parse(line); } catch { continue; }
      const p = this.pending.get(msg.id);
      if (!p) continue;
      this.pending.delete(msg.id);
      clearTimeout(p.timer);
      if (msg.ok) p.resolve(msg.data);
      else p.reject(new Error(msg.error || 'mesin gagal'));
    }
  }

  _onExit() {
    this.proc = null;
    for (const [, p] of this.pending) { clearTimeout(p.timer); p.reject(new Error('mesin berhenti')); }
    this.pending.clear();
    // Mulai ulang otomatis, paling banyak 3 kali, agar crash tidak mematikan pencarian lokal.
    if (this.restarts < 3 && this.bin && !this.stopping) {
      this.restarts += 1;
      setTimeout(() => { this.start().catch(() => {}); }, 300);
    }
  }

  call(req, timeoutMs = 4000) {
    return new Promise((resolve, reject) => {
      if (!this.proc) return reject(new Error('mesin tidak aktif'));
      const id = this.nextId++;
      const timer = setTimeout(() => { this.pending.delete(id); reject(new Error('mesin terlalu lama menjawab')); }, timeoutMs);
      this.pending.set(id, { resolve, reject, timer });
      this.proc.stdin.write(JSON.stringify({ id, ...req }) + '\n');
    });
  }

  stop() {
    this.stopping = true;
    if (this.proc) { try { this.proc.kill(); } catch { /* sudah mati */ } }
    this.proc = null;
  }

  // ---- API tingkat tinggi, dengan jalur cadangan yang jujur ----

  async classify(input, searchTemplate) {
    if (this.available) {
      try { return await this.call({ cmd: 'classify', input, search: searchTemplate }); } catch { /* jatuh ke cadangan */ }
    }
    return fallbackClassify(input, searchTemplate);
  }

  async phishing(url) {
    if (this.available) {
      try { return await this.call({ cmd: 'phishing', url }); } catch { /* cadangan */ }
    }
    return { risk: 'tidak-diperiksa', score: 0, reasons: [] };
  }

  async summarize(text, sentences = 4) {
    if (!this.available) return null;
    try { return await this.call({ cmd: 'summarize', text, sentences }, 8000); } catch { return null; }
  }

  async readable(html) {
    if (!this.available) return null;
    try { return await this.call({ cmd: 'readable', html }, 8000); } catch { return null; }
  }

  async indexAdd(doc) {
    if (!this.available) return;
    try { await this.call({ cmd: 'index_add', ...doc }); } catch { /* abaikan */ }
  }

  async indexRemove(docid) {
    if (!this.available) return;
    try { await this.call({ cmd: 'index_remove', docid }); } catch { /* abaikan */ }
  }

  async search(q, limit = 20) {
    if (!this.available) return null;
    try { return await this.call({ cmd: 'search', q, now: Math.floor(Date.now() / 1000), limit }); } catch { return null; }
  }

  async suggest(q, limit = 6) {
    if (!this.available) return null;
    try { return await this.call({ cmd: 'suggest', q, now: Math.floor(Date.now() / 1000), limit }); } catch { return null; }
  }
}

function fallbackClassify(input, tpl = 'https://duckduckgo.com/?q={q}') {
  const s = String(input).trim();
  if (!s) return { kind: 'empty', value: '', arg: '', target: null };
  if (s.startsWith('>')) {
    const rest = s.slice(1).trim();
    const sp = rest.indexOf(' ');
    return { kind: 'command', value: (sp < 0 ? rest : rest.slice(0, sp)).toLowerCase(), arg: sp < 0 ? '' : rest.slice(sp + 1).trim(), target: null };
  }
  if (!/\s/.test(s)) {
    if (/^https?:\/\/\S+$/i.test(s)) return { kind: 'url', value: s, arg: '', target: s };
    if (/^(localhost|(\d{1,3}\.){3}\d{1,3})(:\d+)?(\/\S*)?$/.test(s)) return { kind: 'url', value: 'http://' + s, arg: '', target: 'http://' + s };
    if (/^[\p{L}\p{N}-]+(\.[\p{L}\p{N}-]+)*\.(com|id|net|org|io|co|dev|app|ai|me|tv|xyz|info|edu|gov)(\/\S*)?$/iu.test(s)) {
      return { kind: 'url', value: 'https://' + s, arg: '', target: 'https://' + s };
    }
  }
  return { kind: 'search', value: s, arg: '', target: tpl.replace('{q}', encodeURIComponent(s)) };
}

module.exports = { EngineBridge, findBinary, fallbackClassify, cleanUrl };
