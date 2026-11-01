package com.raju.aiassistant.domain.ai

import com.raju.aiassistant.data.ai.httpFailure
import com.raju.aiassistant.data.prefs.decodeConnectionTest
import com.raju.aiassistant.data.prefs.encodeConnectionTest
import com.raju.aiassistant.domain.model.ConnectionTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class ProviderStatusTest {
    private fun failed(code: Int, body: String = "") = httpFailure(code, body).let { ConnectionTest(false, it.message, 1L, it.kind) }

    @Test fun connectedOnlyAfterARealSuccessfulTest() {
        assertEquals(ProviderStatus.NOT_CONFIGURED, providerStatus(keySaved = false, testing = false, lastTest = ConnectionTest(true, "OK", 1)))
        assertEquals(ProviderStatus.KEY_SAVED, providerStatus(keySaved = true, testing = false, lastTest = null))
        assertEquals(ProviderStatus.TESTING, providerStatus(keySaved = true, testing = true, lastTest = ConnectionTest(true, "OK", 1)))
        assertEquals(ProviderStatus.CONNECTED, providerStatus(keySaved = true, testing = false, lastTest = ConnectionTest(true, "OK", 1)))
    }

    @Test fun realHttpErrorsMapToTheirOwnStatus() {
        fun st(code: Int, body: String = "") = providerStatus(true, false, failed(code, body))
        assertEquals(ProviderStatus.INVALID_KEY, st(401, """{"error":{"message":"Invalid API Key"}}"""))
        assertEquals(ProviderStatus.AUTH_FAILED, st(403, """{"error":{"message":"Permission denied"}}"""))
        assertEquals(ProviderStatus.INVALID_KEY, st(400, """{"error":{"message":"API key not valid. Please pass a valid API key."}}"""))
        assertEquals(ProviderStatus.RATE_LIMITED, st(429))
        assertEquals(ProviderStatus.QUOTA_EXCEEDED, st(429, """{"error":{"message":"You exceeded your current quota"}}"""))
        assertEquals(ProviderStatus.UNAVAILABLE, st(503))
        assertEquals(ProviderStatus.FAILED, st(404))
    }

    @Test fun networkProblemsAreNotCalledABadKey() {
        assertEquals(ProviderStatus.NETWORK_ERROR, statusFor(FailureKind.NETWORK, null))
        assertEquals(ProviderStatus.NETWORK_ERROR, statusFor(FailureKind.TIMEOUT, null))
        assertFalse(ProviderStatus.NETWORK_ERROR == statusFor(FailureKind.INVALID_KEY, 401))
    }

    @Test fun olderSavedResultsWithoutAKindStillShowTheRightStatus() {
        // Saved by v1.8 as "0|time|message": the HTTP code in the message is used.
        val old = decodeConnectionTest("0|5|API key was rejected (HTTP 401): bad")!!
        assertNull(old.kind)
        assertEquals("API key was rejected (HTTP 401): bad", old.message)
        assertEquals(ProviderStatus.INVALID_KEY, providerStatus(true, false, old))
    }

    @Test fun connectionTestsSurviveSavingAndLoading() {
        val t = ConnectionTest(false, "Too many requests (HTTP 429): slow down", 42L, FailureKind.RATE_LIMITED)
        assertEquals(t, decodeConnectionTest(encodeConnectionTest(t)))
        val ok = ConnectionTest(true, "OK", 7L)
        assertEquals(ok, decodeConnectionTest(encodeConnectionTest(ok)))
        assertEquals(429, httpCodeOf(t.message))
        assertNull(decodeConnectionTest("garbage"))
    }

    @Test fun keysAreNeverShownInMessages() {
        val key = "gsk_abcdefghijklmnopqrstuvwxyz123456"
        val msg = redactSecrets("Incorrect API key provided: $key (HTTP 401)", key)
        assertFalse(msg.contains(key))
        assertTrue(msg.contains("[key hidden]"))
        // Even a different key-like token (e.g. echoed partially by a provider) is hidden.
        assertFalse(redactSecrets("key AIzaSyA1234567890abcdefghijklmnop rejected").contains("AIzaSy"))
        assertFalse(redactSecrets("bad csk-0123456789abcdef0123").contains("csk-0123"))
        // Normal text and model names stay readable.
        assertEquals("Model llama-3.3-70b-versatile is not available (HTTP 404)", redactSecrets("Model llama-3.3-70b-versatile is not available (HTTP 404)"))
    }
}
