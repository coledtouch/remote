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
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.curbscript.tvremote.data.Config
import com.curbscript.tvremote.ui.components.IconKey

@Composable
fun SetupScreen(vm: RemoteViewModel, cfg: Config, canClose: Boolean, onClose: () -> Unit) {
    Box(Modifier.fillMaxSize().background(RemoteColors.bg)) {
        Box(Modifier.fillMaxWidth().height(220.dp).background(RemoteColors.glow))
        Column(
            Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(horizontal = 22.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Spacer(Modifier.height(28.dp))
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                if (canClose) {
                    IconKey(Icons.AutoMirrored.Rounded.ArrowBack, onClose, size = 44.dp,
                        background = RemoteColors.surface, tint = RemoteColors.onSurface, iconSize = 20.dp)
                    Spacer(Modifier.width(14.dp))
                }
                Column {
                    Text("Set up devices", color = RemoteColors.onSurface, fontSize = 24.sp, fontWeight = FontWeight.Bold)
                    Text("Pair over your Wi-Fi network", color = RemoteColors.muted, fontSize = 13.sp)
                }
            }

            Spacer(Modifier.height(22.dp))
            SectionHeader("Living Room")
            PairingCard(
                title = "Vizio VQM65C-10", subtitle = "SmartCast · power, volume, mute, input",
                ready = cfg.vizioReady, initialHost = cfg.vizioHost, phase = vm.vizioPhase,
                codeHint = "PIN shown on TV", showPort = true,
                initialPort = if (cfg.vizioPort != 0) cfg.vizioPort else 7345, requiresCode = true,
                onPair = { host, port -> vm.startVizioPairing(host, port) },
                onConfirm = { vm.confirmVizioPin(it) }, onReset = { vm.resetVizioPhase() }
            )
            Spacer(Modifier.height(14.dp))
            PairingCard(
                title = "onn 4K Pro", subtitle = "Google TV · navigation, apps, playback",
                ready = cfg.onnReady, initialHost = cfg.onnHost, phase = vm.onnPhase,
                codeHint = "Code shown on TV (6 characters)", showPort = false,
                initialPort = 6467, requiresCode = true,
                onPair = { host, _ -> vm.startOnnPairing(host) },
                onConfirm = { vm.confirmOnnCode(it) }, onReset = { vm.resetOnnPhase() }
            )

            Spacer(Modifier.height(22.dp))
            SectionHeader("Bedroom")
            PairingCard(
                title = "Samsung S32DM702UN", subtitle = "Monitor · soundbar volume via monitor",
                ready = cfg.samsungReady, initialHost = cfg.samsungHost, phase = vm.samsungPhase,
                codeHint = "", showPort = false, initialPort = 8002, requiresCode = false,
                onPair = { host, _ -> vm.startSamsungPairing(host) },
                onConfirm = { }, onReset = { vm.resetSamsungPhase() }
            )

            Spacer(Modifier.height(22.dp))
            Text(
                "Find each device's IP in its network settings. Samsung: Menu › Settings › General › " +
                    "Network › Network Status. When you tap Pair on the Samsung, an Allow prompt appears on " +
                    "the monitor — accept it. Vizio firmware 4.0+ uses port 7345 (older uses 9000).",
                color = RemoteColors.muted, fontSize = 12.sp,
                modifier = Modifier.fillMaxWidth().padding(bottom = 40.dp)
            )
        }
    }
}

