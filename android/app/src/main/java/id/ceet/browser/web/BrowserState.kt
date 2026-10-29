package id.ceet.browser.web

import android.annotation.SuppressLint
import android.app.DownloadManager
import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.net.Uri
import android.os.Environment
import android.os.Handler
import android.os.Looper
import android.os.Message
import android.view.View
import android.view.ViewGroup
import android.webkit.CookieManager
import android.webkit.DownloadListener
import android.webkit.GeolocationPermissions
import android.webkit.PermissionRequest
import android.webkit.RenderProcessGoneDetail
import android.webkit.SslErrorHandler
import android.webkit.URLUtil
import android.webkit.ValueCallback
import android.webkit.WebChromeClient
import android.webkit.WebResourceError
import android.webkit.WebResourceRequest
import android.webkit.WebResourceResponse
import android.webkit.WebSettings
import android.webkit.WebStorage
import android.webkit.WebView
import android.webkit.WebViewClient
import android.net.http.SslError
import android.widget.Toast
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.webkit.WebViewCompat
import androidx.webkit.WebViewFeature
import id.ceet.browser.core.Blocker
import id.ceet.browser.core.Intent
import id.ceet.browser.core.Omnibox
import id.ceet.browser.core.Phishing
import id.ceet.browser.core.Risk
import id.ceet.browser.core.Tosh
import id.ceet.browser.core.ToshClient
import id.ceet.browser.core.ToshHit
import id.ceet.browser.core.ToshRemote
import id.ceet.browser.core.UrlTools
import id.ceet.browser.data.Settings
import id.ceet.browser.data.Store
import org.json.JSONObject
import java.io.ByteArrayInputStream
import java.net.HttpURLConnection
import java.net.URL
import java.util.concurrent.Executors
import java.util.concurrent.atomic.AtomicInteger

const val HOME_URL = "ceet://home"

/** Satu tab. State dibaca Compose; WebView dibuat malas saat pertama kali ditampilkan. */
class TabState(val id: Int, val isPrivate: Boolean, startUrl: String, startTitle: String = "") {
    var url by mutableStateOf(startUrl)
    var title by mutableStateOf(startTitle)
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
    var desktop by mutableStateOf(false)

    var webView: WebView? = null
    /** Alamat yang dimuat begitu WebView dibuat (tab hasil pemulihan sesi atau tab yang belum pernah tampil). */
    var pending: String? = if (startUrl == HOME_URL) null else startUrl
    var pendingHtml: String? = null
    val blockedCounter = AtomicInteger(0)

    var toshLoaded = false
    var toshRemote: ToshRemote? = null
    var toshRemoteQuery: String? = null
    var toshFetching: String? = null

    val isHome: Boolean get() = url == HOME_URL
    val isTosh: Boolean get() = Tosh.isTosh(url)
    val host: String get() = if (isTosh) "tosh" else (UrlTools.parse(url)?.host ?: "")
    val label: String get() = when {
        isHome -> "Tab baru"
        title.isNotBlank() -> title
        else -> host.ifEmpty { url }
    }
}

class BrowserState(private val appContext: Context, val store: Store, val blocker: Blocker) {
    val tabs = mutableStateListOf<TabState>()
    var activeId by mutableIntStateOf(0)
        private set
    private var nextId = 1
    private val main = Handler(Looper.getMainLooper())
    private val pool = Executors.newCachedThreadPool { r -> Thread(r).apply { isDaemon = true } }
    private val tosh = ToshClient()
    private val triedIcons = HashSet<String>()
    private val iconCache = HashMap<String, ImageBitmap>()

    var settings by mutableStateOf(store.settings)
        private set
    var stats by mutableStateOf(store.stats)
        private set
    var historyVersion by mutableIntStateOf(0)
        private set
    var bookmarksVersion by mutableIntStateOf(0)
        private set
    var faviconVersion by mutableIntStateOf(0)
        private set
    /** Naik saat WebView sebuah tab dibuat ulang (misalnya proses render mati), supaya tampilan memasangnya lagi. */
    var epoch by mutableIntStateOf(0)
        private set

