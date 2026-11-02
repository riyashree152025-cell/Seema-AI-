package com.raju.aiassistant.domain.ai

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class ModelSizeTest {
    @Test fun sizesAreReadFromModelIds() {
        assertEquals(70.0, ModelAdvice.sizeInBillions("Meta-Llama-3.3-70B-Instruct")!!, 0.0)
        assertEquals(3.0, ModelAdvice.sizeInBillions("meta/llama-3.2-3b-instruct")!!, 0.0)
        assertEquals(17.0, ModelAdvice.sizeInBillions("meta-llama/llama-4-scout-17b-16e-instruct")!!, 0.0)
        assertNull(ModelAdvice.sizeInBillions("gemini-2.5-flash"))
    }

    @Test fun smallModelsAreNeverRecommendedEvenWhenTheNameMatches() {
        // NVIDIA's ordering hints end with plain "llama", which a 3B model also contains.
        assertFalse(ModelAdvice.isRecommended(ProviderId.NVIDIA, "meta/llama-3.2-3b-instruct"))
        assertFalse(ModelAdvice.isRecommended(ProviderId.CLOUDFLARE, "@cf/meta/llama-3.1-8b-instruct"))
        assertFalse(ModelAdvice.isRecommended(ProviderId.OPENROUTER, "meta-llama/llama-3.2-3b-instruct:free"))
        assertTrue(ModelAdvice.isRecommended(ProviderId.NVIDIA, "meta/llama-3.3-70b-instruct"))
        assertTrue(ModelAdvice.isRecommended(ProviderId.GROQ, "llama-3.3-70b-versatile"))
        assertTrue(ModelAdvice.isRecommended(ProviderId.GEMINI, "gemini-2.5-flash"))
    }

    @Test fun theDefaultPickPrefersTheBigModel() {
        val models = listOf(AiModel("meta/llama-3.2-3b-instruct", ""), AiModel("meta/llama-3.3-70b-instruct", ""))
        assertEquals("meta/llama-3.3-70b-instruct", pickDefaultModel(ProviderId.NVIDIA, models)?.id)
    }
}
