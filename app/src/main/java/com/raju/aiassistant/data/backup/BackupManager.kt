package com.raju.aiassistant.data.backup

import android.content.Context
import android.net.Uri
import com.raju.aiassistant.domain.backup.BackupCrypto
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File

/**
 * Makes and restores the password-locked backup (see [BackupCrypto]) in a file the user picks — on
 * the phone, a pen drive or Google Drive. In it: the database (khata, clients, diary, chats, memories,
 * knowledge), Seema's own preference files (reminders, Tatkal alarms, PNR watch …) and the settings.
 * Never in it: the API keys (raju_secure_store), which only this phone's Keystore can open.
 */
class BackupManager(
    private val context: Context,
    /** Writes everything the database still holds in its journal into the main file first. */
    private val flushDatabase: () -> Unit,
    /** Closes the database before its file is replaced. */
    private val closeDatabase: () -> Unit,
) {
    sealed interface Result {
        data class Done(val message: String) : Result
        data class Failed(val message: String) : Result
    }

    suspend fun export(target: Uri, password: CharArray): Result = withContext(Dispatchers.IO) {
        runCatching {
            flushDatabase()
            val files = linkedMapOf<String, ByteArray>()
            val db = context.getDatabasePath(DB_NAME)
            if (db.exists()) files["databases/$DB_NAME"] = db.readBytes()
            prefsDir().listFiles { f -> f.isFile && f.name.endsWith(".xml") && f.name !in NEVER }?.forEach { files["prefs/${it.name}"] = it.readBytes() }
            dataStoreDir().listFiles { f -> f.isFile }?.forEach { files["datastore/${it.name}"] = it.readBytes() }
            val locked = BackupCrypto.lock(files, password)
            context.contentResolver.openOutputStream(target, "w")?.use { it.write(locked) } ?: error("cannot write")
            Result.Done("Backup ban gaya (${files.size} files, ${locked.size / 1024} KB). Password yaad rakhiye — uske bina yeh file nahi khulegi.")
        }.getOrElse { Result.Failed("Backup nahi ban paya: ${it.javaClass.simpleName}") }
    }

    /** Replaces the app's data with the backup's. The app must restart afterwards (the caller does that). */
    suspend fun restore(source: Uri, password: CharArray): Result = withContext(Dispatchers.IO) {
        val data = runCatching { context.contentResolver.openInputStream(source)?.use { it.readBytes() } }.getOrNull()
            ?: return@withContext Result.Failed("File nahi khul payi.")
        when (val opened = BackupCrypto.open(data, password)) {
            BackupCrypto.Opened.NotABackup -> Result.Failed("Yeh Seema ka backup file nahi hai.")
            BackupCrypto.Opened.WrongPassword -> Result.Failed("Password galat hai (ya file badal gayi hai). Kuch bhi nahi badla gaya.")
            is BackupCrypto.Opened.Ok -> {
                val files = opened.files
                if ("databases/$DB_NAME" !in files) return@withContext Result.Failed("Is backup mein data nahi mila. Kuch bhi nahi badla gaya.")
                runCatching {
                    closeDatabase()
                    for ((name, bytes) in files) {
                        val target = when {
                            name == "databases/$DB_NAME" -> context.getDatabasePath(DB_NAME)
                            name.startsWith("prefs/") -> File(prefsDir(), name.removePrefix("prefs/"))
                            name.startsWith("datastore/") -> File(dataStoreDir(), name.removePrefix("datastore/"))
                            else -> null
                        } ?: continue
                        if (target.name in NEVER || target.name.contains('/')) continue
                        target.parentFile?.mkdirs()
                        val tmp = File(target.parentFile, target.name + ".restoring")
                        tmp.writeBytes(bytes)
                        if (!tmp.renameTo(target)) { target.delete(); tmp.renameTo(target) }
                    }
                    // The old journal belongs to the old database.
                    File(context.getDatabasePath(DB_NAME).path + "-wal").delete()
                    File(context.getDatabasePath(DB_NAME).path + "-shm").delete()
                    Result.Done("Backup wapas aa gaya. Seema ab dobara khulegi. API keys Settings mein phir se daaliye.")
                }.getOrElse { Result.Failed("Backup wapas nahi aa paya: ${it.javaClass.simpleName}") }
            }
        }
    }

    private fun prefsDir() = File(context.applicationInfo.dataDir, "shared_prefs")
    private fun dataStoreDir() = File(context.filesDir, "datastore")

    private companion object {
        const val DB_NAME = "raju_ai.db"
        /** The encrypted API keys: bound to this phone's Keystore, useless elsewhere, never copied. */
        val NEVER = setOf("raju_secure_store.xml")
    }
}
