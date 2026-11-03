package com.raju.aiassistant.data.sync

import com.raju.aiassistant.core.security.SecureKeyStore
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

/**
 * Client for the SEEMA AI Windows PC Companion & Unified Brain Sync Server.
 * Keeps memories and conversations synchronized between phone and PC over LAN or USB ADB.
 */
class PcSyncClient(
    private val http: OkHttpClient,
    private val secureKeyStore: SecureKeyStore,
) {
    private suspend fun getEndpoint(): String {
        val raw = secureKeyStore.get(SecretId.PC_SYNC_ENDPOINT)?.trim().orEmpty()
        val defaultUrl = "http://192.168.31.55:11435"
        val endpoint = if (raw.isBlank()) defaultUrl else raw
        val withScheme = if (endpoint.startsWith("http://") || endpoint.startsWith("https://")) endpoint else "http://$endpoint"
        return withScheme.trimEnd('/')
    }

    private suspend fun getToken(): String = secureKeyStore.get(SecretId.PC_SYNC_TOKEN)?.trim().orEmpty()

    suspend fun checkStatus(): AiResult<String> = withContext(Dispatchers.IO) {
        val base = getEndpoint()
        val token = getToken()
        val req = Request.Builder()
            .url("$base/api/v1/status")
            .header("Authorization", "Bearer $token")
            .get()
            .build()
        try {
            http.newCall(req).execute().use { resp ->
                if (resp.isSuccessful) {
                    val body = resp.body?.string().orEmpty()
                    val obj = runCatching { JsonFormat.parseToJsonElement(body) as JsonObject }.getOrNull()
                    val host = (obj?.get("hostname") as? JsonPrimitive)?.contentOrNull ?: "PC"
                    AiResult.Success("PC Companion Connected ($host)")
                } else if (resp.code == 401) {
                    AiResult.Failure(FailureKind.INVALID_KEY, "Authentication failed. Check your PC Companion Token.")
                } else {
                    AiResult.Failure(FailureKind.SERVER, "PC Companion returned HTTP ${resp.code}")
                }
            }
        } catch (e: Exception) {
            AiResult.Failure(FailureKind.NETWORK, "Unable to reach PC Companion at $base")
        }
    }

    suspend fun pushMemories(memories: List<Memory>): AiResult<Int> = withContext(Dispatchers.IO) {
        val base = getEndpoint()
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
        val req = Request.Builder()
            .url("$base/api/v1/sync/memories")
            .header("Authorization", "Bearer $token")
            .post(payload.toRequestBody())
            .build()
        try {
            http.newCall(req).execute().use { resp ->
                if (resp.isSuccessful) {
                    val body = resp.body?.string().orEmpty()
                    val obj = runCatching { JsonFormat.parseToJsonElement(body) as JsonObject }.getOrNull()
                    val total = (obj?.get("total") as? JsonPrimitive)?.intOrNull ?: memories.size
                    AiResult.Success(total)
                } else {
                    AiResult.Failure(FailureKind.SERVER, "Sync failed: HTTP ${resp.code}")
                }
            }
        } catch (e: Exception) {
            AiResult.Failure(FailureKind.NETWORK, "Sync network error: ${e.message}")
        }
    }

    suspend fun pullMemories(): AiResult<List<String>> = withContext(Dispatchers.IO) {
        val base = getEndpoint()
        val token = getToken()
        val req = Request.Builder()
            .url("$base/api/v1/sync/memories")
            .header("Authorization", "Bearer $token")
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
                    AiResult.Success(list)
                } else {
                    AiResult.Failure(FailureKind.SERVER, "Pull failed: HTTP ${resp.code}")
                }
            }
        } catch (e: Exception) {
            AiResult.Failure(FailureKind.NETWORK, "Pull network error: ${e.message}")
        }
    }
}
