package com.raju.aiassistant.data.ai

import com.raju.aiassistant.domain.ai.AiProvider
import com.raju.aiassistant.domain.ai.AiModel
import com.raju.aiassistant.domain.ai.AiResult
import com.raju.aiassistant.domain.ai.FailureKind
import com.raju.aiassistant.domain.ai.ProviderId
import com.raju.aiassistant.domain.ai.Turn
import okhttp3.OkHttpClient

/** [cloudflareAccountId] = the saved Cloudflare account ID (part of the Workers AI URL). */
class AiProviderFactory(private val http: OkHttpClient, private val cloudflareAccountId: suspend () -> String? = { null }) {
    fun create(id: ProviderId, apiKey: String): AiProvider = when (id) {
        ProviderId.GEMINI -> GeminiProvider(http, apiKey)
        ProviderId.GROQ -> OpenAiCompatibleProvider(ProviderId.GROQ, http, apiKey, GROQ_BASE_URL)
        ProviderId.CEREBRAS -> OpenAiCompatibleProvider(ProviderId.CEREBRAS, http, apiKey, CEREBRAS_BASE_URL)
        ProviderId.SAMBANOVA -> OpenAiCompatibleProvider(ProviderId.SAMBANOVA, http, apiKey, SAMBANOVA_BASE_URL)
        ProviderId.NVIDIA -> OpenAiCompatibleProvider(ProviderId.NVIDIA, http, apiKey, NVIDIA_BASE_URL)
        ProviderId.OPENROUTER -> OpenAiCompatibleProvider(ProviderId.OPENROUTER, http, apiKey, OPENROUTER_BASE_URL)
        ProviderId.CLOUDFLARE -> CloudflareProvider(http, apiKey, cloudflareAccountId)
    }
}

/**
 * Workers AI: the URL contains the account ID, which is read when a request is made. Without it
 * every call reports what is missing (never a fake success).
 */
private class CloudflareProvider(
    private val http: OkHttpClient,
    private val apiKey: String,
    private val accountId: suspend () -> String?,
) : AiProvider {
    override val id = ProviderId.CLOUDFLARE
    override val streams = true

    private suspend fun inner(): AiProvider? = accountId()?.takeIf { it.isNotBlank() }?.let {
        OpenAiCompatibleProvider(ProviderId.CLOUDFLARE, http, apiKey, cloudflareBaseUrl(it), cloudflareModelsUrl(it))
    }

    private val missing = AiResult.Failure(FailureKind.NOT_CONFIGURED, "Cloudflare account ID is not set (Settings → AI & Brain → Cloudflare account ID).")

    override suspend fun listModels(): AiResult<List<AiModel>> = inner()?.listModels() ?: missing
    override suspend fun chat(model: String, systemPrompt: String, turns: List<Turn>): AiResult<String> = inner()?.chat(model, systemPrompt, turns) ?: missing
    override suspend fun chatStream(model: String, systemPrompt: String, turns: List<Turn>, onDelta: (String) -> Unit): AiResult<String> =
        inner()?.chatStream(model, systemPrompt, turns, onDelta) ?: missing
}
