/* Antarmuka utama: tab, bilah alat, omnibox, pencarian di halaman. Semua ikon SVG. */
(function () {
  'use strict';
  var I = window.CeetIcons;
  var $ = function (id) { return document.getElementById(id); };
  var S = null;           // snapshot terakhir dari proses utama
  var items = [];         // saran omnibox
  var sel = -1;
  var typing = false;
  var debounce = null;

  I.hydrate();

  function esc(s) { return String(s == null ? '' : s).replace(/[&<>"']/g, function (c) { return { '&': '&amp;', '<': '&lt;', '>': '&gt;', '"': '&quot;', "'": '&#39;' }[c]; }); }
  function rectOf(el) { var r = el.getBoundingClientRect(); return { x: r.left, y: r.top, w: r.width, h: r.height }; }

  function applyTheme(s) {
    var root = document.documentElement;
    if (s.theme === 'system') root.removeAttribute('data-theme'); else root.setAttribute('data-theme', s.theme);
    document.body.classList.toggle('mac', s.platform === 'darwin');
  }

  function render(s) {
    S = s;
    applyTheme(s);
    renderTabs(s);
    renderNav(s);
  }

  function favHtml(t) {
    if (t.loading) return '<span class="fav spin"></span>';
    if (t.favicon) return '<span class="fav"><img alt="" src="' + esc(t.favicon) + '"></span>';
    return '<span class="fav">' + I.svg(t.private ? 'eyeOff' : t.internal ? 'bolt' : 'globe', 15) + '</span>';
  }

  function renderTabs(s) {
    var el = $('tabs');
    var sig = JSON.stringify(s.tabs.map(function (t) { return [t.id, t.title, t.favicon, t.loading, t.private, t.pinned]; })) + s.activeId;
    if (el._sig === sig) return;
    el._sig = sig;
    el.innerHTML = s.tabs.map(function (t) {
      return '<div class="tab' + (t.private ? ' private' : '') + (t.pinned ? ' pinned' : '') + '" role="tab" tabindex="0" aria-selected="' + (t.id === s.activeId) + '" data-id="' + t.id + '" title="' + esc(t.title) + '">' +
        favHtml(t) + '<span class="tt">' + esc(t.internal && (!t.title || t.title === 'Tab baru') ? 'Tab baru' : t.title) + '</span>' +
        '<button class="x" data-close="' + t.id + '" aria-label="Tutup tab" tabindex="-1">' + I.svg('close', 14) + '</button></div>';
    }).join('');
  }

  function renderNav(s) {
    var n = s.nav;
    var priv = !!(n && n.private);
    $('back').disabled = !(n && n.canBack);
    $('forward').disabled = !(n && n.canForward);
    $('reload').innerHTML = I.svg(n && n.loading ? 'stop' : 'reload');
    $('reload').title = n && n.loading ? 'Berhenti' : 'Muat ulang';
    $('privBadge').hidden = !priv;
    $('reader').disabled = !(n && /^https?:/i.test(n.url));
    $('star').innerHTML = I.svg(n && n.bookmarked ? 'starFill' : 'star', 18);
    $('star').classList.toggle('on', !!(n && n.bookmarked));
    $('star').hidden = !(n && /^https?:/i.test(n.url));

    var sh = $('shield');
    var risk = n ? n.risk : 'aman';
    sh.className = 'shieldbtn' + (risk === 'berbahaya' ? ' bad' : risk === 'waspada' ? ' warn' : (n && n.internal) ? ' off' : '');
    $('shieldIc').innerHTML = I.svg(risk === 'berbahaya' || risk === 'waspada' ? 'shieldAlert' : 'shieldCheck', 18);
    var c = $('shieldCount');
    c.hidden = !(n && n.blocked > 0);
    c.textContent = n && n.blocked > 99 ? '99+' : n ? n.blocked : '';

    var sec = $('secure');
    if (n && /^https?:/i.test(n.url)) {
      sec.innerHTML = I.svg(n.secure ? 'lock' : 'lockOpen', 15);
      sec.className = 'secure' + (n.secure ? '' : ' bad');
      sec.title = n.secure ? 'Koneksi terenkripsi (HTTPS)' : 'Koneksi tidak terenkripsi';
    } else { sec.innerHTML = ''; sec.className = 'secure'; }

    var inp = $('omni');
    if (!typing && document.activeElement !== inp) inp.value = n ? n.url : '';
    $('findbar').hidden = !s.findOpen;
  }

  // ---- omnibox ----
  var omni = $('omni');
  omni.addEventListener('focus', function () { omni.select(); });
  omni.addEventListener('input', function () {
    typing = true;
    clearTimeout(debounce);
    debounce = setTimeout(function () {
      var v = omni.value;
      if (!v.trim()) { window.ceet.closeOverlay(); items = []; sel = -1; return; }
      window.ceet.omniboxInput(v, rectOf($('omniForm')));
    }, 70);
  });
  omni.addEventListener('keydown', function (e) {
    if (e.key === 'ArrowDown' || e.key === 'ArrowUp') {
      if (!items.length) return;
      e.preventDefault();
      sel = (sel + (e.key === 'ArrowDown' ? 1 : -1) + items.length + 1) % (items.length + 1) - 1;
      window.ceet.suggestSelect(sel);
      if (sel >= 0 && items[sel].kind !== 'command') omni.value = items[sel].kind === 'search' ? items[sel].title : items[sel].value;
    } else if (e.key === 'Escape') {
      typing = false; window.ceet.closeOverlay(); omni.value = S && S.nav ? S.nav.url : ''; omni.blur();
    }
  });
  $('omniForm').addEventListener('submit', function (e) {
    e.preventDefault();
    var v = omni.value;
    if (sel >= 0 && items[sel]) v = items[sel].kind === 'command' ? items[sel].value : items[sel].value;
    typing = false; items = []; sel = -1;
    omni.blur();
    window.ceet.go(v);
  });
  omni.addEventListener('blur', function () { setTimeout(function () { typing = false; if (S) renderNav(S); }, 150); });

  window.ceet.onSuggestItems(function (list) { items = list || []; sel = -1; });
  window.ceet.onFocusOmnibox(function (prefill) {
    window.focus(); omni.focus();
    if (typeof prefill === 'string') { omni.value = prefill; typing = true; omni.setSelectionRange(prefill.length, prefill.length); omni.dispatchEvent(new Event('input')); }
    else omni.select();
  });
  window.ceet.onOverlayClosed(function () { items = []; sel = -1; });

  // ---- tab ----
  $('tabs').addEventListener('mousedown', function (e) {
    var close = e.target.closest('[data-close]');
    if (close) return;
    var t = e.target.closest('.tab');
    if (!t) return;
    if (e.button === 1) { e.preventDefault(); window.ceet.closeTab(Number(t.dataset.id)); return; }
    if (e.button === 0) window.ceet.activateTab(Number(t.dataset.id));
  });
  $('tabs').addEventListener('error', function (e) {
    var img = e.target;
    if (img && img.tagName === 'IMG') img.replaceWith(Object.assign(document.createElement('span'), { innerHTML: I.svg('globe', 15) }).firstChild);
  }, true);
  $('tabs').addEventListener('click', function (e) {
    var c = e.target.closest('[data-close]');
    if (c) { e.stopPropagation(); window.ceet.closeTab(Number(c.dataset.close)); }
  });
  $('tabs').addEventListener('contextmenu', function (e) {
    var t = e.target.closest('.tab');
    if (t) { e.preventDefault(); window.ceet.tabMenu(Number(t.dataset.id)); }
  });
  $('tabs').addEventListener('keydown', function (e) {
    var t = e.target.closest('.tab');
    if (t && (e.key === 'Enter' || e.key === ' ')) { e.preventDefault(); window.ceet.activateTab(Number(t.dataset.id)); }
  });
  $('newTab').addEventListener('click', function () { window.ceet.newTab(); omni.focus(); });

  // ---- tombol bilah alat ----
  $('back').addEventListener('click', function () { window.ceet.back(); });
  $('forward').addEventListener('click', function () { window.ceet.forward(); });
  $('reload').addEventListener('click', function () { window.ceet.reload(); });
  $('home').addEventListener('click', function () { window.ceet.home(); });
  $('star').addEventListener('click', function () { window.ceet.toggleBookmark(); });
  $('reader').addEventListener('click', function () { window.ceet.openOverlay('reader'); });
  $('shield').addEventListener('click', function () { window.ceet.openOverlay('shield', rectOf($('shield'))); });
  $('menu').addEventListener('click', function () { window.ceet.openOverlay('menu', rectOf($('menu'))); });

  // ---- cari di halaman ----
  var fi = $('findInput');
  fi.addEventListener('input', function () { window.ceet.findQuery(fi.value, true, false); });
  fi.addEventListener('keydown', function (e) {
    if (e.key === 'Enter') { e.preventDefault(); window.ceet.findQuery(fi.value, !e.shiftKey, true); }
    if (e.key === 'Escape') window.ceet.findOpen(false);
  });
  $('findNext').addEventListener('click', function () { window.ceet.findQuery(fi.value, true, true); });
  $('findPrev').addEventListener('click', function () { window.ceet.findQuery(fi.value, false, true); });
  $('findClose').addEventListener('click', function () { window.ceet.findOpen(false); });
  window.ceet.onFind(function (open) { $('findbar').hidden = !open; if (open) { fi.focus(); fi.select(); } else { fi.value = ''; $('findCount').textContent = ''; } });
  window.ceet.onFindResult(function (r) { $('findCount').textContent = r.total ? r.active + ' dari ' + r.total : (fi.value ? 'Tidak ada' : ''); });

  window.ceet.onState(render);
  window.ceet.state().then(render);
})();
