package id.ceet.browser

import id.ceet.browser.core.Tosh
import id.ceet.browser.core.ToshHit
import id.ceet.browser.core.ToshRemote
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class ToshTest {
    @Test fun alamatDanKata() {
        assertEquals("ceet://tosh", Tosh.url("   "))
        assertEquals("ceet://tosh?q=kopi%20gayo", Tosh.url(" kopi gayo "))
        assertEquals("kopi gayo", Tosh.queryOf("ceet://tosh?q=kopi%20gayo"))
        assertEquals("kopi gayo", Tosh.queryOf("ceet://tosh?q=kopi+gayo"))
        assertEquals("", Tosh.queryOf("ceet://tosh"))
        assertEquals("é & ü", Tosh.queryOf(Tosh.url("é & ü")))
        assertTrue(Tosh.isTosh("ceet://tosh"))
        assertTrue(Tosh.isTosh("ceet://tosh?q=a"))
        assertFalse(Tosh.isTosh("ceet://toshiba"))
        assertFalse(Tosh.isTosh("https://tosh.example"))
    }

    @Test fun buangTagDanEntitas() {
        assertEquals("Kopi \"Gayo\" & Toraja", Tosh.stripTags("<span class=\"searchmatch\">Kopi</span> &quot;Gayo&quot; &amp; Toraja"))
    }

    @Test fun wikipedia() {
        val json = """{"pages":[{"id":1,"key":"Kopi_Gayo","title":"Kopi Gayo","excerpt":"<span class=\"searchmatch\">Kopi</span> dari Aceh","description":"jenis kopi"},{"title":""}]}"""
        val hits = Tosh.parseWikipedia(json, "id")
        assertEquals(1, hits.size)
        assertEquals("Kopi Gayo", hits[0].title)
        assertEquals("https://id.wikipedia.org/wiki/Kopi_Gayo", hits[0].url)
        assertEquals("Kopi dari Aceh", hits[0].snippet)
        assertTrue(Tosh.parseWikipedia("bukan json", "id").isEmpty())
        assertTrue(Tosh.parseWikipedia("{}", "en").isEmpty())
    }

    @Test fun duckDuckGo() {
        val a = Tosh.parseDuckDuckGo("""{"Heading":"Kopi","AbstractText":"Kopi adalah minuman.","AbstractURL":"https://id.wikipedia.org/wiki/Kopi","AbstractSource":"Wikipedia"}""")!!
        assertEquals("Kopi", a.heading)
        assertEquals("Wikipedia", a.source)
        assertEquals("4", Tosh.parseDuckDuckGo("""{"Answer":"<b>4</b>","Heading":""}""")!!.text)
        assertNull(Tosh.parseDuckDuckGo("""{"AbstractText":"","Answer":"","Definition":""}"""))
        assertNull(Tosh.parseDuckDuckGo("rusak"))
    }

    @Test fun halamanAmanDariHtmlSisipan() {
        val q = "<script>alert(1)</script>"
        val local = listOf(ToshHit("<img src=x onerror=1>", "https://a.b/?x=\"y\"", "", "Riwayat"))
        val html = Tosh.page(q, local, listOf("Google" to "https://www.google.com/search?q={q}"), online = true)
        assertFalse(html.contains("<script>alert(1)"))
        assertFalse(html.contains("<img src=x"))
        assertTrue(html.contains("action=\"ceet://tosh\""))
        assertTrue(html.contains("https://www.google.com/search?q=%3Cscript%3Ealert%281%29%3C%2Fscript%3E"))
        assertTrue(html.contains("id=\"remote\""))
    }

    @Test fun halamanAwalDanMatiOnline() {
        val start = Tosh.page("", emptyList(), emptyList(), online = true)
        assertTrue(start.contains("tosh"))
        assertFalse(start.contains("id=\"remote\""))
        val off = Tosh.page("kopi", emptyList(), emptyList(), online = false)
        assertFalse(off.contains("id=\"remote\""))
        assertTrue(off.contains("dimatikan"))
    }

    @Test fun hasilInternet() {
        val ok = Tosh.remoteHtml(ToshRemote(null, listOf(ToshHit("A & B", "https://x.y/z", "isi", "Wikipedia")), failed = false))
        assertTrue(ok.contains("A &amp; B"))
        assertTrue(Tosh.remoteHtml(ToshRemote(null, emptyList(), failed = true)).contains("koneksi"))
        assertTrue(Tosh.remoteHtml(ToshRemote(null, emptyList(), failed = false)).contains("Belum ada jawaban"))
    }
}
