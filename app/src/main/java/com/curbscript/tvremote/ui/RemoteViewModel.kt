package com.curbscript.tvremote.ui

import android.app.Application
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.curbscript.tvremote.control.Controller
import com.curbscript.tvremote.data.AppShortcut
import com.curbscript.tvremote.data.Config
import com.curbscript.tvremote.onn.AndroidTvPairing
import com.curbscript.tvremote.proto.RemoteKeyCode
import com.curbscript.tvremote.samsung.SamsungController
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

class RemoteViewModel(app: Application) : AndroidViewModel(app) {

    private val controller = Controller.get(app)

    val config: StateFlow<Config> =
        controller.config.flow.stateIn(viewModelScope, SharingStarted.Eagerly, Config())

    val imeActive: StateFlow<Boolean> = controller.imeActive

    // ---- room + nav preferences ----
    fun setRoom(room: String) = viewModelScope.launch { controller.config.setRoom(room) }
    fun setTrackpad(v: Boolean) = viewModelScope.launch { controller.config.setNavTrackpad(v) }

    // ---- Living room (Vizio + onn) ----
    fun tvPower() = fire { controller.tvPowerToggle() }
    fun volUp() = fire { controller.tvVolumeUp() }
    fun volDown() = fire { controller.tvVolumeDown() }
    fun mute() = fire { controller.tvMuteToggle() }
    fun cycleInput() = fire { controller.tvCycleInput() }

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
    fun launchApp(shortcut: AppShortcut) = fire { controller.onnLaunch(shortcut.appLink) }
    fun launchOnn(url: String) = fire { controller.onnLaunch(url) }
    private fun onn(key: RemoteKeyCode) = fire { controller.onnKey(key) }

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
    fun bedYouTube() = fire { controller.bedLaunch(SamsungController.APP_YOUTUBE) }
    fun bedNetflix() = fire { controller.bedLaunch(SamsungController.APP_NETFLIX) }
    fun bedSpotify() = fire { controller.bedLaunch(SamsungController.APP_SPOTIFY) }
    private fun bed(key: String) = fire { controller.bedKey(key) }

    fun sendQuery(text: String) = fire {
        if (config.value.room == "bedroom") {
            controller.bedText(text); controller.bedKey(SamsungController.KEY_ENTER)
        } else {
            controller.onnType(text); controller.onnKey(RemoteKeyCode.KEYCODE_ENTER)
        }
    }

    private fun fire(block: suspend () -> Unit) { viewModelScope.launch { runCatching { block() } } }

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

    private fun friendly(t: Throwable, fallback: String): String =
        t.message?.takeIf { it.isNotBlank() } ?: fallback
}
