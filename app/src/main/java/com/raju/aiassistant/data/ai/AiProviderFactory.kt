package com.raju.aiassistant.data.ai

import com.raju.aiassistant.domain.ai.AiProvider
import com.raju.aiassistant.domain.ai.ProviderId
import okhttp3.OkHttpClient

class AiProviderFactory(private val http: OkHttpClient) {
    fun create(id: ProviderId, apiKey: String): AiProvider = when (id) {
        ProviderId.GEMINI -> GeminiProvider(http, apiKey)
        ProviderId.GROQ -> OpenAiCompatibleProvider(ProviderId.GROQ, http, apiKey, GROQ_BASE_URL)
        ProviderId.CEREBRAS -> OpenAiCompatibleProvider(ProviderId.CEREBRAS, http, apiKey, CEREBRAS_BASE_URL)
        ProviderId.OPENROUTER -> OpenAiCompatibleProvider(ProviderId.OPENROUTER, http, apiKey, OPENROUTER_BASE_URL)
        ProviderId.DEEPSEEK -> OpenAiCompatibleProvider(ProviderId.DEEPSEEK, http, apiKey, DEEPSEEK_BASE_URL)
        ProviderId.OLLAMA -> {
            val url = if (apiKey.startsWith("http://") || apiKey.startsWith("https://")) {
                apiKey.trimEnd('/') + (if (apiKey.contains("/v1")) "" else "/v1")
            } else {
                OLLAMA_DEFAULT_URL
            }
            OpenAiCompatibleProvider(ProviderId.OLLAMA, http, if (apiKey.startsWith("http")) "ollama" else apiKey, url)
        }
    }
}
