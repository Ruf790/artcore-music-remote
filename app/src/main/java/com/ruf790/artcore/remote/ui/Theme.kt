package com.ruf790.artcore.remote.ui

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Color

/** The ArtCore Music Remote colours: red header and buttons on white (or on #0f1115 in the dark theme). */
@Immutable
data class Palette(
    val red: Color,
    val statusBar: Color,
    val bg: Color,
    val bg2: Color,
    val line: Color,
    val text: Color,
    val text2: Color,
    val playing: Color,
    val coverBg: Color,
    val dark: Boolean,
)

val LightPalette = Palette(
    red = Color(0xFFD7263D),
    statusBar = Color(0xFFA5132A),
    bg = Color(0xFFFFFFFF),
    bg2 = Color(0xFFF5F5F7),
    line = Color(0xFFE7E7EC),
    text = Color(0xFF1B1B1F),
    text2 = Color(0xFF7A7A85),
    playing = Color(0xFFFDECEE),
    coverBg = Color(0xFFF5F5F7),
    dark = false,
)

val DarkPalette = Palette(
    red = Color(0xFFD7263D),
    statusBar = Color(0xFFA5132A),
    bg = Color(0xFF0F1115),
    bg2 = Color(0xFF181B21),
    line = Color(0xFF262A32),
    text = Color(0xFFECECF1),
    text2 = Color(0xFF9A9AA6),
    playing = Color(0xFF2E161B),
    coverBg = Color(0xFF181B21),
    dark = true,
)

val LocalPalette = staticCompositionLocalOf { LightPalette }

@Composable
fun RemoteTheme(dark: Boolean, content: @Composable () -> Unit) {
    val p = if (dark) DarkPalette else LightPalette
    val scheme = if (dark) {
        darkColorScheme(primary = p.red, onPrimary = Color.White, background = p.bg, surface = p.bg,
            onBackground = p.text, onSurface = p.text, surfaceVariant = p.bg2, onSurfaceVariant = p.text2,
            outline = p.line, outlineVariant = p.line, secondaryContainer = p.playing, surfaceContainer = p.bg2,
            surfaceContainerHigh = p.bg2, surfaceContainerHighest = p.bg2)
    } else {
        lightColorScheme(primary = p.red, onPrimary = Color.White, background = p.bg, surface = p.bg,
            onBackground = p.text, onSurface = p.text, surfaceVariant = p.bg2, onSurfaceVariant = p.text2,
            outline = p.line, outlineVariant = p.line, secondaryContainer = p.playing, surfaceContainer = p.bg2,
            surfaceContainerHigh = p.bg2, surfaceContainerHighest = p.bg2)
    }
    CompositionLocalProvider(LocalPalette provides p) {
        MaterialTheme(colorScheme = scheme, content = content)
    }
}
