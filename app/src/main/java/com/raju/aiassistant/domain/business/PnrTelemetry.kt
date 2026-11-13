package com.raju.aiassistant.domain.business

import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.booleanOrNull
import kotlinx.serialization.json.contentOrNull

/** One passenger's booking and current status ("WL 12" → "CNF S4 32"). */
data class PassengerStatus(val booking: String, val current: String)

/** A PNR's status as the user's PNR service reported it. */
data class PnrSnapshot(
    val pnr: String,
    val trainNumber: String? = null,
    val trainName: String? = null,
    val date: String? = null,
    val chartPrepared: Boolean = false,
    val passengers: List<PassengerStatus> = emptyList(),
) {
    /** Every passenger confirmed (CNF, or a coach/berth like "S4 32" / "B1,23"). */
    val allConfirmed: Boolean get() = passengers.isNotEmpty() && passengers.all { isConfirmed(it.current) }

    /** Stored between checks; a change in it is a status change worth telling. */
    val signature: String get() = (if (chartPrepared) "C" else "-") + "|" + passengers.joinToString(";") { normalizeStatus(it.current) }
}

sealed interface PnrFetch {
    data class Ok(val snapshot: PnrSnapshot) : PnrFetch
    /** No PNR service is set up (Settings): the official page is the fallback. */
    data object NotConfigured : PnrFetch
    data class Failed(val message: String) : PnrFetch
}

/** The user's PNR status service (their own backend or a provider they subscribed to). */
fun interface PnrSource {
    suspend fun status(pnr: String): PnrFetch
}

enum class PnrChange { CONFIRMED, CHART_PREPARED, STATUS_CHANGED }

fun normalizeStatus(s: String): String = s.uppercase().replace(Regex("[/,\\-]"), " ").replace(Regex("\\s+"), " ").trim()

private val BERTH = Regex("^[A-Z]{1,2}\\d{1,2} \\d{1,3}")

fun isConfirmed(status: String): Boolean {
    val s = normalizeStatus(status)
    return s.startsWith("CNF") || s.startsWith("CONFIRM") || BERTH.containsMatchIn(s)
}

/** What changed since [oldSignature] (null = first check: nothing to announce). */
fun pnrChanges(oldSignature: String?, now: PnrSnapshot): List<PnrChange> {
    if (oldSignature == null || oldSignature == now.signature) return emptyList()
    val oldChart = oldSignature.startsWith("C")
    val oldStatuses = oldSignature.substringAfter('|').split(';').filter { it.isNotBlank() }
    val out = mutableListOf<PnrChange>()
    val wasConfirmed = oldStatuses.isNotEmpty() && oldStatuses.all { isConfirmed(it) }
    if (now.allConfirmed && !wasConfirmed) out += PnrChange.CONFIRMED
    if (now.chartPrepared && !oldChart) out += PnrChange.CHART_PREPARED
    if (out.isEmpty()) out += PnrChange.STATUS_CHANGED
    return out
}

/** "PNR 4512345678, 12301 Rajdhani Express, 12 Nov. Yatri 1: S4 32 (confirm). Chart ban gaya hai." */
fun spokenPnrStatus(s: PnrSnapshot): String = buildString {
    append("PNR ").append(s.pnr.chunked(5).joinToString(" "))
    val train = listOfNotNull(s.trainNumber, s.trainName).joinToString(" ")
    if (train.isNotBlank()) append(", ").append(train)
    s.date?.let { append(", ").append(it) }
    append("। ")
    if (s.passengers.isEmpty()) append("Yatriyon ka status nahi mila।")
    s.passengers.forEachIndexed { i, p ->
        val cur = p.current.ifBlank { p.booking }
        append("Yatri ${i + 1}: $cur")
        if (isConfirmed(cur)) append(" (confirm)")
        append("। ")
    }
    append(if (s.chartPrepared) "Chart ban gaya hai।" else "Chart abhi nahi bana।")
}.trim()

