package id.ceet.browser.ui

import android.app.DownloadManager
import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.view.ViewGroup
import android.webkit.WebView
import android.widget.FrameLayout
import android.widget.Toast
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.TextRange
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.input.TextFieldValue
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import id.ceet.browser.R
import id.ceet.browser.core.Intent
import id.ceet.browser.core.Omnibox
import id.ceet.browser.core.Risk
import id.ceet.browser.core.Tosh
import id.ceet.browser.core.UrlTools
import id.ceet.browser.data.SEARCH_ENGINES
import id.ceet.browser.web.BrowserState
import id.ceet.browser.web.HOME_URL
import id.ceet.browser.web.TabState
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Locale

// ---------------- dasar ----------------
@Composable
fun Ic(name: String, tint: Color, size: Int = 22, modifier: Modifier = Modifier) {
    Icon(imageVector = CeetIcons[name], contentDescription = null, tint = tint, modifier = modifier.size(size.dp))
}

@Composable
fun IconBtn(name: String, desc: String, tint: Color, enabled: Boolean = true, onClick: () -> Unit) {
    Box(
        Modifier.size(44.dp).clip(CircleShape).clickable(enabled = enabled, onClick = onClick),
        contentAlignment = Alignment.Center,
    ) { Ic(name, if (enabled) tint else tint.copy(alpha = .35f)) }
}

private enum class Sheet { NONE, TABS, SHIELD, HISTORY, BOOKMARKS, SETTINGS, PRIVACY }

private fun bare(url: String) = url.removePrefix("https://").removePrefix("http://").removePrefix("www.").trimEnd('/')

private fun toast(c: Context, msg: String) = Toast.makeText(c, msg, Toast.LENGTH_SHORT).show()

// ---------------- aplikasi ----------------
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CeetApp(state: BrowserState) {
    val c = LocalCeet.current
    val ctx = LocalContext.current
    val focusManager = LocalFocusManager.current
    val keyboard = LocalSoftwareKeyboardController.current
    var sheet by remember { mutableStateOf(Sheet.NONE) }
    var menuOpen by remember { mutableStateOf(false) }
    var focused by remember { mutableStateOf(false) }
    var field by remember { mutableStateOf(TextFieldValue("")) }
    var edited by remember { mutableStateOf(false) }
    val focusRequester = remember { FocusRequester() }
    val tab = state.active

    fun submit(text: String) {
        focusManager.clearFocus(); keyboard?.hide()
        edited = false
        state.go(text)
    }

    Box(Modifier.fillMaxSize().background(c.bg)) {
        Column(Modifier.fillMaxSize().statusBarsPadding()) {
            Box(Modifier.weight(1f).fillMaxWidth()) {
                if (tab.isHome) {
                    HomeScreen(
                        state, tab,
                        onOpen = { state.navigate(tab, it) },
                        onSearchTap = { focusRequester.requestFocus(); keyboard?.show() },
                        onSheet = { sheet = it },
                    )
                } else WebHost(state, tab)
                if (!tab.isHome && tab.progress in 1..99) {
                    LinearProgressIndicator(progress = { tab.progress / 100f }, modifier = Modifier.fillMaxWidth().height(3.dp), color = c.accent, trackColor = Color.Transparent)
                }
                if (focused && edited && field.text.isNotBlank()) {
                    SuggestionList(state, field.text, Modifier.align(Alignment.BottomCenter)) { value, isUrl ->
                        focusManager.clearFocus(); keyboard?.hide(); edited = false
                        if (isUrl) state.navigate(tab, value) else state.go(value)
                    }
                }
            }
            if (state.findOpen) FindBar(state)
            BottomBar(
                state = state, tab = tab, field = field, focused = focused, focusRequester = focusRequester,
                onField = { field = it; edited = true },
                onFocus = { f ->
                    if (f && !focused) {
                        val full = if (tab.isHome) "" else if (tab.isTosh) Tosh.queryOf(tab.url) else tab.url
                        field = TextFieldValue(full, TextRange(0, full.length)); edited = false
                    }
                    focused = f
                },
                onSubmit = { submit(it) },
                onShield = { sheet = Sheet.SHIELD }, onTabs = { sheet = Sheet.TABS },
                menuOpen = menuOpen, onMenu = { menuOpen = it },
                onMenuAction = { key ->
                    menuOpen = false
                    when (key) {
                        "new" -> state.newTab()
                        "private" -> state.newTab(private = true)
                        "share" -> {
                            val send = android.content.Intent(android.content.Intent.ACTION_SEND).apply { type = "text/plain"; putExtra(android.content.Intent.EXTRA_TEXT, tab.url) }
                            ctx.startActivity(android.content.Intent.createChooser(send, null))
                        }
                        "copy" -> {
                            (ctx.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager).setPrimaryClip(ClipData.newPlainText("tautan", tab.url))
                            toast(ctx, "Tautan disalin")
                        }
                        "find" -> state.startFind()
                        "desktop" -> state.toggleDesktop()
                        "history" -> sheet = Sheet.HISTORY
                        "bookmarks" -> sheet = Sheet.BOOKMARKS
                        "downloads" -> try { ctx.startActivity(android.content.Intent(DownloadManager.ACTION_VIEW_DOWNLOADS).addFlags(android.content.Intent.FLAG_ACTIVITY_NEW_TASK)) } catch (_: Exception) { toast(ctx, "Tidak bisa membuka unduhan") }
                        "settings" -> sheet = Sheet.SETTINGS
                        "privacy" -> sheet = Sheet.PRIVACY
                    }
                },
            )
        }
    }

    if (sheet != Sheet.NONE) {
        ModalBottomSheet(onDismissRequest = { sheet = Sheet.NONE }, sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true), containerColor = c.surface) {
            val close = { sheet = Sheet.NONE }
            when (sheet) {
                Sheet.TABS -> TabsSheet(state, close)
                Sheet.SHIELD -> ShieldSheet(state, tab) { sheet = Sheet.PRIVACY }
                Sheet.HISTORY -> HistorySheet(state, close)
                Sheet.BOOKMARKS -> BookmarksSheet(state, close)
                Sheet.SETTINGS -> SettingsSheet(state)
                Sheet.PRIVACY -> PrivacySheet(state)
                Sheet.NONE -> {}
            }
        }
    }
}

