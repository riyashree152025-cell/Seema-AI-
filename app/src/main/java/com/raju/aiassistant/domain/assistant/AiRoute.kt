package com.raju.aiassistant.domain.assistant

import com.raju.aiassistant.domain.ai.ProviderId

/** The last real AI request: which provider was asked, which one answered (null = none did), and why not. */
data class AiRoute(
    val requested: ProviderId,
    val answeredBy: ProviderId?,
    val model: String?,
    val at: Long,
    val error: String?,
) {
    val usedFallback: Boolean get() = answeredBy != null && answeredBy != requested
}

/** Result of Settings → AI & Brain → Test active AI (a real request through the chat path). */
data class ActiveAiTest(
    val requested: ProviderId,
    val answeredBy: ProviderId?,
    val model: String?,
    val elapsedMs: Long,
    val reply: String?,
    val error: String?,
    /** Set when the chosen provider failed and another configured provider answered instead. */
    val fallbackReason: String? = null,
) {
    val ok: Boolean get() = answeredBy != null && error == null
    val usedFallback: Boolean get() = answeredBy != null && answeredBy != requested
}
