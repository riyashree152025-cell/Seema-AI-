package com.raju.aiassistant.domain.voice

import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.booleanOrNull
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.doubleOrNull

/**
 * Collects a live (streaming) Deepgram transcript while the user is still talking. Final pieces are
 * kept; the latest interim piece is shown on screen until it becomes final. When the user stops, the
 * words are already there, so nothing has to be uploaded and waited for. Pure, unit-tested.
 */
class LiveTranscript {
    private val finals = mutableListOf<Pair<String, Double>>()
    private var interim = ""

    /** Reads one message from the socket; true when the text on screen changed. */
    fun onMessage(message: String): Boolean {
        val root = runCatching { json.parseToJsonElement(message) as? JsonObject }.getOrNull() ?: return false
        if ((root["type"] as? JsonPrimitive)?.contentOrNull != "Results") return false
        val alt = ((root["channel"] as? JsonObject)?.get("alternatives") as? JsonArray)?.firstOrNull() as? JsonObject ?: return false
        val text = (alt["transcript"] as? JsonPrimitive)?.contentOrNull?.trim().orEmpty()
        val confidence = (alt["confidence"] as? JsonPrimitive)?.doubleOrNull ?: 1.0
        val isFinal = (root["is_final"] as? JsonPrimitive)?.booleanOrNull == true
        val before = shown()
        if (isFinal) {
            if (text.isNotEmpty()) finals += text to confidence
            interim = ""
        } else {
            interim = text
        }
        return shown() != before
    }

    /** The confirmed words so far. */
    fun text(): String = finals.joinToString(" ") { it.first }.trim()

    /** What to show while the user talks: the confirmed words and the newest guess. */
    fun shown(): String = listOf(text(), interim).filter { it.isNotEmpty() }.joinToString(" ")

    /** Confidence of the confirmed words, weighted by their length (1.0 when there are none). */
    fun confidence(): Double {
        val total = finals.sumOf { it.first.length }
        if (total == 0) return 1.0
        return finals.sumOf { it.second * it.first.length } / total
    }

    private companion object {
        val json = Json { ignoreUnknownKeys = true; isLenient = true }
    }
}