@Composable
private fun SectionHeader(text: String) {
    Row(Modifier.fillMaxWidth().padding(bottom = 10.dp)) {
        Text(text.uppercase(), color = RemoteColors.amber, fontSize = 12.sp,
            fontWeight = FontWeight.Bold, letterSpacing = 1.8.sp)
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
    requiresCode: Boolean,
    onPair: (host: String, port: Int) -> Unit,
    onConfirm: (String) -> Unit,
    onReset: () -> Unit
) {
    var host by rememberSaveable(title) { mutableStateOf(initialHost) }
    var port by rememberSaveable(title) { mutableStateOf(initialPort.toString()) }
    var code by rememberSaveable(title) { mutableStateOf("") }

    Card(
        colors = CardDefaults.cardColors(containerColor = RemoteColors.surface),
        shape = RoundedCornerShape(22.dp),
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(Modifier.padding(18.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    Text(title, color = RemoteColors.onSurface, fontSize = 17.sp, fontWeight = FontWeight.Bold)
                    Text(subtitle, color = RemoteColors.muted, fontSize = 12.sp)
                }
                if (ready) {
                    Icon(Icons.Rounded.CheckCircle, null, tint = RemoteColors.good, modifier = Modifier.size(18.dp))
                    Spacer(Modifier.width(6.dp))
                    Text("Connected", color = RemoteColors.good, fontSize = 13.sp)
                }
            }
            Spacer(Modifier.height(14.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                OutlinedTextField(
                    value = host, onValueChange = { host = it },
                    label = { Text("IP address") }, placeholder = { Text("192.168.1.50") },
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                    colors = fieldColors(), modifier = Modifier.weight(1f)
                )
                if (showPort) {
                    OutlinedTextField(
                        value = port, onValueChange = { port = it.filter { c -> c.isDigit() } },
                        label = { Text("Port") }, singleLine = true,
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                        colors = fieldColors(), modifier = Modifier.width(96.dp)
                    )
                }
            }
            Spacer(Modifier.height(14.dp))
            when (phase) {
                is PairPhase.Connecting -> WaitingRow("Working…")
                is PairPhase.AwaitingCode -> {
                    Text(phase.message, color = RemoteColors.onSurface, fontSize = 13.sp)
                    Spacer(Modifier.height(10.dp))
                    if (requiresCode) {
                        OutlinedTextField(
                            value = code, onValueChange = { code = it },
                            label = { Text(codeHint) }, singleLine = true,
                            colors = fieldColors(), modifier = Modifier.fillMaxWidth()
                        )
                        Spacer(Modifier.height(12.dp))
                        Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                            Button(
                                onClick = { onConfirm(code) },
                                colors = ButtonDefaults.buttonColors(containerColor = RemoteColors.coral),
                                shape = RoundedCornerShape(14.dp), modifier = Modifier.weight(1f)
                            ) { Text("Confirm & pair") }
                            TextButton(onClick = onReset) { Text("Cancel", color = RemoteColors.muted) }
                        }
                    } else {
                        WaitingRow("Waiting for Allow…")
                        Spacer(Modifier.height(6.dp))
                        TextButton(onClick = onReset) { Text("Cancel", color = RemoteColors.muted) }
                    }
                }
                is PairPhase.Success -> Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Rounded.CheckCircle, null, tint = RemoteColors.good, modifier = Modifier.size(20.dp))
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
                        colors = ButtonDefaults.buttonColors(containerColor = RemoteColors.coral),
                        shape = RoundedCornerShape(14.dp), modifier = Modifier.fillMaxWidth()
                    ) { Text(if (ready) "Re-pair" else "Pair") }
                }
            }
        }
    }
}

@Composable
private fun WaitingRow(text: String) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        CircularProgressIndicator(color = RemoteColors.coral, strokeWidth = 2.dp, modifier = Modifier.size(20.dp))
        Spacer(Modifier.width(12.dp))
        Text(text, color = RemoteColors.muted, fontSize = 14.sp)
    }
}

@Composable
private fun fieldColors() = OutlinedTextFieldDefaults.colors(
    focusedBorderColor = RemoteColors.coral,
    unfocusedBorderColor = RemoteColors.border,
    focusedTextColor = RemoteColors.onSurface,
    unfocusedTextColor = RemoteColors.onSurface,
    cursorColor = RemoteColors.coral,
    focusedLabelColor = RemoteColors.coral,
    unfocusedLabelColor = RemoteColors.muted
)
