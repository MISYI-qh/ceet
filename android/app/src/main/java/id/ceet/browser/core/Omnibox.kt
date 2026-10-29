package id.ceet.browser.core

/** Port Kotlin dari engine/src/omnibox.rs. */
sealed class Intent {
    data class Url(val url: String) : Intent()
    data class Search(val query: String) : Intent()
    data class Question(val query: String) : Intent()
    data class Command(val name: String, val arg: String) : Intent()
    object Empty : Intent()
}

object Omnibox {
    private val QUESTION_WORDS = setOf(
        "apa", "siapa", "kapan", "dimana", "di mana", "kenapa", "mengapa", "bagaimana", "berapa", "mana", "apakah",
        "what", "who", "when", "where", "why", "how", "which", "is", "are", "can", "does",
    )
    private val KNOWN_TLDS = setOf(
        "com", "id", "net", "org", "io", "co", "dev", "app", "ai", "me", "tv", "xyz", "info", "edu", "gov", "biz",
        "my", "sg", "jp", "uk", "de", "fr", "cn", "in", "us", "au", "ru", "br", "kr", "nl", "es", "it", "ca", "nz",
        "th", "vn", "ph", "za", "tr", "pl", "se", "ch", "tech", "online", "site", "store", "news",
    )
    private val OK_SCHEMES = setOf("http", "https", "file", "ceet", "about")

    fun classify(raw: String): Intent {
        val s = raw.trim()
        if (s.isEmpty()) return Intent.Empty
        if (s.startsWith(">")) {
            val rest = s.substring(1).trim()
            val sp = rest.indexOf(' ')
            val name = if (sp < 0) rest else rest.substring(0, sp)
            val arg = if (sp < 0) "" else rest.substring(sp + 1).trim()
            return Intent.Command(name.lowercase(), arg)
        }
        val lower = s.lowercase()
        if (s.none { it.isWhitespace() }) {
            val u = UrlTools.parse(s)
            if (u != null && u.scheme in OK_SCHEMES) return Intent.Url(s)
            val host = looksLikeHost(s)
            if (host != null) return Intent.Url(host)
        }
        val words = lower.split(Regex("\\s+")).filter { it.isNotEmpty() }
        val first = words.firstOrNull() ?: ""
        return if (s.endsWith("?") || (words.size >= 3 && first in QUESTION_WORDS)) {
            Intent.Question(s.trimEnd('?').trim())
        } else Intent.Search(s)
    }

    private fun looksLikeHost(s: String): String? {
        val cut = s.indexOfFirst { it == '/' || it == '?' || it == '#' }
        val hostport = if (cut >= 0) s.substring(0, cut) else s
        val rest = if (cut >= 0) s.substring(cut) else ""
        var host = hostport
        var portOk = false
        val c = hostport.lastIndexOf(':')
        if (c >= 0) {
            val p = hostport.substring(c + 1)
            if (p.isNotEmpty() && p.all { it in '0'..'9' }) { host = hostport.substring(0, c); portOk = true }
        }
        if (host == "localhost") return "http://$hostport$rest"
        val parts = host.split(".")
        val ip = parts.size == 4 && parts.all { it.isNotEmpty() && it.all { ch -> ch in '0'..'9' } }
        if (ip) return "http://$hostport$rest"
        if (parts.size < 2 || parts.any { it.isEmpty() || it.startsWith("-") || it.endsWith("-") }) return null
        if (!parts.all { p -> p.all { ch -> ch.isLetterOrDigit() || ch == '-' } }) return null
        val tld = parts.last().lowercase()
        if (tld !in KNOWN_TLDS && !portOk) return null
        if (tld.any { it in '0'..'9' }) return null
        return "https://$hostport$rest"
    }

    fun resolve(raw: String, searchTemplate: String): String? = when (val i = classify(raw)) {
        is Intent.Url -> i.url
        is Intent.Search -> searchTemplate.replace("{q}", UrlTools.percentEncode(i.query))
        is Intent.Question -> searchTemplate.replace("{q}", UrlTools.percentEncode(i.query))
        else -> null
    }
}
