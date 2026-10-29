package id.ceet.browser.ui

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
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
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import id.ceet.browser.core.Intent
import id.ceet.browser.core.Omnibox
import id.ceet.browser.core.Risk
import id.ceet.browser.data.SEARCH_ENGINES
import id.ceet.browser.web.BrowserState
import id.ceet.browser.web.TabState

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

private enum class Sheet { NONE, TABS, SHIELD, MENU_HISTORY, MENU_BOOKMARKS, SETTINGS, PRIVACY }

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CeetApp(state: BrowserState, ensureWebView: @Composable (TabState) -> Unit) {
    val c = LocalCeet.current
    var sheet by remember { mutableStateOf(Sheet.NONE) }
    var menuOpen by remember { mutableStateOf(false) }
    var focused by remember { mutableStateOf(false) }
    var text by remember { mutableStateOf("") }
    val tab = state.active

    Box(Modifier.fillMaxSize().background(c.bg)) {
        Column(Modifier.fillMaxSize().statusBarsPadding()) {
            Box(Modifier.weight(1f).fillMaxWidth()) {
                if (tab.isHome) HomeScreen(state, onSearch = { state.go(it) })
                else androidx.compose.runtime.key(tab.id) { ensureWebView(tab) }
                if (!tab.isHome && tab.progress in 1..99) {
                    LinearProgressIndicator(progress = { tab.progress / 100f }, modifier = Modifier.fillMaxWidth().height(3.dp), color = c.accent, trackColor = Color.Transparent)
                }
                if (focused && text.isNotBlank()) {
                    SuggestionList(state, text, Modifier.align(Alignment.BottomCenter)) { value ->
                        text = ""; focused = false; state.go(value)
                    }
                }
            }
            BottomBar(
                state = state, tab = tab, text = text, onText = { text = it }, focused = focused, onFocus = { focused = it },
                onSubmit = { state.go(it); text = ""; focused = false },
                onShield = { sheet = Sheet.SHIELD }, onTabs = { sheet = Sheet.TABS },
                menuOpen = menuOpen, onMenu = { menuOpen = it },
                onMenuAction = { menuOpen = false; when (it) {
                    "new" -> state.newTab(); "private" -> state.newTab(private = true)
                    "history" -> sheet = Sheet.MENU_HISTORY; "bookmarks" -> sheet = Sheet.MENU_BOOKMARKS
                    "settings" -> sheet = Sheet.SETTINGS; "privacy" -> sheet = Sheet.PRIVACY
                } },
            )
        }
    }

    if (sheet != Sheet.NONE) {
        ModalBottomSheet(onDismissRequest = { sheet = Sheet.NONE }, sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = sheet != Sheet.SHIELD), containerColor = c.surface) {
            when (sheet) {
                Sheet.TABS -> TabsSheet(state) { sheet = Sheet.NONE }
                Sheet.SHIELD -> ShieldSheet(state, tab) { sheet = Sheet.PRIVACY }
                Sheet.MENU_HISTORY -> HistorySheet(state) { sheet = Sheet.NONE }
                Sheet.MENU_BOOKMARKS -> BookmarksSheet(state) { sheet = Sheet.NONE }
                Sheet.SETTINGS -> SettingsSheet(state)
                Sheet.PRIVACY -> PrivacySheet(state)
                Sheet.NONE -> {}
            }
        }
    }
}

