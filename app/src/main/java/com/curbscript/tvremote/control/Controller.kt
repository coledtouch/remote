package com.curbscript.tvremote.control

import android.content.Context
import com.curbscript.tvremote.data.ConfigStore
import com.curbscript.tvremote.onn.AndroidTvPairing
import com.curbscript.tvremote.onn.AndroidTvRemote
import com.curbscript.tvremote.onn.CertManager
import com.curbscript.tvremote.proto.RemoteKeyCode
import com.curbscript.tvremote.vizio.VizioClient
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob

/**
 * Single entry point used by both the app UI and the widget. Owns the persistent
 * onn session and builds Vizio clients on demand from saved config.
 *
 * Routing: TV hardware (power / volume / mute / input) goes to the Vizio SmartCast
 * API; navigation, playback and app launches go to the onn Google TV box.
 */
class Controller private constructor(context: Context) {

    val config = ConfigStore(context)
    private val certManager = CertManager(context)
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    @Volatile private var onn: AndroidTvRemote? = null
    @Volatile private var onnHost: String? = null

    // ---- Pairing factories ----

    fun onnPairing(host: String): AndroidTvPairing =
        AndroidTvPairing(host, certManager.socketFactory(), certManager.clientPublicKey())

    fun vizioClient(host: String, port: Int, token: String? = null): VizioClient =
        VizioClient(host, port, token)

    // ---- onn (Google TV) commands ----

    private suspend fun ensureOnn(): AndroidTvRemote? {
        val cfg = config.get()
        if (!cfg.onnReady) return null
        onn?.let { if (it.isReady && onnHost == cfg.onnHost) return it }
        val remote = AndroidTvRemote(cfg.onnHost, certManager.socketFactory())
        return if (remote.connect(scope)) {
            onn = remote; onnHost = cfg.onnHost; remote
        } else null
    }

    suspend fun onnKey(key: RemoteKeyCode): Boolean = withOnnRetry { it.sendKey(key) }

    suspend fun onnLaunch(appLink: String): Boolean = withOnnRetry { it.launchApp(appLink) }

    private suspend fun withOnnRetry(action: suspend (AndroidTvRemote) -> Unit): Boolean {
        val first = ensureOnn() ?: return false
        return try {
            action(first); true
        } catch (_: Exception) {
            onn = null
            val second = ensureOnn() ?: return false
            try { action(second); true } catch (_: Exception) { false }
        }
    }

    // ---- Vizio (TV) commands ----

    private suspend fun vizio(): VizioClient? {
        val cfg = config.get()
        if (!cfg.vizioReady) return null
        return VizioClient(cfg.vizioHost, cfg.vizioPort, cfg.vizioToken)
    }

    private suspend fun withVizio(action: suspend (VizioClient) -> Unit): Boolean {
        val v = vizio() ?: return false
        return try { action(v); true } catch (_: Exception) { false }
    }

    suspend fun tvPowerToggle() = withVizio { it.powerToggle() }
    suspend fun tvVolumeUp() = withVizio { it.volumeUp() }
    suspend fun tvVolumeDown() = withVizio { it.volumeDown() }
    suspend fun tvMuteToggle() = withVizio { it.muteToggle() }
    suspend fun tvCycleInput() = withVizio { it.cycleInput() }
    suspend fun tvSetInput(name: String) = withVizio { it.setInput(name) }

    suspend fun tvInputs(): List<String> = try {
        vizio()?.listInputs() ?: emptyList()
    } catch (_: Exception) { emptyList() }

    companion object {
        @Volatile private var INSTANCE: Controller? = null
        fun get(context: Context): Controller =
            INSTANCE ?: synchronized(this) {
                INSTANCE ?: Controller(context.applicationContext).also { INSTANCE = it }
            }
    }
}
