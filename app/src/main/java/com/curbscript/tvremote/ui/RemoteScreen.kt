package com.curbscript.tvremote.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.rounded.FastForward
import androidx.compose.material.icons.rounded.FastRewind
import androidx.compose.material.icons.rounded.Home
import androidx.compose.material.icons.rounded.Input
import androidx.compose.material.icons.rounded.Keyboard
import androidx.compose.material.icons.rounded.Menu
import androidx.compose.material.icons.rounded.PlayArrow
import androidx.compose.material.icons.rounded.PowerSettingsNew
import androidx.compose.material.icons.rounded.Refresh
import androidx.compose.material.icons.rounded.Settings
import androidx.compose.material.icons.rounded.VolumeDown
import androidx.compose.material.icons.rounded.VolumeOff
import androidx.compose.material.icons.rounded.VolumeUp
import androidx.compose.material3.Icon
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.curbscript.tvremote.data.AppShortcut
import com.curbscript.tvremote.data.Config
import com.curbscript.tvremote.hubspace.HubspaceLight
import com.curbscript.tvremote.ui.components.AppTile
import com.curbscript.tvremote.ui.components.DPad
import com.curbscript.tvremote.ui.components.IconKey
import com.curbscript.tvremote.ui.components.KeyButton
import com.curbscript.tvremote.ui.components.LabeledIconKey
import com.curbscript.tvremote.ui.components.SectionLabel
import com.curbscript.tvremote.ui.components.SegmentedToggle
import com.curbscript.tvremote.ui.components.StatusChip
import com.curbscript.tvremote.ui.components.Trackpad

private val livingApps = listOf(
    AppShortcut("TiviMate", "market://launch?id=ar.tvplayer.tv", Color(0xFF15B4C4), "T"),
    AppShortcut("YouTube", "market://launch?id=com.google.android.youtube.tv", Color(0xFFFF0000), "▶"),
    AppShortcut("Netflix", "market://launch?id=com.netflix.ninja", Color(0xFFE50914), "N"),
    AppShortcut("Spotify", "market://launch?id=com.spotify.tv.android", Color(0xFF1DB954), "♪")
)

