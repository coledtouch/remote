package com.curbscript.tvremote.iptv

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import java.util.concurrent.TimeUnit

/** Loads channels + EPG from Xtream or M3U and answers now/next per channel. */
class IptvRepository {
    private val http = OkHttpClient.Builder()
        .connectTimeout(15, TimeUnit.SECONDS).readTimeout(25, TimeUnit.SECONDS).build()

    @Volatile private var epg: Map<String, MutableList<IptvProgram>> = emptyMap()
    @Volatile private var pendingEpgUrl: String? = null

    suspend fun loadChannels(s: IptvSettings): List<IptvChannel> = withContext(Dispatchers.IO) {
        when (s.type) {
            "xtream" -> XtreamClient(s.server, s.username, s.password).getChannels()
            "m3u" -> {
                val text = download(s.m3uUrl) ?: return@withContext emptyList()
                val res = M3uParser.parse(text)
                if (s.epgUrl.isBlank()) pendingEpgUrl = res.epgUrl
                res.channels
            }
            else -> emptyList()
        }
    }

    suspend fun loadEpg(s: IptvSettings) = withContext(Dispatchers.IO) {
        val url = when {
            s.epgUrl.isNotBlank() -> s.epgUrl
            s.type == "xtream" -> XtreamClient(s.server, s.username, s.password).epgUrl()
            else -> pendingEpgUrl
        } ?: return@withContext
        try {
            http.newCall(Request.Builder().url(url).build()).execute().use { resp ->
                resp.body?.byteStream()?.use { epg = XmltvParser.parse(it, System.currentTimeMillis()) }
            }
        } catch (_: Exception) {
        }
    }

    fun nowNext(epgId: String?): Pair<IptvProgram?, IptvProgram?> {
        if (epgId == null) return null to null
        val list = epg[epgId] ?: return null to null
        val now = System.currentTimeMillis()
        val current = list.firstOrNull { now >= it.start && now < it.stop }
        val next = list.firstOrNull { it.start >= (current?.stop ?: now) }
        return current to next
    }

    private fun download(url: String): String? = try {
        http.newCall(Request.Builder().url(url).build()).execute().use { it.body?.string() }
    } catch (_: Exception) { null }
}
