package com.raju.aiassistant.data.prefs

import android.content.Context
import com.raju.aiassistant.domain.sms.MoneyFlow
import com.raju.aiassistant.domain.sms.MoneyNote
import com.raju.aiassistant.domain.sms.MoneyNotes
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import java.io.File

/**
 * The UPI apps' payment notifications (amount, person, time — nothing else), kept in the app's private files so
 * "3630 किसका आया?" can name the person after the notification is gone. Never sent anywhere, never logged;
 * only the last [KEEP_DAYS] days and at most [MAX] payments are kept.
 */
class MoneyNotesPrefs(context: Context) : MoneyNotes {
    private val file = File(context.filesDir, "money_notes.json")
    private val lock = Any()

    override suspend fun add(note: MoneyNote) = withContext(Dispatchers.IO) {
        synchronized(lock) {
            val cutoff = System.currentTimeMillis() - KEEP_DAYS * 86_400_000L
            val list = (load().filter { it.atMillis >= cutoff } + note).sortedBy { it.atMillis }.takeLast(MAX)
            save(list)
        }
    }

    override suspend fun since(atMillis: Long): List<MoneyNote> = withContext(Dispatchers.IO) {
        synchronized(lock) { load().filter { it.atMillis >= atMillis } }
    }

    private fun load(): List<MoneyNote> = runCatching {
        if (!file.exists()) return emptyList()
        val arr = JSONArray(file.readText())
        (0 until arr.length()).mapNotNull { i ->
            val o = arr.optJSONObject(i) ?: return@mapNotNull null
            val flow = runCatching { MoneyFlow.valueOf(o.optString("flow")) }.getOrNull() ?: return@mapNotNull null
            MoneyNote(o.optLong("paise"), flow, o.optLong("at"), o.optString("who").takeIf { it.isNotBlank() })
        }
    }.getOrDefault(emptyList())

    private fun save(list: List<MoneyNote>) {
        val arr = JSONArray()
        list.forEach { n ->
            arr.put(JSONObject().put("paise", n.paise).put("flow", n.flow.name).put("at", n.atMillis).put("who", n.who ?: ""))
        }
        runCatching {
            val tmp = File(file.parentFile, "${file.name}.tmp")
            tmp.writeText(arr.toString())
            if (!tmp.renameTo(file)) { file.writeText(arr.toString()); tmp.delete() }
        }
    }

    private companion object {
        const val KEEP_DAYS = 45
        const val MAX = 2_000
    }
}
