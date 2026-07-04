package com.curbscript.tvremote.ui

import android.app.Application
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.curbscript.tvremote.control.Controller
import com.curbscript.tvremote.data.AppShortcut
import com.curbscript.tvremote.adb.AdbInstaller
import com.curbscript.tvremote.data.Config
import com.curbscript.tvremote.discovery.Discovered
import com.curbscript.tvremote.hubspace.HubspaceLight
import com.curbscript.tvremote.iptv.IptvChannel
import com.curbscript.tvremote.onn.AndroidTvPairing
import com.curbscript.tvremote.proto.RemoteKeyCode
import com.curbscript.tvremote.samsung.SamsungController
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import java.util.UUID

sealed interface PairPhase {
    data object Idle : PairPhase
    data object Connecting : PairPhase
    data class AwaitingCode(val message: String) : PairPhase
    data class Error(val message: String) : PairPhase
    data object Success : PairPhase
}

/** Preset light scenes. [mood] optionally launches an app on the room's TV to set the vibe. */
enum class Scene(
    val label: String, val on: Boolean, val brightness: Int, val kelvin: Int?, val mood: String?
) {
    MOVIE("Movie", true, 12, 2700, null),      // warm + dim
    DAYTIME("Daytime", true, 100, 5000, null), // cool + bright
    NIGHT("Night", true, 4, 2700, null),       // warm + very dim
    CHILL("Chill", true, 35, 2700, "spotify")  // warm + music
}

class RemoteViewModel(app: Application) : AndroidViewModel(app) {

    private val controller = Controller.get(app)

    val config: StateFlow<Config> =
        controller.config.flow.stateIn(viewModelScope, SharingStarted.Eagerly, Config())

    val imeActive: StateFlow<Boolean> = controller.imeActive

    // ---- auto-detect ----
    var scanning by mutableStateOf(false)
        private set
    var foundDevices by mutableStateOf<List<Discovered>>(emptyList())
        private set
    fun scan() {
        scanning = true
        viewModelScope.launch {
            foundDevices = try { controller.discoverDevices() } catch (_: Exception) { emptyList() }
            scanning = false
        }
    }

    // ---- room + nav preferences ----
    fun setRoom(room: String) = viewModelScope.launch { controller.config.setRoom(room) }
    fun setTrackpad(v: Boolean) = viewModelScope.launch { controller.config.setNavTrackpad(v) }

    // ---- Living room (Vizio + onn) ----
    fun tvPower() = act("Vizio TV", config.value.vizioReady) { controller.tvPowerToggle() }
    fun volUp() = act("Vizio TV", config.value.vizioReady) { controller.tvVolumeUp() }
    fun volDown() = act("Vizio TV", config.value.vizioReady) { controller.tvVolumeDown() }
    fun mute() = act("Vizio TV", config.value.vizioReady) { controller.tvMuteToggle() }
    fun cycleInput() = act("Vizio TV", config.value.vizioReady) { controller.tvCycleInput() }

    fun dpadUp() = onn(RemoteKeyCode.KEYCODE_DPAD_UP)
    fun dpadDown() = onn(RemoteKeyCode.KEYCODE_DPAD_DOWN)
    fun dpadLeft() = onn(RemoteKeyCode.KEYCODE_DPAD_LEFT)
    fun dpadRight() = onn(RemoteKeyCode.KEYCODE_DPAD_RIGHT)
    fun ok() = onn(RemoteKeyCode.KEYCODE_DPAD_CENTER)
    fun back() = onn(RemoteKeyCode.KEYCODE_BACK)
    fun home() = onn(RemoteKeyCode.KEYCODE_HOME)
    fun menu() = onn(RemoteKeyCode.KEYCODE_MENU)
    fun playPause() = onn(RemoteKeyCode.KEYCODE_MEDIA_PLAY_PAUSE)
    fun rewind() = onn(RemoteKeyCode.KEYCODE_MEDIA_REWIND)
    fun fastForward() = onn(RemoteKeyCode.KEYCODE_MEDIA_FAST_FORWARD)
    fun launchApp(shortcut: AppShortcut) = act("onn box", config.value.onnReady) { controller.onnLaunch(shortcut.appLink) }
    fun launchOnn(url: String) = act("onn box", config.value.onnReady) { controller.onnLaunch(url) }
    private fun onn(key: RemoteKeyCode) = act("onn box", config.value.onnReady) { controller.onnKey(key) }

