package com.raju.aiassistant.domain.backup

import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class BackupCryptoTest {
    private val files = mapOf(
        "databases/raju_ai.db" to ByteArray(5_000) { (it % 251).toByte() },
        "prefs/seema_reminders.xml" to "<map><set name=\"list\"/></map>".toByteArray(),
    )

    @Test
    fun theRightPasswordOpensExactlyWhatWasSaved() {
        val locked = BackupCrypto.lock(files, "raju@2026".toCharArray())
        val opened = BackupCrypto.open(locked, "raju@2026".toCharArray()) as BackupCrypto.Opened.Ok
        assertEquals(files.keys, opened.files.keys)
        files.forEach { (k, v) -> assertArrayEquals(v, opened.files[k]) }
    }

    @Test
    fun aWrongPasswordOrAChangedFileOpensNothing() {
        val locked = BackupCrypto.lock(files, "raju@2026".toCharArray())
        assertEquals(BackupCrypto.Opened.WrongPassword, BackupCrypto.open(locked, "raju@2025".toCharArray()))
        val changed = locked.copyOf().also { it[it.size - 5] = (it[it.size - 5] + 1).toByte() }
        assertEquals(BackupCrypto.Opened.WrongPassword, BackupCrypto.open(changed, "raju@2026".toCharArray()))
        assertEquals(BackupCrypto.Opened.NotABackup, BackupCrypto.open("hello".toByteArray(), "raju@2026".toCharArray()))
    }

    @Test
    fun theDataIsNotReadableInTheFile() {
        val secret = mapOf("note.txt" to "Rahul ko 5000 diye".toByteArray())
        val locked = BackupCrypto.lock(secret, "raju@2026".toCharArray())
        assertFalse(String(locked, Charsets.ISO_8859_1).contains("Rahul"))
    }

    @Test
    fun entriesCannotEscapeTheAppFolder() {
        assertTrue(BackupCrypto.isSafeEntryName("databases/raju_ai.db"))
        assertFalse(BackupCrypto.isSafeEntryName("../shared_prefs/raju_secure_store.xml"))
        assertFalse(BackupCrypto.isSafeEntryName("/data/data/x"))
        assertFalse(BackupCrypto.isSafeEntryName("databases//x"))
    }
}
