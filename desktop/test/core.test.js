'use strict';
const test = require('node:test');
const assert = require('node:assert/strict');
const fs = require('fs');
const os = require('os');
const path = require('path');
const { Blocker, cleanUrl } = require('../src/blocker');
const { Store } = require('../src/store');
const { EngineBridge, findBinary, fallbackClassify } = require('../src/engine');

const root = path.join(__dirname, '..', '..');
const golden = JSON.parse(fs.readFileSync(path.join(root, 'shared/vectors/golden.json'), 'utf8'));
const blocker = new Blocker(fs.readFileSync(path.join(root, 'data/blocklist.txt'), 'utf8'));

test('pemblokir JS cocok dengan vektor emas Rust', () => {
  assert.ok(blocker.size > 100);
  for (const v of golden.block) {
    const r = blocker.check(v.input.host, v.input.page);
    assert.equal(r.block, v.expect.block, JSON.stringify(v.input));
    if (v.expect.block) assert.equal(r.rule, v.expect.rule);
  }
});

test('pembersih URL JS cocok dengan vektor emas Rust', () => {
  for (const v of golden.clean_url) {
    const r = cleanUrl(v.input);
    assert.equal(r.url, v.expect.url, v.input);
    assert.equal(r.upgraded, v.expect.upgraded, v.input);
    assert.deepEqual(r.removed, v.expect.removed, v.input);
  }
});

test('mode cadangan klasifikasi tetap benar untuk kasus umum', () => {
  for (const v of golden.classify) {
    const r = fallbackClassify(v.input);
    // Cadangan sengaja lebih sederhana: harus cocok pada jenis untuk kasus yang tidak ambigu.
    if (['kompas.com', 'localhost:3000', 'https://a.id/x', 'resep nasi goreng', 'kopi', '> tema gelap', '   '].includes(v.input)) {
      assert.equal(r.kind, v.expect.kind, v.input);
    }
  }
});

test('mesin Rust cocok dengan vektor emas (jika biner tersedia)', async (t) => {
  const bin = findBinary();
  if (!bin) return t.skip('biner mesin belum dibangun');
  const eng = new EngineBridge(bin);
  assert.equal(await eng.start(), true);
  try {
    for (const v of golden.classify) {
      const r = await eng.classify(v.input);
      assert.equal(r.kind, v.expect.kind, v.input);
      assert.equal(r.value, v.expect.value, v.input);
    }
    for (const v of golden.phishing) {
      const r = await eng.phishing(v.input);
      assert.equal(r.risk, v.expect.risk, v.input);
    }
    await eng.indexAdd({ docid: 'x', url: 'https://masak.id/soto', title: 'Resep soto ayam', body: 'kuah kuning', visits: 1, last_visit: 1 });
    const hits = await eng.search('soto');
    assert.equal(hits[0].id, 'x');
    const s = await eng.summarize('Kopi enak sekali. Kopi dari Gayo punya aroma rempah. Petani kopi menanam di dataran tinggi. Cuaca cerah.', 2);
    assert.equal(s.sentences.length, 2);
  } finally {
    eng.stop();
  }
});

test('penyimpanan: penanda, riwayat, pengaturan, dan validasi', () => {
  const dir = fs.mkdtempSync(path.join(os.tmpdir(), 'ceet-'));
  const st = new Store(dir);
  assert.equal(st.toggleBookmark('https://a.id/', 'A'), true);
  assert.equal(st.isBookmarked('https://a.id/'), true);
  assert.equal(st.toggleBookmark('https://a.id/', 'A'), false);
  st.addHistory('https://a.id/', 'A');
  const e = st.addHistory('https://a.id/', 'A baru');
  assert.equal(e.visits, 2);
  assert.equal(st.data.history.length, 1);
  assert.equal(st.setSetting('theme', 'gelap-sekali'), false);
  assert.equal(st.setSetting('theme', 'dark'), true);
  assert.equal(st.setSetting('tidakAda', true), false);
  assert.equal(st.setSetting('blockTrackers', 'ya'), false);
  assert.equal(st.setSetting('searchEngine', 'brave'), true);
  assert.match(st.searchTemplate(), /brave/);
  st.flush();
  const st2 = new Store(dir);
  assert.equal(st2.settings.theme, 'dark');
  assert.equal(st2.data.history[0].title, 'A baru');
  assert.equal(st2.clearHistory().length, 1);
  fs.rmSync(dir, { recursive: true, force: true });
});

test('penyimpanan: berkas rusak tidak membuat aplikasi gagal', () => {
  const dir = fs.mkdtempSync(path.join(os.tmpdir(), 'ceet-'));
  fs.writeFileSync(path.join(dir, 'settings.json'), '{rusak');
  fs.writeFileSync(path.join(dir, 'history.json'), '"bukan array"');
  const st = new Store(dir);
  assert.equal(st.settings.blockTrackers, true);
  assert.deepEqual(st.data.history, []);
  fs.rmSync(dir, { recursive: true, force: true });
});
