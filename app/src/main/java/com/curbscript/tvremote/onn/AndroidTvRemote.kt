package com.curbscript.tvremote.onn

import com.curbscript.tvremote.proto.RemoteAppLinkLaunchRequest
import com.curbscript.tvremote.proto.RemoteConfigure
import com.curbscript.tvremote.proto.RemoteDeviceInfo
import com.curbscript.tvremote.proto.RemoteDirection
import com.curbscript.tvremote.proto.RemoteKeyCode
import com.curbscript.tvremote.proto.RemoteKeyInject
import com.curbscript.tvremote.proto.RemoteMessage
import com.curbscript.tvremote.proto.RemotePingResponse
import com.curbscript.tvremote.proto.RemoteSetActive
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import java.io.BufferedInputStream
import java.io.BufferedOutputStream
import java.io.IOException
import javax.net.ssl.SSLSocket
import javax.net.ssl.SSLSocketFactory

/**
 * Persistent Android TV Remote v2 session over port 6466.
 *
 * On [connect] it performs the two-step configuration handshake, then keeps the
 * TLS socket open with a background reader that answers the server's keep-alive
 * pings. Commands ([sendKey], [launchApp]) are written on demand, which keeps the
 * remote feeling instant instead of re-handshaking on every tap.
 */
class AndroidTvRemote(
    private val host: String,
    private val socketFactory: SSLSocketFactory
) {
    private var socket: SSLSocket? = null
    private var input: BufferedInputStream? = null
    private var output: BufferedOutputStream? = null
    private val writeMutex = Mutex()
    private var readerJob: Job? = null

    @Volatile
    var isReady: Boolean = false
        private set

    suspend fun connect(scope: CoroutineScope): Boolean = withContext(Dispatchers.IO) {
        if (isReady && socket?.isConnected == true) return@withContext true
        closeInternal()
        try {
            val s = socketFactory.createSocket(host, 6466) as SSLSocket
            s.soTimeout = 12000
            s.startHandshake()
            socket = s
            input = BufferedInputStream(s.inputStream)
            output = BufferedOutputStream(s.outputStream)

            // Server opens with its configuration; read it, then send ours + go active.
            RemoteMessage.parseDelimitedFrom(input)
                ?: throw IOException("No configuration from TV")
            sendConfigure()
            sendSetActive()

            s.soTimeout = 35000
            isReady = true
            startReader(scope)
            true
        } catch (e: Exception) {
            closeInternal()
            false
        }
    }

    suspend fun sendKey(key: RemoteKeyCode) {
        // Press then release — registers as a single discrete tap on all versions.
        writeMessage(keyMessage(key, RemoteDirection.START_LONG))
        writeMessage(keyMessage(key, RemoteDirection.END_LONG))
    }

    suspend fun launchApp(appLink: String) {
        writeMessage(
            RemoteMessage.newBuilder()
                .setRemoteAppLinkLaunchRequest(
                    RemoteAppLinkLaunchRequest.newBuilder().setAppLink(appLink)
                ).build()
        )
    }

    fun close() {
        readerJob?.cancel()
        readerJob = null
        closeInternal()
    }

    // --- internals ---

    private fun startReader(scope: CoroutineScope) {
        readerJob = scope.launch(Dispatchers.IO) {
            val inp = input ?: return@launch
            try {
                while (isActive) {
                    val msg = RemoteMessage.parseDelimitedFrom(inp) ?: break
                    if (msg.hasRemotePingRequest()) {
                        writeMessage(
                            RemoteMessage.newBuilder()
                                .setRemotePingResponse(
                                    RemotePingResponse.newBuilder()
                                        .setVal1(msg.remotePingRequest.val1)
                                ).build()
                        )
                    }
                    // remote_start / volume / ime messages are not needed here.
                }
            } catch (_: Exception) {
                // Socket dropped or timed out; mark not ready so we reconnect on next use.
            } finally {
                isReady = false
            }
        }
    }

    private suspend fun sendConfigure() = writeMessage(
        RemoteMessage.newBuilder()
            .setRemoteConfigure(
                RemoteConfigure.newBuilder()
                    .setCode1(622)
                    .setDeviceInfo(
                        RemoteDeviceInfo.newBuilder()
                            .setModel("Curb Remote")
                            .setVendor("CurbScript")
                            .setUnknown1(1)
                            .setUnknown2("1")
                            .setPackageName("com.curbscript.tvremote")
                            .setAppVersion("1.0")
                    )
            ).build()
    )

    private suspend fun sendSetActive() = writeMessage(
        RemoteMessage.newBuilder()
            .setRemoteSetActive(RemoteSetActive.newBuilder().setActive(622))
            .build()
    )

    private fun keyMessage(key: RemoteKeyCode, dir: RemoteDirection) =
        RemoteMessage.newBuilder()
            .setRemoteKeyInject(
                RemoteKeyInject.newBuilder().setKeyCode(key).setDirection(dir)
            ).build()

    private suspend fun writeMessage(msg: RemoteMessage) = writeMutex.withLock {
        withContext(Dispatchers.IO) {
            val out = output ?: throw IOException("Not connected")
            msg.writeDelimitedTo(out)
            out.flush()
        }
    }

    private fun closeInternal() {
        isReady = false
        try { socket?.close() } catch (_: Exception) {}
        socket = null; input = null; output = null
    }
}
