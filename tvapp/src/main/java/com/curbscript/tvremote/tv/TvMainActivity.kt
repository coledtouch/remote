package com.curbscript.tvremote.tv

import android.app.Activity
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.view.View
import android.view.WindowManager
import android.widget.TextView
import androidx.annotation.OptIn
import androidx.media3.common.MediaItem
import androidx.media3.common.util.UnstableApi
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.ui.PlayerView
import fi.iki.elonen.NanoHTTPD
import java.net.Inet4Address
import java.net.NetworkInterface

/** Companion player for Android TV. Runs a tiny HTTP server the phone posts channels to. */
class TvMainActivity : Activity() {
    private var player: ExoPlayer? = null
    private var server: PlayServer? = null
    private val main = Handler(Looper.getMainLooper())
    private lateinit var overlay: View

    @OptIn(UnstableApi::class)
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        setContentView(R.layout.activity_tv)
        overlay = findViewById(R.id.overlay)
        findViewById<TextView>(R.id.addr).text = "http://" + localIp() + ":" + PORT
        val exo = ExoPlayer.Builder(this).build().also { player = it }
        findViewById<PlayerView>(R.id.player).player = exo
        server = try { PlayServer().also { it.start(NanoHTTPD.SOCKET_READ_TIMEOUT, true) } } catch (_: Exception) { null }
    }

    private fun playUrl(url: String) = main.post {
        overlay.visibility = View.GONE
        player?.apply { setMediaItem(MediaItem.fromUri(url)); prepare(); playWhenReady = true }
    }

    private fun stopPlayback() = main.post {
        player?.stop()
        overlay.visibility = View.VISIBLE
    }

    override fun onDestroy() {
        super.onDestroy()
        try { server?.stop() } catch (_: Exception) {}
        player?.release(); player = null
    }

    private fun localIp(): String {
        try {
            for (ni in NetworkInterface.getNetworkInterfaces()) {
                for (addr in ni.inetAddresses) {
                    if (!addr.isLoopbackAddress && addr is Inet4Address) return addr.hostAddress ?: "?"
                }
            }
        } catch (_: Exception) {}
        return "?"
    }

    inner class PlayServer : NanoHTTPD(PORT) {
        override fun serve(session: IHTTPSession): Response {
            val p = session.parms
            return when {
                session.uri.startsWith("/play") -> {
                    val url = p["url"]
                    if (!url.isNullOrBlank()) { playUrl(url); json("{\"ok\":true}") }
                    else json("{\"ok\":false}")
                }
                session.uri.startsWith("/stop") -> { stopPlayback(); json("{\"ok\":true}") }
                session.uri.startsWith("/state") -> json("{\"playing\":${player?.isPlaying == true}}")
                else -> json("{\"app\":\"curb-tv\"}")
            }
        }
        private fun json(body: String): Response =
            newFixedLengthResponse(Response.Status.OK, "application/json", body)
    }

    companion object { const val PORT = 8099 }
}
