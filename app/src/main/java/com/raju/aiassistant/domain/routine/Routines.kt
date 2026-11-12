package com.raju.aiassistant.domain.routine

import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.booleanOrNull
import kotlinx.serialization.json.buildJsonArray
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.intOrNull
import kotlinx.serialization.json.longOrNull
import kotlinx.serialization.json.put

/**
 * A routine: a name and steps said the way the user says them ("aaj ki briefing do", "volume 30 karo"),
 * run one after another by voice ("good night chalao"), from the Automation screen, or at a set time.
 * Steps are phone commands Seema understands on the phone; calls and messages inside still ask first.
 */
data class Routine(
    val id: Long,
    val name: String,
    val steps: List<String>,
    /** Daily time to run by itself (24 h), or null. */
    val hour: Int? = null,
    val minute: Int? = null,
    val enabled: Boolean = true,
) {
    val timed: Boolean get() = hour != null && minute != null
}

private val json = Json { ignoreUnknownKeys = true }

fun encodeRoutines(list: List<Routine>): String = buildJsonArray {
    list.forEach { r ->
        add(buildJsonObject {
            put("id", r.id)
            put("name", r.name)
            put("steps", JsonArray(r.steps.map { JsonPrimitive(it) }))
            r.hour?.let { put("hour", it) }
            r.minute?.let { put("minute", it) }
            put("enabled", r.enabled)
        })
    }
}.toString()

fun decodeRoutines(text: String?): List<Routine> {
    if (text.isNullOrBlank()) return emptyList()
    val arr = runCatching { json.parseToJsonElement(text) as? JsonArray }.getOrNull() ?: return emptyList()
    return arr.mapNotNull { e ->
        val o = e as? JsonObject ?: return@mapNotNull null
        val id = (o["id"] as? JsonPrimitive)?.longOrNull ?: return@mapNotNull null
        val name = (o["name"] as? JsonPrimitive)?.contentOrNull ?: return@mapNotNull null
        val steps = (o["steps"] as? JsonArray)?.mapNotNull { (it as? JsonPrimitive)?.contentOrNull }.orEmpty()
        Routine(
            id, name, steps,
            (o["hour"] as? JsonPrimitive)?.intOrNull, (o["minute"] as? JsonPrimitive)?.intOrNull,
            (o["enabled"] as? JsonPrimitive)?.booleanOrNull ?: true,
        )
    }
}

private val RUN_WORDS = Regex(
    "\\s+(?:routine\\s+)?(?:chalao|chala do|chalu karo|shuru karo|start karo|run karo|start|चलाओ|चला दो|चालू करो|शुरू करो|स्टार्ट करो|রুটিন চালাও|চালাও)$",
    RegexOption.IGNORE_CASE,
)

private fun norm(s: String) = s.lowercase().replace(Regex("[^\\p{L}\\p{M}\\p{N} ]"), " ").replace(Regex("\\s+"), " ").trim()

/** "Good night chalao", "good night routine shuru karo", or just the name said alone. */
fun routineFor(text: String, routines: List<Routine>): Routine? {
    val t = text.trim().trimEnd('?', '.', '।', '!')
    val named = RUN_WORDS.find(t)?.let { t.substring(0, it.range.first) } ?: t
    val key = norm(named.removeSuffix(" routine").removeSuffix(" रूटीन"))
    if (key.isEmpty()) return null
    return routines.firstOrNull { it.enabled && norm(it.name) == key }
}

private val JOIN = Regex("\\s+(?:aur|or|phir|fir|then|and|और|फिर|তারপর|আর)\\s+|\\s*,\\s*", RegexOption.IGNORE_CASE)

/** "torch on karo aur volume 50 karo" → the two commands; null when it is one sentence. */
fun splitChain(text: String): List<String>? {
    val parts = text.split(JOIN).map { it.trim() }.filter { it.isNotEmpty() }
    return parts.takeIf { it.size in 2..6 }
}

/** The steps from the editor: one per line, empty lines dropped, at most 12. */
fun stepsFrom(text: String): List<String> = text.lines().map { it.trim().trimStart('-', '•', '*').trim() }.filter { it.isNotEmpty() }.take(12)

/** "07:30" / "7:30" / "7" → (7, 30) / (7, 0); null for anything else. */
fun parseRoutineTime(text: String): Pair<Int, Int>? {
    val m = Regex("^(\\d{1,2})(?::(\\d{2}))?$").find(text.trim()) ?: return null
    val h = m.groupValues[1].toInt()
    val min = m.groupValues[2].ifEmpty { "0" }.toInt()
    return if (h in 0..23 && min in 0..59) h to min else null
}

/** What the editor's Save gives: the routine to keep, or what to fix (in Hindi, shown under the fields). */
sealed interface RoutineCheck {
    data class Ok(val routine: Routine) : RoutineCheck
    data class Problem(val message: String) : RoutineCheck
}

/**
 * Checks the editor's fields before saving. [understood] says whether Seema can do a step on the phone
 * (LocalCommands); a step she cannot do is refused here, not skipped silently at run time.
 */
fun checkRoutine(
    id: Long,
    name: String,
    stepsText: String,
    timeText: String,
    enabled: Boolean,
    existing: List<Routine>,
    understood: (String) -> Boolean,
): RoutineCheck {
    val n = name.trim().replace(Regex("\\s+"), " ")
    if (n.isEmpty()) return RoutineCheck.Problem("Routine का नाम लिखिए, जैसे Good Night।")
    if (n.length > 40) return RoutineCheck.Problem("नाम छोटा रखिए (40 अक्षर तक)।")
    if (existing.any { it.id != id && norm(it.name) == norm(n) }) return RoutineCheck.Problem("\"$n\" नाम की routine पहले से है।")
    val steps = stepsFrom(stepsText)
    if (steps.isEmpty()) return RoutineCheck.Problem("कम से कम एक काम लिखिए, हर लाइन में एक।")
    val unknown = steps.filterNot(understood)
    if (unknown.isNotEmpty()) {
        return RoutineCheck.Problem("ये काम Seema फ़ोन पर नहीं समझ पाई: ${unknown.joinToString(" · ")}। ऐसे लिखिए: torch band karo, volume 20 karo, aaj ki briefing do.")
    }
    val time = timeText.trim()
    val hm = if (time.isEmpty()) null else parseRoutineTime(time) ?: return RoutineCheck.Problem("समय ऐसे लिखिए: 07:30 (24 घंटे वाला), या खाली छोड़िए।")
    return RoutineCheck.Ok(Routine(id, n, steps, hm?.first, hm?.second, enabled))
}
