package cz.misa.quakedeck.data

import android.content.Context
import android.net.Uri
import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties
import android.util.Base64 as AndroidBase64
import androidx.core.content.edit
import cz.misa.quakedeck.BuildConfig
import okhttp3.Call
import okhttp3.Callback
import okhttp3.FormBody
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.Response
import okhttp3.HttpUrl.Companion.toHttpUrl
import org.json.JSONObject
import java.io.IOException
import java.nio.charset.StandardCharsets
import java.security.KeyStore
import java.security.MessageDigest
import java.security.SecureRandom
import java.time.Instant
import java.time.LocalDateTime
import java.time.ZoneOffset
import java.time.format.DateTimeFormatter
import javax.crypto.Cipher
import javax.crypto.KeyGenerator
import javax.crypto.SecretKey
import javax.crypto.spec.GCMParameterSpec

data class DmDssContractEntitlement(
    val classification: String,
    val planName: String?,
    val connectionCount: Int
)

data class DmDssContractSummary(
    val active: List<DmDssContractEntitlement>
) {
    val eewForecastAvailable: Boolean
        get() = active.any { it.classification == "eew.forecast" }
}

/** Native public-client OAuth for the first DM-D.S.S Android integration. */
class DmDssOAuthClient(
    context: Context,
    private val httpClient: OkHttpClient = OkHttpClient(),
    private val clientId: String = BuildConfig.DMDSS_OAUTH_CLIENT_ID,
    private val redirectUri: String = BuildConfig.DMDSS_OAUTH_REDIRECT_URI
) {
    private val store = CredentialStore(context.applicationContext)
    private val random = SecureRandom()
    private val refreshLock = Any()
    private var refreshInFlight = false
    private val refreshCallbacks = mutableListOf<(Result<String>) -> Unit>()

    val isAuthorized: Boolean
        get() = store.loadCredentials() != null

    val authorizationUpdateRequired: Boolean
        get() = store.loadCredentials()?.scopes?.containsAll(REQUIRED_SCOPES) == false

    fun hasGrantedScope(scope: String): Boolean =
        store.loadCredentials()?.scopes?.contains(scope) == true

    fun beginAuthorization(): Uri {
        val state = randomBase64Url(32)
        val verifier = randomBase64Url(64)
        store.savePending(state, verifier, System.currentTimeMillis())
        return Uri.parse(AUTH_URL).buildUpon()
            .appendQueryParameter("client_id", clientId)
            .appendQueryParameter("response_type", "code")
            .appendQueryParameter("response_mode", "query")
            .appendQueryParameter("redirect_uri", redirectUri)
            .appendQueryParameter("scope", REQUIRED_SCOPES.joinToString(" "))
            .appendQueryParameter("state", state)
            .appendQueryParameter("code_challenge", pkceChallenge(verifier))
            .appendQueryParameter("code_challenge_method", "S256")
            .build()
    }

    fun completeAuthorization(callbackUri: Uri, callback: (Result<Unit>) -> Unit) {
        val previousCredentials = store.loadCredentials()
        val pending = store.loadPending()
        store.clearPending()
        val failure = when {
            callbackUri.toString().substringBefore('?') != redirectUri ->
                "DM-D.S.S returned an unexpected OAuth redirect."
            pending == null -> "The DM-D.S.S sign-in request has expired."
            System.currentTimeMillis() - pending.createdAtMillis > PENDING_MAX_AGE_MILLIS ->
                "The DM-D.S.S sign-in request has expired."
            callbackUri.getQueryParameter("state") != pending.state ->
                "DM-D.S.S sign-in state did not match."
            callbackUri.getQueryParameter("error") != null ->
                callbackUri.getQueryParameter("error_description")
                    ?: "DM-D.S.S sign-in was not completed."
            callbackUri.getQueryParameter("code").isNullOrBlank() ->
                "DM-D.S.S did not return an authorization code."
            else -> null
        }
        if (failure != null) {
            callback(Result.failure(IllegalStateException(failure)))
            return
        }

        val body = FormBody.Builder()
            .add("client_id", clientId)
            .add("grant_type", "authorization_code")
            .add("code", callbackUri.getQueryParameter("code")!!)
            .add("redirect_uri", redirectUri)
            .add("code_verifier", pending!!.verifier)
            .build()
        executeTokenRequest(
            body = body,
            previousRefreshToken = null,
            requiredGrantedScopes = REQUIRED_SCOPES
        ) { result ->
            result.onSuccess { credentials ->
                store.saveCredentials(credentials)
                previousCredentials?.let { previous ->
                    val replacedTokens = listOf(previous.accessToken, previous.refreshToken)
                        .filterNot { it == credentials.accessToken || it == credentials.refreshToken }
                        .distinct()
                    if (replacedTokens.isNotEmpty()) {
                        revokeSequentially(replacedTokens, 0, true) { }
                    }
                }
            }
            callback(result.map { Unit })
        }
    }

    fun withAccessToken(
        forceRefresh: Boolean = false,
        callback: (Result<String>) -> Unit
    ) {
        val credentials = store.loadCredentials()
        if (credentials == null) {
            callback(Result.failure(IllegalStateException("DM-D.S.S is not connected.")))
            return
        }
        if (!forceRefresh &&
            credentials.expiresAtMillis > System.currentTimeMillis() + REFRESH_MARGIN_MILLIS
        ) {
            callback(Result.success(credentials.accessToken))
            return
        }

        synchronized(refreshLock) {
            refreshCallbacks += callback
            if (refreshInFlight) return
            refreshInFlight = true
        }
        val body = FormBody.Builder()
            .add("client_id", clientId)
            .add("grant_type", "refresh_token")
            .add("refresh_token", credentials.refreshToken)
            .build()
        executeTokenRequest(
            body = body,
            previousRefreshToken = credentials.refreshToken,
            requiredGrantedScopes = credentials.scopes
        ) { result ->
            result.onSuccess(store::saveCredentials)
            val tokenResult = result.map { it.accessToken }
            val callbacks = synchronized(refreshLock) {
                refreshInFlight = false
                refreshCallbacks.toList().also { refreshCallbacks.clear() }
            }
            callbacks.forEach { it(tokenResult) }
        }
    }

    fun readContracts(callback: (Result<DmDssContractSummary>) -> Unit) {
        withAccessToken { tokenResult ->
            tokenResult.onFailure { callback(Result.failure(it)) }
            tokenResult.onSuccess { accessToken ->
                val request = Request.Builder()
                    .url(CONTRACT_URL)
                    .header("Authorization", "Bearer $accessToken")
                    .header("Cache-Control", "no-store")
                    .build()
                httpClient.newCall(request).enqueue(object : Callback {
                    override fun onFailure(call: Call, e: IOException) {
                        callback(Result.failure(e))
                    }

                    override fun onResponse(call: Call, response: Response) {
                        val result = runCatching {
                            response.use {
                                val json = JSONObject(it.body.string())
                                if (!it.isSuccessful || json.optString("status") == "error") {
                                    val detail = json.optJSONObject("error")?.optString("message")
                                        .orEmpty().ifBlank { "HTTP ${it.code}" }
                                    error("DM-D.S.S plan check failed: $detail")
                                }
                                val items = json.optJSONArray("items")
                                val active = buildList {
                                    if (items != null) {
                                        for (index in 0 until items.length()) {
                                            val item = items.optJSONObject(index) ?: continue
                                            if (!item.optBoolean("isValid", false)) continue
                                            val classification = item.optString("classification")
                                            if (classification.isBlank()) continue
                                            add(
                                                DmDssContractEntitlement(
                                                    classification = classification,
                                                    planName = item.optString("planName")
                                                        .takeIf(String::isNotBlank),
                                                    connectionCount = item.optInt("connectionCounts", 0)
                                                )
                                            )
                                        }
                                    }
                                }.distinctBy { it.classification }.sortedBy { it.classification }
                                DmDssContractSummary(active)
                            }
                        }
                        callback(result)
                    }
                })
            }
        }
    }

    fun closeSocket(socketId: String, callback: (Result<Unit>) -> Unit = {}) {
        if (socketId.isBlank() || socketId.any { !it.isDigit() }) {
            callback(Result.failure(IllegalArgumentException("Invalid DM-D.S.S socket ID.")))
            return
        }
        withAccessToken { tokenResult ->
            tokenResult.onFailure { callback(Result.failure(it)) }
            tokenResult.onSuccess { accessToken ->
                val request = Request.Builder()
                    .url("$SOCKET_URL/$socketId")
                    .header("Authorization", "Bearer $accessToken")
                    .delete()
                    .build()
                httpClient.newCall(request).enqueue(object : Callback {
                    override fun onFailure(call: Call, e: IOException) {
                        callback(Result.failure(e))
                    }

                    override fun onResponse(call: Call, response: Response) {
                        response.use {
                            if (it.isSuccessful || it.code == 404) {
                                callback(Result.success(Unit))
                            } else {
                                callback(
                                    Result.failure(
                                        IOException("DM-D.S.S Socket Close failed: HTTP ${it.code}")
                                    )
                                )
                            }
                        }
                    }
                })
            }
        }
    }

    fun readRecentEew(
        sinceMillis: Long,
        forceRefresh: Boolean = false,
        callback: (Result<List<JSONObject>>) -> Unit
    ) {
        withAccessToken(forceRefresh) { tokenResult ->
            tokenResult.onFailure { callback(Result.failure(it)) }
            tokenResult.onSuccess { accessToken ->
                val url = GD_EEW_URL.toHttpUrl().newBuilder()
                    .addQueryParameter("datetime", gdEewDatetimeRefinement(sinceMillis))
                    .addQueryParameter("limit", "10")
                    .build()
                val request = Request.Builder()
                    .url(url)
                    .header("Authorization", "Bearer $accessToken")
                    .header("Cache-Control", "no-store")
                    .build()
                httpClient.newCall(request).enqueue(object : Callback {
                    override fun onFailure(call: Call, e: IOException) {
                        callback(Result.failure(e))
                    }

                    override fun onResponse(call: Call, response: Response) {
                        if (response.code == 401 && !forceRefresh) {
                            response.close()
                            readRecentEew(sinceMillis, forceRefresh = true, callback = callback)
                            return
                        }
                        val result = runCatching {
                            response.use {
                                val json = JSONObject(it.body.string())
                                if (!it.isSuccessful || json.optString("status") == "error") {
                                    val detail = json.optJSONObject("error")?.optString("message")
                                        .orEmpty().ifBlank { "HTTP ${it.code}" }
                                    error("DM-D.S.S EEW recovery failed: $detail")
                                }
                                val items = json.optJSONArray("items")
                                buildList {
                                    if (items != null) {
                                        for (index in 0 until items.length()) {
                                            items.optJSONObject(index)?.let(::add)
                                        }
                                    }
                                }
                            }
                        }
                        callback(result)
                    }
                })
            }
        }
    }

    fun disconnect(callback: (Boolean) -> Unit = {}) {
        val credentials = store.loadCredentials()
        store.clearAll()
        val tokens = listOfNotNull(credentials?.accessToken, credentials?.refreshToken).distinct()
        if (tokens.isEmpty()) {
            callback(true)
            return
        }
        revokeSequentially(tokens, 0, true, callback)
    }

    private fun revokeSequentially(
        tokens: List<String>,
        index: Int,
        succeeded: Boolean,
        callback: (Boolean) -> Unit
    ) {
        if (index >= tokens.size) {
            callback(succeeded)
            return
        }
        val request = Request.Builder()
            .url(REVOKE_URL)
            .post(FormBody.Builder().add("client_id", clientId).add("token", tokens[index]).build())
            .build()
        httpClient.newCall(request).enqueue(object : Callback {
            override fun onFailure(call: Call, e: IOException) {
                revokeSequentially(tokens, index + 1, false, callback)
            }

            override fun onResponse(call: Call, response: Response) {
                response.use {
                    revokeSequentially(tokens, index + 1, succeeded && it.isSuccessful, callback)
                }
            }
        })
    }

    private fun executeTokenRequest(
        body: FormBody,
        previousRefreshToken: String?,
        requiredGrantedScopes: Set<String>,
        callback: (Result<Credentials>) -> Unit
    ) {
        val request = Request.Builder().url(TOKEN_URL).post(body).build()
        httpClient.newCall(request).enqueue(object : Callback {
            override fun onFailure(call: Call, e: IOException) {
                callback(Result.failure(e))
            }

            override fun onResponse(call: Call, response: Response) {
                val result = runCatching {
                    response.use {
                        val json = JSONObject(it.body.string())
                        if (!it.isSuccessful || json.has("error")) {
                            val detail = json.optString("error_description")
                                .ifBlank { json.optString("error") }
                                .ifBlank { "HTTP ${it.code}" }
                            error("DM-D.S.S sign-in failed: $detail")
                        }
                        val accessToken = json.optString("access_token")
                            .takeIf(String::isNotBlank) ?: error("Missing DM-D.S.S access token")
                        val refreshToken = json.optString("refresh_token")
                            .takeIf(String::isNotBlank) ?: previousRefreshToken
                            ?: error("Missing DM-D.S.S refresh token")
                        val scopes = json.optString("scope").split(' ').filter(String::isNotBlank).toSet()
                        require(scopes.containsAll(requiredGrantedScopes)) {
                            "DM-D.S.S did not grant all required forecast and recovery permissions."
                        }
                        Credentials(
                            accessToken = accessToken,
                            refreshToken = refreshToken,
                            expiresAtMillis = System.currentTimeMillis() +
                                json.optLong("expires_in", 21_600L).coerceAtLeast(60L) * 1_000L,
                            scopes = scopes
                        )
                    }
                }
                if (result.isFailure && previousRefreshToken != null) store.clearAll()
                callback(result)
            }
        })
    }

    private fun randomBase64Url(byteCount: Int): String = ByteArray(byteCount)
        .also(random::nextBytes)
        .let(::base64Url)

    companion object {
        const val AUTH_URL = "https://manager.dmdata.jp/account/oauth2/v1/auth"
        const val TOKEN_URL = "https://manager.dmdata.jp/account/oauth2/v1/token"
        const val REVOKE_URL = "https://manager.dmdata.jp/account/oauth2/v1/revoke"
        const val CONTRACT_URL = "https://api.dmdata.jp/v2/contract"
        const val SOCKET_URL = "https://api.dmdata.jp/v2/socket"
        const val GD_EEW_URL = "https://api.dmdata.jp/v2/gd/eew"
        val REQUIRED_SCOPES = setOf(
            "contract.list",
            "socket.start",
            "socket.close",
            "eew.get.forecast",
            "gd.eew"
        )
        private const val PENDING_MAX_AGE_MILLIS = 10 * 60 * 1_000L
        private const val REFRESH_MARGIN_MILLIS = 60 * 1_000L

        internal fun pkceChallenge(verifier: String): String = base64Url(
            MessageDigest.getInstance("SHA-256")
                .digest(verifier.toByteArray(StandardCharsets.US_ASCII))
        )

        internal fun gdEewDatetimeRefinement(sinceMillis: Long): String =
            LocalDateTime.ofInstant(Instant.ofEpochMilli(sinceMillis), ZoneOffset.UTC)
                .withNano(0)
                .format(DateTimeFormatter.ISO_LOCAL_DATE_TIME) + "~"

        private fun base64Url(bytes: ByteArray): String =
            java.util.Base64.getUrlEncoder().withoutPadding().encodeToString(bytes)
    }

    private data class Credentials(
        val accessToken: String,
        val refreshToken: String,
        val expiresAtMillis: Long,
        val scopes: Set<String>
    )

    private data class PendingAuthorization(
        val state: String,
        val verifier: String,
        val createdAtMillis: Long
    )

    private class CredentialStore(context: Context) {
        private val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)

        fun savePending(state: String, verifier: String, createdAtMillis: Long) {
            prefs.edit {
                putString(KEY_PENDING_STATE, state)
                putString(KEY_PENDING_VERIFIER, verifier)
                putLong(KEY_PENDING_CREATED, createdAtMillis)
            }
        }

        fun loadPending(): PendingAuthorization? {
            val state = prefs.getString(KEY_PENDING_STATE, null) ?: return null
            val verifier = prefs.getString(KEY_PENDING_VERIFIER, null) ?: return null
            return PendingAuthorization(state, verifier, prefs.getLong(KEY_PENDING_CREATED, 0L))
        }

        fun clearPending() {
            prefs.edit {
                remove(KEY_PENDING_STATE)
                remove(KEY_PENDING_VERIFIER)
                remove(KEY_PENDING_CREATED)
            }
        }

        fun saveCredentials(credentials: Credentials) {
            prefs.edit {
                putString(KEY_ACCESS_TOKEN, encrypt(credentials.accessToken))
                putString(KEY_REFRESH_TOKEN, encrypt(credentials.refreshToken))
                putLong(KEY_EXPIRES_AT, credentials.expiresAtMillis)
                putString(KEY_SCOPES, credentials.scopes.sorted().joinToString(" "))
            }
        }

        fun loadCredentials(): Credentials? = runCatching {
            val access = prefs.getString(KEY_ACCESS_TOKEN, null)?.let(::decrypt) ?: return null
            val refresh = prefs.getString(KEY_REFRESH_TOKEN, null)?.let(::decrypt) ?: return null
            Credentials(
                accessToken = access,
                refreshToken = refresh,
                expiresAtMillis = prefs.getLong(KEY_EXPIRES_AT, 0L),
                scopes = prefs.getString(KEY_SCOPES, "").orEmpty()
                    .split(' ').filter(String::isNotBlank).toSet()
            )
        }.getOrElse {
            clearAll()
            null
        }

        fun clearAll() {
            prefs.edit { clear() }
        }

        private fun encrypt(value: String): String {
            val cipher = Cipher.getInstance(TRANSFORMATION)
            cipher.init(Cipher.ENCRYPT_MODE, encryptionKey())
            val encrypted = cipher.doFinal(value.toByteArray(StandardCharsets.UTF_8))
            return base64(cipher.iv + encrypted)
        }

        private fun decrypt(value: String): String {
            val bytes = AndroidBase64.decode(value, AndroidBase64.NO_WRAP)
            require(bytes.size > GCM_IV_BYTES)
            val cipher = Cipher.getInstance(TRANSFORMATION)
            cipher.init(
                Cipher.DECRYPT_MODE,
                encryptionKey(),
                GCMParameterSpec(GCM_TAG_BITS, bytes.copyOfRange(0, GCM_IV_BYTES))
            )
            return String(cipher.doFinal(bytes.copyOfRange(GCM_IV_BYTES, bytes.size)), StandardCharsets.UTF_8)
        }

        private fun encryptionKey(): SecretKey {
            val keyStore = KeyStore.getInstance("AndroidKeyStore").apply { load(null) }
            (keyStore.getKey(KEYSTORE_ALIAS, null) as? SecretKey)?.let { return it }
            val generator = KeyGenerator.getInstance(KeyProperties.KEY_ALGORITHM_AES, "AndroidKeyStore")
            generator.init(
                KeyGenParameterSpec.Builder(
                    KEYSTORE_ALIAS,
                    KeyProperties.PURPOSE_ENCRYPT or KeyProperties.PURPOSE_DECRYPT
                )
                    .setBlockModes(KeyProperties.BLOCK_MODE_GCM)
                    .setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE)
                    .build()
            )
            return generator.generateKey()
        }

        private fun base64(bytes: ByteArray): String =
            AndroidBase64.encodeToString(bytes, AndroidBase64.NO_WRAP)

        companion object {
            private const val PREFS_NAME = "dmdss_oauth"
            private const val KEY_ACCESS_TOKEN = "access_token"
            private const val KEY_REFRESH_TOKEN = "refresh_token"
            private const val KEY_EXPIRES_AT = "expires_at"
            private const val KEY_SCOPES = "scopes"
            private const val KEY_PENDING_STATE = "pending_state"
            private const val KEY_PENDING_VERIFIER = "pending_verifier"
            private const val KEY_PENDING_CREATED = "pending_created"
            private const val KEYSTORE_ALIAS = "quakedeck_dmdss_oauth"
            private const val TRANSFORMATION = "AES/GCM/NoPadding"
            private const val GCM_IV_BYTES = 12
            private const val GCM_TAG_BITS = 128
        }
    }
}
