package com.raju.aiassistant.domain.backup

import com.raju.aiassistant.domain.model.SecretId
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class KeyBackupTest {
    private val keys = mapOf(
        SecretId.GEMINI_API_KEY to "AIza-test-gemini",
        SecretId.GROQ_API_KEY to "gsk_test",
        SecretId.SARVAM_API_KEY to "sarvam-test",
        SecretId.CARTESIA_API_KEY to "cartesia-test",
    )

    @Test
    fun keysComeBackWithTheRightPassword() {
        val file = KeyBackup.lock(keys, "raju-secret".toCharArray(), 1_000L)
        assertEquals(KeyBackup.Opened.Ok(keys), KeyBackup.open(file, "raju-secret".toCharArray()))
    }

    @Test
    fun theFileIsUnreadableWithoutThePassword() {
        val file = KeyBackup.lock(keys, "raju-secret".toCharArray(), 1_000L)
        assertEquals(KeyBackup.Opened.WrongPassword, KeyBackup.open(file, "wrong-pass".toCharArray()))
        // No key is visible in the file itself.
        val text = String(file, Charsets.ISO_8859_1)
        assertFalse(text.contains("AIza") || text.contains("gsk_") || text.contains("gemini_api_key"))
    }

    @Test
    fun jsonUsesTheStorageNamesAndSkipsUnknownOnes() {
        val json = KeyBackup.toJson(keys, 5L)
        assertTrue(json.contains("\"gemini_api_key\":\"AIza-test-gemini\""))
        val withExtra = json.replace("\"keys\":{", "\"keys\":{\"elevenlabs_api_key\":\"old\",\"tavily_api_key\":\"  \",")
        assertEquals(keys, KeyBackup.fromJson(withExtra))
        assertNull(KeyBackup.fromJson("{\"kind\":\"something-else\",\"keys\":{}}"))
        assertNull(KeyBackup.fromJson("not json"))
    }

    @Test
    fun anOrdinaryDataBackupIsNotAKeyBackup() {
        val other = BackupCrypto.lock(mapOf("settings.json" to "{}".toByteArray()), "raju-secret".toCharArray())
        assertEquals(KeyBackup.Opened.NotAKeyBackup, KeyBackup.open(other, "raju-secret".toCharArray()))
    }
}