/** WebView milik tab aktif. Satu AndroidView tetap, isinya ditukar sesuai tab. */
@Composable
private fun WebHost(state: BrowserState, tab: TabState) {
    val epoch = state.epoch // dibaca agar tampilan memasang ulang bila WebView dibuat ulang
    AndroidView(
        modifier = Modifier.fillMaxSize(),
        factory = { ctx -> FrameLayout(ctx) },
        update = { frame ->
            val wv = state.ensureWebView(frame.context, tab)
            if (epoch >= 0 && (frame.childCount != 1 || frame.getChildAt(0) !== wv)) {
                (frame.getChildAt(0) as? WebView)?.onPause()
                (wv.parent as? ViewGroup)?.removeView(wv)
                frame.removeAllViews()
                frame.addView(wv, FrameLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT))
                wv.onResume()
            }
        },
        onRelease = { frame -> frame.removeAllViews() },
    )
}

// ---------------- bilah bawah ----------------
@Composable
private fun BottomBar(
    state: BrowserState, tab: TabState, field: TextFieldValue, focused: Boolean, focusRequester: FocusRequester,
    onField: (TextFieldValue) -> Unit, onFocus: (Boolean) -> Unit, onSubmit: (String) -> Unit,
    onShield: () -> Unit, onTabs: () -> Unit, menuOpen: Boolean, onMenu: (Boolean) -> Unit, onMenuAction: (String) -> Unit,
) {
    val c = LocalCeet.current
    val display = when { tab.isHome -> ""; tab.isTosh -> Tosh.queryOf(tab.url); else -> bare(tab.url) }
    val shown = if (focused) field else TextFieldValue(display)
    val barColor = if (tab.isPrivate) c.priv.copy(alpha = if (c.dark) .16f else .09f).compositeOverSurface(c) else c.surface
    Surface(color = barColor, shadowElevation = 6.dp, modifier = Modifier.fillMaxWidth().imePadding()) {
        Column(Modifier.navigationBarsPadding()) {
            Row(Modifier.fillMaxWidth().padding(horizontal = 10.dp, vertical = 8.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                Row(
                    Modifier.weight(1f).height(44.dp).clip(RoundedCornerShape(22.dp)).background(c.surface2)
                        .border(if (focused) BorderStroke(1.5.dp, c.accent) else BorderStroke(0.dp, Color.Transparent), RoundedCornerShape(22.dp)),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    val shieldTint = when (tab.risk) { Risk.BERBAHAYA -> c.bad; Risk.WASPADA -> c.warn; else -> if (tab.isHome) c.muted else c.good }
                    Box(Modifier.size(44.dp).clip(CircleShape).clickable(onClick = onShield), contentAlignment = Alignment.Center) {
                        Ic(if (tab.risk == Risk.AMAN) "shieldCheck" else "shieldAlert", shieldTint, 20)
                        if (tab.blocked > 0) Box(Modifier.align(Alignment.TopEnd).padding(top = 5.dp, end = 3.dp).clip(CircleShape).background(c.accent).padding(horizontal = 4.dp)) {
                            Text(if (tab.blocked > 99) "99+" else tab.blocked.toString(), color = Color.White, fontSize = 9.sp, fontWeight = FontWeight.Bold)
                        }
                    }
                    if (tab.isPrivate) Ic("eyeOff", c.priv, 16)
                    BasicTextField(
                        value = shown, onValueChange = onField, singleLine = true,
                        textStyle = TextStyle(color = c.fg, fontSize = 15.sp),
                        cursorBrush = SolidColor(c.accent),
                        keyboardOptions = KeyboardOptions(imeAction = ImeAction.Go, capitalization = KeyboardCapitalization.None, autoCorrect = false),
                        keyboardActions = KeyboardActions(onGo = { onSubmit(field.text) }),
                        modifier = Modifier.weight(1f).padding(horizontal = 4.dp).focusRequester(focusRequester).onFocusChanged { onFocus(it.isFocused) },
                        decorationBox = { inner ->
                            Box(contentAlignment = Alignment.CenterStart) {
                                if (shown.text.isEmpty()) Text("Cari atau ketik alamat", color = c.muted, fontSize = 15.sp, maxLines = 1)
                                inner()
                            }
                        },
                    )
                    if (focused && field.text.isNotEmpty()) IconBtn("close", "Hapus teks", c.muted) { onField(TextFieldValue("")) }
                    else if (!tab.isHome && !tab.isTosh && tab.url.startsWith("http")) IconBtn(if (tab.bookmarked) "starFill" else "star", "Penanda", if (tab.bookmarked) Color(0xFFF5A623) else c.muted) { state.toggleBookmark() }
                }
                Box(Modifier.size(44.dp).clip(CircleShape).clickable(onClick = onTabs), contentAlignment = Alignment.Center) {
                    Box(Modifier.size(26.dp).border(1.75.dp, c.fg, RoundedCornerShape(8.dp)), contentAlignment = Alignment.Center) {
                        Text(state.tabs.size.toString(), color = c.fg, fontSize = 12.sp, fontWeight = FontWeight.Bold)
                    }
                }
                Box {
                    IconBtn("dots", "Menu", c.fg) { onMenu(true) }
                    DropdownMenu(expanded = menuOpen, onDismissRequest = { onMenu(false) }, containerColor = c.surface) {
                        @Composable fun item(icon: String, label: String, key: String, checked: Boolean = false) = DropdownMenuItem(
                            text = { Text(label, color = c.fg) }, leadingIcon = { Ic(icon, c.muted, 20) },
                            trailingIcon = { if (checked) Ic("check", c.accent, 18) }, onClick = { onMenuAction(key) },
                        )
                        item("plus", "Tab baru", "new"); item("eyeOff", "Tab privat", "private")
                        if (!tab.isHome) {
                            if (tab.url.startsWith("http")) { item("share", "Bagikan", "share"); item("copy", "Salin tautan", "copy") }
                            item("findPage", "Cari di halaman", "find")
                            if (!tab.isTosh) item("monitor", "Situs desktop", "desktop", checked = tab.desktop)
                        }
                        item("clock", "Riwayat", "history"); item("bookmark", "Penanda", "bookmarks"); item("download", "Unduhan", "downloads")
                        item("shieldCheck", "Dasbor privasi", "privacy"); item("settings", "Pengaturan", "settings")
                    }
                }
            }
            if (!tab.isHome) Row(Modifier.fillMaxWidth().padding(horizontal = 24.dp), horizontalArrangement = Arrangement.SpaceBetween) {
                IconBtn("back", "Kembali", c.fg, enabled = tab.canBack) { tab.webView?.goBack() }
                IconBtn("forward", "Maju", c.fg, enabled = tab.canForward) { tab.webView?.goForward() }
                val loading = tab.progress in 1..99
                IconBtn(if (loading) "stop" else "reload", "Muat ulang", c.fg) { if (loading) tab.webView?.stopLoading() else state.reload(tab) }
                IconBtn("home", "Beranda", c.fg) { state.navigate(tab, HOME_URL) }
            }
        }
    }
}

private fun Color.compositeOverSurface(c: CeetColors): Color {
    val a = alpha
    return Color(red * a + c.surface.red * (1 - a), green * a + c.surface.green * (1 - a), blue * a + c.surface.blue * (1 - a), 1f)
}

@Composable
private fun FindBar(state: BrowserState) {
    val c = LocalCeet.current
    var q by remember { mutableStateOf("") }
    val focus = remember { FocusRequester() }
    androidx.compose.runtime.LaunchedEffect(Unit) { focus.requestFocus() }
    Surface(color = c.surface, shadowElevation = 4.dp, modifier = Modifier.fillMaxWidth()) {
        Row(Modifier.padding(horizontal = 12.dp, vertical = 4.dp), verticalAlignment = Alignment.CenterVertically) {
            BasicTextField(
                value = q, onValueChange = { q = it; state.find(it) }, singleLine = true,
                textStyle = TextStyle(color = c.fg, fontSize = 15.sp), cursorBrush = SolidColor(c.accent),
                keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search, autoCorrect = false),
                keyboardActions = KeyboardActions(onSearch = { state.findNext(true) }),
                modifier = Modifier.weight(1f).focusRequester(focus),
                decorationBox = { inner -> Box { if (q.isEmpty()) Text("Cari di halaman", color = c.muted, fontSize = 15.sp); inner() } },
            )
            if (q.isNotEmpty()) Text("${state.findIndex}/${state.findTotal}", color = c.muted, fontSize = 13.sp, modifier = Modifier.padding(horizontal = 6.dp))
            IconBtn("chevronUp", "Sebelumnya", c.fg, enabled = state.findTotal > 0) { state.findNext(false) }
            IconBtn("chevronDown", "Berikutnya", c.fg, enabled = state.findTotal > 0) { state.findNext(true) }
            IconBtn("close", "Tutup", c.fg) { state.endFind() }
        }
    }
}

