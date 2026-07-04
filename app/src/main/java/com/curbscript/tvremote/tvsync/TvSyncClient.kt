package com.curbscript.tvremote.tvsync

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import java.net.URLEncoder
import java.util.concurrent.TimeUnit

/** Talks to the companion Curb TV app running on the onn (HTTP on port 8099). */
object TvSyncClient {
    const val PORT = 8099
    private val http = OkHttpClient.Builder()
        .connectTimeout(4, TimeUnit.SECONDS).readTimeout(4, TimeUnit.SECONDS).build()

    suspend fun play(host: String, url: String, title: String): Boolean = withContext(Dispatchers.IO) {
        try {
            val u = "http://$host:$PORT/play?url=${enc(url)}&title=${enc(title)}"
            http.newCall(Request.Builder().url(u).build()).execute().use { it.isSuccessful }
        } catch (_: Exception) { false }
    }

    suspend fun stop(host: String): Boolean = withContext(Dispatchers.IO) {
        try {
            http.newCall(Request.Builder().url("http://$host:$PORT/stop").build()).execute().use { it.isSuccessful }
        } catch (_: Exception) { false }
    }

    private fun enc(s: String) = URLEncoder.encode(s, "UTF-8")
}
