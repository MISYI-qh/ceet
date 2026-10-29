package id.ceet.browser.web

import android.annotation.SuppressLint
import android.app.DownloadManager
import android.content.Context
import android.net.Uri
import android.os.Environment
import android.os.Handler
import android.os.Looper
import android.webkit.CookieManager
import android.webkit.DownloadListener
import android.webkit.GeolocationPermissions
import android.webkit.PermissionRequest
import android.webkit.URLUtil
import android.webkit.WebChromeClient
import android.webkit.WebResourceRequest
import android.webkit.WebResourceResponse
import android.webkit.WebSettings
import android.webkit.WebView
import android.webkit.WebViewClient
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.webkit.WebViewCompat
import androidx.webkit.WebViewFeature
import id.ceet.browser.core.Blocker
import id.ceet.browser.core.Intent
import id.ceet.browser.core.Omnibox
import id.ceet.browser.core.Phishing
import id.ceet.browser.core.Risk
import id.ceet.browser.core.UrlTools
import id.ceet.browser.data.Settings
import id.ceet.browser.data.Store
import java.io.ByteArrayInputStream
import java.util.concurrent.atomic.AtomicInteger

const val HOME_URL = "ceet://home"

/** Satu tab. State dibaca Compose, WebView dibuat malas saat pertama kali ditampilkan. */
class TabState(val id: Int, val isPrivate: Boolean, startUrl: String) {
    var url by mutableStateOf(startUrl)
    var title by mutableStateOf("Tab baru")
    var progress by mutableIntStateOf(0)
    var canBack by mutableStateOf(false)
    var canForward by mutableStateOf(false)
    var blocked by mutableIntStateOf(0)
    val trackers = mutableStateListOf<String>()
    var upgraded by mutableStateOf(false)
    var stripped by mutableIntStateOf(0)
    var risk by mutableStateOf(Risk.AMAN)
    var riskReasons by mutableStateOf<List<String>>(emptyList())
    var bookmarked by mutableStateOf(false)
    var webView: WebView? = null
    var pendingUrl: String? = if (startUrl == HOME_URL) null else startUrl
    var pendingHtml: String? = null
    val blockedCounter = AtomicInteger(0)

    val isHome: Boolean get() = url == HOME_URL
    val host: String get() = UrlTools.parse(url)?.host ?: ""
}

class BrowserState(private val appContext: Context, val store: Store, val blocker: Blocker) {
    val tabs = mutableStateListOf<TabState>()
    var activeId by mutableIntStateOf(0)
    private var nextId = 1
    private val main = Handler(Looper.getMainLooper())
    var settings by mutableStateOf(store.settings)
        private set
    var stats by mutableStateOf(store.stats)
        private set
    var historyVersion by mutableIntStateOf(0)
        private set
    var bookmarksVersion by mutableIntStateOf(0)
        private set

    val active: TabState get() = tabs.firstOrNull { it.id == activeId } ?: tabs.first()

    init {
        val (urls, idx) = store.loadSession()
        if (urls.isEmpty()) newTab() else {
            urls.forEach { newTab(it, activate = false) }
            activeId = tabs[idx.coerceIn(0, tabs.lastIndex)].id
        }
    }

    fun updateSettings(s: Settings) { store.update(s); settings = s }

    fun newTab(url: String = HOME_URL, private: Boolean = false, activate: Boolean = true): TabState {
        val t = TabState(nextId++, private, url)
        tabs.add(t)
        if (activate) activeId = t.id
        return t
    }

    fun closeTab(id: Int) {
        val i = tabs.indexOfFirst { it.id == id }
        if (i < 0) return
        val t = tabs.removeAt(i)
        t.webView?.let { it.stopLoading(); it.destroy() }
        t.webView = null
        if (tabs.isEmpty()) newTab() else if (activeId == id) activeId = tabs[minOf(i, tabs.lastIndex)].id
    }

