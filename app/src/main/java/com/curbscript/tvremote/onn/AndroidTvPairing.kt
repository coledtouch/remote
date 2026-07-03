package com.curbscript.tvremote.onn

import com.curbscript.tvremote.proto.PairingConfiguration
import com.curbscript.tvremote.proto.PairingEncoding
import com.curbscript.tvremote.proto.PairingMessage
import com.curbscript.tvremote.proto.PairingOption
import com.curbscript.tvremote.proto.PairingRequest
import com.curbscript.tvremote.proto.PairingSecret
import com.curbscript.tvremote.proto.PairingStatus
import com.curbscript.tvremote.proto.RoleType
import com.google.protobuf.ByteString
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.BufferedInputStream
import java.io.BufferedOutputStream
import java.io.IOException
import java.security.MessageDigest
import java.security.interfaces.RSAPublicKey
import javax.net.ssl.SSLSocket
import javax.net.ssl.SSLSocketFactory

/**
 * Android TV Remote v2 pairing over port 6467.
 *
 * Flow: [begin] opens a TLS connection and exchanges the request / option /
 * configuration messages, at which point the TV displays a 6-character code.
 * The user types the code and [finish] sends the SHA-256 secret that proves
 * both sides share the code, completing the pairing.
 */
class AndroidTvPairing(
    private val host: String,
    private val socketFactory: SSLSocketFactory,
    private val clientPublicKey: RSAPublicKey,
    private val clientName: String = "Curb Remote",
    private val serviceName: String = "com.curbscript.tvremote"
) {
    private var socket: SSLSocket? = null
    private var input: BufferedInputStream? = null
    private var output: BufferedOutputStream? = null
    private var serverPublicKey: RSAPublicKey? = null

    suspend fun begin() = withContext(Dispatchers.IO) {
        val s = socketFactory.createSocket(host, 6467) as SSLSocket
        s.soTimeout = 12000
        s.startHandshake()
        socket = s
        input = BufferedInputStream(s.inputStream)
        output = BufferedOutputStream(s.outputStream)
        serverPublicKey = (s.session.peerCertificates.first() as java.security.cert.X509Certificate)
            .publicKey as RSAPublicKey

        // 1) Pairing request
        send(
            base().setPairingRequest(
                PairingRequest.newBuilder()
                    .setServiceName(serviceName)
                    .setClientName(clientName)
            ).build()
        )
        readOk()

        // 2) Options (we accept a 6-symbol hexadecimal input code)
        send(
            base().setPairingOption(
                PairingOption.newBuilder()
                    .setPreferredRole(RoleType.ROLE_TYPE_INPUT)
                    .addInputEncodings(hexEncoding())
            ).build()
        )
        readOk()

        // 3) Configuration -> TV shows the code
        send(
            base().setPairingConfiguration(
                PairingConfiguration.newBuilder()
                    .setClientRole(RoleType.ROLE_TYPE_INPUT)
                    .setEncoding(hexEncoding())
            ).build()
        )
        readOk()
    }

    /** Sends the secret derived from the on-screen [code]. Returns true when paired. */
    suspend fun finish(code: String): Boolean = withContext(Dispatchers.IO) {
        val secret = computeSecret(code.trim())
        send(
            base().setPairingSecret(
                PairingSecret.newBuilder().setSecret(ByteString.copyFrom(secret))
            ).build()
        )
        val resp = PairingMessage.parseDelimitedFrom(input)
        val ok = resp != null && resp.statusValue == 200 && resp.hasPairingSecretAck()
        close()
        ok
    }

    fun close() {
        try { socket?.close() } catch (_: Exception) {}
        socket = null; input = null; output = null
    }

    // --- helpers ---

    private fun base(): PairingMessage.Builder =
        PairingMessage.newBuilder()
            .setProtocolVersion(2)
            .setStatus(PairingStatus.PAIRING_STATUS_OK)

    private fun hexEncoding() = PairingEncoding.newBuilder()
        .setType(PairingEncoding.EncodingType.ENCODING_TYPE_HEXADECIMAL)
        .setSymbolLength(6)

    private fun send(msg: PairingMessage) {
        val out = output ?: throw IOException("Not connected")
        msg.writeDelimitedTo(out)
        out.flush()
    }

    private fun readOk() {
        val resp = PairingMessage.parseDelimitedFrom(input)
            ?: throw IOException("Connection closed during pairing")
        if (resp.statusValue != 200) {
            throw IOException("Pairing rejected by TV (status ${resp.statusValue})")
        }
    }

    private fun computeSecret(code: String): ByteArray {
        require(code.length >= 6) { "Pairing code must be 6 characters" }
        val client = clientPublicKey
        val server = serverPublicKey ?: throw IOException("Missing server certificate")
        val digest = MessageDigest.getInstance("SHA-256")
        digest.update(stripLeadingZeros(client.modulus.toByteArray()))
        digest.update(stripLeadingZeros(client.publicExponent.toByteArray()))
        digest.update(stripLeadingZeros(server.modulus.toByteArray()))
        digest.update(stripLeadingZeros(server.publicExponent.toByteArray()))
        // nonce = the last 4 hex characters of the 6-character code
        digest.update(hexToBytes(code.substring(2)))
        return digest.digest()
    }

    private fun stripLeadingZeros(b: ByteArray): ByteArray {
        var i = 0
        while (i < b.size - 1 && b[i].toInt() == 0) i++
        return if (i == 0) b else b.copyOfRange(i, b.size)
    }

    private fun hexToBytes(s: String): ByteArray {
        val clean = s.trim()
        val out = ByteArray(clean.length / 2)
        for (i in out.indices) {
            val hi = Character.digit(clean[i * 2], 16)
            val lo = Character.digit(clean[i * 2 + 1], 16)
            out[i] = ((hi shl 4) + lo).toByte()
        }
        return out
    }
}
