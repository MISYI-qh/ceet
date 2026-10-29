package id.ceet.browser.core

import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL
import java.util.concurrent.atomic.AtomicInteger
import kotlin.concurrent.thread

/** Satu hasil pencarian Tosh. */
data class ToshHit(val title: String, val url: String, val snippet: String, val source: String)

/** Jawaban singkat di bagian atas halaman. */
data class ToshAnswer(val heading: String, val text: String, val url: String, val source: String)

/** Hasil dari internet. `failed` berarti tidak satu pun sumber menjawab (tidak ada koneksi, misalnya). */
data class ToshRemote(val answer: ToshAnswer?, val articles: List<ToshHit>, val failed: Boolean)

/**
 * Tosh: mesin pencari bawaan Ceet.
 *
 * Yang Tosh lakukan: menggabungkan (1) riwayat dan penanda di perangkatmu, (2) jawaban singkat dari DuckDuckGo
 * Instant Answer, dan (3) artikel Wikipedia berbahasa Indonesia dan Inggris, lalu menampilkannya dalam satu halaman
 * tanpa iklan dan tanpa pelacak. Tosh tidak menyimpan pencarian apa pun.
 *
 * Yang Tosh belum punya: indeks web sendiri. Untuk hasil web umum, halaman ini menyediakan tombol lanjut ke mesin
 * pencari lain. Sumber web berbayar atau mandiri (misalnya SearXNG milik sendiri) bisa ditambahkan di [ToshClient].
 */
object Tosh {
    const val BASE = "ceet://tosh"

    fun url(q: String): String = if (q.isBlank()) BASE else "$BASE?q=" + UrlTools.percentEncode(q.trim())
    fun isTosh(url: String): Boolean = url == BASE || url.startsWith("$BASE?")

    fun queryOf(url: String): String {
        val i = url.indexOf("?q=")
        if (i < 0) return ""
        val raw = url.substring(i + 3).substringBefore('&').substringBefore('#')
        return try { java.net.URLDecoder.decode(raw, "UTF-8").trim() } catch (e: Exception) { raw }
    }

    fun esc(s: String): String = s.replace("&", "&amp;").replace("<", "&lt;").replace(">", "&gt;").replace("\"", "&quot;").replace("'", "&#39;")

    /** Buang tag HTML dan ubah entitas umum. Hasilnya teks biasa. */
    fun stripTags(html: String): String =
        html.replace(Regex("<[^>]*>"), "")
            .replace("&quot;", "\"").replace("&#039;", "'").replace("&#39;", "'")
            .replace("&lt;", "<").replace("&gt;", ">").replace("&nbsp;", " ").replace("&amp;", "&")
            .replace(Regex("\\s+"), " ").trim()

    fun parseWikipedia(json: String, lang: String): List<ToshHit> {
        val pages = try { JSONObject(json).optJSONArray("pages") } catch (e: Exception) { null } ?: return emptyList()
        val out = ArrayList<ToshHit>()
        for (i in 0 until pages.length()) {
            val o = pages.optJSONObject(i) ?: continue
            val title = o.optString("title")
            if (title.isBlank()) continue
            val key = o.optString("key").ifBlank { title.replace(' ', '_') }
            val snippet = stripTags(o.optString("excerpt")).ifBlank { stripTags(o.optString("description")) }
            out.add(ToshHit(title, "https://$lang.wikipedia.org/wiki/" + UrlTools.percentEncode(key), snippet, if (lang == "id") "Wikipedia Indonesia" else "Wikipedia ($lang)"))
        }
        return out
    }

    fun parseDuckDuckGo(json: String): ToshAnswer? {
        val o = try { JSONObject(json) } catch (e: Exception) { return null }
        val heading = o.optString("Heading")
        val abstract = o.optString("AbstractText")
        if (abstract.isNotBlank()) {
            return ToshAnswer(heading.ifBlank { "Ringkasan" }, abstract, o.optString("AbstractURL"), o.optString("AbstractSource").ifBlank { "DuckDuckGo" })
        }
        val answer = o.optString("Answer")
        if (answer.isNotBlank()) return ToshAnswer(heading.ifBlank { "Jawaban" }, stripTags(answer), "", "DuckDuckGo")
        val def = o.optString("Definition")
        if (def.isNotBlank()) return ToshAnswer(heading.ifBlank { "Definisi" }, def, o.optString("DefinitionURL"), o.optString("DefinitionSource").ifBlank { "DuckDuckGo" })
        return null
    }

    // ---------------- HTML ----------------
    private const val LOGO = """<svg viewBox="0 0 1024 1024" width="34" height="34" aria-hidden="true"><defs><linearGradient id="g" gradientUnits="userSpaceOnUse" x1="200" y1="120" x2="840" y2="840"><stop offset="0" stop-color="#14B8A0"/><stop offset="1" stop-color="#0A7A78"/></linearGradient></defs><circle cx="440" cy="440" r="250" fill="none" stroke="url(#g)" stroke-width="124"/><path d="M620 620L832 832" stroke="url(#g)" stroke-width="124" stroke-linecap="round" fill="none"/></svg>"""