    fun saveSession() {
        val list = tabs.filter { !it.isPrivate }
        store.saveSession(list.map { it.url }, list.indexOfFirst { it.id == activeId }.coerceAtLeast(0))
    }

    fun go(input: String) {
        val tab = active
        when (val i = Omnibox.classify(input)) {
            is Intent.Command -> runCommand(i.name, i.arg)
            Intent.Empty -> {}
            else -> {
                val target = Omnibox.resolve(input, store.searchTemplate()) ?: return
                navigate(tab, target)
            }
        }
    }

    private fun runCommand(name: String, arg: String) {
        when (name) {
            "tema" -> updateSettings(settings.copy(theme = when { arg.contains("gelap") -> "dark"; arg.contains("terang") -> "light"; else -> "system" }))
            "privat" -> newTab(private = true)
            "bersihkan" -> { store.clearHistory(); historyVersion++ }
        }
    }

    /** Semua navigasi utama lewat sini: HTTPS, parameter pelacak, lalu pemeriksaan penipuan. */
    fun navigate(tab: TabState, raw: String) {
        var url = raw
        if (url == HOME_URL) { tab.url = HOME_URL; tab.title = "Tab baru"; return }
        val c = UrlTools.clean(url, upgrade = settings.upgradeHttps, strip = settings.stripParams)
        if (c.upgraded) { store.bump(https = 1); tab.upgraded = true }
        if (c.removed.isNotEmpty()) { store.bump(params = c.removed.size); tab.stripped += c.removed.size }
        url = c.url
        stats = store.stats
        tab.blockedCounter.set(0); tab.blocked = 0; tab.trackers.clear()
        if (settings.phishingCheck) {
            val a = Phishing.assess(url)
            tab.risk = a.risk; tab.riskReasons = a.reasons
            if (a.risk == Risk.BERBAHAYA) {
                store.bump(phishing = 1); stats = store.stats
                tab.url = url
                val wv = tab.webView
                val html = WarningPage.html(url, a.reasons)
                if (wv != null) wv.loadDataWithBaseURL("ceet://warning", html, "text/html", "utf-8", null)
                else { tab.pendingUrl = null; tab.pendingHtml = html }
                return
            }
        } else { tab.risk = Risk.AMAN; tab.riskReasons = emptyList() }
        tab.url = url
        val wv = tab.webView
        if (wv != null) wv.loadUrl(url, headers()) else tab.pendingUrl = url
    }

    private fun headers(): Map<String, String> =
        if (settings.sendGpc) mapOf("Sec-GPC" to "1", "DNT" to "1") else emptyMap()

    fun proceedAnyway(tab: TabState, url: String) {
        tab.risk = Risk.WASPADA
        tab.url = url
        tab.webView?.loadUrl(url, headers())
    }

    fun toggleBookmark() {
        val t = active
        if (t.isHome) return
        t.bookmarked = store.toggleBookmark(t.url, t.title)
        bookmarksVersion++
    }

    fun clearHistory() { store.clearHistory(); historyVersion++ }
    fun removeHistory(id: String) { store.removeHistory(id); historyVersion++ }

    fun onVisited(tab: TabState, url: String, title: String) {
        if (tab.isPrivate || !settings.saveHistory || !url.startsWith("http")) return
        store.addHistory(url, title)
        historyVersion++
    }

