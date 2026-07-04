package com.curbscript.tvremote.ui

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Typography
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color

/** Warm, premium "new-age" dark palette (coral -> amber accent). */
object RemoteColors {
    val bg = Color(0xFF0C0A09)
    val bgElevated = Color(0xFF141010)
    val surface = Color(0xFF1A1512)
    val surfaceHi = Color(0xFF241C17)
    val border = Color(0xFF322A22)
    val onSurface = Color(0xFFF4EEE7)
    val muted = Color(0xFFA2937F)
    val coral = Color(0xFFFF6A45)
    val amber = Color(0xFFFFB84D)
    val power = Color(0xFFFF5C57)
    val good = Color(0xFF4ADE80)

    val accent = Brush.linearGradient(listOf(Color(0xFFFF7A4D), Color(0xFFFFB020)))
    val accentSoft = Brush.linearGradient(listOf(Color(0x33FF7A4D), Color(0x22FFB020)))
    val glow = Brush.radialGradient(
        colors = listOf(Color(0x2EFF7A4D), Color(0x00FF7A4D)),
        radius = 760f
    )
}

private val DarkScheme = darkColorScheme(
    primary = RemoteColors.coral,
    onPrimary = Color(0xFF1B0E07),
    secondary = RemoteColors.amber,
    background = RemoteColors.bg,
    onBackground = RemoteColors.onSurface,
    surface = RemoteColors.surface,
    onSurface = RemoteColors.onSurface,
    surfaceVariant = RemoteColors.surfaceHi,
    outline = RemoteColors.border,
    error = RemoteColors.power
)

@Composable
fun CurbRemoteTheme(content: @Composable () -> Unit) {
    MaterialTheme(colorScheme = DarkScheme, typography = Typography(), content = content)
}