    private const val CSS = """
:root{color-scheme:light dark;--bg:#FBFAF8;--card:#fff;--soft:#F3F5F9;--fg:#0B1A30;--muted:#5B6B82;--line:#E3E8F0;--a:#0A8F86;--link:#1257C9}
@media(prefers-color-scheme:dark){:root{--bg:#0A1222;--card:#111C33;--soft:#0D172B;--fg:#EAF0FB;--muted:#93A3BF;--line:#22304D;--a:#2DD4BF;--link:#7DB4FF}}
*{box-sizing:border-box}body{margin:0;background:var(--bg);color:var(--fg);font:16px/1.5 system-ui,-apple-system,sans-serif}
main{max-width:720px;margin:0 auto;padding:18px 16px 40px}
header{display:flex;align-items:center;gap:10px;margin-bottom:14px}
header b{font-size:24px;font-weight:650;letter-spacing:-.02em}
form{display:flex;gap:8px;margin-bottom:6px}
input[type=search]{flex:1;min-width:0;font:inherit;color:var(--fg);background:var(--card);border:1.5px solid var(--line);border-radius:24px;padding:12px 18px;outline:none}
input[type=search]:focus{border-color:var(--a)}
button{font:inherit;font-weight:600;color:#fff;background:var(--a);border:0;border-radius:24px;padding:0 20px}
h2{font-size:12px;letter-spacing:.08em;text-transform:uppercase;color:var(--muted);margin:26px 0 8px}
.card{background:var(--card);border:1px solid var(--line);border-radius:16px;padding:14px 16px;margin-bottom:10px}
.hit a{color:var(--link);font-weight:600;text-decoration:none;font-size:17px;overflow-wrap:anywhere}
.hit .u{color:var(--muted);font-size:12.5px;overflow-wrap:anywhere}
.hit p{margin:4px 0 0;color:var(--fg);font-size:14.5px}
.ans{border-color:var(--a)}.ans h3{margin:0 0 6px;font-size:19px}.ans p{margin:0 0 8px}
.src{color:var(--muted);font-size:12.5px}.src a{color:var(--link)}
.muted{color:var(--muted);font-size:14px}
.row{display:flex;flex-wrap:wrap;gap:8px}.row a{padding:9px 14px;border-radius:20px;background:var(--soft);border:1px solid var(--line);color:var(--fg);text-decoration:none;font-size:14px;font-weight:550}
.note{margin-top:30px;color:var(--muted);font-size:12.5px}
.sk{height:14px;border-radius:7px;background:linear-gradient(90deg,var(--soft),var(--line),var(--soft));background-size:200% 100%;animation:s 1.2s linear infinite;margin:10px 0}
@keyframes s{to{background-position:-200% 0}}
.start{text-align:center;padding-top:12vh}.start svg{width:84px;height:84px}.start b{display:block;font-size:42px;margin:6px 0 20px;letter-spacing:-.03em}
"""

    private fun hitHtml(h: ToshHit): String =
        """<div class="card hit"><a href="${esc(h.url)}">${esc(h.title)}</a><div class="u">${esc(h.source)} · ${esc(h.url.removePrefix("https://").removePrefix("http://").removePrefix("www."))}</div>${if (h.snippet.isNotBlank()) "<p>${esc(h.snippet)}</p>" else ""}</div>"""

    /** Bagian dari internet. Dipakai langsung saat halaman dibuat, atau disuntikkan setelah data tiba. */
    fun remoteHtml(r: ToshRemote): String {
        val sb = StringBuilder()
        r.answer?.let { a ->
            sb.append("""<div class="card ans"><h3>${esc(a.heading)}</h3><p>${esc(a.text)}</p><div class="src">Sumber: """)
            sb.append(if (a.url.startsWith("http")) """<a href="${esc(a.url)}">${esc(a.source)}</a>""" else esc(a.source))
            sb.append("</div></div>")
        }
        if (r.articles.isNotEmpty()) {
            sb.append("<h2>Artikel</h2>")
            r.articles.forEach { sb.append(hitHtml(it)) }
        }
        if (r.failed) sb.append("""<div class="card muted">Tidak bisa menjangkau sumber jawaban. Periksa koneksi internetmu, atau lanjutkan di mesin pencari lain di bawah.</div>""")
        else if (r.answer == null && r.articles.isEmpty()) sb.append("""<div class="card muted">Belum ada jawaban singkat atau artikel untuk kata ini. Coba kata lain, atau lanjutkan di web di bawah.</div>""")
        return sb.toString()
    }