// ---------------- saran ----------------
@Composable
private fun SuggestionList(state: BrowserState, q: String, modifier: Modifier, onPick: (String, Boolean) -> Unit) {
    val c = LocalCeet.current
    data class Row3(val icon: String, val title: String, val sub: String, val value: String, val isUrl: Boolean)
    val rows = ArrayList<Row3>()
    when (val first = Omnibox.classify(q)) {
        is Intent.Url -> rows.add(Row3("globe", first.url, "Buka alamat", first.url, true))
        is Intent.Command -> rows.add(Row3("command", "> " + first.name, "Perintah", q, false))
        else -> {
            rows.add(Row3("search", q, "Cari di " + state.store.engineName(), q, false))
            if (state.settings.engine != "tosh") rows.add(Row3("search", q, "Cari di Tosh", Tosh.url(q), true))
        }
    }
    state.store.suggestAll(q, 5).forEach { rows.add(Row3(if (it.bookmark) "star" else "clock", it.title, bare(it.url), it.url, true)) }
    Surface(modifier.fillMaxWidth().padding(horizontal = 10.dp, vertical = 6.dp), shape = RoundedCornerShape(16.dp), color = c.surface, shadowElevation = 10.dp, border = BorderStroke(1.dp, c.line)) {
        Column(Modifier.padding(6.dp)) {
            rows.forEach { r ->
                Row(Modifier.fillMaxWidth().clip(RoundedCornerShape(10.dp)).clickable { onPick(r.value, r.isUrl) }.padding(horizontal = 10.dp, vertical = 9.dp), verticalAlignment = Alignment.CenterVertically) {
                    Box(Modifier.size(32.dp).clip(RoundedCornerShape(9.dp)).background(c.surface2), contentAlignment = Alignment.Center) { Ic(r.icon, c.muted, 18) }
                    Spacer(Modifier.width(12.dp))
                    Column(Modifier.weight(1f)) {
                        Text(r.title, color = c.fg, fontWeight = FontWeight.SemiBold, maxLines = 1, overflow = TextOverflow.Ellipsis, fontSize = 14.sp)
                        Text(r.sub, color = c.muted, maxLines = 1, overflow = TextOverflow.Ellipsis, fontSize = 12.sp)
                    }
                }
            }
        }
    }
}

