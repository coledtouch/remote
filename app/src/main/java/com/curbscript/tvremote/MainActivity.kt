package com.curbscript.tvremote

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalContext
import androidx.lifecycle.viewmodel.compose.viewModel
import com.curbscript.tvremote.player.PlayerActivity
import com.curbscript.tvremote.ui.CurbRemoteTheme
import com.curbscript.tvremote.ui.GuideScreen
import com.curbscript.tvremote.ui.RemoteScreen
import com.curbscript.tvremote.ui.RemoteViewModel
import com.curbscript.tvremote.ui.SetupScreen

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            CurbRemoteTheme {
                val vm: RemoteViewModel = viewModel()
                val cfg by vm.config.collectAsState()
                var showSetup by rememberSaveable { mutableStateOf(false) }
                var screen by rememberSaveable { mutableStateOf("remote") }
                val ctx = LocalContext.current

                when {
                    showSetup || !cfg.anyReady -> SetupScreen(
                        vm = vm, cfg = cfg, canClose = cfg.anyReady, onClose = { showSetup = false }
                    )
                    screen == "guide" -> GuideScreen(
                        vm = vm, onBack = { screen = "remote" },
                        onPlay = { ch -> PlayerActivity.start(ctx, ch.streamUrl, ch.name) }
                    )
                    else -> RemoteScreen(
                        vm = vm, cfg = cfg,
                        onOpenSettings = { showSetup = true },
                        onOpenGuide = { screen = "guide" }
                    )
                }
            }
        }
    }
}
