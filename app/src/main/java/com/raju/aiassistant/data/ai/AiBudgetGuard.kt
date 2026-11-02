package com.raju.aiassistant.data.ai

import android.content.Context
import com.raju.aiassistant.domain.ai.AiModel
import com.raju.aiassistant.domain.ai.AiProvider
import com.raju.aiassistant.domain.ai.AiResult
import com.raju.aiassistant.domain.ai.BudgetDecision
import com.raju.aiassistant.domain.ai.BudgetLimits
import com.raju.aiassistant.domain.ai.FailureKind
import com.raju.aiassistant.domain.ai.ModelPrice
import com.raju.aiassistant.domain.ai.ModelUsage
import com.raju.aiassistant.domain.ai.ProviderCooldowns
import com.raju.aiassistant.domain.ai.ProviderId
import com.raju.aiassistant.domain.ai.Turn
import com.raju.aiassistant.domain.ai.decideBudget
import com.raju.aiassistant.domain.ai.estimateCostUsd
import com.raju.aiassistant.domain.ai.estimateTokens
import com.raju.aiassistant.domain.ai.monthKey
import com.raju.aiassistant.domain.ai.price
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/** This month's estimated AI use per provider/model, plus the prices providers published. */
data class AiUsageMonth(val month: String, val models: List<ModelUsage>) {
    val totalUsd: Double get() = models.sumOf { it.costUsd }
}

/** Where usage and learned prices are kept (SharedPreferences in the app, a map in tests). */
interface UsageStorage {
    fun read(key: String): String?
    fun write(values: Map<String, String?>)
}

class PrefsUsageStorage(context: Context) : UsageStorage {
    private val prefs = context.getSharedPreferences("ai_usage", Context.MODE_PRIVATE)
    override fun read(key: String): String? = prefs.getString(key, null)
    override fun write(values: Map<String, String?>) {
        prefs.edit().apply { values.forEach { (k, v) -> if (v == null) remove(k) else putString(k, v) } }.apply()
    }
}

/**
 * Counts estimated tokens and cost per provider/model for the current month (a new month starts at
 * zero) and remembers model prices the providers published.
 */
class AiUsageStore(private val storage: UsageStorage, private val clock: () -> Long = System::currentTimeMillis) {
    private val prices = mutableMapOf<String, ModelPrice>()
    private val _usage = MutableStateFlow(load())
    val usage: StateFlow<AiUsageMonth> = _usage.asStateFlow()

    init {
        storage.read(PRICES)?.lines()?.forEach { line ->
            val p = line.split(';')
            if (p.size == 3) {
                val i = p[1].toDoubleOrNull()
                val o = p[2].toDoubleOrNull()
                if (i != null && o != null) prices[p[0]] = ModelPrice(i, o)
            }
        }
    }

    @Synchronized fun priceOf(provider: ProviderId, model: String): ModelPrice? = prices[key(provider, model)]

    /** Prices from a model list (only models with a published price are remembered). */
    @Synchronized fun learnPrices(provider: ProviderId, models: List<AiModel>) {
        var changed = false
        models.forEach { m -> m.price()?.let { if (prices.put(key(provider, m.id), it) != it) changed = true } }
        if (changed) storage.write(mapOf(PRICES to prices.entries.joinToString("\n") { (k, v) -> "$k;${v.inputUsdPerMTok};${v.outputUsdPerMTok}" }))
    }

    @Synchronized fun spentThisMonthUsd(): Double = current().totalUsd

    @Synchronized fun recordSuccess(provider: ProviderId, model: String, inputChars: Int, outputChars: Int) = update(provider, model) { u ->
        val inT = estimateTokens(inputChars)
        val outT = estimateTokens(outputChars)
        u.copy(requests = u.requests + 1, inputTokens = u.inputTokens + inT, outputTokens = u.outputTokens + outT,
            costUsd = u.costUsd + estimateCostUsd(priceOf(provider, model), inT, outT))
    }

    @Synchronized fun recordFailure(provider: ProviderId, model: String) = update(provider, model) { it.copy(failures = it.failures + 1) }

    /** The current month (starting a new, empty one on the 1st). */
    @Synchronized fun current(): AiUsageMonth {
        val month = monthKey(clock())
        if (_usage.value.month != month) _usage.value = AiUsageMonth(month, emptyList())
        return _usage.value
    }

