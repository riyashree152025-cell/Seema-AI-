package com.raju.aiassistant.domain.booking

import java.time.LocalDate
import java.time.format.DateTimeFormatter
import java.util.Locale

/**
 * Train booking requests the user takes from customers (Tatkal / normal tickets to book later).
 * Spoken by the user ("kal Delhi se Patna, 3A, Anand 34 saal…"), turned into fields by the AI,
 * confirmed by the user, then kept only on the phone. Each one prints as a short slip.
 */
enum class BookingStatus(val label: String, val hindi: String) {
    PENDING("Pending", "बाकी"),
    COMPLETED("Completed", "हो गई"),
    CANCELLED("Cancelled", "रद्द"),
}

/** [gender] is 'M', 'F' or null when not said; [age] in years or null. */
data class Passenger(val name: String, val gender: Char? = null, val age: Int? = null)

data class Booking(
    val id: Long = 0,
    val journeyDate: LocalDate,
    val from: String,
    val to: String,
    val trainNo: String? = null,
    val travelClass: String? = null,
    /** Who sent this booking (customer / agent name or number). */
    val sender: String? = null,
    val passengers: List<Passenger>,
    val status: BookingStatus = BookingStatus.PENDING,
    val createdAt: Long = 0,
    val updatedAt: Long = 0,
)

const val MAX_PASSENGERS = 6

/** Where the daily slip files are saved on the phone (shown to the user). */
const val BOOKING_FOLDER = "Download/Seema Bookings"

/** Bookings kept on the phone; the store also rewrites the daily slip file after every change. */
interface BookingStore {
    suspend fun all(): List<Booking>
    /** Adds the booking when [Booking.id] is 0, otherwise replaces it. Returns the stored booking. */
    suspend fun upsert(booking: Booking): Booking
    suspend fun delete(id: Long): Boolean
}

object NoBookingStore : BookingStore {
    override suspend fun all() = emptyList<Booking>()
    override suspend fun upsert(booking: Booking) = booking
    override suspend fun delete(id: Long) = false
}

/** Writes the text file with all slips of one journey date (e.g. Bookings_06-11-2026.txt). */
fun interface DaySlipWriter {
    suspend fun write(date: LocalDate, fileName: String, text: String)
}

// ------------------------------------------------------------------ normalising what was heard

/** "male", "purush", "पुरुष", "ladka", "M" → 'M'; "female", "mahila", "लड़की", "F" → 'F'. */
fun normalizeGender(value: String?): Char? {
    val v = value?.trim()?.lowercase(Locale.ROOT)?.trimEnd('.') ?: return null
    if (v.isEmpty()) return null
    val male = listOf("m", "male", "man", "boy", "mr", "purush", "aadmi", "admi", "ladka", "larka", "gents", "पुरुष", "आदमी", "लड़का", "लडका", "भाई")
    val female = listOf("f", "female", "woman", "girl", "mrs", "ms", "mahila", "aurat", "ladki", "larki", "ladies", "महिला", "औरत", "लड़की", "लडकी", "स्त्री")
    return when {
        v in male -> 'M'
        v in female -> 'F'
        v.startsWith("fem") || v.startsWith("mahil") -> 'F'
        v.startsWith("mal") -> 'M'
        else -> null
    }
}

/** "34", "34 saal", "34 years" → 34; nonsense → null. */
fun normalizeAge(value: String?): Int? =
    value?.let { Regex("\\d{1,3}").find(it)?.value?.toIntOrNull() }?.takeIf { it in 0..125 }

/** "anand kumar" → "Anand Kumar" (Devanagari names stay as they are). */
fun normalizeName(value: String): String =
    value.trim().replace(Regex("\\s+"), " ").split(' ').joinToString(" ") { w ->
        w.lowercase(Locale.ROOT).replaceFirstChar { it.titlecase(Locale.ROOT) }
    }

/** Station codes (NDLS, PNBE) in capitals; names tidied ("new delhi" → "New Delhi"). */
fun normalizeStation(value: String): String {
    val v = value.trim().replace(Regex("\\s+"), " ")
    return if (v.length in 2..5 && v.all { it in 'a'..'z' || it in 'A'..'Z' }) v.uppercase(Locale.ROOT) else normalizeName(v)
}

