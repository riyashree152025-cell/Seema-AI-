package com.raju.aiassistant.domain.backup

import com.raju.aiassistant.domain.model.SecretId
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

/**
 * An app update must never lose Raju's saved API keys, settings or data: these names are what the phone already
 * has on disk. Renaming any of them silently empties it on the next install. If a test here fails, the change
 * needs a migration that copies the old value, not a new expectation.
 */
class UpgradeSafetyTest {
    private fun source(path: String): String =
        listOf(File("src/main/java/$path"), File("app/src/main/java/$path")).first { it.exists() }.readText()

    @Test
    fun apiKeyStorageNamesNeverChange() {
        val frozen = mapOf(
            "GEMINI_API_KEY" to "gemini_api_key", "GROQ_API_KEY" to "groq_api_key", "CEREBRAS_API_KEY" to "cerebras_api_key",
            "OPENROUTER_API_KEY" to "openrouter_api_key", "DEEPSEEK_API_KEY" to "deepseek_api_key", "MISTRAL_API_KEY" to "mistral_api_key",
            "OLLAMA_ENDPOINT" to "ollama_endpoint", "PC_SYNC_ENDPOINT" to "pc_sync_endpoint", "PC_SYNC_TOKEN" to "pc_sync_token",
            "TAVILY_API_KEY" to "tavily_api_key", "SARVAM_API_KEY" to "sarvam_api_key", "CARTESIA_API_KEY" to "cartesia_api_key",
            "DEEPGRAM_API_KEY" to "deepgram_api_key",
        )
        frozen.forEach { (name, key) -> assertEquals(name, key, SecretId.valueOf(name).storageKey) }
        // No two secrets share a name.
        assertEquals(SecretId.entries.size, SecretId.entries.map { it.storageKey }.toSet().size)
    }

    @Test
    fun theKeyStoreFileAndMasterKeyNamesNeverChange() {
        val src = source("com/raju/aiassistant/core/security/SecureKeyStore.kt")
        assertTrue(src.contains("PREFS_NAME = \"raju_secure_store\""))
        assertTrue(src.contains("KEY_ALIAS = \"raju_ai_secret_master_key\""))
        // An existing master key is never replaced by a new one (that made every saved key unreadable).
        assertTrue(src.contains("containsAlias(KEY_ALIAS)"))
    }

    @Test
    fun everySettingNameIsStillThere() {
        val src = source("com/raju/aiassistant/data/prefs/SettingsRepository.kt")
        assertTrue(src.contains("preferencesDataStore(name = \"raju_settings\")"))
        val frozen = (
            "announce_app_calls announce_apps announce_calls announce_money_in announce_notifications app_lock ask_before_reading " +
                "assistant_paused auto_listen auto_speak barge_in barge_in_speaker_v2 business_reminders cartesia_model cartesia_mood " +
                "cartesia_voice_id cartesia_voice_name continuous_conversation core_appearance deepgram_stt_model_v2 default_provider " +
                "direct_calling floating_popup history_limit home_style hybrid_brain is_pro_user low_power_mode memory_enabled mic_hub " +
                "music_service reduced_motion safety_mode sarvam_speaker save_history sleep_after_seconds smart_power speak_caller_number " +
                "speech_language speech_rate stt_engine thermal_protection tts_engine upi_ids upi_payee_name user_email user_name " +
                "voice_engine_auto voice_mode wake_sensitivity wake_to_start wake_word"
            ).split(' ')
        val missing = frozen.filterNot { src.contains("PreferencesKey(\"$it\")") }
        assertTrue("setting names removed or renamed: $missing", missing.isEmpty())
    }

    @Test
    fun theDatabaseIsOnlyEverMigratedNeverWiped() {
        val db = source("com/raju/aiassistant/data/db/RajuDatabase.kt")
        assertTrue(db.contains("\"raju_ai.db\""))
        assertTrue(db.contains("MIGRATION_4_5"))
        val version = Regex("version = (\\d+)").find(db)!!.groupValues[1].toInt()
        assertTrue(version >= 5)
        // Every step up to the current version has its migration.
        (1 until version).forEach { v -> assertTrue("missing MIGRATION_${v}_${v + 1}", db.contains("MIGRATION_${v}_${v + 1}")) }
        val root = listOf(File("src/main/java"), File("app/src/main/java")).first { it.exists() }
        val destructive = root.walkTopDown().filter { it.isFile && it.extension == "kt" }.filter { it.readText().contains("fallbackToDestructiveMigration") }.toList()
        assertTrue("destructive migration in $destructive", destructive.isEmpty())
    }
}
