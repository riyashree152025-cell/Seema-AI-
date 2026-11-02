package com.raju.aiassistant.data.ai

import com.raju.aiassistant.domain.ai.AiModel
import com.raju.aiassistant.domain.ai.AiProvider
import com.raju.aiassistant.domain.ai.AiResult
import com.raju.aiassistant.domain.ai.BudgetDecision
import com.raju.aiassistant.domain.ai.BudgetLimits
import com.raju.aiassistant.domain.ai.CostClass
import com.raju.aiassistant.domain.ai.FailureKind
import com.raju.aiassistant.domain.ai.ModelPrice
import com.raju.aiassistant.domain.ai.ProviderCooldowns
import com.raju.aiassistant.domain.ai.ProviderId
import com.raju.aiassistant.domain.ai.Turn
import com.raju.aiassistant.domain.ai.TurnRole
import com.raju.aiassistant.domain.ai.costClass
import com.raju.aiassistant.domain.ai.decideBudget
import com.raju.aiassistant.domain.ai.monthKey
import com.raju.aiassistant.domain.ai.pickDefaultModel
import kotlinx.coroutines.runBlocking
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.ZoneOffset

class BudgetGuardTest {
    private val paid = ModelPrice(3.0, 15.0)

    // ---------------------------------------------------------------- decisions

    @Test fun freeAndFreeTierModelsAreAlwaysAllowed() {
        assertTrue(decideBudget(ProviderId.GROQ, "llama-3.3-70b-versatile", null, BudgetLimits(), 999.0, 10_000) is BudgetDecision.Allowed)
        assertTrue(decideBudget(ProviderId.OPENROUTER, "meta-llama/llama-3.3-70b-instruct:free", null, BudgetLimits(), 999.0, 10_000) is BudgetDecision.Allowed)
        assertTrue(decideBudget(ProviderId.OPENROUTER, "x/y", ModelPrice(0.0, 0.0), BudgetLimits(), 999.0, 10_000) is BudgetDecision.Allowed)
    }

    @Test fun unknownPriceFailsClosedEvenWithBudgetLeft() {
        assertEquals(CostClass.UNKNOWN, costClass(ProviderId.OPENROUTER, "openai/gpt-4o", null))
        assertTrue(decideBudget(ProviderId.OPENROUTER, "openai/gpt-4o", null, BudgetLimits(freeOnly = false), 0.0, 100) is BudgetDecision.Blocked)
    }

    @Test fun freeOnlyModeBlocksPaidModels() {
        val d = decideBudget(ProviderId.OPENROUTER, "anthropic/x", paid, BudgetLimits(freeOnly = true), 0.0, 100)
        assertTrue(d is BudgetDecision.Blocked && d.reason.contains("Free-only"))
    }

    @Test fun paidModelsStopBeforeTheMonthlyLimitWithASafetyMargin() {
        val limits = BudgetLimits(monthlyCapUsd = 5.0, freeOnly = false)
        assertTrue(decideBudget(ProviderId.OPENROUTER, "anthropic/x", paid, limits, 0.0, 2_000) is BudgetDecision.Allowed)
        // $3.98 spent: 80 % of $5 = $4 is the usable part; this request's estimate (≈ $0.033) does not fit any more.
        assertTrue(decideBudget(ProviderId.OPENROUTER, "anthropic/x", paid, limits, 3.98, 2_000) is BudgetDecision.Blocked)
        assertTrue(decideBudget(ProviderId.OPENROUTER, "anthropic/x", paid, limits, 5.0, 10) is BudgetDecision.Blocked)
        // A limit of 0 means no paid AI at all.
        assertTrue(decideBudget(ProviderId.OPENROUTER, "anthropic/x", paid, BudgetLimits(0.0, freeOnly = false), 0.0, 10) is BudgetDecision.Blocked)
    }

    @Test fun theDefaultModelIsFreeFirst() {
        val models = listOf(
            AiModel("aaa/expensive", "x", 5.0, 20.0),
            AiModel("bbb/unknown", "x"),
            AiModel("meta-llama/llama-3.3-70b-instruct:free", "x", 0.0, 0.0),
        )
        assertEquals("meta-llama/llama-3.3-70b-instruct:free", pickDefaultModel(ProviderId.OPENROUTER, models)?.id)
    }

    // ---------------------------------------------------------------- model lists

    @Test fun openRouterPricesAreReadAndCloudflareNamesToo() {
        val or = Json.parseToJsonElement(
            """{"data":[{"id":"a/free:free","pricing":{"prompt":"0","completion":"0"}},{"id":"b/paid","pricing":{"prompt":"0.000003","completion":"0.000015"}},{"id":"c/none"}]}""",
        ) as JsonObject
        val list = parseModelList(or)
        assertEquals(0.0, list.first { it.id == "a/free:free" }.inputUsdPerMTok!!, 1e-9)
        assertEquals(3.0, list.first { it.id == "b/paid" }.inputUsdPerMTok!!, 1e-9)
        assertEquals(15.0, list.first { it.id == "b/paid" }.outputUsdPerMTok!!, 1e-9)
        assertNull(list.first { it.id == "c/none" }.inputUsdPerMTok)
        val cf = Json.parseToJsonElement("""{"success":true,"result":[{"name":"@cf/meta/llama-3.1-8b-instruct","task":{"name":"Text Generation"}}]}""") as JsonObject
        assertEquals(listOf("@cf/meta/llama-3.1-8b-instruct"), parseModelList(cf).map { it.id })
    }

