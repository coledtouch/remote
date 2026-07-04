package com.curbscript.tvremote.hubspace

import android.util.Base64
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.Cookie
import okhttp3.CookieJar
import okhttp3.FormBody
import okhttp3.HttpUrl
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONArray
import org.json.JSONObject
import java.security.MessageDigest
import java.security.SecureRandom
import java.util.concurrent.TimeUnit

/** A discovered Hubspace (EcoSmart) bulb. [room] is inferred from the name. */
data class HubspaceLight(
    val id: String,
    val name: String,
    val room: String,
    val on: Boolean,
    val brightness: Int
)

/**
 * Client for the Hubspace / Afero cloud (Home Depot "thd" realm). Cloud-only:
 * OAuth (Keycloak, PKCE) login, then device discovery + state control on api2.afero.net.
 * Written from the public API shape; endpoints may need field-tuning.
 */
class HubspaceClient(
    private var refreshToken: String? = null,
    private var accountId: String? = null
) {
    private var accessToken: String? = null
    private val cookies = mutableMapOf<String, String>()

    private val jar = object : CookieJar {
        override fun saveFromResponse(url: HttpUrl, list: List<Cookie>) {
            list.forEach { cookies[it.name] = it.value }
        }
        override fun loadForRequest(url: HttpUrl): List<Cookie> =
            cookies.map { Cookie.Builder().name(it.key).value(it.value).domain(url.host).build() }
    }

    private val client = OkHttpClient.Builder()
        .cookieJar(jar)
        .connectTimeout(15, TimeUnit.SECONDS)
        .readTimeout(15, TimeUnit.SECONDS)
        .build()

    fun refreshTokenValue(): String? = refreshToken
    fun accountValue(): String? = accountId

    /** Full email/password login. Returns true and stores refresh token + account on success. */
    suspend fun login(email: String, password: String): Boolean = withContext(Dispatchers.IO) {
        try {
            val verifier = randomString(64)
            val challenge = codeChallenge(verifier)
            val authUrl = "$OIDC/auth?response_type=code&client_id=$CLIENT_ID&redirect_uri=$REDIRECT" +
                "&code_challenge=$challenge&code_challenge_method=S256&scope=openid%20offline_access" +
                "&state=${randomString(16)}&nonce=${randomString(16)}"
            val page = client.newCall(Request.Builder().url(authUrl).header("User-Agent", UA).build()).execute()
            val html = page.body?.string() ?: ""
            page.close()
            val action = Regex("<form[^>]*action=\"([^\"]+)\"").find(html)?.groupValues?.get(1)
                ?.replace("&amp;", "&") ?: return@withContext false

            val noRedirect = client.newBuilder().followRedirects(false).build()
            val form = FormBody.Builder()
                .add("username", email).add("password", password).add("credentialId", "").build()
            val loginResp = noRedirect.newCall(
                Request.Builder().url(action).post(form).header("User-Agent", UA).build()
            ).execute()
            val location = loginResp.header("Location") ?: ""
            loginResp.close()
            val code = Regex("code=([^&]+)").find(location)?.groupValues?.get(1) ?: return@withContext false

            val tokenForm = FormBody.Builder()
                .add("grant_type", "authorization_code").add("code", code)
                .add("redirect_uri", REDIRECT).add("code_verifier", verifier)
                .add("client_id", CLIENT_ID).build()
            val tok = client.newCall(Request.Builder().url("$OIDC/token").post(tokenForm)
                .header("User-Agent", UA).build()).execute()
            val tokJson = JSONObject(tok.body?.string() ?: "{}")
            tok.close()
            accessToken = tokJson.optString("access_token").ifBlank { null } ?: return@withContext false
            refreshToken = tokJson.optString("refresh_token").ifBlank { refreshToken }
            accountId = fetchAccountId()
            accountId != null
        } catch (e: Exception) {
            false
        }
    }

    suspend fun listLights(): List<HubspaceLight> = withContext(Dispatchers.IO) {
        ensureAccess()
        val acct = accountId ?: return@withContext emptyList()
        val raw = apiRaw("GET", "/accounts/$acct/metadevices?expansions=state", null)
            ?: return@withContext emptyList()
        val out = ArrayList<HubspaceLight>()
        try {
            val arr = JSONArray(raw)
            for (i in 0 until arr.length()) {
                val d = arr.getJSONObject(i)
                val device = d.optJSONObject("description")?.optJSONObject("device")
                val cls = device?.optString("deviceClass") ?: ""
                if (!cls.equals("light", true)) continue
                val id = d.optString("id")
                val name = d.optString("friendlyName").ifBlank { device?.optString("model") ?: "Light" }
                var on = false
                var bri = 100
                d.optJSONObject("state")?.optJSONArray("values")?.let { vals ->
                    for (j in 0 until vals.length()) {
                        val v = vals.getJSONObject(j)
                        when (v.optString("functionClass")) {
                            "power" -> on = v.optString("value") == "on"
                            "brightness" -> bri = v.optInt("value", 100)
                        }
                    }
                }
                out.add(HubspaceLight(id, name, roomFor(name), on, bri))
            }
        } catch (_: Exception) {
        }
        out
    }

    suspend fun setPower(id: String, on: Boolean): Boolean = withContext(Dispatchers.IO) {
        putState(id, "power", "light-power", if (on) "on" else "off")
    }

    suspend fun setBrightness(id: String, pct: Int): Boolean = withContext(Dispatchers.IO) {
        putState(id, "brightness", null, pct.coerceIn(1, 100))
    }

    // ---- internals ----

    private fun putState(id: String, fnClass: String, fnInst: String?, value: Any): Boolean {
        ensureAccessBlocking()
        val acct = accountId ?: return false
        val v = JSONObject().put("functionClass", fnClass).put("value", value)
        if (fnInst != null) v.put("functionInstance", fnInst)
        val body = JSONObject().put("metadeviceId", id).put("values", JSONArray().put(v))
        return apiRaw("PUT", "/accounts/$acct/metadevices/$id/state", body) != null
    }

    private fun fetchAccountId(): String? {
        val raw = apiRaw("GET", "/users/me?expansions=account", null) ?: return null
        return try {
            val me = JSONObject(raw)
            me.optJSONArray("accountAccess")?.optJSONObject(0)?.optJSONObject("account")?.optString("accountId")
                ?.ifBlank { null }
        } catch (_: Exception) { null }
    }

    private suspend fun ensureAccess() = withContext(Dispatchers.IO) { ensureAccessBlocking() }

    private fun ensureAccessBlocking() {
        if (accessToken != null) return
        val rt = refreshToken ?: return
        try {
            val form = FormBody.Builder()
                .add("grant_type", "refresh_token").add("refresh_token", rt)
                .add("client_id", CLIENT_ID).build()
            val resp = client.newCall(Request.Builder().url("$OIDC/token").post(form)
                .header("User-Agent", UA).build()).execute()
            val txt = resp.body?.string()
            resp.close()
            if (!txt.isNullOrBlank()) {
                val j = JSONObject(txt)
                accessToken = j.optString("access_token").ifBlank { null }
                j.optString("refresh_token").ifBlank { null }?.let { refreshToken = it }
            }
        } catch (_: Exception) {
        }
    }

    private fun apiRaw(method: String, path: String, body: JSONObject?): String? {
        val at = accessToken ?: return null
        return try {
            val rb = Request.Builder().url("$API$path")
                .header("Authorization", "Bearer $at")
                .header("host", AFERO_HOST)
                .header("User-Agent", UA)
            when (method) {
                "PUT" -> rb.put((body?.toString() ?: "{}").toRequestBody(JSON))
                else -> rb.get()
            }
            val resp = client.newCall(rb.build()).execute()
            val txt = resp.body?.string()
            resp.close()
            txt
        } catch (_: Exception) { null }
    }

    private fun roomFor(name: String): String {
        val n = name.lowercase()
        return when {
            listOf("night", "bed", "closet", "dresser", "closest").any { n.contains(it) } -> "bedroom"
            listOf("hall", "living", "lamp", "couch", "sofa").any { n.contains(it) } -> "living"
            else -> "both"
        }
    }

    private fun randomString(len: Int): String {
        val bytes = ByteArray(len)
        SecureRandom().nextBytes(bytes)
        return Base64.encodeToString(bytes, Base64.URL_SAFE or Base64.NO_WRAP or Base64.NO_PADDING).take(len)
    }

    private fun codeChallenge(verifier: String): String {
        val digest = MessageDigest.getInstance("SHA-256").digest(verifier.toByteArray())
        return Base64.encodeToString(digest, Base64.URL_SAFE or Base64.NO_WRAP or Base64.NO_PADDING)
    }

    companion object {
        private const val CLIENT_ID = "hubspace_android"
        private const val REDIRECT = "hubspace-app://loginredirect"
        private const val OIDC = "https://accounts.hubspaceconnect.com/auth/realms/thd/protocol/openid-connect"
        private const val API = "https://api2.afero.net/v1"
        private const val AFERO_HOST = "semantics2.afero.net"
        private const val UA = "Dart/2.15 (dart:io)"
        private val JSON = "application/json; charset=utf-8".toMediaType()
    }
}