// ---------------- bilah bawah ----------------
@Composable
private fun BottomBar(
    state: BrowserState, tab: TabState, text: String, onText: (String) -> Unit, focused: Boolean, onFocus: (Boolean) -> Unit,
    onSubmit: (String) -> Unit, onShield: () -> Unit, onTabs: () -> Unit, menuOpen: Boolean, onMenu: (Boolean) -> Unit, onMenuAction: (String) -> Unit,
) {
    val c = LocalCeet.current
    val shown = if (focused) text else if (tab.isHome) "" else tab.url.removePrefix("https://").removePrefix("http://").removePrefix("www.").trimEnd('/')
    Surface(color = c.surface, shadowElevation = 6.dp, modifier = Modifier.fillMaxWidth().imePadding()) {
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
                        value = shown, onValueChange = onText, singleLine = true,
                        textStyle = TextStyle(color = c.fg, fontSize = 15.sp),
                        cursorBrush = SolidColor(c.accent),
                        keyboardOptions = KeyboardOptions(imeAction = ImeAction.Go, capitalization = KeyboardCapitalization.None),
                        keyboardActions = KeyboardActions(onGo = { onSubmit(text) }),
                        modifier = Modifier.weight(1f).padding(horizontal = 4.dp).onFocusChanged { onFocus(it.isFocused) },
                        decorationBox = { inner ->
                            Box(contentAlignment = Alignment.CenterStart) {
                                if (shown.isEmpty()) Text("Cari atau ketik alamat", color = c.muted, fontSize = 15.sp, maxLines = 1)
                                inner()
                            }
                        },
                    )
                    if (!tab.isHome && !focused) IconBtn(if (tab.bookmarked) "starFill" else "star", "Penanda", if (tab.bookmarked) Color(0xFFF5A623) else c.muted) { state.toggleBookmark() }
                }
                Box(Modifier.size(44.dp).clip(CircleShape).clickable(onClick = onTabs), contentAlignment = Alignment.Center) {
                    Box(Modifier.size(26.dp).border(1.75.dp, c.fg, RoundedCornerShape(8.dp)), contentAlignment = Alignment.Center) {
                        Text(state.tabs.size.toString(), color = c.fg, fontSize = 12.sp, fontWeight = FontWeight.Bold)
                    }
                }
                Box {
                    IconBtn("dots", "Menu", c.fg) { onMenu(true) }
                    DropdownMenu(expanded = menuOpen, onDismissRequest = { onMenu(false) }, containerColor = c.surface) {
                        @Composable fun item(icon: String, label: String, key: String) = DropdownMenuItem(
                            text = { Text(label, color = c.fg) }, leadingIcon = { Ic(icon, c.muted, 20) }, onClick = { onMenuAction(key) },
                        )
                        item("plus", "Tab baru", "new"); item("eyeOff", "Tab privat", "private")
                        item("clock", "Riwayat", "history"); item("bookmark", "Penanda", "bookmarks")
                        item("shieldCheck", "Dasbor privasi", "privacy"); item("settings", "Pengaturan", "settings")
                    }
                }
            }
            if (!tab.isHome) Row(Modifier.fillMaxWidth().padding(horizontal = 24.dp), horizontalArrangement = Arrangement.SpaceBetween) {
                IconBtn("back", "Kembali", c.fg, enabled = tab.canBack) { tab.webView?.goBack() }
                IconBtn("forward", "Maju", c.fg, enabled = tab.canForward) { tab.webView?.goForward() }
                IconBtn(if (tab.progress in 1..99) "stop" else "reload", "Muat ulang", c.fg) { if (tab.progress in 1..99) tab.webView?.stopLoading() else tab.webView?.reload() }
                IconBtn("home", "Beranda", c.fg) { state.navigate(tab, id.ceet.browser.web.HOME_URL) }
            }
        }
    }
}

