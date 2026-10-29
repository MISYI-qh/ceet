package id.ceet.browser.core

/** Pemblokir pelacak. Memakai data/blocklist.txt yang sama dengan mesin Rust dan desktop. */
class Blocker(text: String) {
    private val domains = HashSet<String>()
    private val allow = HashSet<String>()

    init {
        for (raw in text.lineSequence()) {
            val line = raw.trim().lowercase()
            if (line.isEmpty() || line.startsWith("#")) continue
            if (line.startsWith("@@")) allow.add(line.substring(2).trim()) else domains.add(line)
        }
    }

    val size: Int get() = domains.size

    data class Decision(val block: Boolean, val rule: String? = null)

    private fun matchSuffix(set: Set<String>, host: String): String? {
        var h = host
        while (true) {
            if (set.contains(h)) return h
            val i = h.indexOf('.')
            if (i < 0) return null
            h = h.substring(i + 1)
        }
    }

    fun check(requestHost: String, pageHost: String): Decision {
        val rh = requestHost.lowercase()
        val ph = pageHost.lowercase()
        if (ph.isNotEmpty() && UrlTools.registrableDomain(rh) == UrlTools.registrableDomain(ph)) return Decision(false)
        if (matchSuffix(allow, rh) != null) return Decision(false)
        val rule = matchSuffix(domains, rh)
        return if (rule != null) Decision(true, rule) else Decision(false)
    }
}
