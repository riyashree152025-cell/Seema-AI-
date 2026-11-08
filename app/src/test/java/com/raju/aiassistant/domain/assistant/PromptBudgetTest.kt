package com.raju.aiassistant.domain.assistant

import com.raju.aiassistant.domain.ai.FailureKind
import com.raju.aiassistant.domain.model.Personality
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/** The system prompt is sent with every request; Groq allows 8000 tokens a minute in total. */
class PromptBudgetTest {

    @Test
    fun theSystemPromptStaysSmall() {
        // Before the compaction the stable part alone was over 12 000 characters (about 3 200 tokens).
        for (p in Personality.entries) {
            val prompt = buildSystemPrompt(emptyList(), "Saturday 9 PM", p)
            assertTrue("${p.name}: ${prompt.length} chars", prompt.length < 9_500)
        }
    }

    @Test
    fun everyToolTheAppUnderstandsIsStillListed() {
        val prompt = buildSystemPrompt(emptyList(), "now")
        val tools = listOf(
            "call", "end_call", "answer_call", "find_contact", "last_call", "read_notification",
            "whatsapp", "whatsapp_call", "whatsapp_video_call", "sms", "open_app", "open_settings",
            "go_home", "open_seema", "search_web", "play_music", "media", "volume", "flashlight",
            "battery", "find_phone", "set_timer", "set_alarm", "add_event", "take_photo", "look",
            "screenshot", "navigate", "copy", "remember", "read_screen", "tap", "go_back", "scroll",
            "type_text", "expense_add", "expense_undo", "expense_summary", "pnr_add", "pnr_list",
            "pnr_remove", "pnr_status", "mandi_price", "save_file", "research",
        )
        val missing = tools.filter { !Regex("(^|[^a-z_])${it}([^a-z_]|$)").containsMatchIn(prompt) }
        assertEquals(emptyList<String>(), missing)
        // The rules that keep actions honest are still there.
        assertTrue(prompt.contains("never that it is sent"))
        assertTrue(prompt.contains("never guess CNF/WL/RAC"))
        assertTrue(prompt.contains("never passwords"))
        assertTrue(prompt.contains("BE TRUTHFUL"))
    }

    @Test
    fun aPerMinuteLimitBenchesTheProviderForAMinuteNotFifteen() {
        val groqTpm = "Quota exceeded for this key (HTTP 429): Rate limit reached for model `openai/gpt-oss-120b` " +
            "in organization `org_x` service tier `on_demand` on tokens per minute (TPM): Limit 8000, Used 5205, Requested 4636."
        assertTrue(isPerMinuteLimit(groqTpm))
        assertEquals(60_000L, cooldownMsFor(FailureKind.QUOTA_EXCEEDED, groqTpm))
        assertEquals(60_000L, cooldownMsFor(FailureKind.RATE_LIMITED, "requests per minute (RPM): Limit 30"))

        // A daily limit or a bad key still rests for 15 minutes.
        val groqDaily = "Rate limit reached on tokens per day (TPD): Limit 200000, Used 199000"
        assertFalse(isPerMinuteLimit(groqDaily))
        assertEquals(15 * 60_000L, cooldownMsFor(FailureKind.QUOTA_EXCEEDED, groqDaily))
        assertEquals(15 * 60_000L, cooldownMsFor(FailureKind.INVALID_KEY, "per minute"))
        assertEquals(60_000L, cooldownMsFor(FailureKind.SERVER))
    }
}
