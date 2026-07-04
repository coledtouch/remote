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
 * [login] returns null on success, or a human-readable error describing the failing step.
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

    /** Email/password login. Returns null on success, else a message for the failing step. */
    suspend fun login(email: String, password: String): String? = withContext(Dispatchers.IO) {
        try {
            val verifier = randomCodeVerifier()
            val challenge = codeChallenge(verifier)
            val authUrl = "$OIDC/auth?response_type=code&client_id=$CLIENT_ID&redirect_uri=$REDIRECT" +
                "&code_challenge=$challenge&code_challenge_method=S256&scope=openid%20offline_access" +
                "&state=${randomHex(16)}&nonce=${randomHex(16)}"

            val page = client.newCall(
                Request.Builder().url(authUrl)
                    .header("User-Agent", UA).header("Accept", "text/html").build()
            ).execute()
            val html = page.body?.string() ?: ""
            val pageCode = page.code
            page.close()
            if (html.isBlank()) return@withContext "Couldn't reach the Hubspace sign-in page (HTTP $pageCode)"

            val action = (Regex("action=\"([^\"]*authenticate[^\"]*)\"").find(html)
                ?: Regex("<form[^>]*action=\"([^\"]+)\"").find(html))
                ?.groupValues?.get(1)?.replace("&amp;", "&")
                ?: return@withContext "Hubspace sign-in form not found — their login page may have changed"

            val noRedirect = client.newBuilder().followRedirects(false).build()
            val form = FormBody.Builder()
                .add("username", email).add("password", password).add("credentialId", "").build()
            val loginResp = noRedirect.newCall(
                Request.Builder().url(action).post(form)
                    .header("User-Agent", UA).header("Referer", authUrl).build()
            ).execute()
            val location = loginResp.header("Location") ?: ""
            val loginCode = loginResp.code
            loginResp.close()
            if (location.isBlank()) {
                return@withContext "Email or password was rejected (HTTP $loginCode)"
            }
            val code = Regex("[?&]code=([^&]+)").find(location)?.groupValues?.get(1)
                ?: return@withContext "Signed in but no auth code was returned (account may need a step in the Hubspace app)"

            val tokenForm = FormBody.Builder()
                .add("grant_type", "authorization_code").add("code", code)
                .add("redirect_uri", REDIRECT).add("code_verifier", verifier)
                .add("client_id", CLIENT_ID).build()
            val tok = client.newCall(Request.Builder().url("$OIDC/token").post(tokenForm)
                .header("User-Agent", UA).build()).execute()
            val tokBody = tok.body?.string() ?: "{}"
            val tokCode = tok.code
            tok.close()
            val tokJson = JSONObject(tokBody)
            accessToken = tokJson.optString("access_token").ifBlank { null }
                ?: return@withContext "Token exchange failed (HTTP $tokCode)"
            refreshToken = tokJson.optString("refresh_token").ifBlank { refreshToken }
            // Account id is best-effort — login itself has already succeeded.
            accountId = try { fetchAccountId() } catch (_: Exception) { accountId }
            null
        } catch (e: Exception) {
            "Sign-in error: ${e.message ?: e.javaClass.simpleName}"
        }
    }

    suspend fun listLights(): List<HubspaceLight> = withContext(Dispatchers.IO) {
        ensureAccess()
        if (accountId == null) accountId = try { fetchAccountId() } catch (_: Exception) { null }
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
            JSONObject(raw).optJSONArray("accountAccess")?.optJSONObject(0)
                ?.optJSONObject("account")?.optString("accountId")?.ifBlank { null }
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

    private fun randomCodeVerifier(): String {
        val bytes = ByteArray(40)
        SecureRandom().nextBytes(bytes)
        return Base64.encodeToString(bytes, Base64.URL_SAFE or Base64.NO_WRAP or Base64.NO_PADDING)
    }

    private fun randomHex(len: Int): String {
        val bytes = ByteArray(len)
        SecureRandom().nextBytes(bytes)
        return bytes.joinToString("") { "%02x".format(it) }
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
        private const val UA = "Dart/2.15 (dart:io)"
        private val JSON = "application/json; charset=utf-8".toMediaType()
    }
}
