package com.raju.aiassistant.data.ai

import com.raju.aiassistant.domain.ai.AiModel
import com.raju.aiassistant.domain.ai.AiProvider
import com.raju.aiassistant.domain.ai.AiResult
import com.raju.aiassistant.domain.ai.FailureKind
import com.raju.aiassistant.domain.ai.ProviderId
import com.raju.aiassistant.domain.ai.Turn
import com.raju.aiassistant.domain.ai.TurnRole
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.addJsonObject
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.put
import kotlinx.serialization.json.putJsonArray
import okhttp3.OkHttpClient
import okhttp3.Request

/**
 * Adapter for OpenAI-compatible chat APIs (Groq, Cerebras, SambaNova, NVIDIA NIM, OpenRouter,
 * Cloudflare Workers AI). Another such provider only needs a new [ProviderId] and base URL.
 * [modelsUrl] is where the provider lists its models (Cloudflare uses its own search endpoint).
 */
class OpenAiCompatibleProvider(
    override val id: ProviderId,
    private val http: OkHttpClient,
    private val apiKey: String,
    private val baseUrl: String,
    private val modelsUrl: String = "$baseUrl/models",
) : AiProvider {

    override suspend fun listModels(): AiResult<List<AiModel>> = withRetry {
        val request = Request.Builder().url(modelsUrl).header("Authorization", "Bearer $apiKey").get().build()
        when (val r = http.fetch(request)) {
            is AiResult.Failure -> r
            is AiResult.Success -> {
                val (code, body) = r.value
                if (code !in 200..299) httpFailure(code, body) else {
                    val root = runCatching { JsonFormat.parseToJsonElement(body) as JsonObject }.getOrNull()
                    if (root == null) AiResult.Failure(FailureKind.UNKNOWN, "Unreadable model list")
                    else AiResult.Success(parseModelList(root))
                }
            }
        }
    }

    private fun payload(model: String, systemPrompt: String, turns: List<Turn>, stream: Boolean) = buildJsonObject {
        put("model", model)
        put("temperature", 0.7)
        put("max_tokens", 4096)
        // gpt-oss models reason before answering; "low" keeps spoken replies fast (Groq supports it).
        if (model.contains("gpt-oss")) put("reasoning_effort", "low")
        if (stream) put("stream", true)
        putJsonArray("messages") {
            addJsonObject { put("role", "system"); put("content", systemPrompt) }
            turns.forEach { t ->
                addJsonObject {
                    put("role", if (t.role == TurnRole.ASSISTANT) "assistant" else "user")
                    put("content", t.text)
                }
            }
        }
    }

    private fun request(body: kotlinx.serialization.json.JsonObject) = Request.Builder()
        .url("$baseUrl/chat/completions")
        .header("Authorization", "Bearer $apiKey")
        .post(body.toRequestBody())
        .build()

    override suspend fun chat(model: String, systemPrompt: String, turns: List<Turn>): AiResult<String> = withRetry {
        when (val r = http.fetch(request(payload(model, systemPrompt, turns, stream = false)))) {
            is AiResult.Failure -> r
            is AiResult.Success -> {
                val (code, body) = r.value
                if (code !in 200..299) httpFailure(code, body) else parseReply(body)
            }
        }
    }

    override val streams = true

    override suspend fun chatStream(model: String, systemPrompt: String, turns: List<Turn>, onDelta: (String) -> Unit): AiResult<String> {
        val text = StringBuilder()
        var finish: String? = null
        var cut: AiResult.Failure? = null
        // A failure before any text arrived is retried like a normal request; never after (no duplicates).
        val result = withRetry {
            val r = http.streamSse(request(payload(model, systemPrompt, turns, stream = true))) { data ->
                val choice = (runCatching { JsonFormat.parseToJsonElement(data) as JsonObject }.getOrNull()
                    ?.get("choices") as? JsonArray)?.firstOrNull() as? JsonObject ?: return@streamSse
                ((choice["delta"] as? JsonObject)?.get("content") as? JsonPrimitive)?.contentOrNull?.takeIf { it.isNotEmpty() }?.let {
                    text.append(it)
                    onDelta(it)
                }
                (choice["finish_reason"] as? JsonPrimitive)?.contentOrNull?.let { finish = it }
            }
            if (r is AiResult.Failure && text.isNotEmpty()) { cut = r; AiResult.Success(Unit) } else r
        }
        return streamedReply(result, text, if (finish == "length") TRUNCATED_NOTE else "", cut)
    }

    private fun parseReply(body: String): AiResult<String> {
        val root = runCatching { JsonFormat.parseToJsonElement(body) as JsonObject }.getOrNull()
            ?: return AiResult.Failure(FailureKind.UNKNOWN, "Unreadable response")
        val choice = (root["choices"] as? JsonArray)?.firstOrNull() as? JsonObject
        val text = ((choice?.get("message") as? JsonObject)?.get("content") as? JsonPrimitive)?.contentOrNull
        val finish = (choice?.get("finish_reason") as? JsonPrimitive)?.contentOrNull
        return if (!text.isNullOrBlank()) AiResult.Success(text.trim() + if (finish == "length") TRUNCATED_NOTE else "")
        else AiResult.Failure(FailureKind.UNKNOWN, "Empty response")
    }
}

const val GROQ_BASE_URL = "https://api.groq.com/openai/v1"
const val CEREBRAS_BASE_URL = "https://api.cerebras.ai/v1"
const val SAMBANOVA_BASE_URL = "https://api.sambanova.ai/v1"
const val NVIDIA_BASE_URL = "https://integrate.api.nvidia.com/v1"
const val OPENROUTER_BASE_URL = "https://openrouter.ai/api/v1"

/** Workers AI's OpenAI-compatible endpoint is per account. */
fun cloudflareBaseUrl(accountId: String) = "https://api.cloudflare.com/client/v4/accounts/${accountId.trim()}/ai/v1"

/** Workers AI lists models with its own search API (text-generation models only). */
fun cloudflareModelsUrl(accountId: String) =
    "https://api.cloudflare.com/client/v4/accounts/${accountId.trim()}/ai/models/search?task=Text%20Generation&per_page=100"

/**
 * Models from an OpenAI-style list (`{"data":[{"id":…}]}`) or Cloudflare's search result
 * (`{"result":[{"name":…}]}`). OpenRouter's `pricing` (US$ per token, as strings) becomes US$ per
 * million tokens; anything without a published price stays null (= unknown).
 */
internal fun parseModelList(root: JsonObject): List<AiModel> {
    val items = (root["data"] as? JsonArray) ?: (root["result"] as? JsonArray) ?: JsonArray(emptyList())
    return items.mapNotNull { el ->
        val o = el as? JsonObject ?: return@mapNotNull null
        val id = (o["id"] as? JsonPrimitive)?.contentOrNull ?: (o["name"] as? JsonPrimitive)?.contentOrNull ?: return@mapNotNull null
        val pricing = o["pricing"] as? JsonObject
        fun perMTok(key: String) = (pricing?.get(key) as? JsonPrimitive)?.contentOrNull?.toDoubleOrNull()?.takeIf { it >= 0 }?.let { it * 1_000_000 }
        AiModel(id, id, perMTok("prompt"), perMTok("completion"))
    }.distinctBy { it.id }.sortedBy { it.id }
}