/** "3a", "sleeper", "SL", "2 AC" → "3A", "SL", "SL", "2A". Unknown classes are kept as said. */
fun normalizeClass(value: String?): String? {
    val v = value?.trim()?.uppercase(Locale.ROOT)?.replace(" ", "")?.takeIf { it.isNotEmpty() } ?: return null
    return when (v) {
        "SLEEPER", "SL" -> "SL"
        "3A", "3AC", "AC3", "THIRDAC", "3TIER" -> "3A"
        "3E", "3AE", "3ECONOMY" -> "3E"
        "2A", "2AC", "AC2", "SECONDAC", "2TIER" -> "2A"
        "1A", "1AC", "AC1", "FIRSTAC" -> "1A"
        "CC", "CHAIRCAR", "ACCHAIR" -> "CC"
        "EC", "EXECUTIVE", "EXECUTIVECHAIR" -> "EC"
        "2S", "SECONDSITTING", "GENERAL", "SITTING" -> "2S"
        else -> v.take(12)
    }
}

/**
 * A booking from what the AI heard. Null when something essential is missing: date, both stations
 * and 1 to 6 passengers with a name.
 */
fun buildBooking(
    date: LocalDate?,
    from: String?,
    to: String?,
    trainNo: String?,
    travelClass: String?,
    sender: String?,
    passengers: List<Passenger>,
): Booking? {
    if (date == null || from.isNullOrBlank() || to.isNullOrBlank()) return null
    val people = passengers.filter { it.name.isNotBlank() }.map { it.copy(name = normalizeName(it.name)) }
    if (people.isEmpty() || people.size > MAX_PASSENGERS) return null
    return Booking(
        journeyDate = date,
        from = normalizeStation(from),
        to = normalizeStation(to),
        trainNo = trainNo?.trim()?.takeIf { it.isNotEmpty() },
        travelClass = normalizeClass(travelClass),
        sender = sender?.trim()?.takeIf { it.isNotEmpty() }?.let(::normalizeName),
        passengers = people,
    )
}

// ------------------------------------------------------------------ corrections

/**
 * A correction ("नहीं, नाम आनंद है", "तारीख 7 कर दो", "दूसरे की उम्र 40"). Only the fields that
 * were said are set. [passengerNo] (1-based) picks the passenger for [name]/[gender]/[age];
 * without it the change goes to the only passenger. [passengers] replaces the whole list.
 */
data class BookingEdit(
    val date: LocalDate? = null,
    val from: String? = null,
    val to: String? = null,
    val trainNo: String? = null,
    val travelClass: String? = null,
    val sender: String? = null,
    val passengers: List<Passenger>? = null,
    val passengerNo: Int? = null,
    val name: String? = null,
    val gender: Char? = null,
    val age: Int? = null,
    val removePassengerNo: Int? = null,
    val addPassenger: Passenger? = null,
) {
    val touchesPassenger: Boolean get() = name != null || gender != null || age != null
    val isEmpty: Boolean
        get() = date == null && from == null && to == null && trainNo == null && travelClass == null && sender == null &&
            passengers == null && !touchesPassenger && removePassengerNo == null && addPassenger == null
}

sealed interface EditResult {
    data class Ok(val booking: Booking) : EditResult
    /** Why the correction could not be applied (said back to the user). */
    data class Problem(val message: String) : EditResult
}

fun applyEdit(b: Booking, e: BookingEdit): EditResult {
    if (e.isEmpty) return EditResult.Problem("कौन सी चीज़ बदलनी है, यह समझ नहीं आया।")
    var people = e.passengers?.filter { it.name.isNotBlank() }?.map { it.copy(name = normalizeName(it.name)) } ?: b.passengers
    if (e.touchesPassenger) {
        val index = when {
            e.passengerNo != null -> e.passengerNo - 1
            people.size == 1 -> 0
            else -> return EditResult.Problem("इस बुकिंग में ${people.size} यात्री हैं — किस नंबर वाले का बदलना है?")
        }
        if (index !in people.indices) return EditResult.Problem("यात्री नंबर ${index + 1} इस बुकिंग में नहीं है।")
        val p = people[index]
        people = people.toMutableList().also {
            it[index] = p.copy(name = e.name?.let(::normalizeName) ?: p.name, gender = e.gender ?: p.gender, age = e.age ?: p.age)
        }
    }
    e.removePassengerNo?.let { n ->
        if (n - 1 !in people.indices) return EditResult.Problem("यात्री नंबर $n इस बुकिंग में नहीं है।")
        people = people.filterIndexed { i, _ -> i != n - 1 }
    }
    e.addPassenger?.takeIf { it.name.isNotBlank() }?.let { people = people + it.copy(name = normalizeName(it.name)) }
    if (people.isEmpty()) return EditResult.Problem("बुकिंग में कम से कम एक यात्री होना चाहिए।")
    if (people.size > MAX_PASSENGERS) return EditResult.Problem("एक बुकिंग में ज़्यादा से ज़्यादा $MAX_PASSENGERS यात्री हो सकते हैं।")
    return EditResult.Ok(
        b.copy(
            journeyDate = e.date ?: b.journeyDate,
            from = e.from?.takeIf { it.isNotBlank() }?.let(::normalizeStation) ?: b.from,
            to = e.to?.takeIf { it.isNotBlank() }?.let(::normalizeStation) ?: b.to,
            trainNo = e.trainNo?.trim()?.takeIf { it.isNotEmpty() } ?: b.trainNo,
            travelClass = normalizeClass(e.travelClass) ?: b.travelClass,
            sender = e.sender?.trim()?.takeIf { it.isNotEmpty() }?.let(::normalizeName) ?: b.sender,
            passengers = people,
        ),
    )
}

