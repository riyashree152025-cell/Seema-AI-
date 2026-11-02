package com.raju.aiassistant.domain.ai

/**
 * Guidance about provider models for a Hindi/Hinglish/English assistant. Model lists come live from
 * the provider; this only ranks and annotates them (checked against provider model cards, Oct 2026).
 */
object ModelAdvice {
    /** Models that are not general chat models (speech, safety classifiers, embeddings, agents). */
    private val NOT_CHAT = listOf("image", "vision", "embed", "tts", "audio", "whisper", "guard", "playai", "compound", "orpheus")

    /** Language-specialised models that are poor at Hindi (e.g. ALLaM is an Arabic–English model with a ~4K context). */
    private val NOT_FOR_HINDI = listOf("allam", "saba")

    private val SIZE = Regex("(?<![0-9.])([0-9]+(?:\\.[0-9]+)?)b(?![a-z])", RegexOption.IGNORE_CASE)

    /** Preferred defaults, best first. Matched by substring. */
    private val PREFERRED = mapOf(
        ProviderId.GEMINI to listOf("gemini-2.5-flash", "gemini-2.0-flash", "flash"),
        ProviderId.GROQ to listOf("llama-3.3-70b", "gpt-oss-120b", "llama-4", "qwen3", "llama-3.1-8b", "llama"),
        // The list itself comes live from the key's /models endpoint; this only orders it.
        ProviderId.CEREBRAS to listOf("llama-3.3-70b", "gpt-oss-120b", "qwen-3", "llama"),
        // Ordering hints only; what is offered always comes from the key's own model list.
        ProviderId.SAMBANOVA to listOf("llama-3.3-70b", "deepseek-v3", "llama-4", "llama"),
        ProviderId.NVIDIA to listOf("llama-3.3-70b-instruct", "llama-4", "nemotron", "llama"),
        ProviderId.OPENROUTER to listOf("llama-3.3-70b-instruct:free", "deepseek", "gemini", ":free"),
        ProviderId.CLOUDFLARE to listOf("llama-3.3-70b-instruct", "llama-4", "llama-3.1-8b-instruct", "llama"),
    )

    fun isChatModel(id: String) = NOT_CHAT.none { id.contains(it, ignoreCase = true) }

    /** Non-null when the model is a poor fit for this assistant; shown next to the model in Settings and Chat. */
    fun warning(id: String?): String? = when {
        id == null -> null
        NOT_FOR_HINDI.any { id.contains(it, ignoreCase = true) } ->
            "This model is built for Arabic–English and has a small context. Hindi replies may be wrong or repetitive — choose a recommended model."
        !isChatModel(id) -> "This is not a chat model. Choose a recommended model."
        else -> null
    }

    /** Parameter count in billions from the model id ("llama-3.2-3b-instruct" → 3, "mixtral-8x7b" → 7), or null. */
    fun sizeInBillions(id: String): Double? =
        SIZE.findAll(id).mapNotNull { it.groupValues[1].toDoubleOrNull() }.maxOrNull()

    /** Small models (under 12B) are fast but weak at Hindi and following instructions: never "recommended". */
    fun isSmallModel(id: String): Boolean = sizeInBillions(id)?.let { it < 12 } ?: false

    fun isRecommended(provider: ProviderId, id: String): Boolean =
        warning(id) == null && !isSmallModel(id) && PREFERRED[provider].orEmpty().take(4).any { id.contains(it, ignoreCase = true) } &&
            !(provider == ProviderId.GEMINI && id.contains("lite", ignoreCase = true))

    /** Recommended models first, unsuitable ones last, the rest alphabetical. */
    fun rank(provider: ProviderId, models: List<AiModel>): List<AiModel> = models.sortedWith(
        compareBy<AiModel>(
            { if (isRecommended(provider, it.id)) 0 else if (warning(it.id) == null) 1 else 2 },
            { m -> PREFERRED[provider].orEmpty().indexOfFirst { m.id.contains(it, ignoreCase = true) }.let { if (it < 0) Int.MAX_VALUE else it } },
            { it.id },
        )
    )

    /** Rough history budget in characters, so small-context models are not overflowed. */
    fun historyCharBudget(id: String): Int = if (NOT_FOR_HINDI.any { id.contains(it, ignoreCase = true) }) 6_000 else 24_000
}

/**
 * Picks a sensible default when the user has not chosen a model yet. Free-first: a model with a
 * price (or an unknown price) is never picked automatically while a free one exists.
 */
fun pickDefaultModel(provider: ProviderId, models: List<AiModel>): AiModel? {
    val free = models.filter { costClass(provider, it.id, it.price()).let { c -> c == CostClass.FREE || c == CostClass.FREE_TIER } }
    val pool = free.ifEmpty { models }
    return ModelAdvice.rank(provider, pool).firstOrNull { ModelAdvice.warning(it.id) == null } ?: pool.firstOrNull()
}