    /**
     * Halaman Tosh lengkap. Bila [online] benar, bagian internet diisi kerangka dulu lalu diganti lewat
     * `window.__tosh(html)` setelah data tiba, supaya tidak menambah entri riwayat WebView.
     */
    fun page(query: String, local: List<ToshHit>, engines: List<Pair<String, String>>, online: Boolean, remote: ToshRemote? = null): String {
        val q = query.trim()
        val sb = StringBuilder()
        sb.append("""<!doctype html><html lang="id"><head><meta charset="utf-8"><meta name="viewport" content="width=device-width,initial-scale=1"><title>${esc(if (q.isEmpty()) "Tosh" else "$q – Tosh")}</title><style>$CSS</style></head><body><main>""")
        val form = """<form action="ceet://tosh" method="get"><input type="search" name="q" value="${esc(q)}" placeholder="Cari dengan Tosh" autocomplete="off" autocapitalize="none" enterkeyhint="search"${if (q.isEmpty()) " autofocus" else ""}><button type="submit">Cari</button></form>"""
        if (q.isEmpty()) {
            sb.append("""<div class="start">$LOGO<b>tosh</b></div>$form<p class="muted" style="text-align:center;margin-top:18px">Pencarian di perangkat dan di web, tanpa iklan dan tanpa pelacak.</p>""")
        } else {
            sb.append("""<header>$LOGO<b>tosh</b></header>$form""")
            if (local.isNotEmpty()) {
                sb.append("<h2>Di perangkatmu</h2>")
                local.forEach { sb.append(hitHtml(it)) }
            }
            if (online) {
                sb.append("""<div id="remote">""")
                if (remote != null) sb.append(remoteHtml(remote)) else sb.append("""<h2>Mencari</h2><div class="card"><div class="sk" style="width:60%"></div><div class="sk"></div><div class="sk" style="width:80%"></div></div>""")
                sb.append("</div>")
            } else {
                sb.append("""<div class="card muted" style="margin-top:14px">Pencarian internet Tosh sedang dimatikan di Pengaturan. Hanya riwayat dan penandamu yang dicari.</div>""")
            }
            val enc = UrlTools.percentEncode(q)
            sb.append("<h2>Lanjutkan di web</h2><div class=\"row\">")
            engines.forEach { (name, tpl) -> sb.append("""<a href="${esc(tpl.replace("{q}", enc))}">${esc(name)}</a>""") }
            sb.append("</div>")
            sb.append("""<p class="note">Tosh tidak menyimpan pencarianmu. Jawaban singkat dan artikel diambil dari DuckDuckGo dan Wikipedia lewat Ceet tanpa cookie. Tosh belum punya indeks web sendiri, jadi hasil web umum ada di mesin pencari di atas.</p>""")
        }
        sb.append("""</main><script>window.__tosh=function(h){var e=document.getElementById('remote');if(e)e.innerHTML=h;};</script></body></html>""")
        return sb.toString()
    }
}

/** Mengambil jawaban dari internet. Tiga permintaan berjalan paralel, masing-masing dengan batas waktu. */
class ToshClient(private val timeoutMs: Int = 6000) {
    private fun get(url: String): String? = try {
        val c = URL(url).openConnection() as HttpURLConnection
        c.connectTimeout = timeoutMs; c.readTimeout = timeoutMs
        c.setRequestProperty("User-Agent", "Ceet/0.1 (peramban privat; Tosh)")
        c.setRequestProperty("Accept", "application/json")
        c.instanceFollowRedirects = true
        if (c.responseCode in 200..299) c.inputStream.bufferedReader().use { it.readText() } else null
    } catch (e: Exception) { null }

    fun fetch(query: String): ToshRemote {
        val enc = UrlTools.percentEncode(query)
        var answer: ToshAnswer? = null
        val wiki = arrayOfNulls<List<ToshHit>>(2)
        val ok = AtomicInteger(0)
        val workers = listOf(
            thread { get("https://api.duckduckgo.com/?q=$enc&format=json&no_html=1&skip_disambig=1&t=ceet")?.let { answer = Tosh.parseDuckDuckGo(it); ok.incrementAndGet() } },
            thread { get("https://id.wikipedia.org/w/rest.php/v1/search/page?q=$enc&limit=4")?.let { wiki[0] = Tosh.parseWikipedia(it, "id"); ok.incrementAndGet() } },
            thread { get("https://en.wikipedia.org/w/rest.php/v1/search/page?q=$enc&limit=3")?.let { wiki[1] = Tosh.parseWikipedia(it, "en"); ok.incrementAndGet() } },
        )
        workers.forEach { it.join(timeoutMs + 1500L) }
        val id = wiki[0].orEmpty()
        val en = wiki[1].orEmpty().filter { e -> id.none { it.title.equals(e.title, ignoreCase = true) } }
        return ToshRemote(answer, id + en, failed = ok.get() == 0)
    }
}
