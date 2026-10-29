package id.ceet.browser.ui

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.ColorScheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Color

/** Token desain yang sama dengan desktop (desktop/ui/theme.css). */
@Immutable
class CeetColors(
    val bg: Color, val surface: Color, val surface2: Color, val fg: Color, val muted: Color, val line: Color,
    val accent: Color, val accentDeep: Color, val accentSoft: Color, val good: Color, val warn: Color, val bad: Color, val priv: Color,
    val dark: Boolean,
)

val LightColors = CeetColors(
    bg = Color(0xFFFBFAF8), surface = Color(0xFFFFFFFF), surface2 = Color(0xFFF3F5F9), fg = Color(0xFF0B1A30), muted = Color(0xFF5B6B82), line = Color(0xFFE3E8F0),
    accent = Color(0xFF1580FA), accentDeep = Color(0xFF0B3BB8), accentSoft = Color(0xFFE6F1FF), good = Color(0xFF14935A), warn = Color(0xFFC27A00), bad = Color(0xFFD23C3C), priv = Color(0xFF6D4AFF), dark = false,
)
val DarkColors = CeetColors(
    bg = Color(0xFF0A1222), surface = Color(0xFF111C33), surface2 = Color(0xFF0D172B), fg = Color(0xFFEAF0FB), muted = Color(0xFF93A3BF), line = Color(0xFF22304D),
    accent = Color(0xFF4C9BFF), accentDeep = Color(0xFF2F6BE0), accentSoft = Color(0xFF14284A), good = Color(0xFF3DD68C), warn = Color(0xFFF0B34A), bad = Color(0xFFFF6B6B), priv = Color(0xFF9B85FF), dark = true,
)

val LocalCeet = staticCompositionLocalOf { LightColors }

@Composable
fun CeetTheme(theme: String, content: @Composable () -> Unit) {
    val dark = when (theme) { "dark" -> true; "light" -> false; else -> isSystemInDarkTheme() }
    val c = if (dark) DarkColors else LightColors
    val scheme: ColorScheme = if (dark) darkColorScheme(
        primary = c.accent, onPrimary = Color.White, background = c.bg, onBackground = c.fg, surface = c.surface, onSurface = c.fg,
        surfaceVariant = c.surface2, onSurfaceVariant = c.muted, outline = c.line, error = c.bad,
    ) else lightColorScheme(
        primary = c.accent, onPrimary = Color.White, background = c.bg, onBackground = c.fg, surface = c.surface, onSurface = c.fg,
        surfaceVariant = c.surface2, onSurfaceVariant = c.muted, outline = c.line, error = c.bad,
    )
    androidx.compose.runtime.CompositionLocalProvider(LocalCeet provides c) {
        MaterialTheme(colorScheme = scheme, content = content)
    }
}
