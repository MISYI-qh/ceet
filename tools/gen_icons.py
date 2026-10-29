#!/usr/bin/env python3
"""Membuat Icons.kt (Android) dari desktop/ui/icons.js, agar satu set ikon SVG dipakai semua platform.
Elemen path dipakai apa adanya. Circle dan rect diubah menjadi path."""
import re
import sys
from pathlib import Path

root = Path(__file__).resolve().parent.parent
src = (root / "desktop/ui/icons.js").read_text(encoding="utf-8")
body = src[src.index("var P = {"): src.index("};", src.index("var P = {"))]
entries = re.findall(r"(\w+):\s*'((?:[^'\\]|\\.)*)'", body)


def fmt(n):
    s = f"{n:.4f}".rstrip("0").rstrip(".")
    return s if s else "0"


def circle(cx, cy, r):
    return f"M{fmt(cx - r)} {fmt(cy)}a{fmt(r)} {fmt(r)} 0 1 0 {fmt(2 * r)} 0a{fmt(r)} {fmt(r)} 0 1 0 {fmt(-2 * r)} 0z"


def rect(x, y, w, h, rx):
    rx = min(rx, w / 2, h / 2)
    if rx <= 0:
        return f"M{fmt(x)} {fmt(y)}h{fmt(w)}v{fmt(h)}h{fmt(-w)}z"
    return (f"M{fmt(x + rx)} {fmt(y)}h{fmt(w - 2 * rx)}a{fmt(rx)} {fmt(rx)} 0 0 1 {fmt(rx)} {fmt(rx)}"
            f"v{fmt(h - 2 * rx)}a{fmt(rx)} {fmt(rx)} 0 0 1 {fmt(-rx)} {fmt(rx)}h{fmt(-(w - 2 * rx))}"
            f"a{fmt(rx)} {fmt(rx)} 0 0 1 {fmt(-rx)} {fmt(-rx)}v{fmt(-(h - 2 * rx))}"
            f"a{fmt(rx)} {fmt(rx)} 0 0 1 {fmt(rx)} {fmt(-rx)}z")


def attrs(tag):
    return dict(re.findall(r'([\w-]+)="([^"]*)"', tag))


out = []
for name, svg in entries:
    segs = []
    for m in re.finditer(r"<(path|circle|rect)\b([^>]*?)/?>", svg):
        kind, rest = m.group(1), m.group(2)
        a = attrs(rest)
        filled = a.get("fill") == "currentColor"
        if kind == "path":
            d = a["d"]
        elif kind == "circle":
            d = circle(float(a["cx"]), float(a["cy"]), float(a["r"]))
        else:
            d = rect(float(a["x"]), float(a["y"]), float(a["width"]), float(a["height"]), float(a.get("rx", 0)))
        segs.append((d, filled))
    out.append((name, segs))

lines = [
    "package id.ceet.browser.ui",
    "",
    "import androidx.compose.ui.graphics.Color",
    "import androidx.compose.ui.graphics.SolidColor",
    "import androidx.compose.ui.graphics.StrokeCap",
    "import androidx.compose.ui.graphics.StrokeJoin",
    "import androidx.compose.ui.graphics.vector.ImageVector",
    "import androidx.compose.ui.graphics.vector.PathParser",
    "import androidx.compose.ui.unit.dp",
    "",
    "// DIBUAT OTOMATIS oleh tools/gen_icons.py dari desktop/ui/icons.js. Jangan diedit manual.",
    "object CeetIcons {",
    "    private class Seg(val d: String, val filled: Boolean)",
    "",
    "    private val DATA: Map<String, List<Seg>> = mapOf(",
]
for name, segs in out:
    items = ", ".join(f'Seg("{d}", {"true" if f else "false"})' for d, f in segs)
    lines.append(f'        "{name}" to listOf({items}),')
lines += [
    "    )",
    "",
    "    private val cache = HashMap<String, ImageVector>()",
    "",
    "    val names: Set<String> get() = DATA.keys",
    "",
    "    /** Ikon garis 24x24. Warna diatur lewat tint pada Icon(). */",
    "    operator fun get(name: String): ImageVector = cache.getOrPut(name) {",
    "        val segs = DATA[name] ?: emptyList()",
    "        val b = ImageVector.Builder(name = name, defaultWidth = 24.dp, defaultHeight = 24.dp, viewportWidth = 24f, viewportHeight = 24f)",
    "        for (s in segs) {",
    "            val nodes = PathParser().parsePathString(s.d).toNodes()",
    "            if (s.filled) b.addPath(pathData = nodes, fill = SolidColor(Color.Black))",
    "            else b.addPath(pathData = nodes, stroke = SolidColor(Color.Black), strokeLineWidth = 1.75f, strokeLineCap = StrokeCap.Round, strokeLineJoin = StrokeJoin.Round)",
    "        }",
    "        b.build()",
    "    }",
    "}",
    "",
]
dest = root / "android/app/src/main/java/id/ceet/browser/ui/CeetIcons.kt"
dest.parent.mkdir(parents=True, exist_ok=True)
dest.write_text("\n".join(lines), encoding="utf-8")
print(f"{len(out)} ikon ditulis ke {dest}")
if "--check" in sys.argv:
    bad = [n for n, s in out if not s]
    if bad:
        print("ikon tanpa bentuk:", bad)
        sys.exit(1)