    // ---------------------------------------------------------------- guard

    private class MapStorage : UsageStorage {
        val map = mutableMapOf<String, String>()
        override fun read(key: String) = map[key]
        override fun write(values: Map<String, String?>) { values.forEach { (k, v) -> if (v == null) map.remove(k) else map[k] = v } }
    }

    private class Fake(override val id: ProviderId, var reply: AiResult<String> = AiResult.Success("Namaste")) : AiProvider {
        var calls = 0
        override suspend fun listModels(): AiResult<List<AiModel>> = AiResult.Success(listOf(AiModel("b/paid", "b", 3.0, 15.0)))
        override suspend fun chat(model: String, systemPrompt: String, turns: List<Turn>): AiResult<String> { calls++; return reply }
    }

    private val turns = listOf(Turn(TurnRole.USER, "hello"))

    @Test fun blockedPaidRequestIsNeverSentAndFallsBack() = runBlocking {
        val store = AiUsageStore(MapStorage())
        val inner = Fake(ProviderId.OPENROUTER)
        val guard = GuardedAiProvider(inner, store, ProviderCooldowns()) { BudgetLimits(freeOnly = true) }
        guard.listModels() // learns the published price
        val r = guard.chat("b/paid", "sys", turns)
        assertTrue(r is AiResult.Failure && r.kind == FailureKind.QUOTA_EXCEEDED) // = "try another provider"
        assertEquals(0, inner.calls) // nothing was sent, nothing spent
    }

    @Test fun usageAndCostAreCountedPerModel() = runBlocking {
        val store = AiUsageStore(MapStorage())
        val inner = Fake(ProviderId.OPENROUTER)
        val guard = GuardedAiProvider(inner, store, ProviderCooldowns()) { BudgetLimits(freeOnly = false) }
        guard.listModels()
        assertTrue(guard.chat("b/paid", "sys", turns) is AiResult.Success)
        val u = store.usage.value.models.single()
        assertEquals(1, u.requests)
        assertTrue(u.costUsd > 0)
        assertTrue(store.spentThisMonthUsd() > 0)
    }

    @Test fun aFailingProviderRestsInsteadOfBeingRetried() = runBlocking {
        var now = 0L
        val cooldowns = ProviderCooldowns { now }
        val inner = Fake(ProviderId.GROQ, AiResult.Failure(FailureKind.RATE_LIMITED, "Too many requests (HTTP 429)"))
        val guard = GuardedAiProvider(inner, AiUsageStore(MapStorage()), cooldowns) { BudgetLimits() }
        guard.chat("m", "s", turns)
        val second = guard.chat("m", "s", turns)
        assertTrue(second is AiResult.Failure && second.kind == FailureKind.RATE_LIMITED)
        assertEquals(1, inner.calls) // the second request was not sent
        now += 61_000
        inner.reply = AiResult.Success("ok")
        assertTrue(guard.chat("m", "s", turns) is AiResult.Success)
        assertEquals(2, inner.calls)
    }

    @Test fun aBadKeyRestsUntilANewKeyIsSaved() = runBlocking {
        val cooldowns = ProviderCooldowns { 0L }
        val inner = Fake(ProviderId.GEMINI, AiResult.Failure(FailureKind.INVALID_KEY, "API key was rejected (HTTP 401)"))
        val guard = GuardedAiProvider(inner, AiUsageStore(MapStorage()), cooldowns) { BudgetLimits() }
        guard.chat("m", "s", turns)
        guard.chat("m", "s", turns)
        assertEquals(1, inner.calls)
        cooldowns.clear(ProviderId.GEMINI) // what saving a new key does
        guard.chat("m", "s", turns)
        assertEquals(2, inner.calls)
    }

    @Test fun aNewMonthStartsAtZeroAndPricesAreKept() {
        val storage = MapStorage()
        val sep = 1_790_000_000_000L // 2026-09-21 UTC
        var now = sep
        val store = AiUsageStore(storage) { now }
        store.learnPrices(ProviderId.OPENROUTER, listOf(AiModel("b/paid", "b", 3.0, 15.0)))
        store.recordSuccess(ProviderId.OPENROUTER, "b/paid", 2_000, 2_000)
        assertTrue(store.spentThisMonthUsd() > 0)
        // Reloaded from storage in the same month: same numbers.
        assertEquals(store.spentThisMonthUsd(), AiUsageStore(storage) { now }.spentThisMonthUsd(), 1e-12)
        now += 40L * 24 * 3600 * 1000 // next month
        assertEquals(0.0, AiUsageStore(storage) { now }.spentThisMonthUsd(), 0.0)
        assertEquals(3.0, AiUsageStore(storage) { now }.priceOf(ProviderId.OPENROUTER, "b/paid")!!.inputUsdPerMTok, 0.0)
        assertEquals("2026-09", monthKey(sep, ZoneOffset.UTC))
    }
}