    // Pencarian di halaman
    var findOpen by mutableStateOf(false)
        private set
    var findIndex by mutableIntStateOf(0)
        private set
    var findTotal by mutableIntStateOf(0)
        private set

    // Penghubung ke Activity (izin, berkas, layar penuh, aplikasi lain)
    var external: ((String) -> Unit)? = null
    var chooseFile: ((ValueCallback<Array<Uri>>, WebChromeClient.FileChooserParams) -> Boolean)? = null
    var showCustom: ((View, WebChromeClient.CustomViewCallback) -> Unit)? = null
    var hideCustom: (() -> Unit)? = null

    val active: TabState get() = tabs.firstOrNull { it.id == activeId } ?: tabs.first()

    init {
        val (saved, idx) = store.loadSession()
        if (saved.isEmpty()) newTab() else {
            saved.forEach { newTab(it.url, activate = false, title = it.title) }
            activeId = tabs[idx.coerceIn(0, tabs.lastIndex)].id
        }
    }

    fun updateSettings(s: Settings) { store.update(s); settings = s }

    // ---------------- tab ----------------
    fun newTab(url: String = HOME_URL, private: Boolean = false, activate: Boolean = true, title: String = ""): TabState {
        val t = TabState(nextId++, private, url, title)
        tabs.add(t)
        if (activate) select(t.id)
        return t
    }

    fun select(id: Int) {
        if (tabs.none { it.id == id }) return
        if (findOpen) endFind()
        if (id != activeId) tabs.firstOrNull { it.id == activeId }?.webView?.onPause()
        activeId = id
        tabs.firstOrNull { it.id == id }?.webView?.onResume()
    }

    fun closeTab(id: Int) {
        val i = tabs.indexOfFirst { it.id == id }
        if (i < 0) return
        if (findOpen && id == activeId) endFind()
        val t = tabs.removeAt(i)
        destroyWebView(t)
        if (tabs.isEmpty()) newTab() else if (activeId == id) select(tabs[minOf(i, tabs.lastIndex)].id)
    }

    fun closeAll() {
        if (findOpen) endFind()
        tabs.toList().forEach { destroyWebView(it) }
        tabs.clear()
        newTab()
    }

    private fun destroyWebView(t: TabState) {
        t.webView?.let {
            (it.parent as? ViewGroup)?.removeView(it)
            it.stopLoading(); it.destroy()
        }
        t.webView = null
    }

    fun saveSession() {
        val list = tabs.filter { !it.isPrivate }
        store.saveSession(list.map { Store.SavedTab(it.url, it.title) }, list.indexOfFirst { it.id == activeId }.coerceAtLeast(0))
    }

