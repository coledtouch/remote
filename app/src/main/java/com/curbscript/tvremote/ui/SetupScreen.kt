package com.curbscript.tvremote.ui

import androidx.compose.foundation.background
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
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.rounded.CheckCircle
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.curbscript.tvremote.data.Config
import com.curbscript.tvremote.ui.components.IconKey

@Composable
fun SetupScreen(
    vm: RemoteViewModel,
    cfg: Config,
    canClose: Boolean,
    onClose: () -> Unit
) {
    Box(Modifier.fillMaxSize().background(RemoteColors.bg)) {
        Column(
            Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 22.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Spacer(Modifier.height(28.dp))
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                if (canClose) {
                    IconKey(
                        Icons.AutoMirrored.Rounded.ArrowBack, onClose,
                        size = 44.dp, background = RemoteColors.surface, tint = RemoteColors.onSurface,
                        iconSize = 20.dp
                    )
                    Spacer(Modifier.width(14.dp))
                }
                Column {
                    Text("Set up devices", color = RemoteColors.onSurface,
                        fontSize = 22.sp, fontWeight = FontWeight.Bold)
                    Text("Pair over your Wi-Fi network", color = RemoteColors.muted, fontSize = 13.sp)
                }
            }

            Spacer(Modifier.height(22.dp))

            PairingCard(
                title = "onn 4K Pro",
                subtitle = "Google TV · navigation, apps, playback",
                ready = cfg.onnReady,
                initialHost = cfg.onnHost,
                phase = vm.onnPhase,
                codeHint = "Code shown on TV (6 characters)",
                showPort = false,
                initialPort = 6467,
                onPair = { host, _ -> vm.startOnnPairing(host) },
                onConfirm = { vm.confirmOnnCode(it) },
                onReset = { vm.resetOnnPhase() }
            )

            Spacer(Modifier.height(18.dp))

            PairingCard(
                title = "Vizio VQM65C-10",
                subtitle = "SmartCast · power, volume, mute, input",
                ready = cfg.vizioReady,
                initialHost = cfg.vizioHost,
                phase = vm.vizioPhase,
                codeHint = "PIN shown on TV",
                showPort = true,
                initialPort = if (cfg.vizioPort != 0) cfg.vizioPort else 7345,
                onPair = { host, port -> vm.startVizioPairing(host, port) },
                onConfirm = { vm.confirmVizioPin(it) },
                onReset = { vm.resetVizioPhase() }
            )

            Spacer(Modifier.height(22.dp))
            Text(
                "Tip: find each device's IP in its network settings. The onn box is under " +
                    "Settings › Network & Internet; the Vizio under Menu › Network. " +
                    "Vizio firmware 4.0+ uses port 7345 (older uses 9000).",
                color = RemoteColors.muted, fontSize = 12.sp,
                modifier = Modifier.fillMaxWidth().padding(bottom = 40.dp)
            )
        }
    }
}

@Composable
private fun PairingCard(
    title: String,
    subtitle: String,
    ready: Boolean,
    initialHost: String,
    phase: PairPhase,
    codeHint: String,
    showPort: Boolean,
    initialPort: Int,
    onPair: (host: String, port: Int) -> Unit,
    onConfirm: (String) -> Unit,
    onReset: () -> Unit
) {
    var host by rememberSaveable { mutableStateOf(initialHost) }
    var port by rememberSaveable { mutableStateOf(initialPort.toString()) }
    var code by rememberSaveable { mutableStateOf("") }

    Card(
        colors = CardDefaults.cardColors(containerColor = RemoteColors.surface),
        shape = RoundedCornerShape(20.dp),
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(Modifier.padding(18.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    Text(title, color = RemoteColors.onSurface, fontSize = 17.sp, fontWeight = FontWeight.Bold)
                    Text(subtitle, color = RemoteColors.muted, fontSize = 12.sp)
                }
                if (ready) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(Icons.Rounded.CheckCircle, null, tint = RemoteColors.good,
                            modifier = Modifier.size(18.dp))
                        Spacer(Modifier.width(6.dp))
                        Text("Connected", color = RemoteColors.good, fontSize = 13.sp)
                    }
                }
            }

            Spacer(Modifier.height(14.dp))

            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                OutlinedTextField(
                    value = host,
                    onValueChange = { host = it },
                    label = { Text("IP address") },
                    placeholder = { Text("192.168.1.50") },
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                    colors = fieldColors(),
                    modifier = Modifier.weight(1f)
                )
                if (showPort) {
                    OutlinedTextField(
                        value = port,
                        onValueChange = { port = it.filter { c -> c.isDigit() } },
                        label = { Text("Port") },
                        singleLine = true,
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                        colors = fieldColors(),
                        modifier = Modifier.width(96.dp)
                    )
                }
            }

            Spacer(Modifier.height(14.dp))

            when (phase) {
                is PairPhase.Connecting -> Row(verticalAlignment = Alignment.CenterVertically) {
                    CircularProgressIndicator(
                        color = RemoteColors.accent, strokeWidth = 2.dp,
                        modifier = Modifier.size(20.dp)
                    )
                    Spacer(Modifier.width(12.dp))
                    Text("Working…", color = RemoteColors.muted, fontSize = 14.sp)
                }

                is PairPhase.AwaitingCode -> {
                    Text(phase.message, color = RemoteColors.onSurface, fontSize = 13.sp)
                    Spacer(Modifier.height(10.dp))
                    OutlinedTextField(
                        value = code,
                        onValueChange = { code = it },
                        label = { Text(codeHint) },
                        singleLine = true,
                        colors = fieldColors(),
                        modifier = Modifier.fillMaxWidth()
                    )
                    Spacer(Modifier.height(12.dp))
                    Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                        Button(
                            onClick = { onConfirm(code) },
                            colors = ButtonDefaults.buttonColors(containerColor = RemoteColors.accent),
                            shape = RoundedCornerShape(14.dp),
                            modifier = Modifier.weight(1f)
                        ) { Text("Confirm & pair") }
                        TextButton(onClick = onReset) {
                            Text("Cancel", color = RemoteColors.muted)
                        }
                    }
                }

                is PairPhase.Success -> Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Rounded.CheckCircle, null, tint = RemoteColors.good,
                        modifier = Modifier.size(20.dp))
                    Spacer(Modifier.width(10.dp))
                    Text("Paired successfully", color = RemoteColors.good, fontSize = 14.sp)
                }

                else -> {
                    if (phase is PairPhase.Error) {
                        Text(phase.message, color = RemoteColors.power, fontSize = 13.sp)
                        Spacer(Modifier.height(10.dp))
                    }
                    Button(
                        onClick = { onPair(host, port.toIntOrNull() ?: initialPort) },
                        colors = ButtonDefaults.buttonColors(containerColor = RemoteColors.accent),
                        shape = RoundedCornerShape(14.dp),
                        modifier = Modifier.fillMaxWidth()
                    ) { Text(if (ready) "Re-pair" else "Pair") }
                }
            }
        }
    }
}

@Composable
private fun fieldColors() = OutlinedTextFieldDefaults.colors(
    focusedBorderColor = RemoteColors.accent,
    unfocusedBorderColor = RemoteColors.border,
    focusedTextColor = RemoteColors.onSurface,
    unfocusedTextColor = RemoteColors.onSurface,
    cursorColor = RemoteColors.accent,
    focusedLabelColor = RemoteColors.accent,
    unfocusedLabelColor = RemoteColors.muted
)
