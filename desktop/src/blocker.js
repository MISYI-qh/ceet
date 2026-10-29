'use strict';
// Pemblokir pelacak untuk proses utama. Memakai daftar yang sama dengan mesin Rust (data/blocklist.txt),
// dan dicek terhadap vektor emas yang sama (shared/vectors/golden.json).

const MULTI = new Set([
  'co.id', 'or.id', 'ac.id', 'go.id', 'sch.id', 'web.id', 'my.id', 'biz.id', 'net.id', 'mil.id', 'desa.id',
  'co.uk', 'org.uk', 'ac.uk', 'gov.uk', 'com.au', 'net.au', 'org.au', 'co.jp', 'co.in', 'co.nz', 'com.sg',
  'com.my', 'com.br', 'com.cn', 'com.hk', 'com.tw', 'co.kr', 'co.za', 'com.mx', 'com.tr',
]);

function registrableDomain(host) {
  const l = host.split('.');
  if (l.length <= 2) return host;
  const last2 = l.slice(-2).join('.');
  if (MULTI.has(last2) && l.length >= 3) return l.slice(-3).join('.');
  return last2;
}

function parseList(text) {
  const domains = new Set();
  const allow = new Set();
  for (const raw of text.split(/\r?\n/)) {
    const line = raw.trim().toLowerCase();
    if (!line || line.startsWith('#')) continue;
    if (line.startsWith('@@')) allow.add(line.slice(2).trim());
    else domains.add(line);
  }
  return { domains, allow };
}

function matchSuffix(set, host) {
  let h = host;
  for (;;) {
    if (set.has(h)) return h;
    const i = h.indexOf('.');
    if (i < 0) return null;
    h = h.slice(i + 1);
  }
}

class Blocker {
  constructor(text) {
    const { domains, allow } = parseList(text);
    this.domains = domains;
    this.allow = allow;
  }
  get size() { return this.domains.size; }
  check(requestHost, pageHost) {
    const rh = String(requestHost).toLowerCase();
    const ph = String(pageHost || '').toLowerCase();
    if (ph && registrableDomain(rh) === registrableDomain(ph)) return { block: false };
    if (matchSuffix(this.allow, rh)) return { block: false };
    const rule = matchSuffix(this.domains, rh);
    return rule ? { block: true, rule } : { block: false };
  }
}

const TRACK = new Set(['fbclid', 'gclid', 'gclsrc', 'dclid', 'msclkid', 'yclid', 'twclid', 'ttclid', 'li_fat_id', 'mc_cid', 'mc_eid',
  'igshid', 'igsh', '_hsenc', '_hsmi', 'hsctatracking', 'mkt_tok', 'vero_id', 'wickedid', 'oly_enc_id', 'oly_anon_id', 'rb_clickid',
  's_cid', 'ref_src', 'ref_url', 'spm', 'scm', 'cmpid', 'ncid', 'sr_share', 'si', 'feature', 'pp']);
const TRACK_PREFIX = ['utm_', 'pk_', 'mtm_', 'matomo_', 'hsa_', 'ga_', 'itm_', '_ga'];

function isLocalHost(h) {
  if (h === 'localhost' || h.endsWith('.local') || h.endsWith('.localhost') || h === '[::1]') return true;
  const p = h.split('.').map(Number);
  if (p.length === 4 && p.every((n) => Number.isInteger(n))) {
    return p[0] === 10 || p[0] === 127 || (p[0] === 192 && p[1] === 168) || (p[0] === 172 && p[1] >= 16 && p[1] < 32);
  }
  return false;
}
function isIpHost(h) {
  return h.startsWith('[') || /^\d{1,3}(\.\d{1,3}){3}$/.test(h);
}

/** Naikkan ke https (kecuali lokal/IP) dan buang parameter pelacak. Mengembalikan { url, upgraded, removed }. */
function cleanUrl(input) {
  let u;
  try { u = new URL(input); } catch { return { url: input, upgraded: false, removed: [] }; }
  let upgraded = false;
  if (u.protocol === 'http:' && !isLocalHost(u.hostname) && !isIpHost(u.hostname)) {
    u.protocol = 'https:';
    if (u.port === '80') u.port = '';
    upgraded = true;
  }
  const removed = [];
  for (const k of [...u.searchParams.keys()]) {
    const lk = k.toLowerCase();
    if (TRACK.has(lk) || TRACK_PREFIX.some((p) => lk.startsWith(p))) removed.push(k);
  }
  if (removed.length) {
    const keep = [];
    const raw = u.search.replace(/^\?/, '');
    for (const part of raw.split('&')) {
      if (!part) continue;
      const k = decodeURIComponent(part.split('=')[0].replace(/\+/g, ' ')).toLowerCase();
      if (!(TRACK.has(k) || TRACK_PREFIX.some((p) => k.startsWith(p)))) keep.push(part);
    }
    u.search = keep.length ? '?' + keep.join('&') : '';
  }
  return { url: u.toString(), upgraded, removed };
}

module.exports = { Blocker, cleanUrl, registrableDomain, isLocalHost, isIpHost };