// ---------------- saran ----------------
@Composable
private fun SuggestionList(state: BrowserState, q: String, modifier: Modifier, onPick: (String) -> Unit) {
    val c = LocalCeet.current
    val first = Omnibox.classify(q)
    val rows = ArrayList<Triple<String, String, String>>() // ikon, judul, sub (value dipetakan lewat indeks)
    val values = ArrayList<String>()
    when (first) {
        is Intent.Url -> { rows.add(Triple("globe", first.url, "Buka alamat")); values.add(q) }
        is Intent.Command -> { rows.add(Triple("command", "> " + first.name, "Perintah")); values.add(q) }
        else -> { rows.add(Triple("search", q, "Cari di " + state.store.engineName())); values.add(q) }
    }
    state.store.suggest(q, 5).forEach { rows.add(Triple("clock", it.title, it.url.removePrefix("https://").removePrefix("http://").removePrefix("www."))); values.add(it.url) }
    Surface(modifier.fillMaxWidth().padding(horizontal = 10.dp, vertical = 6.dp), shape = RoundedCornerShape(16.dp), color = c.surface, shadowElevation = 10.dp, border = BorderStroke(1.dp, c.line)) {
        Column(Modifier.padding(6.dp)) {
            rows.forEachIndexed { i, r ->
                Row(Modifier.fillMaxWidth().clip(RoundedCornerShape(10.dp)).clickable { onPick(values[i]) }.padding(horizontal = 10.dp, vertical = 9.dp), verticalAlignment = Alignment.CenterVertically) {
                    Box(Modifier.size(32.dp).clip(RoundedCornerShape(9.dp)).background(c.surface2), contentAlignment = Alignment.Center) { Ic(r.first, c.muted, 18) }
                    Spacer(Modifier.width(12.dp))
                    Column(Modifier.weight(1f)) {
                        Text(r.second, color = c.fg, fontWeight = FontWeight.SemiBold, maxLines = 1, overflow = TextOverflow.Ellipsis, fontSize = 14.sp)
                        Text(r.third, color = c.muted, maxLines = 1, overflow = TextOverflow.Ellipsis, fontSize = 12.sp)
                    }
                }
            }
        }
    }
}