    // ---- Bedroom (Samsung monitor; soundbar via monitor) ----
    fun bedPower() = bed(SamsungController.KEY_POWER)
    fun bedVolUp() = bed(SamsungController.KEY_VOLUP)
    fun bedVolDown() = bed(SamsungController.KEY_VOLDOWN)
    fun bedMute() = bed(SamsungController.KEY_MUTE)
    fun bedSource() = bed(SamsungController.KEY_SOURCE)
    fun bedUp() = bed(SamsungController.KEY_UP)
    fun bedDown() = bed(SamsungController.KEY_DOWN)
    fun bedLeft() = bed(SamsungController.KEY_LEFT)
    fun bedRight() = bed(SamsungController.KEY_RIGHT)
    fun bedOk() = bed(SamsungController.KEY_ENTER)
    fun bedBack() = bed(SamsungController.KEY_RETURN)
    fun bedHome() = bed(SamsungController.KEY_HOME)
    fun bedMenu() = bed(SamsungController.KEY_MENU)
    fun bedPlayPause() = bed(SamsungController.KEY_PLAY)
    fun bedRewind() = bed(SamsungController.KEY_REWIND)
    fun bedForward() = bed(SamsungController.KEY_FF)
    fun bedYouTube() = act("Samsung monitor", config.value.samsungReady) { controller.bedLaunch(SamsungController.APP_YOUTUBE) }
    fun bedNetflix() = act("Samsung monitor", config.value.samsungReady) { controller.bedLaunch(SamsungController.APP_NETFLIX) }
    fun bedSpotify() = act("Samsung monitor", config.value.samsungReady) { controller.bedLaunch(SamsungController.APP_SPOTIFY) }
    private fun bed(key: String) = act("Samsung monitor", config.value.samsungReady) { controller.bedKey(key) }

    fun sendQuery(text: String) = fire {
        if (config.value.room == "bedroom") {
            controller.bedText(text); controller.bedKey(SamsungController.KEY_ENTER)
        } else {
            controller.onnType(text); controller.onnKey(RemoteKeyCode.KEYCODE_ENTER)
        }
    }

    private fun fire(block: suspend () -> Unit) { viewModelScope.launch { runCatching { block() } } }

    // ---- action feedback (so a dead button explains itself) ----
    private val _toast = MutableStateFlow<String?>(null)
    val toast: StateFlow<String?> = _toast
    fun clearToast() { _toast.value = null }
    private fun act(device: String, ready: Boolean, block: suspend () -> Boolean) {
        if (!ready) { _toast.value = "$device isn't set up yet — open Setup to pair it."; return }
        viewModelScope.launch {
            val ok = runCatching { block() }.getOrDefault(false)
            _toast.value = if (ok) null else "Couldn't reach the $device. Make sure it's on and on the same Wi-Fi."
        }
    }

    // ---- onn pairing ----
    var onnPhase by mutableStateOf<PairPhase>(PairPhase.Idle)
        private set
    private var pendingOnnPairing: AndroidTvPairing? = null
    private var pendingOnnHost = ""

    fun startOnnPairing(host: String) {
        val h = host.trim()
        if (h.isEmpty()) { onnPhase = PairPhase.Error("Enter the onn box IP address"); return }
        onnPhase = PairPhase.Connecting
        pendingOnnHost = h
        viewModelScope.launch {
            runCatching {
                val pairing = controller.onnPairing(h)
                pairing.begin()
                pendingOnnPairing = pairing
            }.onSuccess {
                onnPhase = PairPhase.AwaitingCode("Enter the code shown on your TV")
            }.onFailure {
                onnPhase = PairPhase.Error(friendly(it, "Couldn't reach the onn box"))
            }
        }
    }

