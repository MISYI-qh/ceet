package id.ceet.browser.ui

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.graphics.vector.PathParser
import androidx.compose.ui.unit.dp

// DIBUAT OTOMATIS oleh tools/gen_icons.py dari desktop/ui/icons.js. Jangan diedit manual.
object CeetIcons {
    private class Seg(val d: String, val filled: Boolean)

    private val DATA: Map<String, List<Seg>> = mapOf(
        "back" to listOf(Seg("M15 5l-7 7 7 7", false)),
        "forward" to listOf(Seg("M9 5l7 7-7 7", false)),
        "reload" to listOf(Seg("M20 11a8 8 0 1 0-2.3 5.7", false), Seg("M20 4v7h-7", false)),
        "stop" to listOf(Seg("M6 6l12 12M18 6L6 18", false)),
        "home" to listOf(Seg("M4 11l8-7 8 7", false), Seg("M6 10v9h4v-5h4v5h4v-9", false)),
        "star" to listOf(Seg("M12 3.5l2.6 5.4 5.9.8-4.3 4.1 1 5.9L12 16.9l-5.2 2.8 1-5.9L3.5 9.7l5.9-.8z", false)),
        "starFill" to listOf(Seg("M12 3.5l2.6 5.4 5.9.8-4.3 4.1 1 5.9L12 16.9l-5.2 2.8 1-5.9L3.5 9.7l5.9-.8z", true)),
        "shield" to listOf(Seg("M12 3l7 3v5.5c0 4.4-3 7.8-7 9.5-4-1.7-7-5.1-7-9.5V6z", false)),
        "shieldCheck" to listOf(Seg("M12 3l7 3v5.5c0 4.4-3 7.8-7 9.5-4-1.7-7-5.1-7-9.5V6z", false), Seg("M9 12l2.2 2.2L15.5 10", false)),
        "shieldAlert" to listOf(Seg("M12 3l7 3v5.5c0 4.4-3 7.8-7 9.5-4-1.7-7-5.1-7-9.5V6z", false), Seg("M12 8.5v4.2", false), Seg("M12 15.6v.1", false)),
        "lock" to listOf(Seg("M7.5 10.5h9a2.5 2.5 0 0 1 2.5 2.5v4.5a2.5 2.5 0 0 1 -2.5 2.5h-9a2.5 2.5 0 0 1 -2.5 -2.5v-4.5a2.5 2.5 0 0 1 2.5 -2.5z", false), Seg("M8 10.5V8a4 4 0 0 1 8 0v2.5", false)),
        "lockOpen" to listOf(Seg("M7.5 10.5h9a2.5 2.5 0 0 1 2.5 2.5v4.5a2.5 2.5 0 0 1 -2.5 2.5h-9a2.5 2.5 0 0 1 -2.5 -2.5v-4.5a2.5 2.5 0 0 1 2.5 -2.5z", false), Seg("M8 10.5V8a4 4 0 0 1 7.5-1.9", false)),
        "plus" to listOf(Seg("M12 5v14M5 12h14", false)),
        "close" to listOf(Seg("M6.5 6.5l11 11M17.5 6.5l-11 11", false)),
        "dots" to listOf(Seg("M10.6 5.5a1.4 1.4 0 1 0 2.8 0a1.4 1.4 0 1 0 -2.8 0z", true), Seg("M10.6 12a1.4 1.4 0 1 0 2.8 0a1.4 1.4 0 1 0 -2.8 0z", true), Seg("M10.6 18.5a1.4 1.4 0 1 0 2.8 0a1.4 1.4 0 1 0 -2.8 0z", true)),
        "search" to listOf(Seg("M4.5 11a6.5 6.5 0 1 0 13 0a6.5 6.5 0 1 0 -13 0z", false), Seg("M20 20l-4.2-4.2", false)),
        "globe" to listOf(Seg("M3.5 12a8.5 8.5 0 1 0 17 0a8.5 8.5 0 1 0 -17 0z", false), Seg("M3.5 12h17", false), Seg("M12 3.5c2.5 2.4 3.8 5.2 3.8 8.5s-1.3 6.1-3.8 8.5c-2.5-2.4-3.8-5.2-3.8-8.5S9.5 5.9 12 3.5z", false)),
        "clock" to listOf(Seg("M3.5 12a8.5 8.5 0 1 0 17 0a8.5 8.5 0 1 0 -17 0z", false), Seg("M12 7.5V12l3 2", false)),
        "bookmark" to listOf(Seg("M7 4h10a1 1 0 0 1 1 1v15l-6-4-6 4V5a1 1 0 0 1 1-1z", false)),
        "settings" to listOf(Seg("M9 12a3 3 0 1 0 6 0a3 3 0 1 0 -6 0z", false), Seg("M19.4 14a1.7 1.7 0 0 0 .3 1.9l.1.1a2 2 0 1 1-2.8 2.8l-.1-.1a1.7 1.7 0 0 0-1.9-.3 1.7 1.7 0 0 0-1 1.5V21a2 2 0 1 1-4 0v-.1a1.7 1.7 0 0 0-1.1-1.5 1.7 1.7 0 0 0-1.9.3l-.1.1a2 2 0 1 1-2.8-2.8l.1-.1a1.7 1.7 0 0 0 .3-1.9 1.7 1.7 0 0 0-1.5-1H3a2 2 0 1 1 0-4h.1a1.7 1.7 0 0 0 1.5-1.1 1.7 1.7 0 0 0-.3-1.9l-.1-.1a2 2 0 1 1 2.8-2.8l.1.1a1.7 1.7 0 0 0 1.9.3h.1a1.7 1.7 0 0 0 1-1.5V3a2 2 0 1 1 4 0v.1a1.7 1.7 0 0 0 1 1.5 1.7 1.7 0 0 0 1.9-.3l.1-.1a2 2 0 1 1 2.8 2.8l-.1.1a1.7 1.7 0 0 0-.3 1.9v.1a1.7 1.7 0 0 0 1.5 1H21a2 2 0 1 1 0 4h-.1a1.7 1.7 0 0 0-1.5 1z", false)),
        "download" to listOf(Seg("M12 4v11", false), Seg("M7.5 11l4.5 4.5 4.5-4.5", false), Seg("M5 19.5h14", false)),
        "reader" to listOf(Seg("M4 5.5C6.5 4.5 9.5 4.5 12 6c2.5-1.5 5.5-1.5 8-.5v13c-2.5-1-5.5-1-8 .5-2.5-1.5-5.5-1.5-8-.5z", false), Seg("M12 6v13", false)),
        "sparkle" to listOf(Seg("M12 4l1.8 5.2L19 11l-5.2 1.8L12 18l-1.8-5.2L5 11l5.2-1.8z", false), Seg("M18.5 3.5v3M17 5h3", false)),
        "moon" to listOf(Seg("M20 14.5A8.5 8.5 0 0 1 9.5 4a8.5 8.5 0 1 0 10.5 10.5z", false)),
        "sun" to listOf(Seg("M8 12a4 4 0 1 0 8 0a4 4 0 1 0 -8 0z", false), Seg("M12 3v2M12 19v2M3 12h2M19 12h2M5.6 5.6L7 7M17 17l1.4 1.4M5.6 18.4L7 17M17 7l1.4-1.4", false)),
        "eyeOff" to listOf(Seg("M3 3l18 18", false), Seg("M10.6 5.2A9.6 9.6 0 0 1 12 5c5 0 8.5 4 9.5 7-.4 1.1-1.2 2.4-2.4 3.6", false), Seg("M6.6 6.7C4.6 8 3.3 10 2.5 12c1 3 4.5 7 9.5 7 1.5 0 2.9-.4 4.1-1", false), Seg("M9.9 9.9A3 3 0 0 0 14.1 14.1", false)),
        "findPage" to listOf(Seg("M6 3.5h8l4 4V20a.5.5 0 0 1-.5.5h-11A.5.5 0 0 1 6 20z", false), Seg("M8.7 12.5a2.8 2.8 0 1 0 5.6 0a2.8 2.8 0 1 0 -5.6 0z", false), Seg("M13.6 14.6l2.2 2.2", false)),
        "zoom" to listOf(Seg("M4.5 11a6.5 6.5 0 1 0 13 0a6.5 6.5 0 1 0 -13 0z", false), Seg("M20 20l-4.2-4.2M8.5 11h5M11 8.5v5", false)),
        "external" to listOf(Seg("M14 4h6v6", false), Seg("M20 4l-9 9", false), Seg("M18 14v4.5a1.5 1.5 0 0 1-1.5 1.5h-11A1.5 1.5 0 0 1 4 18.5v-11A1.5 1.5 0 0 1 5.5 6H10", false)),
        "trash" to listOf(Seg("M4.5 7h15", false), Seg("M9.5 7V4.5h5V7", false), Seg("M6.5 7l.8 12a1.5 1.5 0 0 0 1.5 1.4h6.4a1.5 1.5 0 0 0 1.5-1.4L17.5 7", false)),
        "check" to listOf(Seg("M5 12.5l4.5 4.5L19 7.5", false)),
        "alert" to listOf(Seg("M12 4l9 15.5H3z", false), Seg("M12 10v4.5", false), Seg("M12 17.2v.1", false)),
        "command" to listOf(Seg("M9 6.5a2.5 2.5 0 1 0-2.5 2.5H9zM9 6.5V9m0-2.5h6M15 6.5A2.5 2.5 0 1 1 17.5 9H15zM15 9v6m0 0h2.5A2.5 2.5 0 1 1 15 17.5zM15 15H9m0 0v2.5A2.5 2.5 0 1 1 6.5 15H9zM9 9v6", false)),
        "pin" to listOf(Seg("M9 4h6l-1 5 3 3v1.5H7V12l3-3z", false), Seg("M12 13.5V20", false)),
        "copy" to listOf(Seg("M10.7 8.5h6.6a2.2 2.2 0 0 1 2.2 2.2v6.6a2.2 2.2 0 0 1 -2.2 2.2h-6.6a2.2 2.2 0 0 1 -2.2 -2.2v-6.6a2.2 2.2 0 0 1 2.2 -2.2z", false), Seg("M15.5 8.5V6.2a1.7 1.7 0 0 0-1.7-1.7H6.2A1.7 1.7 0 0 0 4.5 6.2v7.6a1.7 1.7 0 0 0 1.7 1.7h2.3", false)),
        "chevronUp" to listOf(Seg("M6 14.5l6-6 6 6", false)),
        "chevronDown" to listOf(Seg("M6 9.5l6 6 6-6", false)),
        "fullscreen" to listOf(Seg("M4.5 9V4.5H9M15 4.5h4.5V9M19.5 15v4.5H15M9 19.5H4.5V15", false)),
        "code" to listOf(Seg("M8.5 7L4 12l4.5 5M15.5 7l4.5 5-4.5 5M13.5 5l-3 14", false)),
        "undo" to listOf(Seg("M9 8L4.5 12.5 9 17", false), Seg("M5 12.5h9a5 5 0 0 1 5 5", false)),
        "tab" to listOf(Seg("M6 6.5h12a2.5 2.5 0 0 1 2.5 2.5v7a2.5 2.5 0 0 1 -2.5 2.5h-12a2.5 2.5 0 0 1 -2.5 -2.5v-7a2.5 2.5 0 0 1 2.5 -2.5z", false), Seg("M3.5 10h17", false)),
        "list" to listOf(Seg("M8.5 6.5h11M8.5 12h11M8.5 17.5h11M4.5 6.5h.1M4.5 12h.1M4.5 17.5h.1", false)),
        "share" to listOf(Seg("M3.8 12a2.2 2.2 0 1 0 4.4 0a2.2 2.2 0 1 0 -4.4 0z", false), Seg("M15.3 6a2.2 2.2 0 1 0 4.4 0a2.2 2.2 0 1 0 -4.4 0z", false), Seg("M15.3 18a2.2 2.2 0 1 0 4.4 0a2.2 2.2 0 1 0 -4.4 0z", false), Seg("M8 11l7.5-4M8 13l7.5 4", false)),
        "monitor" to listOf(Seg("M5.5 5h13a2 2 0 0 1 2 2v7a2 2 0 0 1 -2 2h-13a2 2 0 0 1 -2 -2v-7a2 2 0 0 1 2 -2z", false), Seg("M9 20h6M12 16v4", false)),
        "bolt" to listOf(Seg("M13 3.5L5.5 13.5H11l-1 7 7.5-10H12z", false)),
    )

    private val cache = HashMap<String, ImageVector>()

    val names: Set<String> get() = DATA.keys

    /** Ikon garis 24x24. Warna diatur lewat tint pada Icon(). */
    operator fun get(name: String): ImageVector = cache.getOrPut(name) {
        val segs = DATA[name] ?: emptyList()
        val b = ImageVector.Builder(name = name, defaultWidth = 24.dp, defaultHeight = 24.dp, viewportWidth = 24f, viewportHeight = 24f)
        for (s in segs) {
            val nodes = PathParser().parsePathString(s.d).toNodes()
            if (s.filled) b.addPath(pathData = nodes, fill = SolidColor(Color.Black))
            else b.addPath(pathData = nodes, stroke = SolidColor(Color.Black), strokeLineWidth = 1.75f, strokeLineCap = StrokeCap.Round, strokeLineJoin = StrokeJoin.Round)
        }
        b.build()
    }
}
