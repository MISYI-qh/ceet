package id.ceet.browser.data

import android.content.Context
import org.json.JSONArray
import org.json.JSONObject

data class Bookmark(val id: String, val url: String, val title: String, val added: Long)
data class HistoryEntry(val id: String, val url: String, val title: String, val visits: Int, val last: Long)

data class Stats(
    val trackersBlocked: Long = 0,
    val paramsStripped: Long = 0,
    val httpsUpgraded: Long = 0,
    val phishingStopped: Long = 0,
)

data class Settings(
    val theme: String = "system",
    val engine: String = "ddg",
    val blockTrackers: Boolean = true,
    val upgradeHttps: Boolean = true,
    val stripParams: Boolean = true,
    val sendGpc: Boolean = true,
    val phishingCheck: Boolean = true,
    val saveHistory: Boolean = true,
)

val SEARCH_ENGINES = linkedMapOf(
    "ddg" to ("DuckDuckGo" to "https://duckduckgo.com/?q={q}"),
    "brave" to ("Brave Search" to "https://search.brave.com/search?q={q}"),
    "startpage" to ("Startpage" to "https://www.startpage.com/do/search?q={q}"),
    "ecosia" to ("Ecosia" to "https://www.ecosia.org/search?q={q}"),
    "google" to ("Google" to "https://www.google.com/search?q={q}"),
    "bing" to ("Bing" to "https://www.bing.com/search?q={q}"),
)

/** Penyimpanan lokal di SharedPreferences. Semua data tetap di perangkat. */
class Store(context: Context) {
    private val prefs = context.getSharedPreferences("ceet", Context.MODE_PRIVATE)

    var settings: Settings = readSettings()
        private set
    var bookmarks: List<Bookmark> = readBookmarks()
        private set
    var history: List<HistoryEntry> = readHistory()
        private set
    var stats: Stats = readStats()
        private set

    fun searchTemplate(): String = (SEARCH_ENGINES[settings.engine] ?: SEARCH_ENGINES.getValue("ddg")).second
    fun engineName(): String = (SEARCH_ENGINES[settings.engine] ?: SEARCH_ENGINES.getValue("ddg")).first

    fun update(s: Settings) {
        settings = s
        val o = JSONObject()
            .put("theme", s.theme).put("engine", s.engine).put("blockTrackers", s.blockTrackers)
            .put("upgradeHttps", s.upgradeHttps).put("stripParams", s.stripParams).put("sendGpc", s.sendGpc)
            .put("phishingCheck", s.phishingCheck).put("saveHistory", s.saveHistory)
        prefs.edit().putString("settings", o.toString()).apply()
    }

    private fun readSettings(): Settings = try {
        val o = JSONObject(prefs.getString("settings", "{}") ?: "{}")
        val d = Settings()
        Settings(
            theme = o.optString("theme", d.theme).takeIf { it in listOf("system", "light", "dark") } ?: d.theme,
            engine = o.optString("engine", d.engine).takeIf { SEARCH_ENGINES.containsKey(it) } ?: d.engine,
            blockTrackers = o.optBoolean("blockTrackers", d.blockTrackers),
            upgradeHttps = o.optBoolean("upgradeHttps", d.upgradeHttps),
            stripParams = o.optBoolean("stripParams", d.stripParams),
            sendGpc = o.optBoolean("sendGpc", d.sendGpc),
            phishingCheck = o.optBoolean("phishingCheck", d.phishingCheck),
            saveHistory = o.optBoolean("saveHistory", d.saveHistory),
        )
    } catch (e: Exception) { Settings() }

    // ---- penanda ----
    fun isBookmarked(url: String) = bookmarks.any { it.url == url }

    fun toggleBookmark(url: String, title: String): Boolean {
        val had = isBookmarked(url)
        bookmarks = if (had) bookmarks.filter { it.url != url }
        else listOf(Bookmark("b" + System.currentTimeMillis().toString(36), url, title.ifBlank { url }, System.currentTimeMillis())) + bookmarks
        val a = JSONArray()
        bookmarks.forEach { a.put(JSONObject().put("id", it.id).put("url", it.url).put("title", it.title).put("added", it.added)) }
        prefs.edit().putString("bookmarks", a.toString()).apply()
        return !had
    }

