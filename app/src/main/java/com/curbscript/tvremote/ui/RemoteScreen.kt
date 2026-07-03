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
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
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
import androidx.compose.material.icons.rounded.SkipNext
import androidx.compose.material.icons.rounded.SkipPrevious
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
import com.curbscript.tvremote.data.Config
import com.curbscript.tvremote.data.DEFAULT_SHORTCUTS
import com.curbscript.tvremote.ui.components.AppTile
import com.curbscript.tvremote.ui.components.DPad
import com.curbscript.tvremote.ui.components.IconKey
import com.curbscript.tvremote.ui.components.KeyButton
import com.curbscript.tvremote.ui.components.LabeledIconKey
import com.curbscript.tvremote.ui.components.SectionLabel
import com.curbscript.tvremote.ui.components.StatusChip

@Composable
fun RemoteScreen(vm: RemoteViewModel, cfg: Config, onOpenSettings: () -> Unit) {
    Box(Modifier.fillMaxSize().background(RemoteColors.bg)) {
        Column(
            Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 22.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Spacer(Modifier.height(28.dp))

            // Header
            Row(
                Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column {
                    Text("Curb Remote", color = RemoteColors.onSurface,
                        fontSize = 22.sp, fontWeight = FontWeight.Bold)
                    Text("onn 4K Pro  ·  Vizio", color = RemoteColors.muted, fontSize = 13.sp)
                }
                IconKey(
                    Icons.Rounded.Settings, onOpenSettings,
                    size = 46.dp, background = RemoteColors.surface, tint = RemoteColors.muted,
                    iconSize = 22.dp
                )
            }

            Spacer(Modifier.height(16.dp))

            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                StatusChip("onn", cfg.onnReady)
                StatusChip("Vizio", cfg.vizioReady)
            }

            Spacer(Modifier.height(26.dp))

            // Power / Mute / Input (Vizio TV)
            Row(horizontalArrangement = Arrangement.spacedBy(30.dp)) {
                LabeledIconKey(
                    Icons.Rounded.PowerSettingsNew, "Power", vm::tvPower,
                    background = RemoteColors.power, tint = Color.White
                )
                LabeledIconKey(Icons.Rounded.VolumeOff, "Mute", vm::mute)
                LabeledIconKey(Icons.Rounded.Input, "Input", vm::cycleInput)
            }

            Spacer(Modifier.height(28.dp))

            DPad(
                onUp = vm::dpadUp, onDown = vm::dpadDown,
                onLeft = vm::dpadLeft, onRight = vm::dpadRight, onOk = vm::ok
            )

            Spacer(Modifier.height(22.dp))

            // Back / Home / Menu (onn)
            Row(horizontalArrangement = Arrangement.spacedBy(30.dp)) {
                LabeledIconKey(Icons.AutoMirrored.Rounded.ArrowBack, "Back", vm::back)
                LabeledIconKey(Icons.Rounded.Home, "Home", vm::home)
                LabeledIconKey(Icons.Rounded.Menu, "Menu", vm::menu)
            }

            Spacer(Modifier.height(28.dp))

            // Volume rocker (Vizio TV)
            SectionLabel("Volume", Modifier.align(Alignment.Start))
            Spacer(Modifier.height(10.dp))
            VolumeRocker(onDown = vm::volDown, onUp = vm::volUp)

            Spacer(Modifier.height(28.dp))

            // Playback (onn)
            SectionLabel("Playback", Modifier.align(Alignment.Start))
            Spacer(Modifier.height(10.dp))
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(14.dp)
            ) {
                IconKey(Icons.Rounded.SkipPrevious, vm::previous, size = 54.dp)
                IconKey(Icons.Rounded.FastRewind, vm::rewind, size = 54.dp)
                KeyButton(vm::playPause, size = 72.dp, background = RemoteColors.accent) {
                    Icon(Icons.Rounded.PlayArrow, null, tint = Color.White, modifier = Modifier.size(34.dp))
                }
                IconKey(Icons.Rounded.FastForward, vm::fastForward, size = 54.dp)
                IconKey(Icons.Rounded.SkipNext, vm::next, size = 54.dp)
            }

            Spacer(Modifier.height(30.dp))

            // App shortcuts (onn)
            SectionLabel("Apps", Modifier.align(Alignment.Start))
            Spacer(Modifier.height(12.dp))
            Column(verticalArrangement = Arrangement.spacedBy(16.dp)) {
                DEFAULT_SHORTCUTS.chunked(4).forEach { rowApps ->
                    Row(horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                        rowApps.forEach { app ->
                            AppTile(app, { vm.launchApp(app) })
                        }
                    }
                }
            }

            Spacer(Modifier.height(36.dp))
        }
    }
}

@Composable
private fun VolumeRocker(onDown: () -> Unit, onUp: () -> Unit) {
    Row(
        Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(40.dp))
            .background(RemoteColors.surface)
            .border(1.dp, RemoteColors.border, RoundedCornerShape(40.dp))
            .padding(8.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        IconKey(Icons.Rounded.VolumeDown, onDown, size = 56.dp, background = RemoteColors.surfaceHi)
        Text("VOLUME", color = RemoteColors.muted, fontSize = 13.sp, fontWeight = FontWeight.SemiBold)
        IconKey(Icons.Rounded.VolumeUp, onUp, size = 56.dp, background = RemoteColors.surfaceHi)
        Spacer(Modifier.width(0.dp))
    }
}