// ---------------- beranda ----------------
@Composable
private fun SiteIcon(state: BrowserState, host: String, size: Int, modifier: Modifier = Modifier) {
    val c = LocalCeet.current
    val bmp = state.faviconFor(host)
    val shape = RoundedCornerShape((size / 4).dp)
    if (bmp != null) {
        Box(modifier.size(size.dp).clip(shape).background(Color.White), contentAlignment = Alignment.Center) {
            Image(bitmap = bmp, contentDescription = null, contentScale = ContentScale.Fit, modifier = Modifier.size((size * 0.72f).dp))
        }
    } else {
        Box(modifier.size(size.dp).clip(shape).background(c.accentSoft), contentAlignment = Alignment.Center) {
            Text(host.removePrefix("www.").firstOrNull()?.uppercase() ?: "", color = c.accent, fontWeight = FontWeight.Bold, fontSize = (size * 0.42f).sp)
        }
    }
}

@Composable
private fun HomeScreen(state: BrowserState, tab: TabState, onOpen: (String) -> Unit, onSearchTap: () -> Unit, onSheet: (Sheet) -> Unit) {
    val c = LocalCeet.current
    val hour = Calendar.getInstance().get(Calendar.HOUR_OF_DAY)
    val greet = when { hour < 4 -> "Selamat malam"; hour < 11 -> "Selamat pagi"; hour < 15 -> "Selamat siang"; hour < 18 -> "Selamat sore"; else -> "Selamat malam" }
    val sites = remember(state.historyVersion, state.bookmarksVersion, tab.isPrivate) {
        if (tab.isPrivate) emptyList<Pair<String, String>>() else {
            val marks = state.store.bookmarks.mapNotNull { b -> UrlTools.parse(b.url)?.host?.removePrefix("www.")?.takeIf { it.isNotEmpty() }?.let { it to b.url } }
            val hist = state.store.history.groupBy { UrlTools.parse(it.url)?.host?.removePrefix("www.") ?: "" }
                .filterKeys { it.isNotEmpty() }.map { (h, e) -> Triple(h, e.sumOf { it.visits }, e.first().url) }.sortedByDescending { it.second }.map { it.first to it.third }
            (marks + hist).distinctBy { it.first }.take(8)
        }
    }
    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(horizontal = 20.dp), horizontalAlignment = Alignment.CenterHorizontally) {
        Spacer(Modifier.height(48.dp))
        Image(painter = painterResource(R.drawable.ceet_mark), contentDescription = "Ceet", modifier = Modifier.size(84.dp))
        Spacer(Modifier.height(6.dp))
        Text("ceet", color = c.fg, fontSize = 32.sp, fontWeight = FontWeight.SemiBold)
        Text(if (tab.isPrivate) "Tab privat. Riwayat tidak disimpan." else "$greet. Mau cari apa?", color = if (tab.isPrivate) c.priv else c.muted, fontSize = 15.sp, modifier = Modifier.padding(top = 4.dp))

        Spacer(Modifier.height(22.dp))
        Row(
            Modifier.fillMaxWidth().height(54.dp).clip(RoundedCornerShape(27.dp)).background(c.surface).border(1.dp, c.line, RoundedCornerShape(27.dp)).clickable(onClick = onSearchTap).padding(horizontal = 16.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Image(painter = painterResource(R.drawable.tosh_mark), contentDescription = "Tosh", modifier = Modifier.size(26.dp))
            Spacer(Modifier.width(12.dp))
            Text("Cari dengan Tosh", color = c.muted, fontSize = 16.sp, modifier = Modifier.weight(1f))
            Ic("search", c.muted, 20)
        }

        Row(Modifier.padding(top = 14.dp), verticalAlignment = Alignment.CenterVertically) {
            Ic("shieldCheck", c.good, 16)
            Text("  ${"%,d".format(state.stats.trackersBlocked)} pelacak diblokir sejak awal", color = c.muted, fontSize = 13.sp)
        }

        Spacer(Modifier.height(18.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Pill("clock", "Riwayat") { onSheet(Sheet.HISTORY) }
            Pill("bookmark", "Penanda") { onSheet(Sheet.BOOKMARKS) }
            Pill("settings", "Pengaturan") { onSheet(Sheet.SETTINGS) }
        }

        if (sites.isNotEmpty()) {
            Spacer(Modifier.height(28.dp))
            Text("SERING DIBUKA", color = c.muted, fontSize = 11.sp, fontWeight = FontWeight.SemiBold, letterSpacing = 1.sp, modifier = Modifier.fillMaxWidth())
            Spacer(Modifier.height(10.dp))
            sites.chunked(4).forEach { row ->
                Row(Modifier.fillMaxWidth().padding(bottom = 10.dp), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    row.forEach { (host, url) ->
                        Column(
                            Modifier.weight(1f).clip(RoundedCornerShape(16.dp)).background(c.surface).border(1.dp, c.line, RoundedCornerShape(16.dp)).clickable { onOpen(url) }.padding(vertical = 14.dp),
                            horizontalAlignment = Alignment.CenterHorizontally,
                        ) {
                            SiteIcon(state, host, 40)
                            Text(host, color = c.fg, fontSize = 11.sp, maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.padding(top = 8.dp, start = 4.dp, end = 4.dp))
                        }
                    }
                    repeat(4 - row.size) { Spacer(Modifier.weight(1f)) }
                }
            }
        }
        Spacer(Modifier.height(24.dp))
    }
}

// ---------------- lembar ----------------
@Composable
private fun SheetTitle(t: String, sub: String? = null) {
    val c = LocalCeet.current
    Column(Modifier.padding(horizontal = 20.dp, vertical = 4.dp)) {
        Text(t, color = c.fg, fontSize = 20.sp, fontWeight = FontWeight.Bold)
        if (sub != null) Text(sub, color = c.muted, fontSize = 13.sp, modifier = Modifier.padding(top = 2.dp))
        Spacer(Modifier.height(10.dp))
    }
}

@Composable
private fun Pill(icon: String, label: String, onClick: () -> Unit) {
    val c = LocalCeet.current
    Row(Modifier.clip(RoundedCornerShape(20.dp)).border(1.dp, c.line, RoundedCornerShape(20.dp)).clickable(onClick = onClick).padding(horizontal = 14.dp, vertical = 9.dp), verticalAlignment = Alignment.CenterVertically) {
        Ic(icon, c.accent, 18); Spacer(Modifier.width(8.dp)); Text(label, color = c.fg, fontWeight = FontWeight.SemiBold, fontSize = 14.sp)
    }
}

@Composable
private fun SearchField(value: String, onChange: (String) -> Unit, hint: String) {
    val c = LocalCeet.current
    Row(Modifier.padding(horizontal = 20.dp, vertical = 6.dp).fillMaxWidth().height(42.dp).clip(RoundedCornerShape(21.dp)).background(c.surface2).padding(horizontal = 14.dp), verticalAlignment = Alignment.CenterVertically) {
        Ic("search", c.muted, 18)
        Spacer(Modifier.width(10.dp))
        BasicTextField(
            value = value, onValueChange = onChange, singleLine = true, textStyle = TextStyle(color = c.fg, fontSize = 15.sp), cursorBrush = SolidColor(c.accent),
            keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search, autoCorrect = false), modifier = Modifier.weight(1f),
            decorationBox = { inner -> Box { if (value.isEmpty()) Text(hint, color = c.muted, fontSize = 15.sp); inner() } },
        )
    }
}

