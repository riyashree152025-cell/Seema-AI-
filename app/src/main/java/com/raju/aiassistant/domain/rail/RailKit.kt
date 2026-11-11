package com.raju.aiassistant.domain.rail

import com.raju.aiassistant.domain.business.PassengerStatus
import com.raju.aiassistant.domain.business.PnrSnapshot
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.booleanOrNull
import kotlinx.serialization.json.contentOrNull
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import java.util.Locale

/*
 * RailKit (https://api.railkit.in, header "x-api-key") — the railway data service the user subscribed
 * to. Everything Seema says about trains comes from these answers, read by the code below; the AI
 * never fills a train number, a time, a status or a fare from its own memory.
 *
 * Every answer has the shape { "success": true, "data": … } or { "success": false, "error": "…" }.
 */

/** One answer from the rail service. */
sealed interface RailResult<out T> {
    data class Ok<T>(val value: T) : RailResult<T>
    /** No RailKit key in Settings. */
    data object NotConfigured : RailResult<Nothing>
    data class Failed(val message: String) : RailResult<Nothing>
}

data class Station(val code: String, val name: String)

data class TrainBetween(
    val number: String,
    val name: String,
    val fromCode: String,
    val toCode: String,
    val departs: String,
    val arrives: String,
    val duration: String,
    /** "1111111" from Monday to Sunday (as the service gives it), or empty. */
    val runningDays: String,
)

data class LiveStatus(
    val number: String,
    val name: String,
    val statusNote: String,
    val lastUpdate: String,
    /** Delay at the last stop the train has reached ("00:25"), or null when none was given. */
    val delay: String?,
    val nextStop: String?,
    val nextStopArrival: String?,
)

data class Fare(val number: String, val name: String, val travelClass: String, val total: Int, val base: Int?, val tatkal: Int?)

data class SeatDay(val date: String, val availability: String, val prediction: String?)

data class SeatAvailability(val number: String, val name: String, val totalFare: Int?, val days: List<SeatDay>)

/** The rail service as Seema uses it. Without a key every call answers NotConfigured. */
interface RailSource {
    suspend fun stations(name: String): RailResult<List<Station>> = RailResult.NotConfigured
    suspend fun trainsBetween(from: String, to: String, date: LocalDate?): RailResult<List<TrainBetween>> = RailResult.NotConfigured
    suspend fun live(train: String, date: LocalDate): RailResult<LiveStatus> = RailResult.NotConfigured
    suspend fun fare(train: String, from: String, to: String, date: LocalDate, travelClass: String, quota: String): RailResult<Fare> = RailResult.NotConfigured
    suspend fun seats(train: String, from: String, to: String, date: LocalDate, travelClass: String, quota: String): RailResult<SeatAvailability> = RailResult.NotConfigured
}

object NoRail : RailSource

/** DD-MM-YYYY, the date form every RailKit path takes. */
fun railDate(date: LocalDate): String = date.format(DateTimeFormatter.ofPattern("dd-MM-yyyy"))

private val json = Json { ignoreUnknownKeys = true; isLenient = true }

private fun JsonElement?.obj(): JsonObject? = this as? JsonObject
private fun JsonElement?.arr(): JsonArray? = this as? JsonArray
private fun JsonObject?.text(key: String): String? =
    (this?.get(key) as? JsonPrimitive)?.contentOrNull?.trim()?.takeIf { it.isNotEmpty() && it != "null" }
private fun JsonObject?.int(key: String): Int? = text(key)?.toDoubleOrNull()?.let { Math.round(it).toInt() }

/** The "data" of a successful answer; or the service's own error text. */
internal sealed interface Envelope {
    data class Data(val data: JsonElement) : Envelope
    data class Error(val message: String) : Envelope
}

internal fun envelope(body: String): Envelope {
    val root = runCatching { json.parseToJsonElement(body) }.getOrNull().obj()
        ?: return Envelope.Error("रेल सेवा का जवाब पढ़ा नहीं जा सका।")
    val ok = (root["success"] as? JsonPrimitive)?.booleanOrNull
    val data = root["data"]
    if (ok == false || data == null || data is kotlinx.serialization.json.JsonNull) {
        return Envelope.Error(root.text("error") ?: root.text("message") ?: "रेल सेवा से जवाब नहीं मिला।")
    }
    return Envelope.Data(data)
}

/** What to tell the user for an HTTP error (the reasons RailKit documents). */
fun railHttpError(code: Int): String = when (code) {
    401 -> "RailKit API key सही नहीं है — Settings में key दोबारा डालिए।"
    403 -> "RailKit API key बंद है, या इस plan में सीधा API नहीं मिलता (Advance plan चाहिए)।"
    404 -> "रेल सेवा में यह जानकारी नहीं मिली।"
    429 -> "RailKit की इस महीने की सीमा पूरी हो गई है।"
    else -> "रेल सेवा ने जवाब नहीं दिया (HTTP $code)।"
}

