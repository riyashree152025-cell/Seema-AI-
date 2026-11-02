package com.raju.aiassistant.domain.ai

import java.time.Instant
import java.time.ZoneId
import kotlin.math.ceil

/**
 * Free-first, fail-closed cost control for the AI brain (Settings → AI & Brain → Budget).
 *
 * The app never pays or buys credits; it can only decide not to send a request. Real charges are
 * made by the provider account the user set up, so everything here is an estimate, and paid
 * routes are used only when the price is published and the estimate stays inside the budget.
 */
enum class CostClass(val label: String) {
    /** The provider published a price of 0 for this model (e.g. OpenRouter ":free" models). */
    FREE("free"),
    /**
     * The provider has a free tier; it can only cost money if billing was enabled on that account.
     * The app cannot see the invoice, so it counts these as free and says so.
     */
    FREE_TIER("free tier"),
    /** A published price above 0. */
    PAID("paid"),
    /** No published price: never used automatically (fail closed). */
    UNKNOWN("price unknown"),
}

data class ModelPrice(val inputUsdPerMTok: Double, val outputUsdPerMTok: Double) {
    val isFree: Boolean get() = inputUsdPerMTok == 0.0 && outputUsdPerMTok == 0.0
}

fun AiModel.price(): ModelPrice? =
    if (inputUsdPerMTok != null && outputUsdPerMTok != null) ModelPrice(inputUsdPerMTok, outputUsdPerMTok) else null

/** Providers whose own docs offer a free tier for their hosted chat models. */
private val FREE_TIER_PROVIDERS = setOf(
    ProviderId.GEMINI, ProviderId.GROQ, ProviderId.CEREBRAS, ProviderId.SAMBANOVA, ProviderId.NVIDIA, ProviderId.CLOUDFLARE,
)

fun costClass(provider: ProviderId, model: String, price: ModelPrice?): CostClass = when {
    price != null -> if (price.isFree) CostClass.FREE else CostClass.PAID
    provider == ProviderId.OPENROUTER -> if (model.endsWith(":free")) CostClass.FREE else CostClass.UNKNOWN
    provider in FREE_TIER_PROVIDERS -> CostClass.FREE_TIER
    else -> CostClass.UNKNOWN
}

/** User limits. Raising [monthlyCapUsd] or turning [freeOnly] off is the user's explicit permission. */
data class BudgetLimits(val monthlyCapUsd: Double = DEFAULT_MONTHLY_CAP_USD, val freeOnly: Boolean = true)

const val DEFAULT_MONTHLY_CAP_USD = 5.0
/** Only this share of the cap is spent, leaving room for currency conversion, taxes and estimate errors. */
const val BUDGET_SAFETY_SHARE = 0.8
/** Longest reply assumed when estimating a request's cost before it is sent. */
const val ASSUMED_REPLY_CHARS = 4_000

/**
 * Tokens estimated from characters, on the high side on purpose: Hindi (Devanagari) text uses far
 * more tokens per character than English, so 1 token per 2 characters over-estimates both.
 */
fun estimateTokens(chars: Int): Long = ceil(chars.coerceAtLeast(0) / 2.0).toLong()

fun estimateCostUsd(price: ModelPrice?, inputTokens: Long, outputTokens: Long): Double =
    if (price == null) 0.0 else (inputTokens * price.inputUsdPerMTok + outputTokens * price.outputUsdPerMTok) / 1_000_000.0

sealed interface BudgetDecision {
    data class Allowed(val costClass: CostClass, val estimatedUsd: Double) : BudgetDecision
    data class Blocked(val reason: String) : BudgetDecision
}

/** Whether a request may be sent, before it is sent. */
fun decideBudget(
    provider: ProviderId,
    model: String,
    price: ModelPrice?,
    limits: BudgetLimits,
    spentThisMonthUsd: Double,
    inputChars: Int,
): BudgetDecision {
    val cls = costClass(provider, model, price)
    return when (cls) {
        CostClass.FREE, CostClass.FREE_TIER -> BudgetDecision.Allowed(cls, 0.0)
        CostClass.UNKNOWN -> BudgetDecision.Blocked("${provider.shortName} model \"$model\" has no published price, so it is not used (cost safety). Choose a free model.")
        CostClass.PAID -> {
            val estimate = estimateCostUsd(price, estimateTokens(inputChars), estimateTokens(ASSUMED_REPLY_CHARS))
            val usable = limits.monthlyCapUsd * BUDGET_SAFETY_SHARE
            when {
                limits.freeOnly -> BudgetDecision.Blocked("Free-only mode is on: paid model \"$model\" is not used.")
                spentThisMonthUsd + estimate > usable ->
                    BudgetDecision.Blocked("Monthly AI budget reached (≈ $${"%.2f".format(spentThisMonthUsd)} of $${"%.2f".format(limits.monthlyCapUsd)}): paid model \"$model\" is paused until next month.")
                else -> BudgetDecision.Allowed(cls, estimate)
            }
        }
    }
}

/** "2026-11": usage is counted per calendar month and starts at zero on the 1st. */
fun monthKey(epochMs: Long, zone: ZoneId = ZoneId.systemDefault()): String {
    val d = Instant.ofEpochMilli(epochMs).atZone(zone)
    return "%04d-%02d".format(d.year, d.monthValue)
}

/** One provider/model's use in a month (tokens and cost are estimates). */
data class ModelUsage(
    val provider: ProviderId,
    val model: String,
    val requests: Int = 0,
    val inputTokens: Long = 0,
    val outputTokens: Long = 0,
    val costUsd: Double = 0.0,
    val failures: Int = 0,
)

/**
 * After a failure a provider rests for a while instead of being asked again at once, so a bad key
 * or a rate limit never causes a burst of retries; the chat falls back to another provider.
 */
fun cooldownMsFor(kind: FailureKind): Long = when (kind) {
    FailureKind.RATE_LIMITED -> 60_000
    FailureKind.QUOTA_EXCEEDED -> 30 * 60_000
    FailureKind.INVALID_KEY -> 10 * 60_000 // cleared at once when a new key is saved
    FailureKind.SERVER -> 20_000
    else -> 0
}

class ProviderCooldowns(private val clock: () -> Long = System::currentTimeMillis) {
    private val until = mutableMapOf<ProviderId, Pair<Long, FailureKind>>()

    @Synchronized fun record(provider: ProviderId, kind: FailureKind) {
        val ms = cooldownMsFor(kind)
        if (ms > 0) until[provider] = (clock() + ms) to kind
    }

    /** Remaining rest in ms and why, or null when the provider may be asked. */
    @Synchronized fun active(provider: ProviderId): Pair<Long, FailureKind>? {
        val (end, kind) = until[provider] ?: return null
        val left = end - clock()
        if (left <= 0) { until.remove(provider); return null }
        return left to kind
    }

    @Synchronized fun clear(provider: ProviderId) { until.remove(provider) }

    @Synchronized fun snapshot(): Map<ProviderId, Pair<Long, FailureKind>> = ProviderId.entries.mapNotNull { p -> active(p)?.let { p to it } }.toMap()
}
