package com.curbscript.tvremote.adb

import android.content.Context
import dadb.AdbKeyPair
import dadb.Dadb
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File

/**
 * One-tap sideload of the companion Curb TV app over network ADB (port 5555),
 * using a pure-Kotlin ADB client — no computer required. The TV must have
 * USB/network debugging enabled; the first connect shows an "Allow debugging"
 * prompt to accept.
 *
 * We generate and persist our own ADB key inside the app's private storage.
 * (Without it, the library falls back to the desktop default `~/.android/adbkey`,
 * which doesn't exist on Android — the ENOENT error.)
 */
object AdbInstaller {
    const val ADB_PORT = 5555

    suspend fun install(context: Context, host: String, onStatus: (String) -> Unit): Boolean =
        withContext(Dispatchers.IO) {
            val h = host.trim()
            if (h.isEmpty()) { onStatus("Enter your TV's IP address first."); return@withContext false }

            val apk: File
            try {
                onStatus("Preparing the Curb TV app…")
                apk = File(context.cacheDir, "curbtv.apk")
                context.assets.open("curbtv.apk").use { input -> apk.outputStream().use { input.copyTo(it) } }
            } catch (e: Exception) {
                onStatus("Curb TV app isn't bundled in this build — grab tvapp-debug.apk from GitHub instead.")
                return@withContext false
            }

            val keyPair = try {
                val dir = File(context.filesDir, "adb").apply { mkdirs() }
                val priv = File(dir, "adbkey")
                val pub = File(dir, "adbkey.pub")
                if (!priv.exists() || !pub.exists()) AdbKeyPair.generate(priv, pub)
                AdbKeyPair.read(priv, pub)
            } catch (e: Exception) {
                onStatus("Couldn't create the ADB key: ${e.message ?: "unknown error"}")
                return@withContext false
            }

            var dadb: Dadb? = null
            return@withContext try {
                onStatus("Connecting to $h:$ADB_PORT — accept the debugging prompt on your TV…")
                dadb = Dadb.create(h, ADB_PORT, keyPair)
                onStatus("Installing Curb TV on your TV…")
                dadb.install(apk)
                onStatus("Installed! Open Curb TV on your TV, then use Watch on TV.")
                true
            } catch (e: Exception) {
                onStatus("Couldn't install: ${e.message ?: "no ADB on the TV. Enable USB/network debugging, then try again."}")
                false
            } finally {
                try { dadb?.close() } catch (_: Exception) {}
            }
        }
}