private fun <T> parsed(body: String, read: (JsonElement) -> T?): RailResult<T> = when (val e = envelope(body)) {
    is Envelope.Error -> RailResult.Failed(e.message)
    is Envelope.Data -> read(e.data)?.let { RailResult.Ok(it) } ?: RailResult.Failed("रेल सेवा के जवाब में यह जानकारी नहीं थी।")
}

// ------------------------------------------------------------------------------------------- PNR

/** "22 Aug 2026, 04:35:00 pm" → 2026-08-22 (null when it is written some other way). */
fun railJourneyDate(text: String?): LocalDate? {
    val day = text?.substringBefore(',')?.trim() ?: return null
    return runCatching { LocalDate.parse(day, DateTimeFormatter.ofPattern("d MMM yyyy", Locale.ENGLISH)) }.getOrNull()
}

/** A PNR answer, with the journey date when it could be read (for the chart watch). */
data class RailPnr(val snapshot: PnrSnapshot, val journeyDate: LocalDate?, val route: String?)

/**
 * A ticket worth watching until its chart is prepared: waiting or RAC, chart still open, journey not
 * over. A confirmed ticket is never tracked.
 */
fun shouldWatchChart(answer: RailPnr, today: LocalDate): Boolean {
    val date = answer.journeyDate ?: return false
    val s = answer.snapshot
    return !date.isBefore(today) && !s.chartPrepared && s.passengers.isNotEmpty() && !s.allConfirmed
}

fun parseRailPnr(pnr: String, body: String): RailResult<RailPnr> = parsed(body) { data ->
    val d = data.obj() ?: return@parsed null
    val train = d["train"].obj()
    val journey = d["journey"].obj()
    val passengers = d["passengers"].arr().orEmpty().mapNotNull { p ->
        val o = p.obj() ?: return@mapNotNull null
        val booking = o["booking"].obj().let { it.text("details") ?: it.text("status") }.orEmpty()
        val current = o["current"].obj().let { it.text("details") ?: it.text("status") }.orEmpty()
        if (booking.isBlank() && current.isBlank()) null else PassengerStatus(booking, current.ifBlank { booking })
    }
    if (train == null && passengers.isEmpty()) return@parsed null
    val chart = d["chart"].obj().text("status").orEmpty().lowercase()
    val dateText = journey.text("dateOfJourney")
    val from = journey?.get("boardingPoint").obj().text("code") ?: journey?.get("source").obj().text("code")
    val to = journey?.get("destination").obj().text("code")
    RailPnr(
        PnrSnapshot(
            pnr = pnr,
            trainNumber = train.text("number"),
            trainName = train.text("name"),
            date = dateText?.substringBefore(',')?.trim(),
            chartPrepared = chart.contains("prepared") && !chart.contains("not"),
            passengers = passengers,
        ),
        journeyDate = railJourneyDate(dateText),
        route = if (from != null && to != null) "$from-$to" else null,
    )
}

// -------------------------------------------------------------------------------------- Stations

fun parseStations(body: String): RailResult<List<Station>> = parsed(body) { data ->
    val list = data.obj()?.get("stations").arr() ?: data.arr() ?: return@parsed null
    list.mapNotNull { s ->
        val o = s.obj() ?: return@mapNotNull null
        val code = o.text("code") ?: return@mapNotNull null
        Station(code.uppercase(), o.text("name") ?: code)
    }
}

/** Common ways a city is said that are not how the station is written ("दिल्ली" → "delhi"). */
private val CITY_WORDS: Map<String, String> = mapOf(
    "दिल्ली" to "delhi", "dilli" to "delhi", "नई दिल्ली" to "new delhi", "कटिहार" to "katihar", "पटना" to "patna",
    "मुंबई" to "mumbai", "बम्बई" to "mumbai", "bombay" to "mumbai", "कोलकाता" to "kolkata", "कलकत्ता" to "kolkata",
    "हावड़ा" to "howrah", "लखनऊ" to "lucknow", "कानपुर" to "kanpur", "वाराणसी" to "varanasi", "बनारस" to "varanasi",
    "banaras" to "varanasi", "गुवाहाटी" to "guwahati", "सिलीगुड़ी" to "siliguri", "भागलपुर" to "bhagalpur",
    "चेन्नई" to "chennai", "बेंगलुरु" to "bengaluru", "बैंगलोर" to "bengaluru", "bangalore" to "bengaluru",
    "हैदराबाद" to "hyderabad", "पुणे" to "pune", "अहमदाबाद" to "ahmedabad", "जयपुर" to "jaipur", "गोरखपुर" to "gorakhpur",
    "रांची" to "ranchi", "अमृतसर" to "amritsar", "चंडीगढ़" to "chandigarh", "पूर्णिया" to "purnea", "किशनगंज" to "kishanganj",
)

