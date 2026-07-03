package com.curbscript.tvremote

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.lifecycle.viewmodel.compose.viewModel
import com.curbscript.tvremote.ui.CurbRemoteTheme
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

                // Force setup until at least one device is configured.
                if (showSetup || !cfg.anyReady) {
                    SetupScreen(
                        vm = vm,
                        cfg = cfg,
                        canClose = cfg.anyReady,
                        onClose = { showSetup = false }
                    )
                } else {
                    RemoteScreen(vm = vm, cfg = cfg, onOpenSettings = { showSetup = true })
                }
            }
        }
    }
}