@Composable
private fun TabsSheet(state: BrowserState, close: () -> Unit) {
    val c = LocalCeet.current
    SheetTitle("${state.tabs.size} tab terbuka")
    Row(Modifier.padding(horizontal = 20.dp, vertical = 4.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        Pill("plus", "Tab baru") { state.newTab(); close() }
        Pill("eyeOff", "Privat") { state.newTab(private = true); close() }
        if (state.tabs.size > 1) Pill("trash", "Tutup semua") { state.closeAll(); close() }
    }
    LazyColumn(Modifier.padding(top = 8.dp).heightIn(max = 520.dp)) {
        items(state.tabs.toList(), key = { it.id }) { t ->
            Row(
                Modifier.fillMaxWidth().background(if (t.id == state.activeId) c.accentSoft else Color.Transparent).clickable { state.select(t.id); close() }.padding(start = 20.dp, end = 8.dp, top = 8.dp, bottom = 8.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Box(Modifier.size(32.dp), contentAlignment = Alignment.Center) {
                    when {
                        t.isPrivate -> Ic("eyeOff", c.priv, 22)
                        t.isHome -> Image(painter = painterResource(R.drawable.ceet_mark), contentDescription = null, modifier = Modifier.size(26.dp))
                        t.isTosh -> Image(painter = painterResource(R.drawable.tosh_mark), contentDescription = null, modifier = Modifier.size(26.dp))
                        else -> SiteIcon(state, t.host, 30)
                    }
                }
                Spacer(Modifier.width(14.dp))
                Column(Modifier.weight(1f)) {
                    Text(t.label, color = c.fg, fontWeight = FontWeight.Medium, maxLines = 1, overflow = TextOverflow.Ellipsis)
                    Text(if (t.isHome) "Beranda Ceet" else if (t.isTosh) "Tosh" else t.host, color = c.muted, fontSize = 12.sp, maxLines = 1)
                }
                IconBtn("close", "Tutup tab", c.muted) { state.closeTab(t.id); if (state.tabs.size <= 1 && t.isHome) close() }
            }
        }
    }
    Spacer(Modifier.height(16.dp).navigationBarsPadding())
}

@Composable
private fun StatRow(icon: String, label: String, value: String, tint: Color) {
    val c = LocalCeet.current
    Row(Modifier.fillMaxWidth().padding(horizontal = 20.dp, vertical = 10.dp), verticalAlignment = Alignment.CenterVertically) {
        Ic(icon, tint, 20); Spacer(Modifier.width(14.dp))
        Text(label, color = c.fg, modifier = Modifier.weight(1f)); Text(value, color = c.muted)
    }
}

@Composable
private fun ShieldSheet(state: BrowserState, tab: TabState, openDashboard: () -> Unit) {
    val c = LocalCeet.current
    val secure = tab.url.startsWith("https://")
    val web = tab.url.startsWith("http")
    val grade = when { tab.risk == Risk.BERBAHAYA -> "D"; tab.risk == Risk.WASPADA || (!secure && web) -> "C"; else -> "A" }
    val gradeColor = when (grade) { "A" -> c.good; "C" -> c.warn; else -> c.bad }
    Column(Modifier.verticalScroll(rememberScrollState()).navigationBarsPadding()) {
        Row(Modifier.padding(horizontal = 20.dp, vertical = 4.dp), verticalAlignment = Alignment.CenterVertically) {
            Box(Modifier.size(52.dp).clip(RoundedCornerShape(16.dp)).background(gradeColor), contentAlignment = Alignment.Center) { Text(grade, color = Color.White, fontSize = 24.sp, fontWeight = FontWeight.Bold) }
            Spacer(Modifier.width(14.dp))
            Column {
                Text(
                    if (!web) "Halaman Ceet" else when (tab.risk) { Risk.BERBAHAYA -> "Situs ini berbahaya"; Risk.WASPADA -> "Hati-hati dengan situs ini"; else -> if (secure) "Perlindungan aktif" else "Koneksi tidak terenkripsi" },
                    color = c.fg, fontWeight = FontWeight.Bold, fontSize = 16.sp,
                )
                Text(if (!web) "Tidak memuat pelacak." else tab.host + " · " + tab.blocked + " pelacak diblokir", color = c.muted, fontSize = 13.sp)
            }
        }
        if (tab.riskReasons.isNotEmpty() && tab.risk != Risk.AMAN) {
            Column(Modifier.padding(20.dp, 12.dp).fillMaxWidth().clip(RoundedCornerShape(12.dp)).background(c.bad.copy(alpha = .12f)).padding(12.dp)) {
                Text("Alasan peringatan", color = c.bad, fontWeight = FontWeight.Bold, fontSize = 13.sp)
                tab.riskReasons.forEach { Text("• $it", color = c.bad, fontSize = 13.sp) }
            }
        }
        if (web) {
            StatRow(if (secure) "lock" else "lockOpen", "Koneksi", if (secure) "HTTPS" else "Tidak aman", if (secure) c.good else c.bad)
            StatRow("shield", "Pelacak diblokir", tab.blocked.toString(), c.muted)
            StatRow("bolt", "Dinaikkan ke HTTPS", if (tab.upgraded) "Ya" else "Tidak perlu", c.muted)
            StatRow("trash", "Parameter pelacak dibuang", tab.stripped.toString(), c.muted)
            if (tab.trackers.isNotEmpty()) Text(tab.trackers.joinToString("\n"), color = c.muted, fontSize = 12.sp, modifier = Modifier.padding(horizontal = 20.dp, vertical = 8.dp))
        }
        ToggleRow("Blokir pelacak", "Berlaku untuk semua situs", state.settings.blockTrackers) { state.updateSettings(state.settings.copy(blockTrackers = it)) }
        Box(Modifier.fillMaxWidth().clickable(onClick = openDashboard).padding(20.dp)) { Text("Buka dasbor privasi", color = c.accent, fontWeight = FontWeight.SemiBold) }
        Spacer(Modifier.height(16.dp))
    }
}

@Composable
private fun ToggleRow(title: String, sub: String?, value: Boolean, onChange: (Boolean) -> Unit) {
    val c = LocalCeet.current
    Row(Modifier.fillMaxWidth().clickable { onChange(!value) }.padding(horizontal = 20.dp, vertical = 12.dp), verticalAlignment = Alignment.CenterVertically) {
        Column(Modifier.weight(1f)) {
            Text(title, color = c.fg, fontWeight = FontWeight.Medium)
            if (sub != null) Text(sub, color = c.muted, fontSize = 12.sp)
        }
        Switch(checked = value, onCheckedChange = onChange, colors = SwitchDefaults.colors(checkedTrackColor = c.accent, checkedThumbColor = Color.White))
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun SettingsSheet(state: BrowserState) {
    val c = LocalCeet.current
    val s = state.settings
    var confirmClear by remember { mutableStateOf(false) }
    Column(Modifier.verticalScroll(rememberScrollState()).navigationBarsPadding()) {
        SheetTitle("Pengaturan")
        Text("Tema", color = c.muted, fontSize = 12.sp, fontWeight = FontWeight.SemiBold, modifier = Modifier.padding(horizontal = 20.dp))
        Row(Modifier.padding(20.dp, 8.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            listOf("system" to "Otomatis", "light" to "Terang", "dark" to "Gelap").forEach { (k, l) -> Chip(l, s.theme == k) { state.updateSettings(s.copy(theme = k)) } }
        }
        Text("Mesin pencari", color = c.muted, fontSize = 12.sp, fontWeight = FontWeight.SemiBold, modifier = Modifier.padding(horizontal = 20.dp, vertical = 4.dp))
        FlowRow(Modifier.padding(20.dp, 4.dp), horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            SEARCH_ENGINES.forEach { (k, v) -> Chip(v.first, s.engine == k) { state.updateSettings(s.copy(engine = k)) } }
        }
        ToggleRow("Tosh mencari di internet", "Jawaban singkat dari DuckDuckGo dan artikel Wikipedia. Mati: hanya riwayat dan penanda.", s.toshOnline) { state.updateSettings(s.copy(toshOnline = it)) }
        ToggleRow("Blokir pelacak dan iklan", "Permintaan ke domain pelacak yang dikenal dibatalkan.", s.blockTrackers) { state.updateSettings(s.copy(blockTrackers = it)) }
        ToggleRow("Utamakan HTTPS", "http:// dinaikkan ke https://, kecuali alamat lokal.", s.upgradeHttps) { state.updateSettings(s.copy(upgradeHttps = it)) }
        ToggleRow("Buang parameter pelacak", "Contoh: utm_source, fbclid, gclid.", s.stripParams) { state.updateSettings(s.copy(stripParams = it)) }
        ToggleRow("Kirim sinyal Global Privacy Control", "Situs boleh mengabaikannya.", s.sendGpc) { state.updateSettings(s.copy(sendGpc = it)) }
        ToggleRow("Periksa situs penipuan", "Dinilai di perangkatmu. Alamat tidak dikirim ke server.", s.phishingCheck) { state.updateSettings(s.copy(phishingCheck = it)) }
        ToggleRow("Simpan riwayat", "Tab privat tidak pernah disimpan.", s.saveHistory) { state.updateSettings(s.copy(saveHistory = it)) }
        Box(Modifier.fillMaxWidth().clickable { if (confirmClear) { state.clearBrowsingData(); confirmClear = false } else confirmClear = true }.padding(20.dp)) {
            Text(if (confirmClear) "Ketuk lagi untuk menghapus riwayat, cookie, dan cache" else "Hapus data penjelajahan", color = c.bad, fontWeight = FontWeight.SemiBold)
        }
        Text("Ceet 0.2.0 · ${state.blocker.size} domain pelacak", color = c.muted, fontSize = 12.sp, modifier = Modifier.padding(20.dp))
    }
}

@Composable
private fun Chip(label: String, on: Boolean, onClick: () -> Unit) {
    val c = LocalCeet.current
    Box(
        Modifier.clip(RoundedCornerShape(18.dp)).background(if (on) c.accentSoft else c.surface2).border(1.dp, if (on) c.accent else Color.Transparent, RoundedCornerShape(18.dp)).clickable(onClick = onClick).padding(horizontal = 14.dp, vertical = 8.dp),
    ) { Text(label, color = if (on) c.accent else c.muted, fontWeight = FontWeight.Medium, fontSize = 13.sp) }
}

private fun dayLabel(ms: Long): String {
    val now = Calendar.getInstance()
    val t = Calendar.getInstance().apply { timeInMillis = ms }
    fun same(a: Calendar, b: Calendar) = a.get(Calendar.YEAR) == b.get(Calendar.YEAR) && a.get(Calendar.DAY_OF_YEAR) == b.get(Calendar.DAY_OF_YEAR)
    if (same(now, t)) return "Hari ini"
    val y = Calendar.getInstance().apply { add(Calendar.DAY_OF_YEAR, -1) }
    if (same(y, t)) return "Kemarin"
    return SimpleDateFormat("d MMM yyyy", Locale("id", "ID")).format(t.time)
}

private sealed class HRow {
    data class Head(val label: String) : HRow()
    data class Item(val id: String, val title: String, val url: String) : HRow()
}

@Composable
private fun HistorySheet(state: BrowserState, close: () -> Unit) {
    val c = LocalCeet.current
    var q by remember { mutableStateOf("") }
    val rows = remember(state.historyVersion, q) {
        val words = q.lowercase().split(Regex("\\s+")).filter { it.isNotEmpty() }
        val list = state.store.history.filter { e -> val hay = (e.title + " " + e.url).lowercase(); words.all { hay.contains(it) } }.take(300)
        val out = ArrayList<HRow>()
        var last = ""
        list.forEach { e ->
            val d = dayLabel(e.last)
            if (d != last) { out.add(HRow.Head(d)); last = d }
            out.add(HRow.Item(e.id, e.title, e.url))
        }
        out
    }
    SheetTitle("Riwayat", "Tersimpan hanya di perangkat ini.")
    SearchField(q, { q = it }, "Cari di riwayat")
    if (rows.isEmpty()) Text(if (q.isBlank()) "Belum ada riwayat. Situs yang kamu buka akan muncul di sini." else "Tidak ada yang cocok.", color = c.muted, modifier = Modifier.padding(20.dp))
    else {
        if (q.isBlank()) Box(Modifier.fillMaxWidth().clickable { state.clearHistory() }.padding(horizontal = 20.dp, vertical = 8.dp)) { Text("Hapus semua riwayat", color = c.bad, fontWeight = FontWeight.SemiBold) }
        LazyColumn(Modifier.heightIn(max = 520.dp)) {
            items(rows, key = { r -> when (r) { is HRow.Head -> "h:" + r.label; is HRow.Item -> r.id } }) { r ->
                when (r) {
                    is HRow.Head -> Text(r.label.uppercase(), color = c.muted, fontSize = 11.sp, fontWeight = FontWeight.SemiBold, letterSpacing = 1.sp, modifier = Modifier.padding(start = 20.dp, top = 14.dp, bottom = 4.dp))
                    is HRow.Item -> Row(Modifier.fillMaxWidth().clickable { state.navigate(state.active, r.url); close() }.padding(start = 20.dp, end = 8.dp, top = 6.dp, bottom = 6.dp), verticalAlignment = Alignment.CenterVertically) {
                        SiteIcon(state, UrlTools.parse(r.url)?.host ?: "", 30)
                        Spacer(Modifier.width(14.dp))
                        Column(Modifier.weight(1f)) {
                            Text(r.title, color = c.fg, maxLines = 1, overflow = TextOverflow.Ellipsis, fontWeight = FontWeight.Medium)
                            Text(bare(r.url), color = c.muted, fontSize = 12.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
                        }
                        IconBtn("close", "Hapus", c.muted) { state.removeHistory(r.id) }
                    }
                }
            }
        }
    }
    Spacer(Modifier.height(16.dp).navigationBarsPadding())
}

@Composable
private fun BookmarksSheet(state: BrowserState, close: () -> Unit) {
    val c = LocalCeet.current
    var q by remember { mutableStateOf("") }
    val list = remember(state.bookmarksVersion, q) {
        val words = q.lowercase().split(Regex("\\s+")).filter { it.isNotEmpty() }
        state.store.bookmarks.filter { b -> val hay = (b.title + " " + b.url).lowercase(); words.all { hay.contains(it) } }
    }
    SheetTitle("Penanda")
    SearchField(q, { q = it }, "Cari di penanda")
    if (list.isEmpty()) Text(if (q.isBlank()) "Belum ada penanda. Buka sebuah situs lalu ketuk bintang di kolom alamat." else "Tidak ada yang cocok.", color = c.muted, modifier = Modifier.padding(20.dp))
    LazyColumn(Modifier.heightIn(max = 520.dp)) {
        items(list, key = { it.id }) { b ->
            Row(Modifier.fillMaxWidth().clickable { state.navigate(state.active, b.url); close() }.padding(start = 20.dp, end = 8.dp, top = 6.dp, bottom = 6.dp), verticalAlignment = Alignment.CenterVertically) {
                SiteIcon(state, UrlTools.parse(b.url)?.host ?: "", 30)
                Spacer(Modifier.width(14.dp))
                Column(Modifier.weight(1f)) {
                    Text(b.title, color = c.fg, maxLines = 1, overflow = TextOverflow.Ellipsis, fontWeight = FontWeight.Medium)
                    Text(bare(b.url), color = c.muted, fontSize = 12.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
                }
                IconBtn("trash", "Hapus penanda", c.muted) { state.removeBookmark(b.id) }
            }
        }
    }
    Spacer(Modifier.height(16.dp).navigationBarsPadding())
}

@Composable
private fun PrivacySheet(state: BrowserState) {
    val c = LocalCeet.current
    val st = state.stats
    Column(Modifier.verticalScroll(rememberScrollState()).navigationBarsPadding()) {
        SheetTitle("Dasbor privasi", "Dihitung di perangkatmu. Tidak ada yang dikirim ke mana pun.")
        listOf("Permintaan pelacak diblokir" to st.trackersBlocked, "Parameter pelacak dibuang" to st.paramsStripped, "Tautan dinaikkan ke HTTPS" to st.httpsUpgraded, "Situs penipuan dihentikan" to st.phishingStopped).forEach { (l, n) ->
            Row(Modifier.fillMaxWidth().padding(horizontal = 20.dp, vertical = 8.dp), verticalAlignment = Alignment.CenterVertically) {
                Text("%,d".format(n), color = c.accent, fontSize = 26.sp, fontWeight = FontWeight.Bold, modifier = Modifier.width(100.dp))
                Text(l, color = c.muted)
            }
        }
        Text(
            "Yang Ceet lakukan: membatalkan permintaan ke domain pelacak yang dikenal, membuang parameter pelacak, memaksa HTTPS bila memungkinkan, mengirim sinyal Global Privacy Control, menolak izin kamera, mikrofon, dan lokasi, memblokir popup otomatis, dan menilai situs penipuan di perangkatmu.\n\n" +
                "Tosh tidak menyimpan pencarianmu. Saat pencarian internet Tosh aktif, kata yang kamu cari dikirim ke DuckDuckGo dan Wikipedia tanpa cookie.\n\n" +
                "Yang Ceet tidak lakukan: menyembunyikan alamat IP-mu, membuatmu anonim, atau menjamin perlindungan penuh. Daftar pelacak hanya mencakup domain yang dikenal. Pemblokiran berlaku untuk permintaan sub-sumber di dalam halaman, bukan untuk pengalihan di server.",
            color = c.muted, fontSize = 13.sp, modifier = Modifier.padding(20.dp),
        )
    }
}