/** "KIR", "kir" → a station code as it is; a city name → the words to search the service with. */
fun stationQuery(said: String): String {
    val t = said.trim().trim('.', '।', '?')
    return CITY_WORDS[t.lowercase()] ?: t
}

/** A 2-5 letter word written in capitals is already a station code ("KIR", "NDLS"). */
fun asStationCode(said: String): String? = said.trim().takeIf { Regex("[A-Z]{2,5}").matches(it) }

/**
 * The station meant by [said] among the service's matches: the one whose name is exactly the city,
 * else a main station of that city ("NEW DELHI", "KATIHAR JN"), else the first match.
 */
fun pickStation(said: String, matches: List<Station>): Station? {
    if (matches.isEmpty()) return null
    val q = stationQuery(said).lowercase()
    fun n(s: Station) = s.name.lowercase()
    return matches.firstOrNull { it.code.equals(said.trim(), ignoreCase = true) }
        ?: matches.firstOrNull { n(it) == q }
        ?: matches.firstOrNull { n(it) == "new $q" }
        ?: matches.firstOrNull { n(it) in setOf("$q jn", "$q junction", "$q jn.", "$q city", "$q central") }
        ?: matches.firstOrNull { n(it).startsWith(q) }
        ?: matches.first()
}

// ---------------------------------------------------------------------- Trains between stations

fun parseTrainsBetween(body: String): RailResult<List<TrainBetween>> = parsed(body) { data ->
    val list = data.arr() ?: data.obj()?.get("trains").arr() ?: return@parsed null
    list.mapNotNull { t ->
        val o = t.obj() ?: return@mapNotNull null
        val number = o.text("train_no") ?: return@mapNotNull null
        TrainBetween(
            number = number,
            name = o.text("train_name").orEmpty(),
            fromCode = o.text("from_stn_code").orEmpty(),
            toCode = o.text("to_stn_code").orEmpty(),
            departs = o.text("from_time").orEmpty(),
            arrives = o.text("to_time").orEmpty(),
            duration = o.text("travel_time").orEmpty(),
            runningDays = o.text("running_days").orEmpty(),
        )
    }
}

/** A train number said digit by digit, so the voice does not read "twelve thousand…". */
fun spokenTrainNumber(number: String): String = number.toList().joinToString(" ")

/** The short answer that is read out: how many, then the first few with number, name and departure. */
fun spokenTrainsBetween(from: Station, to: Station, date: LocalDate?, trains: List<TrainBetween>, spokenLimit: Int = 4): String {
    val day = date?.let { " ${it.dayOfMonth}/${it.monthValue} को" }.orEmpty()
    if (trains.isEmpty()) return "${from.name} से ${to.name} के लिए$day कोई सीधी ट्रेन नहीं मिली।"
    val head = "${from.name} से ${to.name} के लिए$day ${trains.size} ट्रेनें हैं। "
    val list = trains.take(spokenLimit).joinToString("; ") { t ->
        "${spokenTrainNumber(t.number)} ${t.name}, ${t.departs} पर"
    }
    val more = if (trains.size > spokenLimit) " — बाकी ${trains.size - spokenLimit} स्क्रीन पर लिखी हैं।" else "।"
    return head + list + more
}

/** The full list for the screen (one train a line). */
fun writtenTrainsBetween(from: Station, to: Station, date: LocalDate?, trains: List<TrainBetween>): String = buildString {
    append("🚆 ${from.name} (${from.code}) → ${to.name} (${to.code})")
    date?.let { append(" · ${railDate(it)}") }
    append(" · ${trains.size} ट्रेनें")
    trains.forEach { t ->
        append("\n${t.number} ${t.name} · ${t.departs} → ${t.arrives}")
        if (t.duration.isNotBlank()) append(" (${t.duration})")
    }
}

// ----------------------------------------------------------------------------------------- Live

