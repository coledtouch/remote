package com.curbscript.tvremote.control

import android.content.Context
import com.curbscript.tvremote.data.Config
import com.curbscript.tvremote.data.ConfigStore
import com.curbscript.tvremote.iptv.IptvChannel
import com.curbscript.tvremote.iptv.IptvProgram
import com.curbscript.tvremote.iptv.IptvRepository
import com.curbscript.tvremote.iptv.IptvSettings
import com.curbscript.tvremote.tvsync.TvSyncClient
import kotlinx.coroutines.delay
import com.curbscript.tvremote.discovery.Discovered
import com.curbscript.tvremote.discovery.DiscoveryManager
import com.curbscript.tvremote.hubspace.HubspaceClient
import com.curbscript.tvremote.hubspace.HubspaceLight
import com.curbscript.tvremote.onn.AndroidTvPairing
import com.curbscript.tvremote.onn.AndroidTvRemote
import com.curbscript.tvremote.onn.CertManager
import com.curbscript.tvremote.proto.RemoteKeyCode
import com.curbscript.tvremote.samsung.SamsungController
import com.curbscript.tvremote.vizio.VizioClient
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableStateFlow

/**
 * Single entry point for the app UI and widget. Routes commands to the right
 * device per room: Living Room = Vizio (TV) + onn (Google TV); Bedroom = Samsung
 * monitor (soundbar volume routed through it).
 */
class Controller private constructor(context: Context) {

    private val appContext = context.applicationContext
    val config = ConfigStore(context)
    private val certManager = CertManager(context)
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    val imeActive = MutableStateFlow(false)

    @Volatile private var onn: AndroidTvRemote? = null
    @Volatile private var onnHost: String? = null
    @Volatile private var samsung: SamsungController? = null
    @Volatile private var samsungHost: String? = null

    // ---- Pairing factories ----
    fun onnPairing(host: String): AndroidTvPairing =
        AndroidTvPairing(host, certManager.socketFactory(), certManager.clientPublicKey())

    fun vizioClient(host: String, port: Int, token: String? = null): VizioClient =
        VizioClient(host, port, token)

    fun samsungPairing(host: String): SamsungController = SamsungController(host)

