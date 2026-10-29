package id.ceet.browser.core

/** Port Kotlin dari engine/src/phishing.rs. Penilaian berjalan lokal, alamat tidak dikirim ke mana pun. */
enum class Risk(val label: String) { AMAN("aman"), WASPADA("waspada"), BERBAHAYA("berbahaya") }

data class Assessment(val risk: Risk, val score: Int, val reasons: List<String>)

object Phishing {
    private val BRANDS = listOf(
        "google", "facebook", "instagram", "whatsapp", "tokopedia", "shopee", "bukalapak", "gojek", "grab",
        "dana", "ovo", "gopay", "linkaja", "bca", "bni", "bri", "mandiri", "cimb", "permata", "jenius",
        "paypal", "apple", "microsoft", "amazon", "netflix", "tiktok", "telkomsel", "indosat", "traveloka",
        "github", "twitter", "linkedin", "youtube", "spotify", "binance", "coinbase",
    )
    private val OFFICIAL = setOf(
        "google.com", "google.co.id", "facebook.com", "instagram.com", "whatsapp.com", "tokopedia.com",
        "shopee.co.id", "shopee.com", "bukalapak.com", "gojek.com", "grab.com", "dana.id", "ovo.id",
        "linkaja.id", "bca.co.id", "bni.co.id", "bri.co.id", "bankmandiri.co.id", "mandiri.co.id", "cimbniaga.co.id",
        "permatabank.com", "jenius.com", "paypal.com", "apple.com", "microsoft.com", "amazon.com",
        "netflix.com", "tiktok.com", "telkomsel.com", "indosat.com", "traveloka.com", "github.com",
        "twitter.com", "x.com", "linkedin.com", "youtube.com", "spotify.com", "binance.com", "coinbase.com",
    )
    private val RISKY_TLDS = setOf("zip", "mov", "tk", "ml", "ga", "cf", "gq", "top", "xyz", "click", "country", "kim", "loan", "work", "rest")
    private val LURE_WORDS = listOf(
        "login", "signin", "verify", "verifikasi", "secure", "account", "akun", "update", "konfirmasi",
        "banking", "wallet", "bonus", "hadiah", "undian", "klaim", "suspend", "blokir", "password", "otp",
    )
    private val SHORTENERS = setOf("bit.ly", "tinyurl.com", "t.co", "goo.gl", "is.gd", "cutt.ly", "rebrand.ly", "s.id", "shorturl.at")

    fun levenshtein(a: String, b: String): Int {
        var prev = IntArray(b.length + 1) { it }
        for (i in 1..a.length) {
            val cur = IntArray(b.length + 1) { i }
            for (j in 1..b.length) {
                val cost = if (a[i - 1] == b[j - 1]) 0 else 1
                cur[j] = minOf(prev[j] + 1, cur[j - 1] + 1, prev[j - 1] + cost)
            }
            prev = cur
        }
        return prev[b.length]
    }

    private fun deleet(s: String): String {
        val t = s.replace("rn", "m").replace("vv", "w")
        val sb = StringBuilder()
        for (c in t) {
            sb.append(
                when (c) {
                    '0' -> 'o'
                    '1', '!', '|' -> 'l'
                    '3' -> 'e'
                    '4' -> 'a'
                    '5', '$' -> 's'
                    '7' -> 't'
                    else -> c
                },
            )
        }
        return sb.toString()
    }

    fun assess(url: String): Assessment {
        var score = 0
        val reasons = ArrayList<String>()
        fun add(pts: Int, why: String) { score += pts; reasons.add(why) }

        val u = UrlTools.parse(url) ?: return Assessment(Risk.AMAN, 0, reasons)
        val reg = UrlTools.registrableDomain(u.host)
        val official = reg in OFFICIAL

        if (u.scheme == "http" && !u.isLocalHost()) add(10, "Koneksi tidak terenkripsi (http)")
        if (u.userinfo) add(35, "Alamat memuat tanda @ yang menyembunyikan tujuan asli")
        if (u.isIpHost() && !u.isLocalHost()) add(30, "Memakai alamat IP, bukan nama situs")
        if (u.host.contains("xn--")) add(35, "Nama situs memakai karakter menyerupai (punycode)")
        else if (u.host.any { it.code > 127 }) add(35, "Nama situs memuat karakter non-Latin yang bisa menyerupai huruf biasa")
        if (u.host.split(".").size >= 5) add(15, "Terlalu banyak subdomain")
        if (u.host.length > 45) add(10, "Nama situs sangat panjang")
        if (u.host.count { it == '-' } >= 3) add(10, "Banyak tanda hubung di nama situs")
        if (u.host.substringAfterLast('.') in RISKY_TLDS) add(12, "Akhiran domain sering dipakai penipuan")
        if (u.host in SHORTENERS) add(8, "Tautan pendek menyembunyikan tujuan asli")

        if (!official) {
            val hostFlat = deleet(u.host.replace("-", ""))
            val first = reg.split(".").first()
            val firstD = deleet(first)
            val regLabels = reg.split(".").size
            val subLabels = u.host.split(".").reversed().drop(regLabels)
            for (b in BRANDS) {
                if (subLabels.any { it.contains(b) }) {
                    add(40, "Memakai nama \"$b\" di subdomain padahal domain aslinya $reg"); break
                }
                if (firstD == b && first != b) {
                    add(60, "Menyamar sebagai \"$b\" dengan huruf pengganti"); break
                }
                if (firstD.length >= 5 && b.length >= 5 && levenshtein(firstD, b) == 1) {
                    add(40, "Ejaan hampir sama dengan \"$b\""); break
                }
                if (firstD.contains(b) && firstD != b) {
                    add(25, "Nama situs menempelkan merek \"$b\""); break
                }
                if (hostFlat.contains(b) && !firstD.contains(b)) {
                    add(20, "Nama \"$b\" muncul di bagian lain alamat"); break
                }
            }
        }

        val lower = (u.host + u.path).lowercase()
        val lures = LURE_WORDS.count { lower.contains(it) }
        if (!official) {
            when {
                lures == 0 -> {}
                lures == 1 -> add(8, "Kata pancingan di alamat (login, verifikasi, dan sejenisnya)")
                else -> add(18, "Beberapa kata pancingan di alamat (login, verifikasi, dan sejenisnya)")
            }
        }

        val risk: Risk
        val finalScore: Int
        if (official && score < 40) { risk = Risk.AMAN; finalScore = 0 }
        else if (score >= 60) { risk = Risk.BERBAHAYA; finalScore = minOf(score, 100) }
        else if (score >= 25) { risk = Risk.WASPADA; finalScore = score }
        else { risk = Risk.AMAN; finalScore = score }
        if (official && risk == Risk.AMAN) reasons.clear()
        return Assessment(risk, finalScore, reasons)
    }
}