    // ---------------- navigasi ----------------
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
            "bersihkan" -> clearHistory()
            "tosh" -> navigate(active, Tosh.url(arg))
        }
    }

    /** Semua navigasi utama lewat sini: Tosh, HTTPS, parameter pelacak, lalu pemeriksaan penipuan. */
    fun navigate(tab: TabState, raw: String) {
        if (raw == HOME_URL) {
            tab.url = HOME_URL; tab.title = ""; tab.risk = Risk.AMAN; tab.riskReasons = emptyList(); tab.progress = 0
            return
        }
        if (Tosh.isTosh(raw)) { openTosh(tab, Tosh.queryOf(raw)); return }
        var url = raw
        val c = UrlTools.clean(url, upgrade = settings.upgradeHttps, strip = settings.stripParams)
        tab.upgraded = c.upgraded
        if (c.upgraded) store.bump(https = 1)
        if (c.removed.isNotEmpty()) { store.bump(params = c.removed.size); tab.stripped += c.removed.size }
        url = c.url
        stats = store.stats
        tab.blockedCounter.set(0); tab.blocked = 0; tab.trackers.clear()
        tab.title = ""
        if (settings.phishingCheck) {
            val a = Phishing.assess(url)
            tab.risk = a.risk; tab.riskReasons = a.reasons
            if (a.risk == Risk.BERBAHAYA) {
                store.bump(phishing = 1); stats = store.stats
                tab.url = url
                val html = WarningPage.html(url, a.reasons)
                val wv = tab.webView
                if (wv != null) wv.loadDataWithBaseURL("ceet://warning", html, "text/html", "utf-8", null)
                else { tab.pending = null; tab.pendingHtml = html }
                return
            }
        } else { tab.risk = Risk.AMAN; tab.riskReasons = emptyList() }
        tab.url = url
        tab.bookmarked = store.isBookmarked(url)
        val wv = tab.webView
        if (wv != null) wv.loadUrl(url, headers()) else { tab.pending = url; tab.pendingHtml = null }
    }

    private fun headers(): Map<String, String> =
        if (settings.sendGpc) mapOf("Sec-GPC" to "1", "DNT" to "1") else emptyMap()

    fun proceedAnyway(tab: TabState, url: String) {
        tab.risk = Risk.WASPADA
        tab.url = url
        tab.webView?.loadUrl(url, headers())
    }

    fun reload(tab: TabState) {
        val wv = tab.webView
        when {
            tab.isHome -> {}
            tab.isTosh -> openTosh(tab, Tosh.queryOf(tab.url))
            wv == null -> tab.pending = tab.url
            else -> wv.reload()
        }
    }

    // ---------------- Tosh ----------------
    fun openTosh(tab: TabState, query: String) {
        val q = query.trim()
        tab.url = Tosh.url(q)
        tab.title = if (q.isEmpty()) "Tosh" else "$q – Tosh"
        tab.risk = Risk.AMAN; tab.riskReasons = emptyList()
        tab.blocked = 0; tab.blockedCounter.set(0); tab.trackers.clear(); tab.upgraded = false; tab.stripped = 0
        tab.bookmarked = false
        tab.progress = 0
        val wv = tab.webView
        if (wv != null) renderTosh(tab, wv) else { tab.pending = tab.url; tab.pendingHtml = null }
    }

    private fun renderTosh(tab: TabState, wv: WebView) {
        val q = Tosh.queryOf(tab.url)
        tab.toshLoaded = false
        tab.toshRemote = null
        tab.toshRemoteQuery = null
        val local = if (q.isBlank()) emptyList() else store.suggestAll(q, 5).map {
            ToshHit(it.title, it.url, "", if (it.bookmark) "Penanda" else "Riwayat")
        }
        val online = settings.toshOnline && q.isNotBlank()
        wv.loadDataWithBaseURL(tab.url, Tosh.page(q, local, store.otherEngines(), online), "text/html", "utf-8", null)
        if (online) fetchTosh(tab, q)
    }

    private fun fetchTosh(tab: TabState, q: String) {
        if (tab.toshFetching == q) return
        tab.toshFetching = q
        pool.execute {
            val r = tosh.fetch(q)
            main.post {
                if (tab.toshFetching == q) tab.toshFetching = null
                tab.toshRemote = r
                tab.toshRemoteQuery = q
                if (tab.toshLoaded && tab.isTosh && Tosh.queryOf(tab.url) == q) injectTosh(tab)
            }
        }
    }

    private fun injectTosh(tab: TabState) {
        val r = tab.toshRemote ?: return
        tab.webView?.evaluateJavascript("window.__tosh(" + JSONObject.quote(Tosh.remoteHtml(r)) + ")", null)
    }

    // ---------------- penanda dan riwayat ----------------
    fun toggleBookmark() {
        val t = active
        if (t.isHome || t.isTosh || !t.url.startsWith("http")) return
        t.bookmarked = store.toggleBookmark(t.url, t.title)
        bookmarksVersion++
    }

    fun removeBookmark(id: String) { store.removeBookmark(id); bookmarksVersion++; active.bookmarked = store.isBookmarked(active.url) }
    fun clearHistory() { store.clearHistory(); historyVersion++ }
    fun removeHistory(id: String) { store.removeHistory(id); historyVersion++ }

    fun onVisited(tab: TabState, url: String, title: String) {
        if (tab.isPrivate || !settings.saveHistory || !url.startsWith("http")) return
        store.addHistory(url, title)
        historyVersion++
    }

    /** Hapus riwayat, ikon, cookie, penyimpanan situs, dan cache. */
    fun clearBrowsingData() {
        store.clearBrowsingData()
        iconCache.clear(); triedIcons.clear()
        historyVersion++; faviconVersion++
        try { CookieManager.getInstance().removeAllCookies(null); CookieManager.getInstance().flush() } catch (_: Exception) {}
        try { WebStorage.getInstance().deleteAllData() } catch (_: Exception) {}
        tabs.forEach { it.webView?.clearCache(true) }
    }

    // ---------------- ikon situs ----------------
    fun faviconFor(host: String): ImageBitmap? {
        @Suppress("UNUSED_VARIABLE") val v = faviconVersion // dibaca supaya Compose menggambar ulang saat ikon baru tiba
        if (host.isEmpty()) return null
        iconCache[host]?.let { return it }
        val bmp = store.loadFavicon(host)?.asImageBitmap() ?: return null
        iconCache[host] = bmp
        return bmp
    }

    private fun saveIcon(host: String, bmp: Bitmap) {
        store.saveFavicon(host, bmp)
        iconCache.remove(host)
        faviconVersion++
    }

    private fun fetchIconFallback(host: String) {
        if (!triedIcons.add(host)) return
        pool.execute {
            try {
                val c = URL("https://$host/favicon.ico").openConnection() as HttpURLConnection
                c.connectTimeout = 4000; c.readTimeout = 4000
                c.setRequestProperty("User-Agent", "Ceet/0.1")
                val bmp = if (c.responseCode in 200..299) c.inputStream.use { BitmapFactory.decodeStream(it) } else null
                if (bmp != null) main.post { saveIcon(host, bmp) }
            } catch (_: Exception) { /* situs tanpa ikon, tidak apa-apa */ }
        }
    }

    // ---------------- cari di halaman ----------------
    fun startFind() { findOpen = true; findIndex = 0; findTotal = 0 }

    fun find(q: String) {
        val wv = active.webView ?: return
        if (q.isBlank()) { wv.clearMatches(); findIndex = 0; findTotal = 0 } else wv.findAllAsync(q)
    }

    fun findNext(forward: Boolean) { active.webView?.findNext(forward) }

    fun endFind() {
        findOpen = false
        tabs.forEach { it.webView?.clearMatches() }
    }

    // ---------------- situs desktop ----------------
    fun toggleDesktop() {
        val t = active
        t.desktop = !t.desktop
        t.webView?.let { applyUserAgent(it, t.desktop); it.reload() }
    }

    private fun applyUserAgent(wv: WebView, desktop: Boolean) {
        if (!desktop) { wv.settings.userAgentString = null; return }
        val d = WebSettings.getDefaultUserAgent(wv.context)
        wv.settings.userAgentString = d.replace(Regex("\\(Linux; Android[^)]*\\)"), "(X11; Linux x86_64)").replace(" Mobile", "")
    }

    // ---------------- WebView ----------------
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
            setSupportMultipleWindows(true)
            javaScriptCanOpenWindowsAutomatically = false
        }
        if (tab.desktop) applyUserAgent(wv, true)
        CookieManager.getInstance().setAcceptThirdPartyCookies(wv, false)
        wv.webViewClient = CeetClient(tab)
        wv.webChromeClient = object : WebChromeClient() {
            override fun onProgressChanged(view: WebView, newProgress: Int) { tab.progress = newProgress }

            override fun onReceivedTitle(view: WebView, title: String?) {
                if (title.isNullOrBlank() || tab.isHome || title.startsWith("data:") || title == "about:blank") return
                tab.title = title
                if (!tab.isPrivate && tab.url.startsWith("http")) store.updateHistoryTitle(tab.url, title)
            }

            override fun onReceivedIcon(view: WebView, icon: Bitmap?) {
                if (icon == null || tab.isPrivate || !tab.url.startsWith("http")) return
                saveIcon(tab.host, icon)
            }

            // Izin kamera, mikrofon, dan lokasi ditolak otomatis.
            override fun onPermissionRequest(request: PermissionRequest) { request.deny() }
            override fun onGeolocationPermissionsShowPrompt(origin: String, callback: GeolocationPermissions.Callback) { callback.invoke(origin, false, false) }

            // Tautan target=_blank dan window.open hasil ketukan pengguna dibuka sebagai tab baru. Popup otomatis diblokir.
            override fun onCreateWindow(view: WebView, isDialog: Boolean, isUserGesture: Boolean, resultMsg: Message): Boolean {
                if (!isUserGesture) return false
                val temp = WebView(view.context)
                temp.webViewClient = object : WebViewClient() {
                    private var done = false
                    private fun open(u: String) {
                        if (done || u.startsWith("about:")) return
                        done = true
                        main.post {
                            val t = newTab(private = tab.isPrivate)
                            navigate(t, u)
                            temp.destroy()
                        }
                    }
                    override fun shouldOverrideUrlLoading(v: WebView, r: WebResourceRequest): Boolean { open(r.url.toString()); return true }
                    override fun onPageStarted(v: WebView, url: String, f: Bitmap?) { if (!url.startsWith("about:")) { v.stopLoading(); open(url) } }
                }
                (resultMsg.obj as WebView.WebViewTransport).webView = temp
                resultMsg.sendToTarget()
                return true
            }

            override fun onShowFileChooser(webView: WebView, filePathCallback: ValueCallback<Array<Uri>>, fileChooserParams: FileChooserParams): Boolean {
                val h = chooseFile
                if (h == null) { filePathCallback.onReceiveValue(null); return false }
                return h(filePathCallback, fileChooserParams)
            }

            override fun onShowCustomView(view: View, callback: CustomViewCallback) { showCustom?.invoke(view, callback) ?: callback.onCustomViewHidden() }
            override fun onHideCustomView() { hideCustom?.invoke() }
        }
        wv.setFindListener { index, total, _ ->
            if (tab.id == activeId) { findIndex = if (total == 0) 0 else index + 1; findTotal = total }
        }
        wv.setDownloadListener(DownloadListener { dl, ua, cd, mime, _ ->
            try {
                val name = URLUtil.guessFileName(dl, cd, mime)
                val req = DownloadManager.Request(Uri.parse(dl))
                    .setMimeType(mime).addRequestHeader("User-Agent", ua)
                    .setNotificationVisibility(DownloadManager.Request.VISIBILITY_VISIBLE_NOTIFY_COMPLETED)
                    .setDestinationInExternalPublicDir(Environment.DIRECTORY_DOWNLOADS, name)
                CookieManager.getInstance().getCookie(dl)?.let { req.addRequestHeader("Cookie", it) }
                (appContext.getSystemService(Context.DOWNLOAD_SERVICE) as DownloadManager).enqueue(req)
                Toast.makeText(appContext, "Mengunduh $name", Toast.LENGTH_SHORT).show()
            } catch (_: Exception) {
                Toast.makeText(appContext, "Unduhan tidak bisa dimulai", Toast.LENGTH_SHORT).show()
            }
        })
        tab.webView = wv
        val html = tab.pendingHtml
        val p = tab.pending
        tab.pendingHtml = null
        tab.pending = null
        when {
            html != null -> wv.loadDataWithBaseURL("ceet://warning", html, "text/html", "utf-8", null)
            p != null && Tosh.isTosh(p) -> renderTosh(tab, wv)
            p != null -> wv.loadUrl(p, headers())
        }
        return wv
    }

    private fun showError(tab: TabState, view: WebView, failing: String, title: String, detail: String) {
        val http = if (tab.upgraded && failing.startsWith("https://")) "http://" + failing.removePrefix("https://") else null
        tab.progress = 100
        view.loadDataWithBaseURL("ceet://error", ErrorPage.html(failing, title, detail, http), "text/html", "utf-8", null)
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
            when {
                u.startsWith("ceet://proceed") -> { request.url.getQueryParameter("u")?.let { t -> main.post { proceedAnyway(tab, t) } }; return true }
                u.startsWith("ceet://back") -> { main.post { if (view.canGoBack()) view.goBack() else navigate(tab, HOME_URL) }; return true }
                u.startsWith("ceet://retry") -> { request.url.getQueryParameter("u")?.let { t -> main.post { navigate(tab, t) } }; return true }
                u.startsWith("ceet://http") -> {
                    request.url.getQueryParameter("u")?.let { t -> main.post { tab.url = t; tab.upgraded = false; view.loadUrl(t, headers()) } }
                    return true
                }
                Tosh.isTosh(u) -> { main.post { navigate(tab, u) }; return true }
                u.startsWith("ceet://") -> return true
            }
            if (!request.isForMainFrame) return false
            val scheme = request.url.scheme ?: ""
            if (scheme != "http" && scheme != "https") {
                // Skema lain (tel, mailto, intent, ...) hanya dibuka bila pengguna mengetuknya.
                if (request.hasGesture()) external?.invoke(u)
                return true
            }
            val c = UrlTools.clean(u, upgrade = settings.upgradeHttps, strip = settings.stripParams)
            val a = if (settings.phishingCheck) Phishing.assess(c.url) else null
            if (c.url != u || (a != null && a.risk == Risk.BERBAHAYA)) {
                main.post { navigate(tab, u) }
                return true
            }
            return false
        }

        override fun onPageStarted(view: WebView, url: String, favicon: Bitmap?) {
            if (url.startsWith("http") || Tosh.isTosh(url)) tab.url = url
            if (Tosh.isTosh(url)) tab.toshLoaded = false
            tab.progress = 5
        }

        override fun onPageFinished(view: WebView, url: String) {
            tab.progress = 100
            tab.canBack = view.canGoBack(); tab.canForward = view.canGoForward()
            if (tab.isTosh) {
                tab.toshLoaded = true
                val q = Tosh.queryOf(tab.url)
                if (settings.toshOnline && q.isNotBlank()) {
                    if (tab.toshRemoteQuery == q) injectTosh(tab) else fetchTosh(tab, q)
                }
            }
            if (url.startsWith("http")) {
                tab.bookmarked = store.isBookmarked(url)
                onVisited(tab, url, view.title ?: "")
                if (!tab.isPrivate && !store.hasFavicon(tab.host)) fetchIconFallback(tab.host)
            }
        }

        override fun doUpdateVisitedHistory(view: WebView, url: String, isReload: Boolean) {
            tab.canBack = view.canGoBack(); tab.canForward = view.canGoForward()
        }

        override fun onReceivedError(view: WebView, request: WebResourceRequest, error: WebResourceError) {
            if (!request.isForMainFrame) return
            val u = request.url.toString()
            if (!u.startsWith("http")) return
            val (title, detail) = when (error.errorCode) {
                WebViewClient.ERROR_HOST_LOOKUP -> "Alamat tidak ditemukan" to "Periksa ejaan alamat dan koneksi internetmu."
                WebViewClient.ERROR_CONNECT, WebViewClient.ERROR_TIMEOUT -> "Tidak bisa terhubung" to "Situs tidak merespons. Coba lagi sebentar lagi."
                WebViewClient.ERROR_FAILED_SSL_HANDSHAKE -> "Koneksi aman gagal" to "Ceet tidak bisa membuat koneksi terenkripsi ke situs ini."
                WebViewClient.ERROR_TOO_MANY_REQUESTS, WebViewClient.ERROR_REDIRECT_LOOP -> "Terlalu banyak pengalihan" to "Situs ini mengalihkanmu berulang kali."
                else -> "Halaman tidak bisa dibuka" to (error.description?.toString() ?: "Terjadi kesalahan.")
            }
            main.post { showError(tab, view, u, title, detail) }
        }

        override fun onReceivedSslError(view: WebView, handler: SslErrorHandler, error: SslError) {
            handler.cancel()
            main.post { showError(tab, view, error.url ?: tab.url, "Sertifikat situs tidak tepercaya", "Ceet menghentikan koneksi karena identitas situs tidak bisa dipastikan. Data yang kamu kirim bisa dibaca orang lain.") }
        }

        override fun onRenderProcessGone(view: WebView, detail: RenderProcessGoneDetail): Boolean {
            main.post {
                tab.webView?.let { (it.parent as? ViewGroup)?.removeView(it); it.destroy() }
                tab.webView = null
                if (!tab.isHome) tab.pending = tab.url
                epoch++
            }
            return true
        }
    }
}