fun parseLive(body: String): RailResult<LiveStatus> = parsed(body) { data ->
    val d = data.obj() ?: return@parsed null
    val number = d.text("trainNo") ?: d.text("trainNumber") ?: return@parsed null
    val note = d.text("statusNote") ?: return@parsed null
    val stops = d["timeline"].arr().orEmpty().mapNotNull { it.obj() }.filter { it.text("type") != "intermediate" }
    // The delay the train is running with: at the last stop it has reached (departure, else arrival).
    val reached = stops.lastOrNull { it.text("status") == "passed" || it.text("status") == "current" }
    val delay = reached?.let { s ->
        s["departure"].obj().text("delay") ?: s["arrival"].obj().text("delay")
    }
    val next = stops.firstOrNull { it.text("status") == "upcoming" }
    LiveStatus(
        number = number,
        name = d.text("trainName").orEmpty(),
        statusNote = note,
        lastUpdate = d.text("lastUpdate").orEmpty(),
        delay = delay,
        nextStop = next?.text("stationName"),
        nextStopArrival = next?.get("arrival").obj().let { it.text("actual") ?: it.text("scheduled") },
    )
}

/** "00:25" → "25 मिनट", "01:05" → "1 घंटा 5 मिनट"; "00:00" or unreadable → null (said as on time / not said). */
fun spokenDelay(delay: String?): String? {
    val m = Regex("^(\\d{1,2}):(\\d{2})$").find(delay?.trim().orEmpty()) ?: return null
    val h = m.groupValues[1].toInt()
    val min = m.groupValues[2].toInt()
    return when {
        h == 0 && min == 0 -> null
        h == 0 -> "$min मिनट"
        min == 0 -> "$h घंटा"
        else -> "$h घंटा $min मिनट"
    }
}

fun spokenLive(s: LiveStatus): String = buildString {
    append("ट्रेन ${spokenTrainNumber(s.number)}")
    if (s.name.isNotBlank()) append(" ${s.name}")
    append(": ${s.statusNote}। ")
    when {
        s.delay == null -> Unit // the service gave no delay: nothing is claimed
        spokenDelay(s.delay) == null -> append("समय पर चल रही है। ")
        else -> append("${spokenDelay(s.delay)} देर से चल रही है। ")
    }
    if (s.nextStop != null) {
        append("अगला स्टेशन ${s.nextStop}")
        s.nextStopArrival?.let { append(", $it") }
        append("। ")
    }
    if (s.lastUpdate.isNotBlank()) append("जानकारी ${s.lastUpdate} की है।")
}.trim()

// --------------------------------------------------------------------------------- Fare & seats

fun parseFare(body: String): RailResult<Fare> = parsed(body) { data ->
    val d = data.obj() ?: return@parsed null
    val total = d.int("totalFare") ?: return@parsed null
    Fare(d.text("trainNo").orEmpty(), d.text("trainName").orEmpty(), d.text("class").orEmpty(), total, d.int("baseFare"), d.int("tatkalFare")?.takeIf { it > 0 })
}

fun spokenFare(f: Fare, from: String, to: String): String =
    "ट्रेन ${spokenTrainNumber(f.number)} ${f.name}, $from से $to, ${f.travelClass}: कुल किराया ${f.total} रुपये।" +
        (f.tatkal?.let { " तत्काल किराया $it रुपये।" } ?: "")

fun parseSeats(body: String): RailResult<SeatAvailability> = parsed(body) { data ->
    val d = data.obj() ?: return@parsed null
    val days = d["availability"].arr().orEmpty().mapNotNull { a ->
        val o = a.obj() ?: return@mapNotNull null
        val text = o.text("availabilityText") ?: return@mapNotNull null
        SeatDay(o.text("date").orEmpty(), text, o.text("prediction"))
    }
    if (days.isEmpty()) return@parsed null
    val train = d["train"].obj()
    SeatAvailability(train.text("trainNo").orEmpty(), train.text("trainName").orEmpty(), d["fare"].obj().int("totalFare"), days)
}

fun spokenSeats(s: SeatAvailability, travelClass: String): String = buildString {
    val first = s.days.first()
    append("ट्रेन ${spokenTrainNumber(s.number)} ${s.name}, $travelClass")
    if (first.date.isNotBlank()) append(", ${first.date}")
    append(": ${first.availability}।")
    s.totalFare?.let { append(" किराया $it रुपये।") }
}

/** Class and quota as RailKit takes them; null when it is not one of its codes (then she asks). */
fun railClass(said: String?): String? {
    val t = said?.trim()?.uppercase()?.replace(" ", "") ?: return null
    return when (t) {
        "SL", "SLEEPER", "स्लीपर" -> "SL"
        "3A", "3AC", "AC3", "THIRDAC" -> "3A"
        "2A", "2AC", "AC2", "SECONDAC" -> "2A"
        "1A", "1AC", "AC1", "FIRSTAC" -> "1A"
        "3E", "CC", "EC", "2S" -> t
        else -> null
    }
}

fun railQuota(said: String?): String = when (said?.trim()?.uppercase()) {
    "TQ", "TATKAL", "तत्काल" -> "TQ"
    "LD", "LADIES" -> "LD"
    "SS", "SENIOR" -> "SS"
    else -> "GN"
}