/** The booking a correction without a number means: the one saved or changed last. */
fun lastTouched(bookings: List<Booking>): Booking? = bookings.maxWithOrNull(compareBy<Booking>({ it.updatedAt }, { it.id }))

// ------------------------------------------------------------------ slip, list, search

private val SLIP_DATE = DateTimeFormatter.ofPattern("dd-MM-yyyy", Locale.ENGLISH)
private val SHORT_DATE = DateTimeFormatter.ofPattern("d MMM", Locale.ENGLISH)
private val DAY_NAME = DateTimeFormatter.ofPattern("EEE", Locale.ENGLISH)
private const val LINE = "========================================"
private const val THIN = "----------------------------------------"

fun slipDate(date: LocalDate): String = date.format(SLIP_DATE)

/** The short slip in the user's own format. */
fun formatSlip(b: Booking): String = buildString {
    appendLine(LINE)
    appendLine("🚆 JOURNEY DETAILS")
    appendLine(LINE)
    appendLine("📍 FROM: ${b.from}")
    appendLine("🏁 TO:   ${b.to}")
    append("📅 DATE: ${slipDate(b.journeyDate)} (${b.journeyDate.format(DAY_NAME)}) | 🚂 TRAIN NO: ${b.trainNo ?: "-"}")
    appendLine()
    if (b.travelClass != null || b.sender != null) {
        val parts = listOfNotNull(b.travelClass?.let { "💺 CLASS: $it" }, b.sender?.let { "👤 SENDER: $it" })
        appendLine(parts.joinToString(" | "))
    }
    appendLine(THIN)
    appendLine("👥 PASSENGER LIST")
    appendLine(THIN)
    b.passengers.forEachIndexed { i, p ->
        appendLine("${i + 1}. ${p.name} | ${p.gender ?: "-"} | ${p.age?.let { "$it Yrs" } ?: "- Yrs"}")
    }
    append(LINE)
}

/** One line for lists and the prompt: "#7 6 Nov Delhi → Patna, 3A, 2 यात्री (Anand, Sita) — Pending". */
fun describeBooking(b: Booking): String = buildString {
    append('#').append(b.id).append(' ').append(b.journeyDate.format(SHORT_DATE)).append(' ')
    append(b.from).append(" → ").append(b.to)
    b.trainNo?.let { append(", train ").append(it) }
    b.travelClass?.let { append(", ").append(it) }
    append(", ").append(b.passengers.size).append(" यात्री (").append(b.passengers.joinToString(", ") { it.name }).append(')')
    b.sender?.let { append(", from ").append(it) }
    append(" — ").append(b.status.label)
}

/** Newest journey first inside a status; search over names, stations, train, sender, date and number. */
fun searchBookings(all: List<Booking>, status: BookingStatus?, query: String): List<Booking> {
    val q = query.trim().lowercase(Locale.ROOT).removePrefix("#")
    return all.asSequence()
        .filter { status == null || it.status == status }
        .filter { b ->
            q.isEmpty() || b.id.toString() == q || listOfNotNull(
                b.from, b.to, b.trainNo, b.travelClass, b.sender, slipDate(b.journeyDate), b.journeyDate.format(SHORT_DATE),
            ).plus(b.passengers.map { it.name }).any { it.lowercase(Locale.ROOT).contains(q) }
        }
        .sortedWith(
            if (status == BookingStatus.PENDING) compareBy<Booking>({ it.journeyDate }, { it.id })
            else compareByDescending<Booking> { it.journeyDate }.thenByDescending { it.id },
        )
        .toList()
}

