/* Overlay: saran omnibox, perisai privasi, menu, dan pembaca. Dirender di atas tab. */
(function () {
  'use strict';
  var I = window.CeetIcons;
  var panel = document.getElementById('panel');
  var scrim = document.getElementById('scrim');
  var current = null;
  var items = [];
  var sel = -1;

  function esc(s) { return String(s == null ? '' : s).replace(/[&<>"']/g, function (c) { return { '&': '&amp;', '<': '&lt;', '>': '&gt;', '"': '&quot;', "'": '&#39;' }[c]; }); }
  function place(rect, width, align) {
    panel.style.transform = ''; panel.style.left = ''; panel.style.right = ''; panel.style.top = '';
    var top = rect ? rect.y + rect.h + 6 : 96;
    panel.style.top = top + 'px';
    if (align === 'right' && rect) panel.style.right = Math.max(8, window.innerWidth - (rect.x + rect.w)) + 'px';
    else if (rect) panel.style.left = Math.max(8, Math.min(rect.x, window.innerWidth - width - 8)) + 'px';
    if (width) panel.style.width = width + 'px';
  }
  function applyTheme(s) {
    var r = document.documentElement;
    if (s.theme === 'system') r.removeAttribute('data-theme'); else r.setAttribute('data-theme', s.theme);
  }

  scrim.addEventListener('mousedown', function () { window.ceet.closeOverlay(); });
  document.addEventListener('keydown', function (e) { if (e.key === 'Escape') window.ceet.closeOverlay(); });

  // ---------- saran ----------
  function showSuggest(data, rect) {
    items = data.items || [];
    sel = -1;
    panel.className = 'panel';
    panel.hidden = false;
    place(rect, rect ? rect.w : 560);
    paintSuggest();
  }
  function paintSuggest() {
    panel.innerHTML = '<ul class="sug" role="listbox" id="sug">' + items.map(function (it, i) {
      return '<li role="option" data-i="' + i + '" class="' + (i === sel ? 'on' : '') + '"><span class="ico">' + I.svg(it.icon || 'search', 16) + '</span>' +
        '<span class="t"><b>' + esc(it.title) + '</b><small>' + esc(it.sub) + '</small></span>' +
        (it.why ? '<span class="why">' + esc(it.why) + '</span>' : '') + '</li>';
    }).join('') + '</ul><div class="hint"><span><kbd>↑</kbd> <kbd>↓</kbd> pilih</span><span><kbd>Enter</kbd> buka</span><span><kbd>Esc</kbd> tutup</span></div>';
  }
  panel.addEventListener('mousedown', function (e) {
    var li = e.target.closest('li[data-i]');
    if (li && current === 'suggest') {
      e.preventDefault();
      var it = items[Number(li.dataset.i)];
      if (it) window.ceet.go(it.kind === 'search' ? it.title : it.value);
    }
  });
  window.ceet.onSuggestSelect(function (i) { sel = i; if (current === 'suggest') paintSuggest(); });

  // ---------- menu ----------
  function mi(icon, label, action, key) {
    return '<button data-act="' + action + '">' + I.svg(icon, 18) + '<span>' + label + '</span>' + (key ? '<span class="k">' + key + '</span>' : '') + '</button>';
  }
  function showMenu(rect, state) {
    panel.className = 'panel menu';
    panel.hidden = false;
    place(rect, 288, 'right');
    var dark = state.dark;
    panel.innerHTML =
      mi('plus', 'Tab baru', 'newTab', 'Ctrl+T') + mi('eyeOff', 'Tab privat', 'privateTab', 'Ctrl+Shift+N') +
      (state.closedCount ? mi('undo', 'Buka kembali tab tertutup', 'reopen', 'Ctrl+Shift+T') : '') + '<hr>' +
      mi('clock', 'Riwayat', 'history', 'Ctrl+H') + mi('bookmark', 'Penanda', 'bookmarks') + mi('download', 'Unduhan', 'downloads', 'Ctrl+J') + '<hr>' +
      mi('sparkle', 'Ringkas dan baca halaman', 'reader') + mi('findPage', 'Cari di halaman', 'find', 'Ctrl+F') +
      '<div class="zoomrow">' + I.svg('zoom', 18) + '<span>Zoom</span><span class="sp"></span><button data-act="zoomOut" aria-label="Perkecil">−</button><button data-act="zoomReset" aria-label="Setel ulang zoom">1×</button><button data-act="zoomIn" aria-label="Perbesar">+</button></div><hr>' +
      mi('shieldCheck', 'Dasbor privasi', 'privacy') + mi(dark ? 'sun' : 'moon', dark ? 'Tema terang' : 'Tema gelap', 'themeToggle') +
      mi('fullscreen', 'Layar penuh', 'fullscreen', 'F11') + mi('code', 'Alat pengembang', 'devtools', 'F12') + mi('settings', 'Pengaturan', 'settings');
  }
  panel.addEventListener('click', function (e) {
    var b = e.target.closest('[data-act]');
    if (b) {
      var a = b.dataset.act;
      if (a === 'zoomIn') return window.ceet.zoom(0.5);
      if (a === 'zoomOut') return window.ceet.zoom(-0.5);
      if (a === 'zoomReset') return window.ceet.zoom(0);
      return window.ceet.menuAction(a);
    }
    var t = e.target.closest('[data-toggle]');
    if (t) {
      var on = t.getAttribute('aria-checked') !== 'true';
      t.setAttribute('aria-checked', String(on));
      window.ceet.setSetting(t.dataset.toggle, on);
    }
    var o = e.target.closest('[data-open]');
    if (o) window.ceet.menuAction(o.dataset.open);
    var r = e.target.closest('[data-reader]');
    if (r) {
      if (r.dataset.reader === 'close') window.ceet.closeOverlay();
      if (r.dataset.reader === 'open') window.ceet.openUrlNew(r.dataset.url);
    }
  });

  // ---------- perisai ----------
  function row(icon, label, value, cls) {
    return '<div class="row ' + (cls || '') + '">' + I.svg(icon, 18) + '<span class="l">' + label + '</span><span class="v">' + value + '</span></div>';
  }
  function showShield(rect, state) {
    var n = state.nav || {};
    panel.className = 'panel shield';
    panel.hidden = false;
    place(rect, 340, 'left');
    panel.style.left = Math.max(8, rect ? rect.x : 8) + 'px';
    if (!n.url) {
      panel.innerHTML = '<div class="sh-head"><div class="grade">A</div><div><h3>Halaman Ceet</h3><p>Halaman internal tidak memuat pelacak.</p></div></div>' + shieldFooter(state);
      return;
    }
    var grade = n.risk === 'berbahaya' ? 'D' : n.risk === 'waspada' ? 'C' : !n.secure ? 'C' : 'A';
    var title = n.risk === 'berbahaya' ? 'Situs ini berbahaya' : n.risk === 'waspada' ? 'Hati-hati dengan situs ini' : n.secure ? 'Perlindungan aktif' : 'Koneksi tidak terenkripsi';
    var sub = n.host + (n.blocked ? ' · ' + n.blocked + ' pelacak diblokir' : ' · tidak ada pelacak terdeteksi');
    var html = '<div class="sh-head"><div class="grade ' + grade + '">' + grade + '</div><div><h3>' + esc(title) + '</h3><p>' + esc(sub) + '</p></div></div>';
    if (n.riskReasons && n.riskReasons.length && n.risk !== 'aman') {
      html += '<div class="warnbox"><b>Alasan peringatan</b><ul>' + n.riskReasons.map(function (r) { return '<li>' + esc(r) + '</li>'; }).join('') + '</ul></div>';
    }
    html += '<div class="sh-list">' +
      row(n.secure ? 'lock' : 'lockOpen', 'Koneksi', n.secure ? 'HTTPS' : 'Tidak aman', n.secure ? 'good' : 'bad') +
      row('shield', 'Pelacak diblokir', n.blocked, n.blocked ? 'good' : '') +
      row('bolt', 'Dinaikkan ke HTTPS', n.upgraded ? 'Ya' : 'Tidak perlu') +
      row('trash', 'Parameter pelacak dibuang', n.stripped || 0) + '</div>';
    if (n.trackers && n.trackers.length) html += '<div class="trk">' + n.trackers.map(function (t) { return '<div>' + esc(t) + '</div>'; }).join('') + '</div>';
    html += shieldFooter(state);
    panel.innerHTML = html;
  }
  function shieldFooter(state) {
    var on = !state.settings || state.settings.blockTrackers !== false;
    return '<div class="sw"><span class="l">Blokir pelacak<small>Berlaku untuk semua situs</small></span><button class="toggle" role="switch" aria-label="Blokir pelacak" data-toggle="blockTrackers" aria-checked="' + on + '"></button></div>' +
      '<div class="linkrow"><button data-open="privacy">Buka dasbor privasi</button></div>';
  }

  // ---------- pembaca ----------
  function showReader(data) {
    panel.className = 'panel reader';
    panel.hidden = false;
    panel.style.left = '50%'; panel.style.top = '100px'; panel.style.right = ''; panel.style.width = '';
    var bar = '<div class="rd-bar"><span class="l">' + I.svg('sparkle', 18) + 'Ringkasan dan mode baca</span>' +
      (data.url ? '<button data-reader="open" data-url="' + esc(data.url) + '" aria-label="Buka asli di tab baru" title="Buka asli di tab baru">' + I.svg('external', 16) + '</button>' : '') +
      '<button data-reader="close" aria-label="Tutup" title="Tutup">' + I.svg('close', 16) + '</button></div>';
    var body;
    if (data.loading) body = '<div class="rd-body"><h1>' + esc(data.title || 'Memuat') + '</h1><div class="skeleton" style="width:90%"></div><div class="skeleton" style="width:78%"></div><div class="skeleton" style="width:84%"></div><div class="skeleton" style="width:60%"></div></div>';
    else if (data.error) body = '<div class="note">' + esc(data.error) + '</div>';
    else if (data.empty) body = '<div class="note">Tidak ada teks artikel yang bisa dibaca di halaman ini.</div>';
    else {
      var paras = String(data.body || '').split(/\n\n+/).map(function (p) { return '<p>' + esc(p) + '</p>'; }).join('');
      var sum = data.summary && data.summary.length
        ? '<div class="rd-sum"><h4>Intisari</h4><ul>' + data.summary.map(function (s) { return '<li>' + esc(s) + '</li>'; }).join('') + '</ul><small>Setiap kalimat dikutip langsung dari halaman. Dibuat di perangkatmu, tanpa AI dan tanpa server.</small></div>' : '';
      body = '<div class="rd-body"><h1>' + esc(data.title) + '</h1><div class="rd-meta"><span>' + (data.minutes || 1) + ' menit baca</span>' +
        (data.keywords || []).map(function (k) { return '<span class="chip">' + esc(k) + '</span>'; }).join('') + '</div>' + sum + '<div class="rd-text">' + paras + '</div></div>';
    }
    panel.innerHTML = bar + body;
  }

  window.ceet.onOverlay(function (msg) {
    applyTheme(msg.state);
    current = msg.type;
    if (msg.type === 'suggest') showSuggest(msg.data, msg.rect);
    else if (msg.type === 'menu') showMenu(msg.rect, msg.state);
    else if (msg.type === 'shield') showShield(msg.rect, msg.state);
    else if (msg.type === 'reader') showReader(msg.data || {});
  });
  window.ceet.onOverlayClosed(function () { panel.hidden = true; panel.innerHTML = ''; current = null; });
  window.ceet.onState(function (s) { applyTheme(s); });
})();
