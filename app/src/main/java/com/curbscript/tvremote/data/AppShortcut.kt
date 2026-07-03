package com.curbscript.tvremote.data

import androidx.compose.ui.graphics.Color

/**
 * A streaming app that can be launched on the onn 4K Pro via an Android TV
 * "app link" (deep link URL the app registers to handle).
 */
data class AppShortcut(
    val label: String,
    val appLink: String,
    val brand: Color,
    val glyph: String = label.take(1)
)

val DEFAULT_SHORTCUTS: List<AppShortcut> = listOf(
    AppShortcut("YouTube", "https://www.youtube.com", Color(0xFFFF0033), "▶"),
    AppShortcut("Netflix", "https://www.netflix.com/title", Color(0xFFE50914), "N"),
    AppShortcut("Prime Video", "https://app.primevideo.com", Color(0xFF1FA2FF), "P"),
    AppShortcut("Disney+", "https://www.disneyplus.com", Color(0xFF0B3D91), "D+"),
    AppShortcut("Max", "https://play.max.com", Color(0xFF7B2FF7), "M"),
    AppShortcut("Hulu", "https://www.hulu.com", Color(0xFF1CE783), "h"),
    AppShortcut("Spotify", "spotify://", Color(0xFF1DB954), "♪"),
    AppShortcut("Plex", "https://app.plex.tv", Color(0xFFE5A00D), "P")
)
