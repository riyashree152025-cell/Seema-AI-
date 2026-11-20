package com.raju.aiassistant.data.prefs

import android.content.Context
import com.raju.aiassistant.domain.assistant.PresenceStore

/** When Raju last talked to Seema (a time only, never words), so she knows how long he was away. Its own file. */
class PresencePrefs(context: Context) : PresenceStore {
    private val prefs = context.getSharedPreferences("emotion_state", Context.MODE_PRIVATE)

    override fun lastInteraction(): Long? = prefs.getLong(KEY, 0L).takeIf { it > 0 }

    override fun markInteraction(atMillis: Long) {
        prefs.edit().putLong(KEY, atMillis).apply()
    }

    private companion object {
        const val KEY = "last_interaction_timestamp"
    }
}
