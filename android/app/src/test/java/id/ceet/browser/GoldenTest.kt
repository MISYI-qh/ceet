package id.ceet.browser

import id.ceet.browser.core.Blocker
import id.ceet.browser.core.Intent
import id.ceet.browser.core.Omnibox
import id.ceet.browser.core.Phishing
import id.ceet.browser.core.UrlTools
import org.json.JSONArray
import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

/** Mencocokkan port Kotlin dengan vektor emas yang dihasilkan mesin Rust (shared/vectors/golden.json). */
class GoldenTest {
    private val golden: JSONObject by lazy {
        val stream = javaClass.classLoader!!.getResourceAsStream("golden.json")
            ?: error("golden.json tidak ditemukan di resource tes")
        JSONObject(stream.bufferedReader().readText())
    }

    private fun each(key: String, f: (JSONObject) -> Unit) {
        val arr: JSONArray = golden.getJSONArray(key)
        for (i in 0 until arr.length()) f(arr.getJSONObject(i))
    }

    @Test fun klasifikasiOmnibox() = each("classify") { v ->
        val input = v.getString("input")
        val e = v.getJSONObject("expect")
        val got = Omnibox.classify(input)
        val (kind, value) = when (got) {
            is Intent.Url -> "url" to got.url
            is Intent.Search -> "search" to got.query
            is Intent.Question -> "question" to got.query
            is Intent.Command -> "command" to got.name
            Intent.Empty -> "empty" to ""
        }
        assertEquals("jenis: $input", e.getString("kind"), kind)
        assertEquals("nilai: $input", e.getString("value"), value)
    }

    @Test fun pembersihUrl() = each("clean_url") { v ->
        val input = v.getString("input")
        val e = v.getJSONObject("expect")
        val got = UrlTools.clean(input)
        assertEquals(input, e.getString("url"), got.url)
        assertEquals(input, e.getBoolean("upgraded"), got.upgraded)
        val removed = e.getJSONArray("removed")
        assertEquals(input, (0 until removed.length()).map { removed.getString(it) }, got.removed)
    }

    @Test fun phishing() = each("phishing") { v ->
        val input = v.getString("input")
        val e = v.getJSONObject("expect")
        val got = Phishing.assess(input)
        assertEquals(input, e.getString("risk"), got.risk.label)
        assertEquals(input, e.getInt("score"), got.score)
    }

    @Test fun pemblokir() {
        val text = File("../../data/blocklist.txt").takeIf { it.exists() }?.readText()
            ?: File("../data/blocklist.txt").readText()
        val b = Blocker(text)
        assertTrue(b.size > 100)
        each("block") { v ->
            val input = v.getJSONObject("input")
            val e = v.getJSONObject("expect")
            val got = b.check(input.getString("host"), input.getString("page"))
            assertEquals(input.toString(), e.getBoolean("block"), got.block)
            if (e.getBoolean("block")) assertEquals(e.getString("rule"), got.rule)
        }
    }
}
