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
 * Adapter for OpenAI-compatible chat APIs (Groq, Cerebras). Another such provider only needs a
 * new [ProviderId] and base URL.
 */
class OpenAiCompatibleProvider(
    override val id: ProviderId,
    private val http: OkHttpClient,
    private val apiKey: String,
    private val baseUrl: String,
) : AiProvider {

    override suspend fun listModels(): AiResult<List<AiModel>> = withRetry {
        val request = Request.Builder().url("$baseUrl/models").header("Authorization", "Bearer $apiKey").get().build()
        when (val r = http.fetch(request)) {
            is AiResult.Failure -> r
            is AiResult.Success -> {
                val (code, body) = r.value
                if (code !in 200..299) httpFailure(code, body) else {
                    val root = runCatching { JsonFormat.parseToJsonElement(body) as JsonObject }.getOrNull()
                    if (root == null) AiResult.Failure(FailureKind.UNKNOWN, "Unreadable model list")
                    else AiResult.Success(
                        (root["data"] as? JsonArray).orEmpty().mapNotNull { el ->
                            ((el as? JsonObject)?.get("id") as? JsonPrimitive)?.contentOrNull?.let { AiModel(it, it) }
                        }.sortedBy { it.id }
                    )
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
