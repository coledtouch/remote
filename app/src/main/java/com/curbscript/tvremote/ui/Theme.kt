package com.curbscript.tvremote.ui

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Typography
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

/** Dark, minimal palette used throughout the remote. */
object RemoteColors {
    val bg = Color(0xFF0B0C0F)
    val surface = Color(0xFF16181D)
    val surfaceHi = Color(0xFF1F232B)
    val border = Color(0xFF2A2F38)
    val onSurface = Color(0xFFEAECF1)
    val muted = Color(0xFF888E9A)
    val accent = Color(0xFF6E8BFF)
    val power = Color(0xFFFF5A5F)
    val good = Color(0xFF3DDC97)
}

private val DarkScheme = darkColorScheme(
    primary = RemoteColors.accent,
    onPrimary = Color.White,
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
    // Always dark — this remote is designed for a single, focused look.
    @Suppress("UNUSED_EXPRESSION") isSystemInDarkTheme()
    MaterialTheme(
        colorScheme = DarkScheme,
        typography = Typography(),
        content = content
    )
}
