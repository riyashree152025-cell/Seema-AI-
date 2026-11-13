package com.raju.aiassistant.domain.ai

import com.raju.aiassistant.domain.model.SecretId
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class MistralProviderTest {

    @Test
    fun mistralHasItsOwnKey() {
        assertEquals(SecretId.MISTRAL_API_KEY, ProviderId.MISTRAL.secret)
        assertTrue(SecretId.MISTRAL_API_KEY.helpUrl.startsWith("https://console.mistral.ai"))
    }

    @Test
    fun theBestMistralChatModelComesFirstAndNonChatModelsAreHidden() {
        val listed = listOf("mistral-embed", "mistral-small-latest", "mistral-moderation-latest", "mistral-ocr-latest", "mistral-medium-latest", "voxtral-mini-latest", "mistral-large-latest")
            .map { AiModel(it, it) }
        val ranked = ModelAdvice.rank(ProviderId.MISTRAL, listed.filter { ModelAdvice.isChatModel(it.id) })
        assertEquals("mistral-medium-latest", ranked.first().id)
        listOf("mistral-embed", "mistral-moderation-latest", "mistral-ocr-latest", "voxtral-mini-latest").forEach { assertFalse(it, ModelAdvice.isChatModel(it)) }
        assertTrue(ModelAdvice.isRecommended(ProviderId.MISTRAL, "mistral-small-latest"))
    }
}
