/* Halaman internal ceet://app/<rute>: home, history, bookmarks, downloads, settings, privacy, warning, error. */
(function () {
  'use strict';
  var I = window.CeetIcons;
  var api = window.ceetPage;
  var root = document.getElementById('root');
  var q = new URLSearchParams(location.search);
  var route = window.__CEET_ROUTE__ || location.pathname.replace(/^\/+/, '') || 'home';

  function esc(s) { return String(s == null ? '' : s).replace(/[&<>"']/g, function (c) { return { '&': '&amp;', '<': '&lt;', '>': '&gt;', '"': '&quot;', "'": '&#39;' }[c]; }); }
  function hue(s) { var h = 0; for (var i = 0; i < s.length; i++) h = (h * 31 + s.charCodeAt(i)) % 360; return 205 + (h % 55); }
  function host(u) { try { return new URL(u).hostname.replace(/^www\./, ''); } catch (e) { return u; } }
  function badge(name) { var l = (name || '?').replace(/^https?:\/\/(www\.)?/, '').charAt(0).toUpperCase(); return '<span class="badge" style="background:hsl(' + hue(name || '?') + ' 72% 46%)">' + esc(l) + '</span>'; }
  function ago(t) {
    var s = Math.max(0, (Date.now() - t) / 1000);
    if (s < 60) return 'baru saja';
    if (s < 3600) return Math.floor(s / 60) + ' menit lalu';
    if (s < 86400) return Math.floor(s / 3600) + ' jam lalu';
    if (s < 604800) return Math.floor(s / 86400) + ' hari lalu';
    return new Date(t).toLocaleDateString('id-ID', { day: 'numeric', month: 'short', year: 'numeric' });
  }
  function bytes(n) { if (!n || n < 0) return '-'; var u = ['B', 'KB', 'MB', 'GB']; var i = 0; while (n >= 1024 && i < 3) { n /= 1024; i++; } return n.toFixed(i ? 1 : 0) + ' ' + u[i]; }
  function applyTheme(t) { var r = document.documentElement; if (t === 'system') r.removeAttribute('data-theme'); else r.setAttribute('data-theme', t); }
  function head(title, sub) { return '<h1>' + esc(title) + '</h1>' + (sub ? '<p class="sub">' + esc(sub) + '</p>' : ''); }
  function mount(html, cls) { root.innerHTML = '<div class="page ' + (cls || '') + '">' + html + '</div>'; }

  // ---------- beranda ----------
  async function home() {
    var s = await api.settings();
    applyTheme(s.settings.theme);
    var sites = await api.topSites();
    var h = new Date().getHours();
    var greet = h < 4 ? 'Selamat malam' : h < 11 ? 'Selamat pagi' : h < 15 ? 'Selamat siang' : h < 18 ? 'Selamat sore' : 'Selamat malam';
    var tiles = sites.length
      ? sites.map(function (x) { return '<a class="site" data-url="' + esc(x.url) + '" title="' + esc(x.title) + '">' + badge(x.host) + '<span class="n">' + esc(x.host) + '</span></a>'; }).join('')
      : '';
    root.innerHTML = '<div class="page home"><div class="mark">' + I.logo + '</div><div class="word">ceet</div>' +
      '<div class="greet">' + greet + '. Mau cari apa?</div>' +
      '<form id="f">' + I.svg('search', 20) + '<input id="q" type="text" placeholder="Cari atau ketik alamat" aria-label="Cari" autofocus autocomplete="off" spellcheck="false"><button class="go" type="submit">Cari</button></form>' +
      '<div class="meta">' + I.svg('shieldCheck', 16) + '<span>' + s.stats.trackersBlocked.toLocaleString('id-ID') + ' pelacak diblokir sejak awal</span></div>' +
      (tiles ? '<div class="sitehead">Sering dibuka</div><div class="sites">' + tiles + '</div>' : '') + '</div>';
    document.getElementById('q').focus();
    document.getElementById('f').addEventListener('submit', function (e) { e.preventDefault(); var v = document.getElementById('q').value; if (v.trim()) api.search(v); });
    root.addEventListener('click', function (e) { var a = e.target.closest('.site'); if (a) api.navigate(a.dataset.url); });
  }

  // ---------- riwayat ----------
  async function history() {
    mount(head('Riwayat', 'Tersimpan hanya di perangkat ini. Pencarian memahami makna, bukan hanya kata yang sama.') +
      '<div class="bar"><label class="search">' + I.svg('search', 18) + '<input id="s" placeholder="Cari riwayat" aria-label="Cari riwayat" autocomplete="off"></label><button class="btn danger" id="clear">' + I.svg('trash', 16) + 'Hapus semua</button></div><div id="out"></div>');
    var out = document.getElementById('out');
    var timer;
    async function load(qv) {
      var r = await api.history(qv);
      if (!r.items.length) { out.innerHTML = '<div class="card empty">' + (qv ? 'Tidak ada yang cocok dengan "' + esc(qv) + '".' : 'Belum ada riwayat.') + '</div>'; return; }
      out.innerHTML = '<div class="card list">' + r.items.map(function (e) {
        return '<div class="item" data-id="' + esc(e.id) + '">' + badge(host(e.url)) + '<div class="t"><a data-url="' + esc(e.url) + '">' + esc(e.title || e.url) + '</a><small>' + esc(host(e.url)) + '</small></div>' +
          (e.why && e.why[0] ? '<span class="why">' + esc(e.why[0]) + '</span>' : '') + '<span class="when">' + ago(e.last) + '</span><button class="x" data-rm="' + esc(e.id) + '" aria-label="Hapus dari riwayat">' + I.svg('close', 16) + '</button></div>';
      }).join('') + '</div>';
    }
    document.getElementById('s').addEventListener('input', function (e) { clearTimeout(timer); timer = setTimeout(function () { load(e.target.value); }, 120); });
    var clear = document.getElementById('clear');
    clear.addEventListener('click', async function () {
      if (clear.dataset.sure !== '1') { clear.dataset.sure = '1'; clear.lastChild.textContent = 'Ketuk lagi untuk menghapus'; setTimeout(function () { clear.dataset.sure = ''; clear.lastChild.textContent = 'Hapus semua'; }, 3000); return; }
      await api.historyClear(); load('');
    });
    out.addEventListener('click', async function (e) {
      var a = e.target.closest('a[data-url]'); if (a) return api.navigate(a.dataset.url);
      var x = e.target.closest('[data-rm]'); if (x) { await api.historyRemove(x.dataset.rm); load(document.getElementById('s').value); }
    });
    load('');
  }

  // ---------- penanda ----------
  async function bookmarks() {
    mount(head('Penanda', 'Situs yang kamu simpan dengan tombol bintang.') + '<div id="out"></div>');
    var out = document.getElementById('out');
    async function load() {
      var list = await api.bookmarks();
      if (!list.length) { out.innerHTML = '<div class="card empty">Belum ada penanda. Buka sebuah situs lalu ketuk bintang di kolom alamat.</div>'; return; }
      out.innerHTML = '<div class="card list">' + list.map(function (b) {
        return '<div class="item">' + badge(host(b.url)) + '<div class="t"><a data-url="' + esc(b.url) + '">' + esc(b.title) + '</a><small>' + esc(host(b.url)) + '</small></div><span class="when">' + ago(b.added) + '</span><button class="x" data-rm="' + esc(b.id) + '" aria-label="Hapus penanda">' + I.svg('close', 16) + '</button></div>';
      }).join('') + '</div>';
    }
    out.addEventListener('click', async function (e) {
      var a = e.target.closest('a[data-url]'); if (a) return api.navigate(a.dataset.url);
      var x = e.target.closest('[data-rm]'); if (x) { await api.bookmarkRemove(x.dataset.rm); load(); }
    });
    load();
  }

  // ---------- unduhan ----------
  async function downloads() {
    mount(head('Unduhan') + '<div id="out"></div>');
    var out = document.getElementById('out');
    async function load() {
      var list = await api.downloads();
      if (!list.length) { out.innerHTML = '<div class="card empty">Belum ada unduhan.</div>'; return; }
      out.innerHTML = '<div class="card list">' + list.map(function (d) {
        var pct = d.total > 0 ? Math.min(100, Math.round(d.received / d.total * 100)) : 0;
        return '<div class="item"><span class="badge" style="background:var(--accent)">' + I.svg('download', 16) + '</span><div class="t"><a data-show="' + esc(d.id) + '">' + esc(d.name) + '</a><small>' + esc(d.state) + ' · ' + bytes(d.received) + (d.total > 0 ? ' dari ' + bytes(d.total) : '') + '</small>' +
          (d.state === 'berjalan' ? '<div class="progress"><i style="width:' + pct + '%"></i></div>' : '') + '</div></div>';
      }).join('') + '</div>';
    }
    out.addEventListener('click', function (e) { var a = e.target.closest('[data-show]'); if (a) api.showDownload(a.dataset.show); });
    load(); setInterval(load, 1500);
  }

  // ---------- pengaturan ----------
  var TOGGLES = [
    ['Privasi', [
      ['blockTrackers', 'Blokir pelacak dan iklan', 'Permintaan ke domain pelacak yang dikenal dibatalkan sebelum terkirim.'],
      ['upgradeHttps', 'Utamakan HTTPS', 'Alamat http:// otomatis dinaikkan ke https://, kecuali alamat lokal.'],
      ['stripParams', 'Buang parameter pelacak dari tautan', 'Contoh: utm_source, fbclid, gclid.'],
      ['sendGpc', 'Kirim sinyal Global Privacy Control', 'Meminta situs tidak menjual atau membagikan datamu. Situs boleh mengabaikannya.'],
      ['trimReferrer', 'Persingkat informasi asal tautan', 'Situs lain hanya melihat nama situsmu, bukan alamat lengkap halaman.'],
      ['phishingCheck', 'Periksa situs penipuan', 'Dinilai di perangkatmu. Alamat tidak dikirim ke server mana pun.'],
      ['askPermissions', 'Tanya sebelum situs memakai kamera, mikrofon, atau lokasi', 'Bila mati, semua izin itu ditolak otomatis.'],
    ]],
    ['Data', [
      ['saveHistory', 'Simpan riwayat', 'Tab privat tidak pernah disimpan.'],
      ['indexContent', 'Cari berdasarkan isi halaman', 'Isi halaman hanya diindeks di memori selama aplikasi berjalan, tidak ditulis ke disk.'],
      ['restoreSession', 'Pulihkan tab saat dibuka kembali', ''],
    ]],
  ];
  async function settings() {
    var s = await api.settings();
    function render() {
      applyTheme(s.settings.theme);
      var eng = Object.keys(s.engines).map(function (k) { return '<button data-set="searchEngine:' + k + '" aria-pressed="' + (s.settings.searchEngine === k) + '">' + esc(s.engines[k]) + '</button>'; }).join('');
      var th = [['system', 'Otomatis'], ['light', 'Terang'], ['dark', 'Gelap']].map(function (t) { return '<button data-set="theme:' + t[0] + '" aria-pressed="' + (s.settings.theme === t[0]) + '">' + t[1] + '</button>'; }).join('');
      var html = head('Pengaturan') +
        '<div class="group"><h2>Tampilan dan pencarian</h2><div class="card"><div class="field"><div class="l"><b>Tema</b><small>Ikuti perangkat atau pilih sendiri</small></div><div class="seg">' + th + '</div></div>' +
        '<div class="field"><div class="l"><b>Mesin pencari</b><small>Dipakai untuk kata kunci yang kamu ketik di omnibox</small></div><div class="seg">' + eng + '</div></div></div></div>';
      TOGGLES.forEach(function (g) {
        html += '<div class="group"><h2>' + g[0] + '</h2><div class="card">' + g[1].map(function (t) {
          return '<div class="field"><div class="l"><b>' + esc(t[1]) + '</b>' + (t[2] ? '<small>' + esc(t[2]) + '</small>' : '') + '</div><button class="toggle" role="switch" aria-label="' + esc(t[1]) + '" data-toggle="' + t[0] + '" aria-checked="' + !!s.settings[t[0]] + '"></button></div>';
        }).join('') + '</div></div>';
      });
      html += '<div class="group"><h2>Tentang</h2><div class="card"><div class="field"><div class="l"><b>Mesin lokal</b><small>' + (s.engineAvailable ? 'Rust ' + esc(s.engine && s.engine.version) + ' · ' + (s.engine && s.engine.blocklist) + ' domain pelacak' : 'Tidak aktif. Pencarian makna, ringkasan, dan deteksi penipuan dimatikan.') + '</small></div><span class="status ' + (s.engineAvailable ? 'ok' : 'off') + '">' + (s.engineAvailable ? 'Aktif' : 'Terbatas') + '</span></div></div></div>';
      mount(html);
    }
    render();
    root.addEventListener('click', async function (e) {
      var b = e.target.closest('[data-set]');
      if (b) { var kv = b.dataset.set.split(':'); if (await api.setSetting(kv[0], kv[1])) { s.settings[kv[0]] = kv[1]; render(); } return; }
      var t = e.target.closest('[data-toggle]');
      if (t) { var k = t.dataset.toggle; var v = !s.settings[k]; if (await api.setSetting(k, v)) { s.settings[k] = v; render(); } }
    });
  }

  // ---------- dasbor privasi ----------
  async function privacy() {
    var s = await api.settings();
    applyTheme(s.settings.theme);
    var st = s.stats;
    var since = new Date(st.since).toLocaleDateString('id-ID', { day: 'numeric', month: 'long', year: 'numeric' });
    function stat(n, d) { return '<div class="card stat"><div class="n">' + Number(n).toLocaleString('id-ID') + '</div><div class="d">' + d + '</div></div>'; }
    mount(head('Dasbor privasi', 'Dihitung di perangkatmu sejak ' + since + '. Tidak ada yang dikirim ke mana pun.', 'wide') +
      '<div class="stats">' + stat(st.trackersBlocked, 'Permintaan pelacak diblokir') + stat(st.paramsStripped, 'Parameter pelacak dibuang') + stat(st.httpsUpgraded, 'Tautan dinaikkan ke HTTPS') + stat(st.phishingStopped, 'Situs penipuan dihentikan') + '</div>' +
      '<div class="card honest"><b>Yang Ceet lakukan.</b> Membatalkan permintaan ke domain pelacak yang dikenal, membuang parameter pelacak dari tautan, memaksa HTTPS bila memungkinkan, mengirim sinyal Global Privacy Control, memangkas informasi asal tautan, menolak izin kamera, mikrofon, dan lokasi secara bawaan, dan menilai situs penipuan di perangkatmu.<br><br>' +
      '<b>Yang Ceet tidak lakukan.</b> Ceet tidak menyembunyikan alamat IP-mu dari situs atau penyedia internet, tidak menjadikanmu anonim, dan tidak menjamin perlindungan penuh. Daftar pelacak hanya mencakup domain yang dikenal, jadi sebagian pelacak baru bisa lolos. Mode privat hanya tidak menyimpan riwayat dan data situs di perangkat ini.</div>' +
      '<p style="margin-top:18px"><button class="btn" id="reset">Setel ulang hitungan</button></p>');
    document.getElementById('reset').addEventListener('click', async function () { await api.resetStats(); privacy(); });
  }

  // ---------- peringatan situs berbahaya ----------
  async function warning() {
    var s = await api.settings(); applyTheme(s.settings.theme);
    var url = q.get('url') || '';
    var reasons = (q.get('reasons') || '').split('|').filter(Boolean);
    root.innerHTML = '<div class="notice"><div class="big bad">' + I.svg('shieldAlert', 38) + '</div><h1>Situs ini kemungkinan penipuan</h1>' +
      '<p>Ceet menghentikan pemuatan karena alamat ini punya ciri-ciri situs palsu.</p><span class="url">' + esc(url) + '</span>' +
      (reasons.length ? '<br><ul>' + reasons.map(function (r) { return '<li>' + esc(r) + '</li>'; }).join('') + '</ul>' : '') +
      '<div class="acts"><button class="btn main" id="back">Kembali ke aman</button></div>' +
      '<p class="small">Penilaian ini perkiraan dan bisa keliru. <button id="go">Saya paham risikonya, buka tetap</button></p></div>';
    document.getElementById('back').addEventListener('click', function () { history.length > 1 ? history.back() : api.navigate('ceet://app/home'); });
    document.getElementById('go').addEventListener('click', function () { api.proceed(url); });
  }

  // ---------- galat muat ----------
  async function failure() {
    var s = await api.settings(); applyTheme(s.settings.theme);
    var url = q.get('url') || '';
    var code = Number(q.get('code'));
    var msg = { '-105': 'Nama situs tidak ditemukan. Periksa ejaan alamatnya atau koneksi internetmu.', '-106': 'Kamu sedang offline. Periksa koneksi internet lalu coba lagi.', '-102': 'Situs menolak sambungan.', '-118': 'Situs terlalu lama menjawab.', '-200': 'Sertifikat keamanan situs bermasalah, jadi Ceet tidak membukanya.' }[String(code)] || 'Halaman tidak bisa dimuat.';
    root.innerHTML = '<div class="notice"><div class="big neutral">' + I.svg('globe', 38) + '</div><h1>Tidak bisa membuka situs ini</h1><p>' + esc(msg) + '</p><span class="url">' + esc(url) + '</span>' +
      '<div class="acts"><button class="btn main" id="retry">Coba lagi</button></div><p class="small">Kode galat ' + esc(q.get('code') || '') + ' ' + esc(q.get('desc') || '') + '</p></div>';
    document.getElementById('retry').addEventListener('click', function () { api.navigate(url); });
  }

  var routes = { home: home, history: history, bookmarks: bookmarks, downloads: downloads, settings: settings, privacy: privacy, warning: warning, error: failure };
  document.title = { home: 'Tab baru', history: 'Riwayat', bookmarks: 'Penanda', downloads: 'Unduhan', settings: 'Pengaturan', privacy: 'Dasbor privasi', warning: 'Peringatan keamanan', error: 'Gagal dimuat' }[route] || 'Ceet';
  (routes[route] || home)().catch(function (e) { root.innerHTML = '<div class="page"><div class="card empty">Terjadi kesalahan: ' + esc(e && e.message) + '</div></div>'; });
})();