/** The alert for a change found in the background. */
fun spokenPnrAlert(s: PnrSnapshot, changes: List<PnrChange>): String {
    val head = when {
        PnrChange.CHART_PREPARED in changes && s.allConfirmed -> "सुनिए, चार्ट बन गया है — टिकट कन्फर्म हो गया। "
        PnrChange.CHART_PREPARED in changes -> "सुनिए, चार्ट बन गया है — टिकट पूरा कन्फर्म नहीं हुआ। "
        PnrChange.CONFIRMED in changes -> "Khushkhabri! Ticket confirm ho gaya। "
        else -> "PNR status badla hai। "
    }
    return head + spokenPnrStatus(s)
}

private val json = Json { ignoreUnknownKeys = true; isLenient = true }

private val TRAIN_NO = listOf("trainnumber", "train_number", "trainno", "train_no", "train_num", "trainnum")
private val TRAIN_NAME = listOf("trainname", "train_name")
private val DATE = listOf("dateofjourney", "date_of_journey", "doj", "journeydate", "journey_date", "boardingdate")
private val CHART = listOf("chartstatus", "chart_status", "chartprepared", "chart_prepared", "ischartprepared")
private val PASSENGERS = listOf("passengerlist", "passengers", "passenger_list", "passengerstatus", "passenger_status", "passenger")
private val BOOKING = listOf("bookingstatus", "booking_status", "bookingstatusdetails", "booking_status_details", "bookingstatusnew")
private val CURRENT = listOf("currentstatus", "current_status", "currentstatusdetails", "current_status_details", "currentstatusnew")

/**
 * Reads a PNR answer in the shapes PNR services commonly use (field names in camelCase or snake
 * case, the data at the top or inside "data"). Null when it holds no train or passenger at all.
 */
fun parsePnrJson(pnr: String, body: String): PnrSnapshot? {
    val root = runCatching { json.parseToJsonElement(body) }.getOrNull() ?: return null
    val passengersEl = find(root, PASSENGERS) as? JsonArray
    val passengers = passengersEl.orEmpty().mapNotNull { p ->
        val o = p as? JsonObject ?: return@mapNotNull null
        val booking = text(find(o, BOOKING)).orEmpty()
        val current = text(find(o, CURRENT)).orEmpty()
        if (booking.isBlank() && current.isBlank()) null else PassengerStatus(booking, current.ifBlank { booking })
    }
    val trainNo = text(find(root, TRAIN_NO))
    val trainName = text(find(root, TRAIN_NAME))
    if (passengers.isEmpty() && trainNo == null && trainName == null) return null
    val chart = when (val c = find(root, CHART)) {
        is JsonPrimitive -> c.booleanOrNull ?: c.contentOrNull?.lowercase()?.let { it.contains("prepared") && !it.contains("not") || it == "yes" || it == "y" }
        else -> null
    } ?: false
    return PnrSnapshot(pnr, trainNo, trainName, text(find(root, DATE)), chart, passengers)
}

private fun text(e: JsonElement?): String? = (e as? JsonPrimitive)?.contentOrNull?.trim()?.takeIf { it.isNotEmpty() && it != "null" }

/** First value under any of [keys] (case-insensitive), searched breadth-first. */
private fun find(root: JsonElement, keys: List<String>): JsonElement? {
    val queue = ArrayDeque<JsonElement>().apply { add(root) }
    var seen = 0
    while (queue.isNotEmpty() && seen < 2_000) {
        val e = queue.removeFirst()
        seen++
        when (e) {
            is JsonObject -> {
                e.entries.firstOrNull { it.key.lowercase() in keys }?.let { return it.value }
                e.values.forEach { if (it !is JsonPrimitive) queue.add(it) }
            }
            is JsonArray -> e.forEach { if (it !is JsonPrimitive) queue.add(it) }
            else -> Unit
        }
    }
    return null
}