    // ---------- WebView ----------
    @SuppressLint("SetJavaScriptEnabled")
    fun ensureWebView(context: Context, tab: TabState): WebView {
        tab.webView?.let { return it }
        val wv = WebView(context)
        if (tab.isPrivate && WebViewFeature.isFeatureSupported(WebViewFeature.MULTI_PROFILE)) {
            try { WebViewCompat.setProfile(wv, "ceet-private") } catch (_: Exception) { /* profil tidak tersedia */ }
        }
        wv.settings.apply {
            javaScriptEnabled = true
            domStorageEnabled = true
            allowFileAccess = false
            allowContentAccess = false
            setGeolocationEnabled(false)
            mixedContentMode = WebSettings.MIXED_CONTENT_NEVER_ALLOW
            setSupportZoom(true); builtInZoomControls = true; displayZoomControls = false
            loadWithOverviewMode = true; useWideViewPort = true
            safeBrowsingEnabled = true
            mediaPlaybackRequiresUserGesture = true
        }
        CookieManager.getInstance().setAcceptThirdPartyCookies(wv, false)
        wv.webViewClient = CeetClient(tab)
        wv.webChromeClient = object : WebChromeClient() {
            override fun onProgressChanged(view: WebView, newProgress: Int) { tab.progress = newProgress }
            override fun onReceivedTitle(view: WebView, title: String?) {
                if (!title.isNullOrBlank() && !tab.isHome) {
                    tab.title = title
                    if (!tab.isPrivate) store.updateHistoryTitle(tab.url, title)
                }
            }
            // Izin kamera, mikrofon, dan lokasi ditolak otomatis.
            override fun onPermissionRequest(request: PermissionRequest) { request.deny() }
            override fun onGeolocationPermissionsShowPrompt(origin: String, callback: GeolocationPermissions.Callback) { callback.invoke(origin, false, false) }
        }
        wv.setDownloadListener(DownloadListener { dl, ua, cd, mime, _ ->
            try {
                val req = DownloadManager.Request(Uri.parse(dl))
                    .setMimeType(mime).addRequestHeader("User-Agent", ua)
                    .setNotificationVisibility(DownloadManager.Request.VISIBILITY_VISIBLE_NOTIFY_COMPLETED)
                    .setDestinationInExternalPublicDir(Environment.DIRECTORY_DOWNLOADS, URLUtil.guessFileName(dl, cd, mime))
                (appContext.getSystemService(Context.DOWNLOAD_SERVICE) as DownloadManager).enqueue(req)
            } catch (_: Exception) { /* unduhan gagal dimulai */ }
        })
        tab.webView = wv
        val html = tab.pendingHtml
        when {
            html != null -> wv.loadDataWithBaseURL("ceet://warning", html, "text/html", "utf-8", null)
            tab.pendingUrl != null -> wv.loadUrl(tab.pendingUrl!!, headers())
        }
        tab.pendingUrl = null
        tab.pendingHtml = null
        return wv
    }

    private inner class CeetClient(private val tab: TabState) : WebViewClient() {
        override fun shouldInterceptRequest(view: WebView, request: WebResourceRequest): WebResourceResponse? {
            if (!settings.blockTrackers || request.isForMainFrame) return null
            val host = request.url.host ?: return null
            val d = blocker.check(host, tab.host)
            if (!d.block) return null
            tab.blockedCounter.incrementAndGet()
            val rule = d.rule ?: host
            main.post {
                tab.blocked = tab.blockedCounter.get()
                if (!tab.trackers.contains(rule)) tab.trackers.add(rule)
                store.bump(trackers = 1)
                stats = store.stats
            }
            return WebResourceResponse("text/plain", "utf-8", 204, "Diblokir Ceet", emptyMap(), ByteArrayInputStream(ByteArray(0)))
        }

        override fun shouldOverrideUrlLoading(view: WebView, request: WebResourceRequest): Boolean {
            val u = request.url.toString()
            if (u.startsWith("ceet://proceed")) {
                val target = request.url.getQueryParameter("u")
                if (target != null) proceedAnyway(tab, target)
                return true
            }
            if (u.startsWith("ceet://back")) { if (view.canGoBack()) view.goBack() else tab.url = HOME_URL; return true }
            if (!request.isForMainFrame) return false
            val scheme = request.url.scheme ?: ""
            if (scheme != "http" && scheme != "https") return true // skema lain tidak dibuka diam-diam
            val c = UrlTools.clean(u, upgrade = settings.upgradeHttps, strip = settings.stripParams)
            val a = if (settings.phishingCheck) Phishing.assess(c.url) else null
            if (c.url != u || (a != null && a.risk == Risk.BERBAHAYA)) {
                main.post { navigate(tab, u) }
                return true
            }
            return false
        }

        override fun onPageStarted(view: WebView, url: String, favicon: android.graphics.Bitmap?) {
            if (!url.startsWith("ceet://") && !url.startsWith("data:") && !url.startsWith("about:")) tab.url = url
            tab.progress = 5
        }

        override fun onPageFinished(view: WebView, url: String) {
            tab.progress = 100
            tab.canBack = view.canGoBack(); tab.canForward = view.canGoForward()
            if (url.startsWith("http")) {
                tab.bookmarked = store.isBookmarked(url)
                onVisited(tab, url, view.title ?: "")
            }
        }

        override fun doUpdateVisitedHistory(view: WebView, url: String, isReload: Boolean) {
            tab.canBack = view.canGoBack(); tab.canForward = view.canGoForward()
        }
    }
}