    private fun readBookmarks(): List<Bookmark> = try {
        val a = JSONArray(prefs.getString("bookmarks", "[]"))
        (0 until a.length()).map { val o = a.getJSONObject(it); Bookmark(o.getString("id"), o.getString("url"), o.optString("title"), o.optLong("added")) }
    } catch (e: Exception) { emptyList() }

    // ---- riwayat ----
    fun addHistory(url: String, title: String) {
        val now = System.currentTimeMillis()
        val old = history.firstOrNull { it.url == url }
        val entry = if (old != null) old.copy(visits = old.visits + 1, last = now, title = title.ifBlank { old.title })
        else HistoryEntry("h" + now.toString(36), url, title.ifBlank { url }, 1, now)
        history = (listOf(entry) + history.filter { it.url != url }).take(3000)
        saveHistory()
    }

    fun updateHistoryTitle(url: String, title: String) {
        if (title.isBlank()) return
        val i = history.indexOfFirst { it.url == url }
        if (i >= 0) { history = history.toMutableList().also { it[i] = it[i].copy(title = title) }; saveHistory() }
    }

    fun removeHistory(id: String) { history = history.filter { it.id != id }; saveHistory() }
    fun clearHistory() { history = emptyList(); saveHistory() }

    private fun saveHistory() {
        val a = JSONArray()
        history.forEach { a.put(JSONObject().put("id", it.id).put("url", it.url).put("title", it.title).put("visits", it.visits).put("last", it.last)) }
        prefs.edit().putString("history", a.toString()).apply()
    }

    private fun readHistory(): List<HistoryEntry> = try {
        val a = JSONArray(prefs.getString("history", "[]"))
        (0 until a.length()).map { val o = a.getJSONObject(it); HistoryEntry(o.getString("id"), o.getString("url"), o.optString("title"), o.optInt("visits", 1), o.optLong("last")) }
    } catch (e: Exception) { emptyList() }

    /** Pencarian riwayat dan penanda secara lokal: kecocokan kata di judul dan alamat, diurutkan dengan frecency. */
    fun suggest(query: String, limit: Int = 5): List<HistoryEntry> {
        val words = query.lowercase().split(Regex("\\s+")).filter { it.isNotEmpty() }
        if (words.isEmpty()) return emptyList()
        val now = System.currentTimeMillis()
        return history.mapNotNull { e ->
            val hay = (e.title + " " + e.url).lowercase()
            if (!words.all { hay.contains(it) }) return@mapNotNull null
            val ageDays = (now - e.last) / 86_400_000.0
            val frecency = (1 + Math.log1p(e.visits.toDouble())) * Math.pow(0.5, ageDays / 14.0)
            val bare = e.url.removePrefix("https://").removePrefix("http://").removePrefix("www.")
            val bonus = (if (bare.startsWith(words[0])) 3.0 else 0.0) + (if (e.title.lowercase().startsWith(words[0])) 2.0 else 0.0)
            e to (frecency + bonus)
        }.sortedByDescending { it.second }.take(limit).map { it.first }
    }

    // ---- statistik ----
    fun bump(trackers: Int = 0, params: Int = 0, https: Int = 0, phishing: Int = 0) {
        stats = stats.copy(
            trackersBlocked = stats.trackersBlocked + trackers, paramsStripped = stats.paramsStripped + params,
            httpsUpgraded = stats.httpsUpgraded + https, phishingStopped = stats.phishingStopped + phishing,
        )
        prefs.edit().putString("stats", JSONObject().put("t", stats.trackersBlocked).put("p", stats.paramsStripped).put("h", stats.httpsUpgraded).put("f", stats.phishingStopped).toString()).apply()
    }

    private fun readStats(): Stats = try {
        val o = JSONObject(prefs.getString("stats", "{}") ?: "{}")
        Stats(o.optLong("t"), o.optLong("p"), o.optLong("h"), o.optLong("f"))
    } catch (e: Exception) { Stats() }

    // ---- sesi ----
    fun saveSession(urls: List<String>, active: Int) {
        prefs.edit().putString("session", JSONObject().put("urls", JSONArray(urls)).put("active", active).toString()).apply()
    }

    fun loadSession(): Pair<List<String>, Int> = try {
        val o = JSONObject(prefs.getString("session", "{}") ?: "{}")
        val a = o.optJSONArray("urls") ?: JSONArray()
        (0 until a.length()).map { a.getString(it) } to o.optInt("active", 0)
    } catch (e: Exception) { emptyList<String>() to 0 }
}
