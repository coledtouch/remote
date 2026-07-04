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
import androidx.compose.material.icons.rounded.Menu
import androidx.compose.material.icons.rounded.PlayArrow
import androidx.compose.material.icons.rounded.PowerSettingsNew
import androidx.compose.material.icons.rounded.Settings
import androidx.compose.material.icons.rounded.VolumeDown
import androidx.compose.material.icons.rounded.VolumeOff
import androidx.compose.material.icons.rounded.VolumeUp
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.curbscript.tvremote.data.AppShortcut
import com.curbscript.tvremote.data.Config
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
                IconKey(Icons.Rounded.Settings, onOpenSettings, size = 46.dp,
                    background = RemoteColors.surface, tint = RemoteColors.muted, iconSize = 22.dp)
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
            Spacer(Modifier.height(22.dp))
            if (bedroom) BedroomRemote(vm, cfg.navTrackpad) else LivingRemote(vm, cfg.navTrackpad)
            Spacer(Modifier.height(40.dp))
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