/** Halaman peringatan situs penipuan. HTML mandiri, tanpa sumber luar. */
object WarningPage {
    private fun esc(s: String) = s.replace("&", "&amp;").replace("<", "&lt;").replace(">", "&gt;").replace("\"", "&quot;")

    fun html(url: String, reasons: List<String>): String {
        val li = reasons.joinToString("") { "<li>${esc(it)}</li>" }
        val enc = java.net.URLEncoder.encode(url, "UTF-8")
        return """<!doctype html><html lang="id"><head><meta charset="utf-8"><meta name="viewport" content="width=device-width,initial-scale=1">
<style>
:root{color-scheme:light dark;--bg:#FBFAF8;--fg:#0B1A30;--muted:#5B6B82;--bad:#D23C3C;--soft:#F3F5F9;--accent:#1580FA}
@media(prefers-color-scheme:dark){:root{--bg:#0A1222;--fg:#EAF0FB;--muted:#93A3BF;--bad:#FF6B6B;--soft:#0D172B;--accent:#4C9BFF}}
body{margin:0;background:var(--bg);color:var(--fg);font:16px/1.55 system-ui,sans-serif;padding:48px 24px;text-align:center}
.i{width:72px;height:72px;border-radius:22px;margin:0 auto 20px;display:grid;place-items:center;background:color-mix(in srgb,var(--bad) 14%,transparent);color:var(--bad)}
h1{font-size:24px;margin:0 0 8px}p{color:var(--muted);margin:8px 0}
.u{display:inline-block;max-width:100%;padding:6px 12px;border-radius:10px;background:var(--soft);font-size:13px;overflow-wrap:anywhere;color:var(--fg)}
ul{text-align:left;display:inline-block;margin:12px 0}
a.b{display:inline-block;margin-top:18px;padding:12px 28px;border-radius:24px;background:var(--accent);color:#fff;font-weight:600;text-decoration:none}
a.s{display:block;margin-top:28px;color:var(--muted);font-size:13px}
</style></head><body>
<div class="i"><svg width="38" height="38" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="1.75" stroke-linecap="round" stroke-linejoin="round"><path d="M12 3l7 3v5.5c0 4.4-3 7.8-7 9.5-4-1.7-7-5.1-7-9.5V6z"/><path d="M12 8.5v4.2"/><path d="M12 15.6v.1"/></svg></div>
<h1>Situs ini kemungkinan penipuan</h1>
<p>Ceet menghentikan pemuatan karena alamat ini punya ciri-ciri situs palsu.</p>
<span class="u">${esc(url)}</span><br><ul>$li</ul><br>
<a class="b" href="ceet://back">Kembali ke aman</a>
<a class="s" href="ceet://proceed?u=$enc">Penilaian ini perkiraan dan bisa keliru. Saya paham risikonya, buka tetap.</a>
</body></html>"""
    }
}