@Composable
fun RemoteScreen(vm: RemoteViewModel, cfg: Config, onOpenSettings: () -> Unit) {
    val bedroom = cfg.room == "bedroom"
    val imeActive by vm.imeActive.collectAsState()
    var showKb by remember { mutableStateOf(false) }
    LaunchedEffect(imeActive) { if (imeActive) showKb = true }
    LaunchedEffect(cfg.hubspaceReady) { if (cfg.hubspaceReady) vm.refreshLights() }
    Box(Modifier.fillMaxSize().background(RemoteColors.bg)) {
        Box(Modifier.fillMaxWidth().height(240.dp).background(RemoteColors.glow))
        Column(
            Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(horizontal = 20.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Spacer(Modifier.height(28.dp))
            Row(Modifier.fillMaxWidth(), Arrangement.SpaceBetween, Alignment.CenterVertically) {
                Column {
                    Text("Curb Remote", color = RemoteColors.onSurface, fontSize = 24.sp, fontWeight = FontWeight.Bold)
                    Text(
                        if (bedroom) "Samsung monitor  ·  soundbar" else "onn 4K Pro  ·  Vizio",
                        color = RemoteColors.muted, fontSize = 13.sp
                    )
                }
                Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    IconKey(Icons.Rounded.Keyboard, { showKb = true }, size = 46.dp,
                        background = RemoteColors.surface, tint = RemoteColors.muted, iconSize = 22.dp)
                    IconKey(Icons.Rounded.Settings, onOpenSettings, size = 46.dp,
                        background = RemoteColors.surface, tint = RemoteColors.muted, iconSize = 22.dp)
                }
            }
            Spacer(Modifier.height(16.dp))
            SegmentedToggle(
                listOf("Living Room", "Bedroom"),
                if (bedroom) 1 else 0,
                { vm.setRoom(if (it == 1) "bedroom" else "living") }
            )
            Spacer(Modifier.height(14.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                if (bedroom) {
                    StatusChip("Samsung", cfg.samsungReady)
                } else {
                    StatusChip("Vizio", cfg.vizioReady)
                    StatusChip("onn", cfg.onnReady)
                }
            }
            Spacer(Modifier.height(6.dp))
            LightsSection(vm, cfg.room, cfg.hubspaceReady)
            Spacer(Modifier.height(10.dp))
            if (bedroom) BedroomRemote(vm, cfg.navTrackpad) else LivingRemote(vm, cfg.navTrackpad)
            Spacer(Modifier.height(40.dp))
        }
        if (showKb) KeyboardBar(
            Modifier.align(Alignment.BottomCenter),
            onSend = { vm.sendQuery(it) },
            onClose = { showKb = false }
        )
    }
}

@Composable
private fun KeyboardBar(modifier: Modifier, onSend: (String) -> Unit, onClose: () -> Unit) {
    var text by remember { mutableStateOf("") }
    Column(modifier.fillMaxWidth().background(RemoteColors.bgElevated).padding(16.dp)) {
        Text("Type for the TV", color = RemoteColors.muted, fontSize = 12.sp)
        Spacer(Modifier.height(8.dp))
        OutlinedTextField(
            value = text, onValueChange = { text = it }, singleLine = true,
            modifier = Modifier.fillMaxWidth(),
            colors = OutlinedTextFieldDefaults.colors(
                focusedBorderColor = RemoteColors.coral,
                unfocusedBorderColor = RemoteColors.border,
                focusedTextColor = RemoteColors.onSurface,
                unfocusedTextColor = RemoteColors.onSurface,
                cursorColor = RemoteColors.coral
            )
        )
        Spacer(Modifier.height(10.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            Button(
                onClick = { if (text.isNotEmpty()) { onSend(text); text = "" } },
                colors = ButtonDefaults.buttonColors(containerColor = RemoteColors.coral),
                shape = RoundedCornerShape(14.dp),
                modifier = Modifier.weight(1f)
            ) { Text("Send") }
            TextButton(onClick = onClose) { Text("Close", color = RemoteColors.muted) }
        }
    }
}

@Composable
private fun LivingRemote(vm: RemoteViewModel, trackpad: Boolean) {
    Row(horizontalArrangement = Arrangement.spacedBy(30.dp)) {
        LabeledIconKey(Icons.Rounded.PowerSettingsNew, "Power", vm::tvPower,
            background = RemoteColors.power, tint = Color.White)
        LabeledIconKey(Icons.Rounded.VolumeOff, "Mute", vm::mute)
        LabeledIconKey(Icons.Rounded.Input, "Input", vm::cycleInput)
    }
    Spacer(Modifier.height(24.dp))
    NavBlock(trackpad, vm::setTrackpad, vm::dpadUp, vm::dpadDown, vm::dpadLeft, vm::dpadRight, vm::ok)
    Spacer(Modifier.height(20.dp))
    Row(horizontalArrangement = Arrangement.spacedBy(30.dp)) {
        LabeledIconKey(Icons.AutoMirrored.Rounded.ArrowBack, "Back", vm::back)
        LabeledIconKey(Icons.Rounded.Home, "Home", vm::home)
        LabeledIconKey(Icons.Rounded.Menu, "Menu", vm::menu)
    }
    Spacer(Modifier.height(26.dp))
    Row(Modifier.fillMaxWidth()) { SectionLabel("Volume") }
    Spacer(Modifier.height(10.dp))
    VolumeRocker(vm::volDown, vm::volUp)
    Spacer(Modifier.height(26.dp))
    Row(Modifier.fillMaxWidth()) { SectionLabel("Playback") }
    Spacer(Modifier.height(10.dp))
    PlaybackRow(vm::rewind, vm::playPause, vm::fastForward)
    Spacer(Modifier.height(30.dp))
    Row(Modifier.fillMaxWidth()) { SectionLabel("Apps") }
    Spacer(Modifier.height(12.dp))
    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceEvenly) {
        livingApps.forEach { app -> AppTile(app, { vm.launchOnn(app.appLink) }) }
    }
}

@Composable
private fun BedroomRemote(vm: RemoteViewModel, trackpad: Boolean) {
    Row(horizontalArrangement = Arrangement.spacedBy(30.dp)) {
        LabeledIconKey(Icons.Rounded.PowerSettingsNew, "Power", vm::bedPower,
            background = RemoteColors.power, tint = Color.White)
        LabeledIconKey(Icons.Rounded.VolumeOff, "Mute", vm::bedMute)
        LabeledIconKey(Icons.Rounded.Input, "Source", vm::bedSource)
    }
    Spacer(Modifier.height(24.dp))
    NavBlock(trackpad, vm::setTrackpad, vm::bedUp, vm::bedDown, vm::bedLeft, vm::bedRight, vm::bedOk)
    Spacer(Modifier.height(20.dp))
    Row(horizontalArrangement = Arrangement.spacedBy(30.dp)) {
        LabeledIconKey(Icons.AutoMirrored.Rounded.ArrowBack, "Back", vm::bedBack)
        LabeledIconKey(Icons.Rounded.Home, "Home", vm::bedHome)
        LabeledIconKey(Icons.Rounded.Menu, "Menu", vm::bedMenu)
    }
    Spacer(Modifier.height(26.dp))
    Row(Modifier.fillMaxWidth()) { SectionLabel("Volume  ·  soundbar via monitor") }
    Spacer(Modifier.height(10.dp))
    VolumeRocker(vm::bedVolDown, vm::bedVolUp)
    Spacer(Modifier.height(26.dp))
    Row(Modifier.fillMaxWidth()) { SectionLabel("Playback") }
    Spacer(Modifier.height(10.dp))
    PlaybackRow(vm::bedRewind, vm::bedPlayPause, vm::bedForward)
    Spacer(Modifier.height(30.dp))
    Row(Modifier.fillMaxWidth()) { SectionLabel("Apps") }
    Spacer(Modifier.height(12.dp))
    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceEvenly) {
        AppTile(AppShortcut("YouTube", "", Color(0xFFFF0000), "▶"), { vm.bedYouTube() })
        AppTile(AppShortcut("Netflix", "", Color(0xFFE50914), "N"), { vm.bedNetflix() })
        AppTile(AppShortcut("Spotify", "", Color(0xFF1DB954), "♪"), { vm.bedSpotify() })
    }
}

