package com.curbscript.tvremote.iptv

import okhttp3.OkHttpClient
import okhttp3.Request
import org.json.JSONArray
import java.util.concurrent.TimeUnit

/** Xtream Codes player_api client: live channels + XMLTV EPG URL. */
class XtreamClient(server: String, private val username: String, private val password: String) {
    private val base = normalize(server)
    private val http = OkHttpClient.Builder()
        .connectTimeout(15, TimeUnit.SECONDS).readTimeout(20, TimeUnit.SECONDS).build()

    private fun api(action: String): String? = try {
        val url = "$base/player_api.php?username=$username&password=$password&action=$action"
        http.newCall(Request.Builder().url(url).build()).execute().use { it.body?.string() }
    } catch (_: Exception) { null }

    fun getChannels(): List<IptvChannel> {
        val body = api("get_live_streams") ?: return emptyList()
        return try {
            val arr = JSONArray(body)
            (0 until arr.length()).mapNotNull { i ->
                val o = arr.optJSONObject(i) ?: return@mapNotNull null
                val id = o.optString("stream_id")
                if (id.isBlank()) return@mapNotNull null
                IptvChannel(
                    id = id,
                    name = o.optString("name"),
                    logo = o.optString("stream_icon").ifBlank { null },
                    streamUrl = "$base/live/$username/$password/$id.ts",
                    epgId = o.optString("epg_channel_id").ifBlank { null },
                    group = o.optString("category_id").ifBlank { null }
                )
            }
        } catch (_: Exception) { emptyList() }
    }

    fun epgUrl(): String = "$base/xmltv.php?username=$username&password=$password"

    private fun normalize(s: String): String {
        var u = s.trim()
        if (!u.startsWith("http")) u = "http://$u"
        return u.trimEnd('/')
    }
}