/** Spoken/shown list answer: counts per status and the next pending bookings. */
fun summarizeBookings(all: List<Booking>, status: BookingStatus?, query: String?): String {
    val found = searchBookings(all, status, query.orEmpty())
    if (found.isEmpty()) return if (query.isNullOrBlank()) "कोई ${status?.hindi?.let { "$it " }.orEmpty()}बुकिंग नहीं है।" else "\"$query\" से कोई बुकिंग नहीं मिली।"
    val head = if (status == null && query.isNullOrBlank()) {
        val counts = all.groupingBy { it.status }.eachCount()
        "बुकिंग: ${counts[BookingStatus.PENDING] ?: 0} बाकी, ${counts[BookingStatus.COMPLETED] ?: 0} हो गईं, ${counts[BookingStatus.CANCELLED] ?: 0} रद्द।"
    } else {
        "${found.size} बुकिंग मिलीं:"
    }
    return head + "\n" + found.take(8).joinToString("\n") { "• " + describeBooking(it) }
}

/** File name for one journey date's slips. */
fun daySlipFileName(date: LocalDate): String = "Bookings_${slipDate(date)}.txt"

/** All slips of one journey date in one text file; pending first, cancelled marked. */
fun daySlipFileText(date: LocalDate, bookings: List<Booking>, updatedAt: String): String = buildString {
    val day = bookings.filter { it.journeyDate == date }.sortedWith(compareBy({ it.status.ordinal }, { it.id }))
    appendLine("SEEMA AI — TRAIN BOOKINGS FOR ${slipDate(date)} (${date.format(DAY_NAME)})")
    appendLine("Updated: $updatedAt")
    val counts = day.groupingBy { it.status }.eachCount()
    appendLine("Pending: ${counts[BookingStatus.PENDING] ?: 0} | Completed: ${counts[BookingStatus.COMPLETED] ?: 0} | Cancelled: ${counts[BookingStatus.CANCELLED] ?: 0}")
    if (day.isEmpty()) appendLine("\n(no bookings)")
    day.forEach { b ->
        appendLine()
        appendLine("#${b.id} — ${b.status.label.uppercase(Locale.ROOT)}")
        appendLine(formatSlip(b))
    }
}

/**
 * Morning reminder: pending bookings for today and tomorrow (Tatkal for tomorrow opens today).
 * Null when there is nothing pending.
 */
fun bookingReminder(today: LocalDate, all: List<Booking>): String? {
    val pending = all.filter { it.status == BookingStatus.PENDING && (it.journeyDate == today || it.journeyDate == today.plusDays(1)) }
    if (pending.isEmpty()) return null
    return buildString {
        for ((label, date) in listOf("आज" to today, "कल" to today.plusDays(1))) {
            val list = pending.filter { it.journeyDate == date }
            if (list.isEmpty()) continue
            if (isNotEmpty()) append('\n')
            append("$label (${date.format(SHORT_DATE)}) की ${list.size} बुकिंग बाकी:")
            list.sortedBy { it.id }.forEach { b ->
                append("\n• ").append(b.from).append(" → ").append(b.to).append(", ")
                append(b.passengers.joinToString(", ") { it.name })
                b.sender?.let { append(" (").append(it).append(')') }
            }
        }
    }
}

/**
 * Bookings for the system prompt, so Seema can fix "नहीं, नाम आनंद है" on the right booking and
 * answer "कल की कितनी बुकिंग बाकी है?". Compact on purpose (it is sent with every request).
 */
fun bookingContext(all: List<Booking>, today: LocalDate, nowMillis: Long): String? {
    val recent = all.filter { nowMillis - it.updatedAt < 3 * 60 * 60_000L }.sortedByDescending { it.updatedAt }.take(2)
    val pending = all.filter { it.status == BookingStatus.PENDING && !it.journeyDate.isBefore(today) }
    if (recent.isEmpty() && pending.isEmpty()) return null
    return buildString {
        append("USER'S TRAIN BOOKINGS (on this phone; never invent):")
        if (pending.isNotEmpty()) append("\n- Pending: ").append(pending.size).append(", next: ")
            .append(pending.sortedBy { it.journeyDate }.take(3).joinToString("; ") { "#${it.id} ${it.journeyDate} ${it.from}-${it.to}" })
        recent.forEach { b ->
            append("\n- Last edited #").append(b.id).append(": ").append(b.journeyDate).append(' ').append(b.from).append('-').append(b.to)
            b.trainNo?.let { append(", train ").append(it) }
            b.travelClass?.let { append(", ").append(it) }
            b.sender?.let { append(", sender ").append(it) }
            append(", passengers: ").append(
                b.passengers.mapIndexed { i, p -> "${i + 1}) ${p.name} ${p.gender ?: "?"} ${p.age ?: "?"}" }.joinToString(", "),
            )
        }
    }
}