    fun confirmOnnCode(code: String) {
        val pairing = pendingOnnPairing ?: return
        onnPhase = PairPhase.Connecting
        viewModelScope.launch {
            runCatching { pairing.finish(code.trim()) }
                .onSuccess { ok ->
                    if (ok) {
                        controller.config.setOnn(pendingOnnHost, paired = true)
                        onnPhase = PairPhase.Success
                    } else {
                        onnPhase = PairPhase.Error("That code didn't match. Try again.")
                    }
                }
                .onFailure { onnPhase = PairPhase.Error(friendly(it, "Pairing failed")) }
            pendingOnnPairing = null
        }
    }

    fun resetOnnPhase() { onnPhase = PairPhase.Idle }

    // ---- Vizio pairing ----
    var vizioPhase by mutableStateOf<PairPhase>(PairPhase.Idle)
        private set
    private var vizioDeviceId = ""
    private var vizioToken = 0
    private var pendingVizioHost = ""
    private var pendingVizioPort = 7345

    fun startVizioPairing(host: String, port: Int) {
        val h = host.trim()
        if (h.isEmpty()) { vizioPhase = PairPhase.Error("Enter the TV IP address"); return }
        vizioPhase = PairPhase.Connecting
        pendingVizioHost = h
        pendingVizioPort = port
        vizioDeviceId = UUID.randomUUID().toString().take(12)
        viewModelScope.launch {
            runCatching {
                val client = controller.vizioClient(h, port)
                vizioToken = client.startPairing("Curb Remote", vizioDeviceId)
            }.onSuccess {
                vizioPhase = PairPhase.AwaitingCode("Enter the PIN shown on your TV")
            }.onFailure {
                vizioPhase = PairPhase.Error(friendly(it, "Couldn't reach the TV"))
            }
        }
    }

    fun confirmVizioPin(pin: String) {
        vizioPhase = PairPhase.Connecting
        viewModelScope.launch {
            runCatching {
                val client = controller.vizioClient(pendingVizioHost, pendingVizioPort)
                client.completePairing(vizioDeviceId, pin.trim(), vizioToken)
            }.onSuccess { token ->
                controller.config.setVizio(pendingVizioHost, pendingVizioPort, token)
                vizioPhase = PairPhase.Success
            }.onFailure {
                vizioPhase = PairPhase.Error(friendly(it, "Wrong PIN or pairing failed"))
            }
        }
    }

    fun resetVizioPhase() { vizioPhase = PairPhase.Idle }

    // ---- Samsung pairing (one step: Allow on the monitor) ----
    var samsungPhase by mutableStateOf<PairPhase>(PairPhase.Idle)
        private set
    private var pendingSamsungHost = ""

    fun startSamsungPairing(host: String) {
        val h = host.trim()
        if (h.isEmpty()) { samsungPhase = PairPhase.Error("Enter the monitor IP address"); return }
        pendingSamsungHost = h
        samsungPhase = PairPhase.AwaitingCode("Tap Allow on your monitor, then wait a moment…")
        viewModelScope.launch {
            runCatching {
                controller.samsungPairing(h).pairAndGetToken()
            }.onSuccess { token ->
                if (!token.isNullOrBlank()) {
                    controller.config.setSamsung(h, token)
                    samsungPhase = PairPhase.Success
                } else {
                    samsungPhase = PairPhase.Error("No token received — tap Allow on the monitor and retry")
                }
            }.onFailure {
                samsungPhase = PairPhase.Error(friendly(it, "Couldn't reach the monitor"))
            }
        }
    }

    fun resetSamsungPhase() { samsungPhase = PairPhase.Idle }


    // ---- Hubspace lights ----
    var hubspacePhase by mutableStateOf<PairPhase>(PairPhase.Idle)
        private set
    private val _lights = MutableStateFlow<List<HubspaceLight>>(emptyList())
    val lights: StateFlow<List<HubspaceLight>> = _lights
    var lightsLoading by mutableStateOf(false)
        private set
    var lightsDiag by mutableStateOf("")
        private set

