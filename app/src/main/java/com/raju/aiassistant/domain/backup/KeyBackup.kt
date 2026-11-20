package com.raju.aiassistant.domain.backup

import com.raju.aiassistant.domain.model.SecretId
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.put

/**
 * "API keys ka backup": every saved key (Gemini, Groq, Sarvam, Cartesia …) in one JSON, locked with the user's
 * password by [BackupCrypto] (AES-256-GCM, the key from the password with PBKDF2). Without the password the file is
 * unreadable; with it the keys come back with one tap — after a reinstall, a new phone, or a lost Keystore key.
 * Keys are written under their storage names, which never change ([SecretId.storageKey]).
 */
object KeyBackup {
    const val FILE = "api_keys.json"
    const val KIND = "seema-ai-api-keys"
    const val VERSION = 1

    fun toJson(values: Map<SecretId, String>, exportedAtMillis: Long): String = buildJsonObject {
        put("kind", KIND)
        put("version", VERSION)
        put("exportedAt", exportedAtMillis)
        put("keys", buildJsonObject { values.forEach { (id, v) -> put(id.storageKey, v) } })
    }.toString()

    /** The keys in [json] this app knows (unknown names and blank values are skipped), or null when it is not a key backup. */
    fun fromJson(json: String): Map<SecretId, String>? {
        val root = runCatching { Json.parseToJsonElement(json) as? JsonObject }.getOrNull() ?: return null
        if ((root["kind"] as? JsonPrimitive)?.contentOrNull != KIND) return null
        val keys = root["keys"] as? JsonObject ?: return null
        return SecretId.entries.mapNotNull { id ->
            (keys[id.storageKey] as? JsonPrimitive)?.contentOrNull?.trim()?.takeIf { it.isNotEmpty() }?.let { id to it }
        }.toMap()
    }

    /** The locked file to save. */
    fun lock(values: Map<SecretId, String>, password: CharArray, nowMillis: Long): ByteArray =
        BackupCrypto.lock(mapOf(FILE to toJson(values, nowMillis).toByteArray(Charsets.UTF_8)), password)

    sealed interface Opened {
        data class Ok(val keys: Map<SecretId, String>) : Opened
        data object WrongPassword : Opened
        data object NotAKeyBackup : Opened
    }

    fun open(data: ByteArray, password: CharArray): Opened = when (val r = BackupCrypto.open(data, password)) {
        BackupCrypto.Opened.WrongPassword -> Opened.WrongPassword
        BackupCrypto.Opened.NotABackup -> Opened.NotAKeyBackup
        is BackupCrypto.Opened.Ok -> r.files[FILE]?.let { fromJson(String(it, Charsets.UTF_8)) }?.let { Opened.Ok(it) } ?: Opened.NotAKeyBackup
    }
}
