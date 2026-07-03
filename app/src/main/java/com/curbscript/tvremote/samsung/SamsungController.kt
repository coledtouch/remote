package com.curbscript.tvremote.samsung

import android.util.Base64
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.Response
import okhttp3.WebSocket
import okhttp3.WebSocketListener
import org.json.JSONObject
import java.security.SecureRandom
import java.security.cert.X509Certificate
import java.util.concurrent.TimeUnit
import javax.net.ssl.SSLContext
import javax.net.ssl.SSLSocketFactory
import javax.net.ssl.TrustManager
import javax.net.ssl.X509TrustManager

/**
 * Samsung Tizen WebSocket remote for the S32DM702UN smart monitor.
 * Secure channel on wss://<host>:8002. The first connection shows an "Allow"
 * prompt on the monitor and returns a token; reusing the token skips the prompt.
 *
 * The onn Bluetooth soundbar has no network control, so the bedroom's volume/mute
 * is routed to this monitor (which drives the soundbar's audio output).
 */
class SamsungController(
    private val host: String,
    private var token: String? = null,
    private val appName: String = "Curb Remote"
) {
    @Volatile private var ws: WebSocket? = null
    @Volatile private var ready: Boolean = false

    fun setToken(t: String?) { token = t }

    /** Opens the channel (prompting Allow if unpaired) and returns the token. */
    suspend fun pairAndGetToken(): String? = withContext(Dispatchers.IO) {
        val result = CompletableDeferred<String?>()
        open(result)
        val t = withTimeoutOrNull(35_000) { result.await() }
        t?.let { token = it }
        t
    }

    suspend fun sendKey(key: String): Boolean = withContext(Dispatchers.IO) {
        if (!ensureOpen()) return@withContext false
        val msg = JSONObject().apply {
            put("method", "ms.remote.control")
            put("params", JSONObject().apply {
                put("Cmd", "Click")
                put("DataOfCmd", key)
                put("Option", "false")
                put("TypeOfRemote", "SendRemoteKey")
            })
        }
        ws?.send(msg.toString()) ?: false
    }

    /** Types a string into a focused text field on the monitor. */
    suspend fun sendText(text: String): Boolean = withContext(Dispatchers.IO) {
        if (!ensureOpen()) return@withContext false
        val encoded = Base64.encodeToString(text.toByteArray(), Base64.NO_WRAP)
        val msg = JSONObject().apply {
            put("method", "ms.remote.control")
            put("params", JSONObject().apply {
                put("Cmd", encoded)
                put("DataOfCmd", "base64")
                put("TypeOfRemote", "SendInputString")
            })
        }
        ws?.send(msg.toString()) ?: false
    }

    suspend fun launchApp(appId: String): Boolean = withContext(Dispatchers.IO) {
        if (!ensureOpen()) return@withContext false
        val msg = JSONObject().apply {
            put("method", "ms.channel.emit")
            put("params", JSONObject().apply {
                put("event", "ed.apps.launch")
                put("to", "host")
                put("data", JSONObject().apply {
                    put("action_type", "DEEP_LINK")
                    put("appId", appId)
                })
            })
        }
        ws?.send(msg.toString()) ?: false
    }

    fun close() {
        try { ws?.close(1000, null) } catch (_: Exception) {}
        ws = null; ready = false
    }

    private suspend fun ensureOpen(): Boolean {
        if (ready && ws != null) return true
        val result = CompletableDeferred<String?>()
        open(result)
        val ok = withTimeoutOrNull(15_000) { result.await(); true } ?: false
        return ok && ready
    }

    private fun open(connectResult: CompletableDeferred<String?>) {
        close()
        val nameB64 = Base64.encodeToString(appName.toByteArray(), Base64.NO_WRAP)
        val tokenPart = token?.takeIf { it.isNotBlank() }?.let { "&token=$it" } ?: ""
        val url = "wss://$host:8002/api/v2/channels/samsung.remote.control?name=$nameB64$tokenPart"
        val request = Request.Builder().url(url).build()
        ws = client().newWebSocket(request, object : WebSocketListener() {
            override fun onMessage(webSocket: WebSocket, text: String) {
                try {
                    val json = JSONObject(text)
                    if (json.optString("event") == "ms.channel.connect") {
                        val t = json.optJSONObject("data")?.optString("token")?.takeIf { it.isNotBlank() }
                        if (t != null) token = t
                        ready = true
                        if (!connectResult.isCompleted) connectResult.complete(t ?: token)
                    }
                } catch (_: Exception) {
                }
            }

            override fun onFailure(webSocket: WebSocket, t: Throwable, response: Response?) {
                ready = false
                if (!connectResult.isCompleted) connectResult.complete(null)
            }

            override fun onClosed(webSocket: WebSocket, code: Int, reason: String) {
                ready = false
            }
        })
    }

    private fun client(): OkHttpClient =
        OkHttpClient.Builder()
            .sslSocketFactory(trustAllFactory(), TrustAll)
            .hostnameVerifier { _, _ -> true }
            .connectTimeout(10, TimeUnit.SECONDS)
            .pingInterval(20, TimeUnit.SECONDS)
            .build()

    private fun trustAllFactory(): SSLSocketFactory {
        val ctx = SSLContext.getInstance("TLS")
        ctx.init(null, arrayOf<TrustManager>(TrustAll), SecureRandom())
        return ctx.socketFactory
    }

    private object TrustAll : X509TrustManager {
        override fun checkClientTrusted(chain: Array<out X509Certificate>?, authType: String?) {}
        override fun checkServerTrusted(chain: Array<out X509Certificate>?, authType: String?) {}
        override fun getAcceptedIssuers(): Array<X509Certificate> = emptyArray()
    }

    companion object {
        const val KEY_POWER = "KEY_POWER"
        const val KEY_VOLUP = "KEY_VOLUP"
        const val KEY_VOLDOWN = "KEY_VOLDOWN"
        const val KEY_MUTE = "KEY_MUTE"
        const val KEY_UP = "KEY_UP"
        const val KEY_DOWN = "KEY_DOWN"
        const val KEY_LEFT = "KEY_LEFT"
        const val KEY_RIGHT = "KEY_RIGHT"
        const val KEY_ENTER = "KEY_ENTER"
        const val KEY_RETURN = "KEY_RETURN"
        const val KEY_HOME = "KEY_HOME"
        const val KEY_SOURCE = "KEY_SOURCE"
        const val KEY_MENU = "KEY_MENU"
        const val KEY_PLAY = "KEY_PLAY"
        const val KEY_PAUSE = "KEY_PAUSE"
        const val APP_NETFLIX = "11101200001"
        const val APP_YOUTUBE = "111299001912"
        const val APP_SPOTIFY = "3201606009684"
    }
}
