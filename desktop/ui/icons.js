/* Semua ikon Ceet adalah SVG garis (24x24, stroke 1.75). Tidak ada emoji. */
(function () {
  var P = {
    back: '<path d="M15 5l-7 7 7 7"/>',
    forward: '<path d="M9 5l7 7-7 7"/>',
    reload: '<path d="M20 11a8 8 0 1 0-2.3 5.7"/><path d="M20 4v7h-7"/>',
    stop: '<path d="M6 6l12 12M18 6L6 18"/>',
    home: '<path d="M4 11l8-7 8 7"/><path d="M6 10v9h4v-5h4v5h4v-9"/>',
    star: '<path d="M12 3.5l2.6 5.4 5.9.8-4.3 4.1 1 5.9L12 16.9l-5.2 2.8 1-5.9L3.5 9.7l5.9-.8z"/>',
    starFill: '<path fill="currentColor" d="M12 3.5l2.6 5.4 5.9.8-4.3 4.1 1 5.9L12 16.9l-5.2 2.8 1-5.9L3.5 9.7l5.9-.8z"/>',
    shield: '<path d="M12 3l7 3v5.5c0 4.4-3 7.8-7 9.5-4-1.7-7-5.1-7-9.5V6z"/>',
    shieldCheck: '<path d="M12 3l7 3v5.5c0 4.4-3 7.8-7 9.5-4-1.7-7-5.1-7-9.5V6z"/><path d="M9 12l2.2 2.2L15.5 10"/>',
    shieldAlert: '<path d="M12 3l7 3v5.5c0 4.4-3 7.8-7 9.5-4-1.7-7-5.1-7-9.5V6z"/><path d="M12 8.5v4.2"/><path d="M12 15.6v.1"/>',
    lock: '<rect x="5" y="10.5" width="14" height="9.5" rx="2.5"/><path d="M8 10.5V8a4 4 0 0 1 8 0v2.5"/>',
    lockOpen: '<rect x="5" y="10.5" width="14" height="9.5" rx="2.5"/><path d="M8 10.5V8a4 4 0 0 1 7.5-1.9"/>',
    plus: '<path d="M12 5v14M5 12h14"/>',
    close: '<path d="M6.5 6.5l11 11M17.5 6.5l-11 11"/>',
    dots: '<circle cx="12" cy="5.5" r="1.4" fill="currentColor" stroke="none"/><circle cx="12" cy="12" r="1.4" fill="currentColor" stroke="none"/><circle cx="12" cy="18.5" r="1.4" fill="currentColor" stroke="none"/>',
    search: '<circle cx="11" cy="11" r="6.5"/><path d="M20 20l-4.2-4.2"/>',
    globe: '<circle cx="12" cy="12" r="8.5"/><path d="M3.5 12h17"/><path d="M12 3.5c2.5 2.4 3.8 5.2 3.8 8.5s-1.3 6.1-3.8 8.5c-2.5-2.4-3.8-5.2-3.8-8.5S9.5 5.9 12 3.5z"/>',
    clock: '<circle cx="12" cy="12" r="8.5"/><path d="M12 7.5V12l3 2"/>',
    bookmark: '<path d="M7 4h10a1 1 0 0 1 1 1v15l-6-4-6 4V5a1 1 0 0 1 1-1z"/>',
    settings: '<circle cx="12" cy="12" r="3"/><path d="M19.4 14a1.7 1.7 0 0 0 .3 1.9l.1.1a2 2 0 1 1-2.8 2.8l-.1-.1a1.7 1.7 0 0 0-1.9-.3 1.7 1.7 0 0 0-1 1.5V21a2 2 0 1 1-4 0v-.1a1.7 1.7 0 0 0-1.1-1.5 1.7 1.7 0 0 0-1.9.3l-.1.1a2 2 0 1 1-2.8-2.8l.1-.1a1.7 1.7 0 0 0 .3-1.9 1.7 1.7 0 0 0-1.5-1H3a2 2 0 1 1 0-4h.1a1.7 1.7 0 0 0 1.5-1.1 1.7 1.7 0 0 0-.3-1.9l-.1-.1a2 2 0 1 1 2.8-2.8l.1.1a1.7 1.7 0 0 0 1.9.3h.1a1.7 1.7 0 0 0 1-1.5V3a2 2 0 1 1 4 0v.1a1.7 1.7 0 0 0 1 1.5 1.7 1.7 0 0 0 1.9-.3l.1-.1a2 2 0 1 1 2.8 2.8l-.1.1a1.7 1.7 0 0 0-.3 1.9v.1a1.7 1.7 0 0 0 1.5 1H21a2 2 0 1 1 0 4h-.1a1.7 1.7 0 0 0-1.5 1z"/>',
    download: '<path d="M12 4v11"/><path d="M7.5 11l4.5 4.5 4.5-4.5"/><path d="M5 19.5h14"/>',
    reader: '<path d="M4 5.5C6.5 4.5 9.5 4.5 12 6c2.5-1.5 5.5-1.5 8-.5v13c-2.5-1-5.5-1-8 .5-2.5-1.5-5.5-1.5-8-.5z"/><path d="M12 6v13"/>',
    sparkle: '<path d="M12 4l1.8 5.2L19 11l-5.2 1.8L12 18l-1.8-5.2L5 11l5.2-1.8z"/><path d="M18.5 3.5v3M17 5h3"/>',
    moon: '<path d="M20 14.5A8.5 8.5 0 0 1 9.5 4a8.5 8.5 0 1 0 10.5 10.5z"/>',
    sun: '<circle cx="12" cy="12" r="4"/><path d="M12 3v2M12 19v2M3 12h2M19 12h2M5.6 5.6L7 7M17 17l1.4 1.4M5.6 18.4L7 17M17 7l1.4-1.4"/>',
    eyeOff: '<path d="M3 3l18 18"/><path d="M10.6 5.2A9.6 9.6 0 0 1 12 5c5 0 8.5 4 9.5 7-.4 1.1-1.2 2.4-2.4 3.6"/><path d="M6.6 6.7C4.6 8 3.3 10 2.5 12c1 3 4.5 7 9.5 7 1.5 0 2.9-.4 4.1-1"/><path d="M9.9 9.9A3 3 0 0 0 14.1 14.1"/>',
    findPage: '<path d="M6 3.5h8l4 4V20a.5.5 0 0 1-.5.5h-11A.5.5 0 0 1 6 20z"/><circle cx="11.5" cy="12.5" r="2.8"/><path d="M13.6 14.6l2.2 2.2"/>',
    zoom: '<circle cx="11" cy="11" r="6.5"/><path d="M20 20l-4.2-4.2M8.5 11h5M11 8.5v5"/>',
    external: '<path d="M14 4h6v6"/><path d="M20 4l-9 9"/><path d="M18 14v4.5a1.5 1.5 0 0 1-1.5 1.5h-11A1.5 1.5 0 0 1 4 18.5v-11A1.5 1.5 0 0 1 5.5 6H10"/>',
    trash: '<path d="M4.5 7h15"/><path d="M9.5 7V4.5h5V7"/><path d="M6.5 7l.8 12a1.5 1.5 0 0 0 1.5 1.4h6.4a1.5 1.5 0 0 0 1.5-1.4L17.5 7"/>',
    check: '<path d="M5 12.5l4.5 4.5L19 7.5"/>',
    alert: '<path d="M12 4l9 15.5H3z"/><path d="M12 10v4.5"/><path d="M12 17.2v.1"/>',
    command: '<path d="M9 6.5a2.5 2.5 0 1 0-2.5 2.5H9zM9 6.5V9m0-2.5h6M15 6.5A2.5 2.5 0 1 1 17.5 9H15zM15 9v6m0 0h2.5A2.5 2.5 0 1 1 15 17.5zM15 15H9m0 0v2.5A2.5 2.5 0 1 1 6.5 15H9zM9 9v6"/>',
    pin: '<path d="M9 4h6l-1 5 3 3v1.5H7V12l3-3z"/><path d="M12 13.5V20"/>',
    copy: '<rect x="8.5" y="8.5" width="11" height="11" rx="2.2"/><path d="M15.5 8.5V6.2a1.7 1.7 0 0 0-1.7-1.7H6.2A1.7 1.7 0 0 0 4.5 6.2v7.6a1.7 1.7 0 0 0 1.7 1.7h2.3"/>',
    chevronUp: '<path d="M6 14.5l6-6 6 6"/>',
    chevronDown: '<path d="M6 9.5l6 6 6-6"/>',
    fullscreen: '<path d="M4.5 9V4.5H9M15 4.5h4.5V9M19.5 15v4.5H15M9 19.5H4.5V15"/>',
    code: '<path d="M8.5 7L4 12l4.5 5M15.5 7l4.5 5-4.5 5M13.5 5l-3 14"/>',
    undo: '<path d="M9 8L4.5 12.5 9 17"/><path d="M5 12.5h9a5 5 0 0 1 5 5"/>',
    tab: '<rect x="3.5" y="6.5" width="17" height="12" rx="2.5"/><path d="M3.5 10h17"/>',
    list: '<path d="M8.5 6.5h11M8.5 12h11M8.5 17.5h11M4.5 6.5h.1M4.5 12h.1M4.5 17.5h.1"/>',
    bolt: '<path d="M13 3.5L5.5 13.5H11l-1 7 7.5-10H12z"/>'
  };
  function svg(name, size, extra) {
    var s = size || 20;
    return '<svg class="ic ' + (extra || '') + '" width="' + s + '" height="' + s + '" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="1.75" stroke-linecap="round" stroke-linejoin="round" aria-hidden="true">' + (P[name] || '') + '</svg>';
  }
  var LOGO = '<svg viewBox="0 0 100 100" aria-hidden="true"><defs><linearGradient id="lg" x1="0.2" y1="0" x2="0.8" y2="1"><stop offset="0" stop-color="#1A86FF"/><stop offset="1" stop-color="#0B2F9E"/></linearGradient></defs><path d="M80 25A38 38 0 1 0 80 75" fill="none" stroke="url(#lg)" stroke-width="20"/><polygon points="33,52 68,35 59,68 49,57" fill="#1580FA"/><polygon points="33,52 68,35 49,57" fill="#0B3BB8"/></svg>';
  window.CeetIcons = { svg: svg, names: Object.keys(P), logo: LOGO };
  // Ganti <i data-ic="nama" data-s="20"> menjadi SVG.
  window.CeetIcons.hydrate = function (root) {
    (root || document).querySelectorAll('i[data-ic]').forEach(function (el) {
      el.outerHTML = svg(el.getAttribute('data-ic'), Number(el.getAttribute('data-s')) || 20, el.className);
    });
  };
})();