    fun loginHubspace(email: String, password: String) {
        hubspacePhase = PairPhase.Connecting
        viewModelScope.launch {
            val err = controller.hubspaceLogin(email.trim(), password)
            if (err == null) { hubspacePhase = PairPhase.Success; refreshLights() }
            else hubspacePhase = PairPhase.Error(err)
        }
    }
    fun resetHubspacePhase() { hubspacePhase = PairPhase.Idle }
    fun refreshLights() {
        lightsLoading = true
        viewModelScope.launch {
            _lights.value = controller.lights()
            lightsDiag = controller.hubspaceDiagnostic()
            lightsLoading = false
        }
    }
    fun toggleLight(id: String, on: Boolean) = viewModelScope.launch {
        controller.setLightPower(id, on)
        _lights.value = _lights.value.map { if (it.id == id) it.copy(on = on) else it }
    }
    fun setBrightness(id: String, pct: Int) = viewModelScope.launch {
        controller.setLightBrightness(id, pct)
        _lights.value = _lights.value.map { if (it.id == id) it.copy(brightness = pct) else it }
    }

    // ---- room light selection + scene modes ----
    var editingLights by mutableStateOf(false)
        private set
    fun toggleEditLights() { editingLights = !editingLights }
    fun setLightSelected(id: String, selected: Boolean) = viewModelScope.launch {
        val cfg = controller.config.get()
        val set = cfg.lightsFor(cfg.room).toMutableSet()
        if (selected) set.add(id) else set.remove(id)
        controller.config.setRoomLights(cfg.room, set)
    }
    fun runScene(scene: Scene) = viewModelScope.launch {
        val cfg = controller.config.get()
        val selected = cfg.lightsFor(cfg.room)
        val targets = _lights.value.filter { selected.isEmpty() || it.id in selected }
        targets.forEach { controller.setLight(it.id, scene.on, scene.brightness, scene.kelvin) }
        _lights.value = _lights.value.map { l ->
            if (targets.any { it.id == l.id }) l.copy(on = scene.on, brightness = scene.brightness) else l
        }
        if (scene.mood == "spotify") {
            if (cfg.room == "bedroom") controller.bedLaunch(SamsungController.APP_SPOTIFY)
            else controller.onnLaunch("market://launch?id=com.spotify.tv.android")
        }
    }

    // ---- IPTV / TV Guide ----
    private val _channels = MutableStateFlow<List<IptvChannel>>(emptyList())
    val channels: StateFlow<List<IptvChannel>> = _channels
    var iptvLoading by mutableStateOf(false)
        private set
    var watchOnTv by mutableStateOf(false)
        private set
    fun updateWatchOnTv(v: Boolean) { watchOnTv = v }
    fun playOnTv(ch: IptvChannel) = fire { controller.playOnTv(ch.streamUrl, ch.name) }

    // ---- one-tap sideload of Curb TV over ADB ----
    var installing by mutableStateOf(false)
        private set
    var installStatus by mutableStateOf("")
        private set
    fun installCurbTv(host: String) {
        installing = true
        installStatus = "Starting…"
        viewModelScope.launch {
            AdbInstaller.install(getApplication(), host) { msg -> installStatus = msg }
            installing = false
        }
    }
    var guideQuery by mutableStateOf("")
        private set
    fun updateGuideQuery(q: String) { guideQuery = q }
    fun loadIptv() {
        iptvLoading = true
        viewModelScope.launch {
            _channels.value = controller.iptvChannels()
            iptvLoading = false
            controller.iptvLoadEpg()
            _channels.value = _channels.value.toList()
        }
    }
    fun saveIptv(type: String, server: String, user: String, pass: String, m3u: String, epg: String) {
        viewModelScope.launch {
            controller.config.setIptv(type, server, user, pass, m3u, epg)
            loadIptv()
        }
    }
    fun nowNext(ch: IptvChannel) = controller.iptvNowNext(ch.epgId)

    private fun friendly(t: Throwable, fallback: String): String =
        t.message?.takeIf { it.isNotBlank() } ?: fallback
}