/** Halaman peringatan situs penipuan. HTML mandiri, tanpa sumber luar. */
object WarningPage {
    private fun esc(s: String) = Tosh.esc(s)

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

/** Halaman galat pemuatan. HTML mandiri. */
object ErrorPage {
    fun html(url: String, title: String, detail: String, httpUrl: String?): String {
        val enc = java.net.URLEncoder.encode(url, "UTF-8")
        val http = if (httpUrl != null) """<a class="s" href="ceet://http?u=${java.net.URLEncoder.encode(httpUrl, "UTF-8")}">Situs ini mungkin tidak mendukung HTTPS. Buka versi tidak terenkripsi (tidak aman).</a>""" else ""
        return """<!doctype html><html lang="id"><head><meta charset="utf-8"><meta name="viewport" content="width=device-width,initial-scale=1">
<style>
:root{color-scheme:light dark;--bg:#FBFAF8;--fg:#0B1A30;--muted:#5B6B82;--soft:#F3F5F9;--accent:#1580FA}
@media(prefers-color-scheme:dark){:root{--bg:#0A1222;--fg:#EAF0FB;--muted:#93A3BF;--soft:#0D172B;--accent:#4C9BFF}}
body{margin:0;background:var(--bg);color:var(--fg);font:16px/1.55 system-ui,sans-serif;padding:56px 24px;text-align:center}
.i{width:72px;height:72px;border-radius:22px;margin:0 auto 20px;display:grid;place-items:center;background:var(--soft);color:var(--muted)}
h1{font-size:22px;margin:0 0 8px}p{color:var(--muted);margin:8px 0}
.u{display:inline-block;max-width:100%;padding:6px 12px;border-radius:10px;background:var(--soft);font-size:13px;overflow-wrap:anywhere}
a.b{display:inline-block;margin-top:22px;padding:12px 28px;border-radius:24px;background:var(--accent);color:#fff;font-weight:600;text-decoration:none}
a.s{display:block;margin-top:26px;color:var(--muted);font-size:13px}
</style></head><body>
<div class="i"><svg width="36" height="36" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="1.75" stroke-linecap="round" stroke-linejoin="round"><circle cx="12" cy="12" r="8.5"/><path d="M3.5 12h17"/><path d="M12 3.5c2.5 2.4 3.8 5.2 3.8 8.5s-1.3 6.1-3.8 8.5c-2.5-2.4-3.8-5.2-3.8-8.5S9.5 5.9 12 3.5z"/></svg></div>
<h1>${Tosh.esc(title)}</h1>
<p>${Tosh.esc(detail)}</p>
<span class="u">${Tosh.esc(url)}</span><br>
<a class="b" href="ceet://retry?u=$enc">Coba lagi</a>
$http
</body></html>"""
    }
}
