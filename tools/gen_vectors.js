#!/usr/bin/env node
// Membuat "vektor emas" dari mesin Rust. Port Kotlin dan JavaScript wajib menghasilkan jawaban yang sama.
// Pakai: node tools/gen_vectors.js [path-biner]
const { spawnSync } = require('child_process');
const fs = require('fs');
const path = require('path');

const root = path.join(__dirname, '..');
const bin = process.argv[2] || path.join(root, 'engine/target/release/ceet-engine');

const classify = [
  'kompas.com', 'detik.com/news?x=1', 'localhost:3000', 'https://a.id/x', '192.168.1.1', 'github.com/anthropics',
  'resep nasi goreng', 'file.rs', 'versi 2.0', 'kopi', 'kenapa langit berwarna biru', 'harga emas hari ini?',
  'what is the capital of france', '> tema gelap', '   ', 'ftp://berkas.id/a', 'bca.co.id', 'a.b', 'tokopedia.com/p?id=1',
];
const clean = [
  'https://toko.id/p?id=7&utm_campaign=a&fbclid=xyz&_ga=1&warna=biru',
  'http://contoh.id/a?gclid=1', 'http://localhost:8080/x?utm_source=a', 'http://192.168.0.5/p',
  'https://youtube.com/watch?v=abc&si=zzz&feature=share', 'https://a.id/?q=kopi&utm_medium=x#bagian',
];
const phishing = [
  'https://www.google.com/search?q=a', 'https://accounts.google.com/signin', 'https://www.bca.co.id/id/login',
  'https://github.com/x/y', 'https://paypa1.com/login', 'https://tokopedla.com/', 'https://login.bca.co.id.verifikasi-akun.xyz/',
  'http://secure-dana-hadiah.top/klaim/login', 'https://xn--pypal-4ve.com/', 'http://45.12.77.9/bank/login',
  'https://google.com@evil.example/', 'https://id.wikipedia.org/wiki/Kopi', 'https://kompas.com/read/2026', 'http://localhost:8080/login',
];
const blocks = [
  ['www.google-analytics.com', 'toko.id'], ['fonts.gstatic.com', 'toko.id'], ['cdn.hotjar.com', 'berita.id'],
  ['static.hotjar.com', 'hotjar.com'], ['connect.facebook.net', 'facebook.com'], ['api.mixpanel.com', 'toko.id'], ['kompas.com', 'detik.com'],
];

const lines = [];
const meta = [];
let id = 0;
const push = (kind, input, req) => { lines.push(JSON.stringify({ id: ++id, ...req })); meta.push({ kind, input }); };
classify.forEach((input) => push('classify', input, { cmd: 'classify', input }));
clean.forEach((url) => push('clean_url', url, { cmd: 'clean_url', url }));
phishing.forEach((url) => push('phishing', url, { cmd: 'phishing', url }));
blocks.forEach(([host, page]) => push('block', { host, page }, { cmd: 'block_check', host, page }));

const r = spawnSync(bin, { input: lines.join('\n') + '\n', encoding: 'utf8' });
if (r.status !== 0) { console.error(r.stderr); process.exit(1); }
const out = r.stdout.trim().split('\n').map((l) => JSON.parse(l));
const vectors = {};
out.forEach((o, i) => {
  if (!o.ok) throw new Error('gagal: ' + JSON.stringify(o));
  const m = meta[i];
  (vectors[m.kind] ||= []).push({ input: m.input, expect: o.data });
});
const dest = path.join(root, 'shared/vectors/golden.json');
fs.mkdirSync(path.dirname(dest), { recursive: true });
fs.writeFileSync(dest, JSON.stringify(vectors, null, 1) + '\n');
console.log('ditulis', dest, Object.fromEntries(Object.entries(vectors).map(([k, v]) => [k, v.length])));
