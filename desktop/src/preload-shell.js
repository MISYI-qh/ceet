'use strict';
// Jembatan aman untuk antarmuka (shell + overlay). Hanya kanal yang diizinkan yang terbuka.
const { contextBridge, ipcRenderer } = require('electron');

const invoke = (ch, ...a) => ipcRenderer.invoke(ch, ...a);
const on = (ch, fn) => {
  const l = (_e, p) => fn(p);
  ipcRenderer.on(ch, l);
  return () => ipcRenderer.removeListener(ch, l);
};

contextBridge.exposeInMainWorld('ceet', {
  state: () => invoke('state:get'),
  onState: (fn) => on('state', fn),
  onFocusOmnibox: (fn) => on('focus:omnibox', fn),
  onFind: (fn) => on('find:toggle', fn),
  onFindResult: (fn) => on('find:result', fn),
  onOverlay: (fn) => on('overlay:show', fn),
  onOverlayClosed: (fn) => on('overlay:closed', fn),

  newTab: (url, priv) => invoke('tab:new', url, priv),
  closeTab: (id) => invoke('tab:close', id),
  activateTab: (id) => invoke('tab:activate', id),
  tabMenu: (id) => invoke('tab:menu', id),
  reopenTab: () => invoke('tab:reopen'),

  go: (input) => invoke('nav:go', input),
  back: () => invoke('nav:back'),
  forward: () => invoke('nav:forward'),
  reload: () => invoke('nav:reload'),
  home: () => invoke('nav:home'),
  omniboxInput: (t, rect) => invoke('omnibox:input', t, rect),
  suggestSelect: (i) => invoke('suggest:select', i),
  onSuggestItems: (fn) => on('suggest:items', fn),
  onSuggestSelect: (fn) => on('suggest:select', fn),
  setSetting: (k, v) => invoke('setting:set', k, v),
  menuAction: (n) => invoke('menu:action', n),
  closeOverlay: () => invoke('overlay:close'),
  openOverlay: (type, rect) => invoke('overlay:open', type, rect),
  toggleBookmark: () => invoke('bookmark:toggle'),
  findOpen: (o) => invoke('find:open', o),
  findQuery: (q, fwd, next) => invoke('find:query', q, fwd, next),
  zoom: (d) => invoke('zoom', d),
  devtools: () => invoke('devtools'),
  copyUrl: () => invoke('page:copyUrl'),
  openUrl: (u) => invoke('page:open', u),
  openUrlNew: (u) => invoke('page:openNew', u),
  showDownload: (id) => invoke('download:show', id),
});