    // ---- onn (Google TV) ----
    private suspend fun ensureOnn(): AndroidTvRemote? {
        val cfg = config.get()
        if (!cfg.onnReady) return null
        onn?.let { if (it.isReady && onnHost == cfg.onnHost) return it }
        val remote = AndroidTvRemote(cfg.onnHost, certManager.socketFactory())
        remote.onImeShow = { imeActive.value = it }
        return if (remote.connect(scope)) { onn = remote; onnHost = cfg.onnHost; remote } else null
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

    // ---- Vizio (living room TV) ----
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
    suspend fun tvInputs(): List<String> =
        try { vizio()?.listInputs() ?: emptyList() } catch (_: Exception) { emptyList() }

    // ---- Samsung (bedroom monitor) ----
    private suspend fun ensureSamsung(): SamsungController? {
        val cfg = config.get()
        if (!cfg.samsungReady) return null
        samsung?.let { if (samsungHost == cfg.samsungHost) return it }
        val c = SamsungController(cfg.samsungHost, cfg.samsungToken)
        samsung = c; samsungHost = cfg.samsungHost
        return c
    }

    suspend fun bedKey(key: String): Boolean {
        val c = ensureSamsung() ?: return false
        return try { c.sendKey(key) } catch (_: Exception) { false }
    }

    suspend fun bedLaunch(appId: String): Boolean {
        val c = ensureSamsung() ?: return false
        return try { c.launchApp(appId) } catch (_: Exception) { false }
    }

    suspend fun bedText(text: String): Boolean {
        val c = ensureSamsung() ?: return false
        return try { c.sendText(text) } catch (_: Exception) { false }
    }

    suspend fun onnType(text: String): Boolean {
        val r = ensureOnn() ?: return false
        return try {
            for (ch in text) charToKeyCode(ch)?.let { r.sendKey(it) }
            true
        } catch (_: Exception) { false }
    }

    private fun charToKeyCode(c: Char): RemoteKeyCode? = when (c) {
        in 'a'..'z' -> RemoteKeyCode.forNumber(29 + (c - 'a'))
        in 'A'..'Z' -> RemoteKeyCode.forNumber(29 + (c - 'A'))
        in '0'..'9' -> RemoteKeyCode.forNumber(7 + (c - '0'))
        ' ' -> RemoteKeyCode.KEYCODE_SPACE
        '.' -> RemoteKeyCode.KEYCODE_PERIOD
        ',' -> RemoteKeyCode.KEYCODE_COMMA
        '-' -> RemoteKeyCode.KEYCODE_MINUS
        '@' -> RemoteKeyCode.KEYCODE_AT
        '/' -> RemoteKeyCode.KEYCODE_SLASH
        '+' -> RemoteKeyCode.KEYCODE_PLUS
        '=' -> RemoteKeyCode.KEYCODE_EQUALS
        ';' -> RemoteKeyCode.KEYCODE_SEMICOLON
        '\'' -> RemoteKeyCode.KEYCODE_APOSTROPHE
        '`' -> RemoteKeyCode.KEYCODE_GRAVE
        else -> null
    }

    // ---- Hubspace (EcoSmart lights, cloud) ----
    @Volatile private var hubspace: HubspaceClient? = null

    private suspend fun hs(): HubspaceClient? {
        val cfg = config.get()
        if (!cfg.hubspaceReady) return null
        hubspace?.let { return it }
        val c = HubspaceClient(cfg.hubspaceRefresh, cfg.hubspaceAccount.ifBlank { null })
        hubspace = c
        return c
    }

    /** Returns null on success, or a message describing the failing step. */
    suspend fun hubspaceLogin(email: String, password: String): String? {
        val c = HubspaceClient()
        val err = try { c.login(email, password) } catch (e: Exception) { e.message ?: "Sign-in error" }
        if (err == null) {
            hubspace = c
            config.setHubspace(c.refreshTokenValue() ?: "", c.accountValue() ?: "")
        }
        return err
    }

    @Volatile private var lastLightsDiag = ""
    fun hubspaceDiagnostic(): String = lastLightsDiag
    suspend fun lights(): List<HubspaceLight> {
        val c = hs() ?: run { lastLightsDiag = "not signed in"; return emptyList() }
        return try {
            val l = c.listLights(); lastLightsDiag = c.lastDiagnostic; l
        } catch (e: Exception) { lastLightsDiag = e.message ?: "error"; emptyList() }
    }
    suspend fun setLightPower(id: String, on: Boolean): Boolean =
        try { hs()?.setPower(id, on) ?: false } catch (_: Exception) { false }
    suspend fun setLightBrightness(id: String, pct: Int): Boolean =
        try { hs()?.setBrightness(id, pct) ?: false } catch (_: Exception) { false }
    suspend fun setLight(id: String, on: Boolean, brightness: Int): Boolean {
        val c = hs() ?: return false
        return try {
            if (on) { c.setPower(id, true); c.setBrightness(id, brightness) } else c.setPower(id, false)
            true
        } catch (_: Exception) { false }
    }

    suspend fun discoverDevices(): List<Discovered> {
        val list = try { DiscoveryManager(appContext).scan() } catch (_: Exception) { emptyList() }
        list.forEach { d ->
            when (d.type) {
                "vizio" -> config.setVizioHost(d.ip)
                "onn" -> config.setOnnHost(d.ip)
                "samsung" -> config.setSamsungHost(d.ip)
            }
        }
        return list
    }

    // ---- IPTV (TV Guide) ----
    private val iptvRepo = IptvRepository()
    private fun iptvSettings(cfg: Config) =
        IptvSettings(cfg.iptvType, cfg.iptvServer, cfg.iptvUser, cfg.iptvPass, cfg.iptvM3u, cfg.iptvEpg)
    suspend fun iptvChannels(): List<IptvChannel> {
        val cfg = config.get()
        return if (cfg.iptvReady) iptvRepo.loadChannels(iptvSettings(cfg)) else emptyList()
    }
    suspend fun iptvLoadEpg() {
        val cfg = config.get()
        if (cfg.iptvReady) iptvRepo.loadEpg(iptvSettings(cfg))
    }
    fun iptvNowNext(epgId: String?): Pair<IptvProgram?, IptvProgram?> = iptvRepo.nowNext(epgId)

    // ---- Companion TV app (phone -> onn player) ----
    suspend fun playOnTv(url: String, title: String): Boolean {
        val host = config.get().onnHost
        if (host.isBlank()) return false
        onnLaunch("market://launch?id=com.curbscript.tvremote.tv")
        repeat(6) { i ->
            delay(if (i == 0) 1500L else 1000L)
            if (TvSyncClient.play(host, url, title)) return true
        }
        return false
    }

    companion object {
        @Volatile private var INSTANCE: Controller? = null
        fun get(context: Context): Controller =
            INSTANCE ?: synchronized(this) {
                INSTANCE ?: Controller(context.applicationContext).also { INSTANCE = it }
            }
    }
}