    private fun update(provider: ProviderId, model: String, f: (ModelUsage) -> ModelUsage) {
        val now = current()
        val old = now.models.firstOrNull { it.provider == provider && it.model == model } ?: ModelUsage(provider, model)
        val next = now.copy(models = now.models.filterNot { it.provider == provider && it.model == model } + f(old))
        _usage.value = next
        storage.write(mapOf(USAGE to encode(next)))
    }

    private fun load(): AiUsageMonth {
        val month = monthKey(clock())
        val text = storage.read(USAGE) ?: return AiUsageMonth(month, emptyList())
        val lines = text.lines()
        if (lines.firstOrNull() != month) return AiUsageMonth(month, emptyList()) // last month's numbers are not carried over
        return AiUsageMonth(month, lines.drop(1).mapNotNull { line ->
            val p = line.split('\t')
            val provider = ProviderId.entries.firstOrNull { it.name == p.getOrNull(0) } ?: return@mapNotNull null
            if (p.size < 7) return@mapNotNull null
            ModelUsage(provider, p[1], p[2].toIntOrNull() ?: 0, p[3].toLongOrNull() ?: 0, p[4].toLongOrNull() ?: 0, p[5].toDoubleOrNull() ?: 0.0, p[6].toIntOrNull() ?: 0)
        })
    }

    private fun encode(m: AiUsageMonth) = (listOf(m.month) + m.models.map {
        listOf(it.provider.name, it.model.replace('\t', ' ').replace('\n', ' '), it.requests, it.inputTokens, it.outputTokens, it.costUsd, it.failures).joinToString("\t")
    }).joinToString("\n")

    private fun key(provider: ProviderId, model: String) = "${provider.name}|$model"

    private companion object {
        const val USAGE = "usage"
        const val PRICES = "prices"
    }
}

/**
 * Wraps every AI provider: before a request it checks the provider is not resting after a failure
 * and that the model is free or fits the monthly budget; afterwards it records the use. A blocked
 * request returns a failure the assistant already treats as "try another provider", so the chat
 * falls back to a free provider instead of spending money. It never retries on its own.
 */
class GuardedAiProvider(
    private val inner: AiProvider,
    private val usage: AiUsageStore,
    private val cooldowns: ProviderCooldowns,
    private val limits: () -> BudgetLimits,
) : AiProvider {
    override val id: ProviderId get() = inner.id
    override val streams: Boolean get() = inner.streams

    override suspend fun listModels(): AiResult<List<AiModel>> {
        val r = inner.listModels()
        if (r is AiResult.Success) usage.learnPrices(id, r.value)
        return r
    }

    override suspend fun chat(model: String, systemPrompt: String, turns: List<Turn>): AiResult<String> =
        guarded(model, systemPrompt, turns) { inner.chat(model, systemPrompt, turns) }

    override suspend fun chatStream(model: String, systemPrompt: String, turns: List<Turn>, onDelta: (String) -> Unit): AiResult<String> =
        guarded(model, systemPrompt, turns) { inner.chatStream(model, systemPrompt, turns, onDelta) }

    private suspend fun guarded(model: String, systemPrompt: String, turns: List<Turn>, call: suspend () -> AiResult<String>): AiResult<String> {
        cooldowns.active(id)?.let { (ms, kind) ->
            return AiResult.Failure(kind, "${id.shortName} is resting for ${ms / 1000 + 1} s after: ${kind.userMessage}.")
        }
        val inputChars = systemPrompt.length + turns.sumOf { it.text.length }
        when (val d = decideBudget(id, model, usage.priceOf(id, model), limits(), usage.spentThisMonthUsd(), inputChars)) {
            is BudgetDecision.Blocked -> return AiResult.Failure(FailureKind.QUOTA_EXCEEDED, d.reason)
            is BudgetDecision.Allowed -> Unit
        }
        val r = call()
        when (r) {
            is AiResult.Success -> usage.recordSuccess(id, model, inputChars, r.value.length)
            is AiResult.Failure -> {
                usage.recordFailure(id, model)
                cooldowns.record(id, r.kind)
            }
        }
        return r
    }
}
