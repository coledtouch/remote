package com.curbscript.tvremote.vizio

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import java.io.IOException
import java.net.URL
import java.security.SecureRandom
import java.security.cert.X509Certificate
import javax.net.ssl.HostnameVerifier
import javax.net.ssl.HttpsURLConnection
import javax.net.ssl.SSLContext
import javax.net.ssl.SSLSocketFactory
import javax.net.ssl.TrustManager
import javax.net.ssl.X509TrustManager

/**
 * Client for the Vizio SmartCast local REST API (HTTPS, self-signed cert).
 * Firmware 4.0+ serves on port 7345; older firmware uses 9000.
 *
 * Key command codes (CODESET, CODE) come from the documented SmartCast API.
 */
class VizioClient(
    private val host: String,
    private val port: Int = 7345,
    private var authToken: String? = null
) {
    fun setAuth(token: String?) { authToken = token }

    // ---- Pairing ----

    /** Starts pairing; the TV displays a PIN. Returns the pairing request token. */
    suspend fun startPairing(deviceName: String, deviceId: String): Int = io {
        val body = JSONObject()
            .put("DEVICE_NAME", deviceName)
            .put("DEVICE_ID", deviceId)
        val resp = request("/pairing/start", "PUT", body)
        checkStatus(resp)
        resp.getJSONObject("ITEM").getInt("PAIRING_REQ_TOKEN")
    }

    /** Completes pairing with the on-screen [pin]; returns and stores the auth token. */
    suspend fun completePairing(deviceId: String, pin: String, token: Int): String = io {
        val body = JSONObject()
            .put("DEVICE_ID", deviceId)
            .put("CHALLENGE_TYPE", 1)
            .put("RESPONSE_VALUE", pin)
            .put("PAIRING_REQ_TOKEN", token)
        val resp = request("/pairing/pair", "PUT", body)
        checkStatus(resp)
        val tok = resp.getJSONObject("ITEM").getString("AUTH_TOKEN")
        authToken = tok
        tok
    }

    // ---- Remote keys ----

    suspend fun keyCommand(codeset: Int, code: Int, action: String = "KEYPRESS") = io {
        val key = JSONObject()
            .put("CODESET", codeset)
            .put("CODE", code)
            .put("ACTION", action)
        val body = JSONObject().put("KEYLIST", JSONArray().put(key))
        request("/key_command/", "PUT", body)
        Unit
    }

    suspend fun powerToggle() = keyCommand(11, 2)
    suspend fun powerOn() = keyCommand(11, 1)
    suspend fun powerOff() = keyCommand(11, 0)
    suspend fun volumeUp() = keyCommand(5, 1)
    suspend fun volumeDown() = keyCommand(5, 0)
    suspend fun muteToggle() = keyCommand(5, 4)
    suspend fun cycleInput() = keyCommand(7, 1)

    // ---- Inputs ----

    /** Reads current input; returns its VALUE name and HASHVAL (needed to change it). */
    suspend fun currentInput(): InputState = io {
        val resp = request("/menu_native/dynamic/tv_settings/devices/current_input", "GET", null)
        val item = resp.getJSONArray("ITEMS").getJSONObject(0)
        InputState(item.optString("VALUE"), item.optInt("HASHVAL"))
    }

    /** Lists selectable input names (e.g. HDMI-1, HDMI-2, SMARTCAST). */
    suspend fun listInputs(): List<String> = io {
        val resp = request("/menu_native/dynamic/tv_settings/devices/name_input", "GET", null)
        val items = resp.getJSONArray("ITEMS")
        (0 until items.length()).map { items.getJSONObject(it).optString("NAME") }
            .filter { it.isNotBlank() }
    }

    suspend fun setInput(name: String) = io {
        val current = currentInput()
        val body = JSONObject()
            .put("REQUEST", "MODIFY")
            .put("VALUE", name)
            .put("HASHVAL", current.hashval)
        request("/menu_native/dynamic/tv_settings/devices/current_input", "PUT", body)
        Unit
    }

    /** Power state: true if the panel is on. */
    suspend fun isOn(): Boolean = io {
        val resp = request("/state/device/power_mode", "GET", null)
        resp.getJSONArray("ITEMS").getJSONObject(0).optInt("VALUE", 0) == 1
    }

    data class InputState(val value: String, val hashval: Int)

    // ---- HTTP plumbing ----

    private suspend fun <T> io(block: suspend () -> T): T = withContext(Dispatchers.IO) { block() }

    private fun request(path: String, method: String, body: JSONObject?): JSONObject {
        val conn = URL("https://$host:$port$path").openConnection() as HttpsURLConnection
        conn.sslSocketFactory = trustAll
        conn.hostnameVerifier = HostnameVerifier { _, _ -> true }
        conn.requestMethod = method
        conn.connectTimeout = 6000
        conn.readTimeout = 6000
        conn.setRequestProperty("Content-Type", "application/json")
        authToken?.takeIf { it.isNotBlank() }?.let { conn.setRequestProperty("AUTH", it) }
        if (body != null) {
            conn.doOutput = true
            conn.outputStream.use { it.write(body.toString().toByteArray(Charsets.UTF_8)) }
        }
        return try {
            val code = conn.responseCode
            val stream = if (code in 200..299) conn.inputStream else (conn.errorStream ?: conn.inputStream)
            val text = stream.bufferedReader().use { it.readText() }
            JSONObject(text)
        } finally {
            conn.disconnect()
        }
    }

    private fun checkStatus(resp: JSONObject) {
        val result = resp.optJSONObject("STATUS")?.optString("RESULT")
        if (result != null && !result.equals("SUCCESS", ignoreCase = true)) {
            throw IOException("Vizio: $result")
        }
    }

    companion object {
        private val trustAll: SSLSocketFactory by lazy {
            val tm = arrayOf<TrustManager>(object : X509TrustManager {
                override fun checkClientTrusted(chain: Array<out X509Certificate>?, authType: String?) {}
                override fun checkServerTrusted(chain: Array<out X509Certificate>?, authType: String?) {}
                override fun getAcceptedIssuers(): Array<X509Certificate> = emptyArray()
            })
            SSLContext.getInstance("TLS").apply { init(null, tm, SecureRandom()) }.socketFactory
        }
    }
}
