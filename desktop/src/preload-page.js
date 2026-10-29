'use strict';
// Dipasang pada setiap tab. API hanya terbuka untuk halaman internal ceet://, tidak untuk situs web.
const { contextBridge, ipcRenderer } = require('electron');

if (location.protocol === 'ceet:') {
  const invoke = (ch, ...a) => ipcRenderer.invoke(ch, ...a);
  contextBridge.exposeInMainWorld('ceetPage', {
    settings: () => invoke('page:settings'),
    setSetting: (k, v) => invoke('page:setSetting', k, v),
    history: (q) => invoke('page:history', q),
    historyRemove: (id) => invoke('page:historyRemove', id),
    historyClear: () => invoke('page:historyClear'),
    bookmarks: () => invoke('page:bookmarks'),
    bookmarkRemove: (id) => invoke('page:bookmarkRemove', id),
    downloads: () => invoke('page:downloads'),
    showDownload: (id) => invoke('page:downloadShow', id),
    resetStats: () => invoke('page:resetStats'),
    navigate: (u) => invoke('page:navigate', u),
    proceed: (u) => invoke('page:proceed', u),
    topSites: () => invoke('page:topSites'),
    search: (q) => invoke('page:search', q),
  });
}