@Composable
private fun NavBlock(
    trackpad: Boolean,
    onToggle: (Boolean) -> Unit,
    onUp: () -> Unit,
    onDown: () -> Unit,
    onLeft: () -> Unit,
    onRight: () -> Unit,
    onOk: () -> Unit
) {
    SegmentedToggle(listOf("D-pad", "Trackpad"), if (trackpad) 1 else 0, { onToggle(it == 1) })
    Spacer(Modifier.height(16.dp))
    if (trackpad) Trackpad(onUp, onDown, onLeft, onRight, onOk) else DPad(onUp, onDown, onLeft, onRight, onOk)
}

@Composable
private fun PlaybackRow(onRewind: () -> Unit, onPlayPause: () -> Unit, onForward: () -> Unit) {
    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(18.dp)) {
        IconKey(Icons.Rounded.FastRewind, onRewind, size = 56.dp)
        KeyButton(onPlayPause, size = 74.dp, brush = RemoteColors.accent, borderColor = Color.Transparent) {
            Icon(Icons.Rounded.PlayArrow, null, tint = Color.White, modifier = Modifier.size(34.dp))
        }
        IconKey(Icons.Rounded.FastForward, onForward, size = 56.dp)
    }
}

@Composable
private fun VolumeRocker(onDown: () -> Unit, onUp: () -> Unit) {
    Row(
        Modifier.fillMaxWidth().clip(RoundedCornerShape(40.dp)).background(RemoteColors.surface)
            .border(1.dp, RemoteColors.border, RoundedCornerShape(40.dp)).padding(8.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        IconKey(Icons.Rounded.VolumeDown, onDown, size = 56.dp, background = RemoteColors.surfaceHi)
        Text("VOLUME", color = RemoteColors.muted, fontSize = 13.sp, fontWeight = FontWeight.SemiBold)
        IconKey(Icons.Rounded.VolumeUp, onUp, size = 56.dp, background = RemoteColors.surfaceHi)
    }
}

@Composable
private fun LightsSection(vm: RemoteViewModel, room: String, hubspaceReady: Boolean) {
    if (!hubspaceReady) return
    val all by vm.lights.collectAsState()
    val matched = all.filter { it.room == room || it.room == "both" }
    val lights = if (matched.isEmpty() && all.isNotEmpty()) all else matched
    Row(Modifier.fillMaxWidth(), Arrangement.SpaceBetween, Alignment.CenterVertically) {
        SectionLabel("Lights")
        IconKey(Icons.Rounded.Refresh, { vm.refreshLights() }, size = 38.dp,
            background = RemoteColors.surface, tint = RemoteColors.muted, iconSize = 18.dp)
    }
    Spacer(Modifier.height(12.dp))
    when {
        vm.lightsLoading && lights.isEmpty() ->
            Text("Loading your bulbs…", color = RemoteColors.muted, fontSize = 13.sp)
        lights.isEmpty() ->
            Text("Signed in, but no bulbs came back yet. Tap refresh; if they still don't appear, tell me.",
                color = RemoteColors.muted, fontSize = 13.sp)
        else -> Column(Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            lights.forEach { light ->
                LightCard(light, { vm.toggleLight(light.id, it) }, { vm.setBrightness(light.id, it) })
            }
        }
    }
}

@Composable
private fun LightCard(light: HubspaceLight, onToggle: (Boolean) -> Unit, onBrightness: (Int) -> Unit) {
    var bri by remember(light.id) { mutableStateOf(light.brightness.toFloat()) }
    Column(
        Modifier.fillMaxWidth().clip(RoundedCornerShape(20.dp)).background(RemoteColors.surface)
            .border(1.dp, RemoteColors.border, RoundedCornerShape(20.dp)).padding(16.dp)
    ) {
        Row(Modifier.fillMaxWidth(), Arrangement.SpaceBetween, Alignment.CenterVertically) {
            Text(light.name, color = RemoteColors.onSurface, fontSize = 15.sp, fontWeight = FontWeight.Medium)
            Switch(
                checked = light.on, onCheckedChange = onToggle,
                colors = SwitchDefaults.colors(
                    checkedThumbColor = Color.White, checkedTrackColor = RemoteColors.coral,
                    uncheckedTrackColor = RemoteColors.surfaceHi
                )
            )
        }
        Slider(
            value = bri, onValueChange = { bri = it }, valueRange = 1f..100f,
            onValueChangeFinished = { onBrightness(bri.toInt()) },
            colors = SliderDefaults.colors(thumbColor = RemoteColors.coral, activeTrackColor = RemoteColors.amber)
        )
    }
}
