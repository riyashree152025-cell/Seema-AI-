package com.raju.aiassistant.domain.backup

import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.nio.ByteBuffer
import java.security.SecureRandom
import java.util.zip.ZipEntry
import java.util.zip.ZipInputStream
import java.util.zip.ZipOutputStream
import javax.crypto.AEADBadTagException
import javax.crypto.Cipher
import javax.crypto.SecretKeyFactory
import javax.crypto.spec.GCMParameterSpec
import javax.crypto.spec.PBEKeySpec
import javax.crypto.spec.SecretKeySpec

/**
 * The backup file: the user's own data (khata, diary, memories, reminders, settings) packed and locked
 * with a password the user chooses. AES-256-GCM with a key from PBKDF2-HMAC-SHA256 (210,000 rounds,
 * random salt): without the password the file is unreadable, and any change to it is detected.
 * API keys are never in it (they are bound to this phone's Keystore and are entered again).
 *
 * Layout: "SEEMABK1" | salt(16) | iv(12) | ciphertext+tag.
 */
object BackupCrypto {
    private val MAGIC = "SEEMABK1".toByteArray(Charsets.US_ASCII)
    private const val SALT = 16
    private const val IV = 12
    const val ITERATIONS = 210_000
    const val MIN_PASSWORD = 6

    sealed interface Opened {
        data class Ok(val files: Map<String, ByteArray>) : Opened
        data object WrongPassword : Opened
        data object NotABackup : Opened
    }

    fun lock(files: Map<String, ByteArray>, password: CharArray, random: SecureRandom = SecureRandom()): ByteArray {
        require(password.size >= MIN_PASSWORD) { "password too short" }
        val salt = ByteArray(SALT).also(random::nextBytes)
        val iv = ByteArray(IV).also(random::nextBytes)
        val cipher = Cipher.getInstance("AES/GCM/NoPadding")
        cipher.init(Cipher.ENCRYPT_MODE, key(password, salt), GCMParameterSpec(128, iv))
        cipher.updateAAD(MAGIC)
        val sealed = cipher.doFinal(zip(files))
        return ByteBuffer.allocate(MAGIC.size + SALT + IV + sealed.size).put(MAGIC).put(salt).put(iv).put(sealed).array()
    }

    fun open(data: ByteArray, password: CharArray): Opened {
        if (data.size < MAGIC.size + SALT + IV + 16 || !data.copyOfRange(0, MAGIC.size).contentEquals(MAGIC)) return Opened.NotABackup
        val salt = data.copyOfRange(MAGIC.size, MAGIC.size + SALT)
        val iv = data.copyOfRange(MAGIC.size + SALT, MAGIC.size + SALT + IV)
        val body = data.copyOfRange(MAGIC.size + SALT + IV, data.size)
        return try {
            val cipher = Cipher.getInstance("AES/GCM/NoPadding")
            cipher.init(Cipher.DECRYPT_MODE, key(password, salt), GCMParameterSpec(128, iv))
            cipher.updateAAD(MAGIC)
            Opened.Ok(unzip(cipher.doFinal(body)))
        } catch (e: AEADBadTagException) {
            Opened.WrongPassword // wrong password, or the file was changed
        }
    }

    private fun key(password: CharArray, salt: ByteArray): SecretKeySpec {
        val spec = PBEKeySpec(password, salt, ITERATIONS, 256)
        try {
            val bytes = SecretKeyFactory.getInstance("PBKDF2WithHmacSHA256").generateSecret(spec).encoded
            return SecretKeySpec(bytes, "AES")
        } finally {
            spec.clearPassword()
        }
    }

    private fun zip(files: Map<String, ByteArray>): ByteArray {
        val out = ByteArrayOutputStream()
        ZipOutputStream(out).use { z ->
            files.toSortedMap().forEach { (name, bytes) ->
                z.putNextEntry(ZipEntry(name))
                z.write(bytes)
                z.closeEntry()
            }
        }
        return out.toByteArray()
    }

    private fun unzip(bytes: ByteArray): Map<String, ByteArray> {
        val out = linkedMapOf<String, ByteArray>()
        ZipInputStream(ByteArrayInputStream(bytes)).use { z ->
            while (true) {
                val e = z.nextEntry ?: break
                // Only plain relative names: a "../" entry must never write outside the app's folder.
                if (!isSafeEntryName(e.name)) continue
                out[e.name] = z.readBytes()
            }
        }
        return out
    }

    fun isSafeEntryName(name: String): Boolean =
        name.isNotBlank() && !name.startsWith("/") && !name.contains('\\') && name.split('/').none { it == ".." || it.isEmpty() }
}
