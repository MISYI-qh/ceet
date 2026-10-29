package id.ceet.browser.core

/** Port Kotlin dari engine/src/url.rs. Hasilnya dicocokkan dengan vektor emas dari mesin Rust. */
data class ParsedUrl(
    val scheme: String,
    val userinfo: Boolean,
    val host: String,
    val port: Int?,
    val path: String,
    val query: List<Pair<String, String>>,
    val fragment: String,
) {
    fun isIpHost(): Boolean {
        if (host.startsWith("[")) return true
        val p = host.split(".")
        return p.size == 4 && p.all { it.isNotEmpty() && it.length <= 3 && it.all { c -> c in '0'..'9' } }
    }

    fun isLocalHost(): Boolean {
        val h = host
        if (h == "localhost" || h.endsWith(".local") || h.endsWith(".localhost") || h == "[::1]") return true
        if (isIpHost() && !h.startsWith("[")) {
            val p = h.split(".").mapNotNull { it.toIntOrNull() }
            if (p.size == 4) {
                return p[0] == 10 || p[0] == 127 || (p[0] == 192 && p[1] == 168) || (p[0] == 172 && p[1] in 16..31)
            }
        }
        return false
    }

    fun registrableDomain(): String = UrlTools.registrableDomain(host)

    fun build(): String {
        val sb = StringBuilder("$scheme://$host")
        if (port != null) sb.append(':').append(port)
        sb.append(path)
        if (query.isNotEmpty()) {
            sb.append('?')
            sb.append(query.joinToString("&") { (k, v) -> if (v.isEmpty()) k else "$k=$v" })
        }
        if (fragment.isNotEmpty()) sb.append('#').append(fragment)
        return sb.toString()
    }

    /** Buang parameter pelacak. Mengembalikan URL bersih dan nama parameter yang dibuang. */
    fun stripTracking(): Pair<ParsedUrl, List<String>> {
        val removed = ArrayList<String>()
        val kept = ArrayList<Pair<String, String>>()
        for ((k, v) in query) {
            val lk = k.lowercase()
            val bad = UrlTools.TRACKING_PARAMS.contains(lk) || UrlTools.TRACKING_PREFIXES.any { lk.startsWith(it) }
            if (bad) removed.add(k) else kept.add(k to v)
        }
        return copy(query = kept) to removed
    }
}

object UrlTools {
    val TRACKING_PARAMS = setOf(
        "fbclid", "gclid", "gclsrc", "dclid", "msclkid", "yclid", "twclid", "ttclid", "li_fat_id",
        "mc_cid", "mc_eid", "igshid", "igsh", "_hsenc", "_hsmi", "hsctatracking", "mkt_tok",
        "vero_id", "wickedid", "oly_enc_id", "oly_anon_id", "rb_clickid", "s_cid", "ref_src",
        "ref_url", "spm", "scm", "cmpid", "ncid", "sr_share", "si", "feature", "pp",
    )
    val TRACKING_PREFIXES = listOf("utm_", "pk_", "mtm_", "matomo_", "hsa_", "ga_", "itm_", "_ga")

    private val MULTI_SUFFIX = setOf(
        "co.id", "or.id", "ac.id", "go.id", "sch.id", "web.id", "my.id", "biz.id", "net.id", "mil.id", "desa.id",
        "co.uk", "org.uk", "ac.uk", "gov.uk", "com.au", "net.au", "org.au", "co.jp", "co.in", "co.nz", "com.sg",
        "com.my", "com.br", "com.cn", "com.hk", "com.tw", "co.kr", "co.za", "com.mx", "com.tr",
    )

    fun registrableDomain(host: String): String {
        val labels = host.split(".")
        if (labels.size <= 2) return host
        val last2 = labels.takeLast(2).joinToString(".")
        if (MULTI_SUFFIX.contains(last2) && labels.size >= 3) return labels.takeLast(3).joinToString(".")
        return last2
    }

    fun parse(input: String): ParsedUrl? {
        val s = input.trim()
        val sep = s.indexOf("://")
        if (sep < 0) return null
        val scheme = s.substring(0, sep)
        var rest = s.substring(sep + 3)
        if (scheme.isEmpty() || !scheme.all { it.isLetterOrDigit() && it.code < 128 || it == '+' || it == '-' || it == '.' }) return null

        var fragment = ""
        val hash = rest.indexOf('#')
        if (hash >= 0) { fragment = rest.substring(hash + 1); rest = rest.substring(0, hash) }
        var queryS = ""
        val qm = rest.indexOf('?')
        if (qm >= 0) { queryS = rest.substring(qm + 1); rest = rest.substring(0, qm) }
        val slash = rest.indexOf('/')
        val authority = if (slash >= 0) rest.substring(0, slash) else rest
        val path = if (slash >= 0) rest.substring(slash) else "/"

        val at = authority.lastIndexOf('@')
        val userinfo = at >= 0
        val hostport = if (at >= 0) authority.substring(at + 1) else authority

        val host: String
        var port: Int? = null
        if (hostport.startsWith("[")) {
            val end = hostport.indexOf(']')
            if (end < 0) return null
            host = hostport.substring(0, end + 1)
            val after = hostport.substring(end + 1)
            if (after.startsWith(":")) port = after.substring(1).toIntOrNull()
        } else {
            val c = hostport.lastIndexOf(':')
            if (c >= 0) {
                val p = hostport.substring(c + 1)
                if (p.isNotEmpty() && p.all { it in '0'..'9' }) {
                    host = hostport.substring(0, c)
                    port = p.toIntOrNull()
                } else host = hostport
            } else host = hostport
        }
        if (host.isEmpty()) return null

        val query = queryS.split("&").filter { it.isNotEmpty() }.map {
            val eq = it.indexOf('=')
            if (eq >= 0) it.substring(0, eq) to it.substring(eq + 1) else it to ""
        }
        return ParsedUrl(scheme.lowercase(), userinfo, host.lowercase(), port, path, query, fragment)
    }

    data class Cleaned(val url: String, val upgraded: Boolean, val removed: List<String>)

    /** Naikkan http ke https (kecuali alamat lokal atau IP), lalu buang parameter pelacak. */
    fun clean(url: String, upgrade: Boolean = true, strip: Boolean = true): Cleaned {
        var u = parse(url) ?: return Cleaned(url, false, emptyList())
        var upgraded = false
        if (upgrade && u.scheme == "http" && !u.isLocalHost() && !u.isIpHost()) {
            u = u.copy(scheme = "https", port = if (u.port == 80) null else u.port)
            upgraded = true
        }
        var removed: List<String> = emptyList()
        if (strip) {
            val r = u.stripTracking()
            u = r.first
            removed = r.second
        }
        return Cleaned(u.build(), upgraded, removed)
    }

    fun percentEncode(s: String): String {
        val sb = StringBuilder()
        for (b in s.toByteArray(Charsets.UTF_8)) {
            val c = b.toInt() and 0xFF
            val ch = c.toChar()
            if ((ch in 'a'..'z') || (ch in 'A'..'Z') || (ch in '0'..'9') || ch == '-' || ch == '_' || ch == '.' || ch == '~') sb.append(ch)
            else sb.append('%').append("%02X".format(c))
        }
        return sb.toString()
    }
}
