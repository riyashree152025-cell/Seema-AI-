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
            val url = normalizeOllamaUrl(apiKey)
            OpenAiCompatibleProvider(ProviderId.OLLAMA, http, "ollama", url)
        }
    }

    companion object {
        fun normalizeOllamaUrl(raw: String): String {
            val trimmed = raw.trim()
            if (trimmed.isBlank()) return "http://192.168.31.55:11434/v1"
            val withScheme = if (trimmed.startsWith("http://") || trimmed.startsWith("https://")) trimmed else "http://$trimmed"
            val clean = withScheme.trimEnd('/')
            val hostAndPort = clean.substringAfter("://")
            val withPort = if (!hostAndPort.contains(":")) "$clean:11434" else clean
            return if (withPort.endsWith("/v1")) withPort else "$withPort/v1"
        }
    }
}
