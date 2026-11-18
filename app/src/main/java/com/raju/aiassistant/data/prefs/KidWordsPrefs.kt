package com.raju.aiassistant.data.prefs

import android.content.Context
import com.raju.aiassistant.domain.kids.KidLexicon
import com.raju.aiassistant.domain.kids.KidWord
import com.raju.aiassistant.domain.kids.KidWordStore
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject

/**
 * Riya's words on the phone (app-private storage, never backed up to a server by this app, never sent anywhere
 * except as part of a Riya-mode request to the AI). [words] feeds the parent's list in Settings.
 */
class KidWordsPrefs(context: Context) : KidWordStore {
    private val prefs = context.getSharedPreferences("riya_words", Context.MODE_PRIVATE)
    private val _words = MutableStateFlow(load())
    val words: StateFlow<List<KidWord>> = _words.asStateFlow()

    override suspend fun all(): List<KidWord> = _words.value

    override suspend fun put(word: KidWord) = withContext(Dispatchers.IO) {
        synchronized(this@KidWordsPrefs) {
            val k = KidLexicon.key(word.heard)
            val next = _words.value.filterNot { KidLexicon.key(it.heard) == k } + word
            save(next)
            _words.value = next.sortedByDescending { it.learnedAt }
        }
    }

    override suspend fun remove(heard: String) = withContext(Dispatchers.IO) {
        synchronized(this@KidWordsPrefs) {
            val k = KidLexicon.key(heard)
            val next = _words.value.filterNot { KidLexicon.key(it.heard) == k }
            save(next)
            _words.value = next
        }
    }

    private fun load(): List<KidWord> = runCatching {
        val arr = JSONArray(prefs.getString(KEY, "[]"))
        (0 until arr.length()).mapNotNull { i ->
            val o = arr.optJSONObject(i) ?: return@mapNotNull null
            KidWord(
                heard = o.optString("heard").takeIf { it.isNotBlank() } ?: return@mapNotNull null,
                meaning = o.optString("meaning").takeIf { it.isNotBlank() } ?: return@mapNotNull null,
                source = runCatching { KidWord.Source.valueOf(o.optString("source")) }.getOrDefault(KidWord.Source.GUESSED),
                learnedAt = o.optLong("learnedAt"),
                lastUsedAt = o.optLong("lastUsedAt", o.optLong("learnedAt")),
                uses = o.optInt("uses"),
            )
        }.sortedByDescending { it.learnedAt }
    }.getOrDefault(emptyList())

    private fun save(list: List<KidWord>) {
        val arr = JSONArray()
        list.forEach { w ->
            arr.put(
                JSONObject().put("heard", w.heard).put("meaning", w.meaning).put("source", w.source.name)
                    .put("learnedAt", w.learnedAt).put("lastUsedAt", w.lastUsedAt).put("uses", w.uses),
            )
        }
        prefs.edit().putString(KEY, arr.toString()).apply()
    }

    private companion object { const val KEY = "words" }
}
