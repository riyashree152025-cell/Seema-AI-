package com.raju.aiassistant.data.sync

import com.raju.aiassistant.core.security.SecureKeyStore
import com.raju.aiassistant.core.security.isLocalNetworkHost
import com.raju.aiassistant.data.ai.JsonFormat
import com.raju.aiassistant.data.ai.toRequestBody
import com.raju.aiassistant.domain.ai.AiResult
import com.raju.aiassistant.domain.ai.FailureKind
import com.raju.aiassistant.domain.assistant.Memory
import com.raju.aiassistant.domain.model.ChatMessage
import com.raju.aiassistant.domain.model.SecretId
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.addJsonObject
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.intOrNull
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.put
import kotlinx.serialization.json.putJsonArray
import okhttp3.OkHttpClient
import okhttp3.Request
import java.net.URI
import java.time.Duration

/**
 * Client for the SEEMA AI Windows PC Companion & MONA Remote Bridge.
 * Keeps memories and conversations synchronized between phone and PC, and executes authorized PC agent actions.
 * Supports dual-endpoint failover: tries Local Bridge URL (Wi-Fi/LAN/USB ADB) first, then falls back to Remote / Tunnel URL.
 */
class PcSyncClient(
    private val http: OkHttpClient,
    private val secureKeyStore: SecureKeyStore? = null,
    private val secretLookup: (suspend (SecretId) -> String?)? = null,
) {
    private suspend fun getSecret(id: SecretId): String? =
        secretLookup?.invoke(id) ?: secureKeyStore?.get(id)

    private suspend fun getToken(): String = getSecret(SecretId.PC_SYNC_TOKEN)?.trim().orEmpty()

    /**
     * Resolves the candidate base URLs in priority order:
     * 1. Local Bridge URL (from PC_SYNC_ENDPOINT): tries Wi-Fi/LAN/localhost/ADB first.
     * 2. Remote / Tunnel URL (from PC_REMOTE_ENDPOINT): fallback for 4G/5G mobile data or outside home.
     */
    suspend fun getCandidateEndpoints(): List<String> {
        val localRaw = getSecret(SecretId.PC_SYNC_ENDPOINT)?.trim().orEmpty()
        val remoteRaw = getSecret(SecretId.PC_REMOTE_ENDPOINT)?.trim().orEmpty()
        val candidates = mutableListOf<String>()

        fun normalizeAndValidate(raw: String, isLocal: Boolean): String? {
            if (raw.isBlank()) return null
            val withScheme = if (raw.startsWith("http://", true) || raw.startsWith("https://", true)) {
                raw
            } else {
                if (isLocal) "http://$raw" else "https://$raw"
            }
            val base = withScheme.trimEnd('/')
            val uri = runCatching { URI(base) }.getOrNull() ?: return null
            val scheme = uri.scheme?.lowercase() ?: return null
            val host = uri.host?.lowercase()?.removePrefix("[")?.removeSuffix("]") ?: return null

            if (scheme == "https") return base
            if (scheme == "http") {
                if (host == "localhost" || host == "127.0.0.1" || host == "::1" || host.endsWith(".ts.net")) return base
                if (isLocal && isLocalNetworkHost(host)) return base
            }
            return null
        }

        normalizeAndValidate(localRaw, isLocal = true)?.let { candidates.add(it) }
        normalizeAndValidate(remoteRaw, isLocal = false)?.let { if (it !in candidates) candidates.add(it) }
        return candidates
    }

    private fun Request.Builder.auth(token: String): Request.Builder = apply {
        if (token.isNotEmpty()) {
            header("Authorization", "Bearer $token")
            header("X-Seema-Token", token)
        }
    }

    suspend fun checkStatus(): AiResult<String> = withContext(Dispatchers.IO) {
        val candidates = getCandidateEndpoints()
        if (candidates.isEmpty()) {
            return@withContext AiResult.Failure(FailureKind.NOT_CONFIGURED, "Offline / Bridge Not Configured ❌")
        }
        val token = getToken()
        var wrongToken = false

        // Snappy timeout per candidate probe so fallback to remote URL is fast
        val probeClient = http.newBuilder()
            .connectTimeout(Duration.ofMillis(2500))
            .readTimeout(Duration.ofMillis(3000))
            .build()

        for (base in candidates) {
            val statusUrls = listOf("$base/status", "$base/api/v1/status")
            for (url in statusUrls) {
                val req = Request.Builder()
                    .url(url)
                    .auth(token)
                    .get()
                    .build()
                try {
                    probeClient.newCall(req).execute().use { resp ->
                        if (resp.isSuccessful) {
                            val body = resp.body?.string().orEmpty()
                            val obj = runCatching { JsonFormat.parseToJsonElement(body) as JsonObject }.getOrNull()
                            val host = (obj?.get("hostname") as? JsonPrimitive)?.contentOrNull
                                ?: (obj?.get("service") as? JsonPrimitive)?.contentOrNull
                                ?: "PC"
                            return@withContext AiResult.Success("Connected to MONA PC ✅ ($host)")
                        } else if (resp.code == 401) {
                            wrongToken = true
                            return@withContext AiResult.Failure(FailureKind.INVALID_KEY, "Offline / Wrong Token ❌")
                        }
                    }
                } catch (_: Exception) {
                    // Try next URL or next candidate
                }
            }
        }

        if (wrongToken) {
            AiResult.Failure(FailureKind.INVALID_KEY, "Offline / Wrong Token ❌")
        } else {
            AiResult.Failure(FailureKind.NETWORK, "Offline / Unreachable ❌")
        }
    }

    /**
     * The PC / VPS bridge: one action on the companion ("open_app", "system_info", "instruction").
     * Tries local URL first (Wi-Fi), then falls back to remote tunnel URL (mobile data) with token auth.
     */
    suspend fun sendAction(action: String, params: Map<String, String>): com.raju.aiassistant.domain.remote.RemoteResult = withContext(Dispatchers.IO) {
        val candidates = getCandidateEndpoints()
        if (candidates.isEmpty()) return@withContext com.raju.aiassistant.domain.remote.RemoteResult.NotConfigured
        val token = getToken()
        val payload = buildJsonObject {
            put("action", action)
            put("params", buildJsonObject { params.forEach { (k, v) -> put(k, v) } })
        }
        val callClient = http.newBuilder()
            .connectTimeout(Duration.ofMillis(3000))
            .readTimeout(Duration.ofMillis(5000))
            .build()

        var lastUnauthorized = false
        var lastRefused = false

        for (base in candidates) {
            val urls = listOf("$base/api/v1/actions/pc", "$base/actions/pc")
            for (url in urls) {
                val req = Request.Builder()
                    .url(url)
                    .auth(token)
                    .post(payload.toRequestBody())
                    .build()
                try {
                    callClient.newCall(req).execute().use { resp ->
                        when {
                            resp.isSuccessful -> {
                                val obj = runCatching { JsonFormat.parseToJsonElement(resp.body?.string().orEmpty()) as JsonObject }.getOrNull()
                                val fields = obj?.mapNotNull { (k, v) -> (v as? JsonPrimitive)?.contentOrNull?.let { k to it.take(80) } }?.toMap().orEmpty()
                                return@withContext com.raju.aiassistant.domain.remote.RemoteResult.Done(fields)
                            }
                            resp.code == 401 -> {
                                lastUnauthorized = true
                                return@withContext com.raju.aiassistant.domain.remote.RemoteResult.Unauthorized
                            }
                            resp.code == 400 || resp.code == 403 -> {
                                lastRefused = true
                            }
                            else -> {}
                        }
                    }
                } catch (_: Exception) {
                    // Try next candidate
                }
            }
        }

        when {
            lastUnauthorized -> com.raju.aiassistant.domain.remote.RemoteResult.Unauthorized
            lastRefused -> com.raju.aiassistant.domain.remote.RemoteResult.Refused
            else -> com.raju.aiassistant.domain.remote.RemoteResult.Unreachable
        }
    }

    suspend fun pushMemories(memories: List<Memory>): AiResult<Int> = withContext(Dispatchers.IO) {
        val candidates = getCandidateEndpoints()
        if (candidates.isEmpty()) {
            return@withContext AiResult.Failure(FailureKind.NOT_CONFIGURED, "PC sync is off: no PC Companion address is saved.")
        }
        val token = getToken()
        val payload = buildJsonObject {
            putJsonArray("memories") {
                memories.forEach { m ->
                    addJsonObject {
                        put("text", m.text)
                        put("createdAt", m.createdAt)
                    }
                }
            }
        }
        for (base in candidates) {
            val req = Request.Builder()
                .url("$base/api/v1/sync/memories")
                .auth(token)
                .post(payload.toRequestBody())
                .build()
            try {
                http.newCall(req).execute().use { resp ->
                    if (resp.isSuccessful) {
                        val body = resp.body?.string().orEmpty()
                        val obj = runCatching { JsonFormat.parseToJsonElement(body) as JsonObject }.getOrNull()
                        val total = (obj?.get("total") as? JsonPrimitive)?.intOrNull ?: memories.size
                        return@withContext AiResult.Success(total)
                    } else if (resp.code == 401) {
                        return@withContext AiResult.Failure(FailureKind.INVALID_KEY, "Authentication failed. Check your PC Companion Token.")
                    }
                }
            } catch (_: Exception) {
                // Try next candidate
            }
        }
        AiResult.Failure(FailureKind.NETWORK, "Unable to reach PC Companion")
    }

    suspend fun pullMemories(): AiResult<List<String>> = withContext(Dispatchers.IO) {
        val candidates = getCandidateEndpoints()
        if (candidates.isEmpty()) {
            return@withContext AiResult.Failure(FailureKind.NOT_CONFIGURED, "PC sync is off: no PC Companion address is saved.")
        }
        val token = getToken()
        for (base in candidates) {
            val req = Request.Builder()
                .url("$base/api/v1/sync/memories")
                .auth(token)
                .get()
                .build()
            try {
                http.newCall(req).execute().use { resp ->
                    if (resp.isSuccessful) {
                        val body = resp.body?.string().orEmpty()
                        val obj = runCatching { JsonFormat.parseToJsonElement(body) as JsonObject }.getOrNull()
                        val list = (obj?.get("memories") as? JsonArray)?.mapNotNull {
                            ((it as? JsonObject)?.get("text") as? JsonPrimitive)?.contentOrNull
                        } ?: emptyList()
                        return@withContext AiResult.Success(list)
                    } else if (resp.code == 401) {
                        return@withContext AiResult.Failure(FailureKind.INVALID_KEY, "Authentication failed. Check your PC Companion Token.")
                    }
                }
            } catch (_: Exception) {
                // Try next candidate
            }
        }
        AiResult.Failure(FailureKind.NETWORK, "Unable to reach PC Companion")
    }
}

/** Why plain http on the Wi-Fi is refused for PC sync (shown to the user). */
const val PC_SYNC_INSECURE =
    "PC sync needs an encrypted connection: use an https:// address or the PC's Tailscale name (…ts.net). " +
        "Plain http:// on Wi-Fi would let others on the network read the token and your memories, so nothing was sent."