// ---------------- beranda ----------------
@Composable
private fun HomeScreen(state: BrowserState, onSearch: (String) -> Unit) {
    val c = LocalCeet.current
    val hour = java.util.Calendar.getInstance().get(java.util.Calendar.HOUR_OF_DAY)
    val greet = when { hour < 4 -> "Selamat malam"; hour < 11 -> "Selamat pagi"; hour < 15 -> "Selamat siang"; hour < 18 -> "Selamat sore"; else -> "Selamat malam" }
    val sites = remember(state.historyVersion) {
        state.store.history.groupBy { id.ceet.browser.core.UrlTools.parse(it.url)?.host?.removePrefix("www.") ?: "" }
            .filterKeys { it.isNotEmpty() }.map { (h, e) -> Triple(h, e.sumOf { it.visits }, e.first().url) }.sortedByDescending { it.second }.take(8)
    }
    Column(Modifier.fillMaxSize().padding(horizontal = 20.dp), horizontalAlignment = Alignment.CenterHorizontally) {
        Spacer(Modifier.height(72.dp))
        Box(Modifier.size(76.dp).clip(RoundedCornerShape(22.dp)).background(Brush.linearGradient(listOf(c.accent, c.accentDeep))), contentAlignment = Alignment.Center) {
            Text("c", color = Color.White, fontSize = 44.sp, fontWeight = FontWeight.Bold)
        }
        Spacer(Modifier.height(12.dp))
        Text("ceet", color = c.fg, fontSize = 32.sp, fontWeight = FontWeight.SemiBold)
        Text("$greet. Mau cari apa?", color = c.muted, fontSize = 15.sp, modifier = Modifier.padding(top = 6.dp))
        Row(Modifier.padding(top = 14.dp), verticalAlignment = Alignment.CenterVertically) {
            Ic("shieldCheck", c.good, 16)
            Text("  ${"%,d".format(state.stats.trackersBlocked)} pelacak diblokir sejak awal", color = c.muted, fontSize = 13.sp)
        }
        if (sites.isNotEmpty()) {
            Spacer(Modifier.height(32.dp))
            Text("SERING DIBUKA", color = c.muted, fontSize = 11.sp, fontWeight = FontWeight.SemiBold, letterSpacing = 1.sp, modifier = Modifier.fillMaxWidth())
            Spacer(Modifier.height(10.dp))
            sites.chunked(4).forEach { row ->
                Row(Modifier.fillMaxWidth().padding(bottom = 10.dp), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    row.forEach { (host, _, url) ->
                        Column(
                            Modifier.weight(1f).clip(RoundedCornerShape(16.dp)).background(c.surface).border(1.dp, c.line, RoundedCornerShape(16.dp)).clickable { onSearch(url) }.padding(vertical = 14.dp),
                            horizontalAlignment = Alignment.CenterHorizontally,
                        ) {
                            val hue = host.fold(0) { a, ch -> (a * 31 + ch.code) % 360 }
                            Box(Modifier.size(38.dp).clip(RoundedCornerShape(11.dp)).background(Color.hsv((205 + hue % 55).toFloat(), .72f, .85f)), contentAlignment = Alignment.Center) {
                                Text(host.first().uppercase(), color = Color.White, fontWeight = FontWeight.Bold)
                            }
                            Text(host, color = c.fg, fontSize = 11.sp, maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.padding(top = 8.dp, start = 4.dp, end = 4.dp))
                        }
                    }
                    repeat(4 - row.size) { Spacer(Modifier.weight(1f)) }
                }
            }
        }
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
private fun TabsSheet(state: BrowserState, close: () -> Unit) {
    val c = LocalCeet.current
    SheetTitle("${state.tabs.size} tab terbuka")
    Row(Modifier.padding(horizontal = 20.dp, vertical = 4.dp), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
        Pill("plus", "Tab baru") { state.newTab(); close() }
        Pill("eyeOff", "Tab privat") { state.newTab(private = true); close() }
    }
    LazyColumn(Modifier.padding(top = 8.dp, bottom = 24.dp)) {
        items(state.tabs.toList(), key = { it.id }) { t ->
            Row(
                Modifier.fillMaxWidth().clickable { state.activeId = t.id; close() }.background(if (t.id == state.activeId) c.accentSoft else Color.Transparent).padding(horizontal = 20.dp, vertical = 12.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Ic(if (t.isPrivate) "eyeOff" else if (t.isHome) "bolt" else "globe", if (t.isPrivate) c.priv else c.muted, 20)
                Spacer(Modifier.width(14.dp))
                Column(Modifier.weight(1f)) {
                    Text(if (t.isHome) "Tab baru" else t.title, color = c.fg, fontWeight = FontWeight.Medium, maxLines = 1, overflow = TextOverflow.Ellipsis)
                    Text(if (t.isHome) "Beranda Ceet" else t.host, color = c.muted, fontSize = 12.sp, maxLines = 1)
                }
                IconBtn("close", "Tutup tab", c.muted) { state.closeTab(t.id); if (state.tabs.isEmpty()) close() }
            }
        }
    }
}

@Composable
private fun Pill(icon: String, label: String, onClick: () -> Unit) {
    val c = LocalCeet.current
    Row(Modifier.clip(RoundedCornerShape(20.dp)).border(1.dp, c.line, RoundedCornerShape(20.dp)).clickable(onClick = onClick).padding(horizontal = 16.dp, vertical = 10.dp), verticalAlignment = Alignment.CenterVertically) {
        Ic(icon, c.accent, 18); Spacer(Modifier.width(8.dp)); Text(label, color = c.fg, fontWeight = FontWeight.SemiBold, fontSize = 14.sp)
    }
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
    val grade = when { tab.risk == Risk.BERBAHAYA -> "D"; tab.risk == Risk.WASPADA || (!secure && !tab.isHome) -> "C"; else -> "A" }
    val gradeColor = when (grade) { "A" -> c.good; "C" -> c.warn; else -> c.bad }
    Row(Modifier.padding(horizontal = 20.dp, vertical = 4.dp), verticalAlignment = Alignment.CenterVertically) {
        Box(Modifier.size(52.dp).clip(RoundedCornerShape(16.dp)).background(gradeColor), contentAlignment = Alignment.Center) { Text(grade, color = Color.White, fontSize = 24.sp, fontWeight = FontWeight.Bold) }
        Spacer(Modifier.width(14.dp))
        Column {
            Text(if (tab.isHome) "Halaman Ceet" else when (tab.risk) { Risk.BERBAHAYA -> "Situs ini berbahaya"; Risk.WASPADA -> "Hati-hati dengan situs ini"; else -> if (secure) "Perlindungan aktif" else "Koneksi tidak terenkripsi" }, color = c.fg, fontWeight = FontWeight.Bold, fontSize = 16.sp)
            Text(if (tab.isHome) "Tidak memuat pelacak." else tab.host + " · " + tab.blocked + " pelacak diblokir", color = c.muted, fontSize = 13.sp)
        }
    }
    if (tab.riskReasons.isNotEmpty() && tab.risk != Risk.AMAN) {
        Column(Modifier.padding(20.dp, 12.dp).fillMaxWidth().clip(RoundedCornerShape(12.dp)).background(c.bad.copy(alpha = .12f)).padding(12.dp)) {
            Text("Alasan peringatan", color = c.bad, fontWeight = FontWeight.Bold, fontSize = 13.sp)
            tab.riskReasons.forEach { Text("• $it", color = c.bad, fontSize = 13.sp) }
        }
    }
    if (!tab.isHome) {
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

@OptIn(androidx.compose.foundation.layout.ExperimentalLayoutApi::class)
@Composable
private fun SettingsSheet(state: BrowserState) {
    val c = LocalCeet.current
    val s = state.settings
    LazyColumn(Modifier.padding(bottom = 24.dp)) {
        item { SheetTitle("Pengaturan") }
        item {
            Text("Tema", color = c.muted, fontSize = 12.sp, fontWeight = FontWeight.SemiBold, modifier = Modifier.padding(horizontal = 20.dp))
            Row(Modifier.padding(20.dp, 8.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                listOf("system" to "Otomatis", "light" to "Terang", "dark" to "Gelap").forEach { (k, l) -> Chip(l, s.theme == k) { state.updateSettings(s.copy(theme = k)) } }
            }
            Text("Mesin pencari", color = c.muted, fontSize = 12.sp, fontWeight = FontWeight.SemiBold, modifier = Modifier.padding(horizontal = 20.dp, vertical = 4.dp))
            androidx.compose.foundation.layout.FlowRow(Modifier.padding(20.dp, 4.dp), horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                SEARCH_ENGINES.forEach { (k, v) -> Chip(v.first, s.engine == k) { state.updateSettings(s.copy(engine = k)) } }
            }
        }
        item { ToggleRow("Blokir pelacak dan iklan", "Permintaan ke domain pelacak yang dikenal dibatalkan.", s.blockTrackers) { state.updateSettings(s.copy(blockTrackers = it)) } }
        item { ToggleRow("Utamakan HTTPS", "http:// dinaikkan ke https://, kecuali alamat lokal.", s.upgradeHttps) { state.updateSettings(s.copy(upgradeHttps = it)) } }
        item { ToggleRow("Buang parameter pelacak", "Contoh: utm_source, fbclid, gclid.", s.stripParams) { state.updateSettings(s.copy(stripParams = it)) } }
        item { ToggleRow("Kirim sinyal Global Privacy Control", "Situs boleh mengabaikannya.", s.sendGpc) { state.updateSettings(s.copy(sendGpc = it)) } }
        item { ToggleRow("Periksa situs penipuan", "Dinilai di perangkatmu. Alamat tidak dikirim ke server.", s.phishingCheck) { state.updateSettings(s.copy(phishingCheck = it)) } }
        item { ToggleRow("Simpan riwayat", "Tab privat tidak pernah disimpan.", s.saveHistory) { state.updateSettings(s.copy(saveHistory = it)) } }
        item { Text("Ceet 0.1.0 · ${state.blocker.size} domain pelacak", color = c.muted, fontSize = 12.sp, modifier = Modifier.padding(20.dp)) }
    }
}

@Composable
private fun Chip(label: String, on: Boolean, onClick: () -> Unit) {
    val c = LocalCeet.current
    Box(
        Modifier.clip(RoundedCornerShape(18.dp)).background(if (on) c.accentSoft else c.surface2).border(1.dp, if (on) c.accent else Color.Transparent, RoundedCornerShape(18.dp)).clickable(onClick = onClick).padding(horizontal = 14.dp, vertical = 8.dp),
    ) { Text(label, color = if (on) c.accent else c.muted, fontWeight = FontWeight.Medium, fontSize = 13.sp) }
}

@Composable
private fun HistorySheet(state: BrowserState, close: () -> Unit) {
    val c = LocalCeet.current
    val list = remember(state.historyVersion) { state.store.history.take(200) }
    SheetTitle("Riwayat", "Tersimpan hanya di perangkat ini.")
    if (list.isEmpty()) Text("Belum ada riwayat.", color = c.muted, modifier = Modifier.padding(20.dp))
    else {
        Box(Modifier.fillMaxWidth().clickable { state.clearHistory() }.padding(horizontal = 20.dp, vertical = 8.dp)) { Text("Hapus semua riwayat", color = c.bad, fontWeight = FontWeight.SemiBold) }
        LazyColumn(Modifier.padding(bottom = 24.dp)) {
            items(list, key = { it.id }) { e ->
                Row(Modifier.fillMaxWidth().clickable { state.navigate(state.active, e.url); close() }.padding(horizontal = 20.dp, vertical = 10.dp), verticalAlignment = Alignment.CenterVertically) {
                    Column(Modifier.weight(1f)) {
                        Text(e.title, color = c.fg, maxLines = 1, overflow = TextOverflow.Ellipsis, fontWeight = FontWeight.Medium)
                        Text(e.url.removePrefix("https://").removePrefix("http://").removePrefix("www."), color = c.muted, fontSize = 12.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
                    }
                    IconBtn("close", "Hapus", c.muted) { state.removeHistory(e.id) }
                }
            }
        }
    }
}

@Composable
private fun BookmarksSheet(state: BrowserState, close: () -> Unit) {
    val c = LocalCeet.current
    val list = remember(state.bookmarksVersion) { state.store.bookmarks }
    SheetTitle("Penanda")
    if (list.isEmpty()) Text("Belum ada penanda. Buka sebuah situs lalu ketuk bintang di kolom alamat.", color = c.muted, modifier = Modifier.padding(20.dp))
    LazyColumn(Modifier.padding(bottom = 24.dp)) {
        items(list, key = { it.id }) { b ->
            Row(Modifier.fillMaxWidth().clickable { state.navigate(state.active, b.url); close() }.padding(horizontal = 20.dp, vertical = 10.dp), verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    Text(b.title, color = c.fg, maxLines = 1, overflow = TextOverflow.Ellipsis, fontWeight = FontWeight.Medium)
                    Text(b.url.removePrefix("https://").removePrefix("http://").removePrefix("www."), color = c.muted, fontSize = 12.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
                }
            }
        }
    }
}

@Composable
private fun PrivacySheet(state: BrowserState) {
    val c = LocalCeet.current
    val st = state.stats
    LazyColumn(Modifier.padding(bottom = 24.dp)) {
        item { SheetTitle("Dasbor privasi", "Dihitung di perangkatmu. Tidak ada yang dikirim ke mana pun.") }
        item {
            listOf("Permintaan pelacak diblokir" to st.trackersBlocked, "Parameter pelacak dibuang" to st.paramsStripped, "Tautan dinaikkan ke HTTPS" to st.httpsUpgraded, "Situs penipuan dihentikan" to st.phishingStopped).forEach { (l, n) ->
                Row(Modifier.fillMaxWidth().padding(horizontal = 20.dp, vertical = 8.dp), verticalAlignment = Alignment.CenterVertically) {
                    Text("%,d".format(n), color = c.accent, fontSize = 26.sp, fontWeight = FontWeight.Bold, modifier = Modifier.width(100.dp))
                    Text(l, color = c.muted)
                }
            }
            Text(
                "Yang Ceet lakukan: membatalkan permintaan ke domain pelacak yang dikenal, membuang parameter pelacak, memaksa HTTPS bila memungkinkan, mengirim sinyal Global Privacy Control, menolak izin kamera, mikrofon, dan lokasi, dan menilai situs penipuan di perangkatmu.\n\n" +
                    "Yang Ceet tidak lakukan: menyembunyikan alamat IP-mu, membuatmu anonim, atau menjamin perlindungan penuh. Daftar pelacak hanya mencakup domain yang dikenal. Pemblokiran berlaku untuk permintaan sub-sumber di dalam halaman, bukan untuk pengalihan di server.",
                color = c.muted, fontSize = 13.sp, modifier = Modifier.padding(20.dp),
            )
        }
    }
}
