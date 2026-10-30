package com.raju.aiassistant.domain.ai

import com.raju.aiassistant.domain.model.SecretId

/** Identifies an AI backend. New providers (e.g. OpenAI) are added here plus an adapter. */
enum class ProviderId(val displayName: String, val shortName: String, val secret: SecretId) {
    GEMINI("Google Gemini", "Gemini", SecretId.GEMINI_API_KEY),
    GROQ("Groq", "Groq", SecretId.GROQ_API_KEY),
    CEREBRAS("Cerebras", "Cerebras", SecretId.CEREBRAS_API_KEY),
}

data class AiModel(val id: String, val displayName: String)

/** Outcome of a remote call. Errors are explicit so the UI never shows fake success. */
sealed interface AiResult<out T> {
    data class Success<T>(val value: T) : AiResult<T>
    data class Failure(val kind: FailureKind, val message: String) : AiResult<Nothing>
}

enum class FailureKind(val userMessage: String) {
    NOT_CONFIGURED("API key is not set"),
    INVALID_KEY("API key was rejected"),
    RATE_LIMITED("Too many requests — wait a moment"),
    QUOTA_EXCEEDED("Quota exceeded for this key"),
    TIMEOUT("Request timed out"),
    NETWORK("Network error"),
    SERVER("Provider server error"),
    BLOCKED("Response blocked by the provider's safety filter"),
    UNKNOWN("Unexpected error"),
}

enum class TurnRole { USER, ASSISTANT }

/** One message sent to a provider. Consecutive same-role turns are already merged. */
data class Turn(val role: TurnRole, val text: String)

/** Adapter contract every AI provider implements. */
interface AiProvider {
    val id: ProviderId

    /** Performs a real authenticated request and reports the genuine result. */
    suspend fun testConnection(): AiResult<Unit> = when (val r = listModels()) {
        is AiResult.Success -> AiResult.Success(Unit)
        is AiResult.Failure -> r
    }

    /** Lists chat models actually available to the configured key. */
    suspend fun listModels(): AiResult<List<AiModel>>

    suspend fun chat(model: String, systemPrompt: String, turns: List<Turn>): AiResult<String>

    /**
     * Like [chat], but [onDelta] receives the reply piece by piece while it is generated, so speech
     * can start before the whole answer is written. Returns the full text. Providers without
     * streaming deliver the whole reply as one piece.
     */
    /** True when [chatStream] really delivers the reply piece by piece (then speech can start early). */
    val streams: Boolean get() = false

    suspend fun chatStream(model: String, systemPrompt: String, turns: List<Turn>, onDelta: (String) -> Unit): AiResult<String> =
        chat(model, systemPrompt, turns).also { if (it is AiResult.Success) onDelta(it.value) }
}
