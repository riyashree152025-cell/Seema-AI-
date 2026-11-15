package com.raju.aiassistant.domain.rail

/**
 * An IRCTC e-ticket read from its PDF text: PNR, train, date, stations, class and passengers. Every
 * field may be missing (a different layout, a scanned page): the readout then says so plainly instead
 * of guessing.
 */
data class IrctcTicket(
    val pnr: String?,
    val trainNumber: String?,
    val trainName: String?,
    val date: String?,
    val from: String?,
    val to: String?,
    val travelClass: String?,
    val passengers: List<TicketPassenger>,
)

data class TicketPassenger(
    val name: String,
    val age: Int?,
    val gender: String?,
    /** The latest status on the ticket ("CNF/B2/45/LOWER", "WL/5", "RAC/3"). */
    val status: String,
) {
    val confirmed: Boolean get() = status.uppercase().startsWith("CNF")
    /** Coach, seat and berth of a confirmed seat ("B2", "45", "LOWER"), when written. */
    val seat: Triple<String?, String?, String?>
        get() {
            if (!confirmed) return Triple(null, null, null)
            val parts = status.split('/').drop(1)
            return Triple(parts.getOrNull(0), parts.getOrNull(1), parts.getOrNull(2))
        }
}

object IrctcTicketParser {
    private val PNR = Regex("PNR(?:\\s*No\\.?)?\\s*[:\\-]?\\s*(\\d{10})", RegexOption.IGNORE_CASE)
    private val TRAIN = Regex(
        "Train\\s*(?:No\\.?)?\\s*/?\\s*(?:Name)?\\s*[:\\-]?\\s*(\\d{5})\\s*/?\\s*([A-Z][A-Z .\\-]{2,40}?)(?=\\s{2,}|\\s+(?:Class|Quota|Date|Distance)\\b|$)",
        setOf(RegexOption.IGNORE_CASE, RegexOption.MULTILINE),
    )
    private val DATE = Regex(
        "Date\\s*of\\s*Journey\\s*[:\\-]?\\s*(\\d{1,2}[-/ ][A-Za-z]{3}[-/ ]\\d{4}|\\d{1,2}[-/.]\\d{1,2}[-/.]\\d{4})",
        RegexOption.IGNORE_CASE,
    )
    private val FROM = Regex("(?<![A-Za-z])From\\s*[:\\-]?\\s*([A-Z][A-Z .]{1,40}?)\\s*\\(([A-Z]{2,5})\\)")
    private val TO = Regex("(?<![A-Za-z])To\\s*[:\\-]?\\s*([A-Z][A-Z .]{1,40}?)\\s*\\(([A-Z]{2,5})\\)")
    private val CLASS = Regex("Class\\s*[:\\-]?\\s*[A-Z .]*?\\(?\\b(1A|2A|3A|3E|SL|CC|2S|EC|FC)\\b\\)?", RegexOption.IGNORE_CASE)
    private val STATUS = Regex("\\b(?:CNF|WL|RAC|RLWL|PQWL|GNWL|RSWL|TQWL|CAN)(?:/[A-Z0-9]+)*\\b", RegexOption.IGNORE_CASE)
    private val PASSENGER = Regex(
        "^\\s*(\\d{1,2})[.)]?\\s+([A-Za-z][A-Za-z .]{1,40}?)\\s+(\\d{1,3})\\s+(MALE|FEMALE|TRANSGENDER|M|F|T)\\b(.*)$",
        setOf(RegexOption.IGNORE_CASE, RegexOption.MULTILINE),
    )

    /** Reads what it can; null only when the text does not look like a railway ticket at all. */
    fun parse(text: String): IrctcTicket? {
        val pnr = PNR.find(text)?.groupValues?.get(1)
        val train = TRAIN.find(text)
        val passengers = PASSENGER.findAll(text).mapNotNull { m ->
            val statuses = STATUS.findAll(m.groupValues[5]).map { it.value.uppercase() }.toList()
            // Booking status first, current status last: the current one is what matters now.
            val status = statuses.lastOrNull() ?: return@mapNotNull null
            TicketPassenger(
                name = m.groupValues[2].trim().replace(Regex("\\s+"), " "),
                age = m.groupValues[3].toIntOrNull(),
                gender = when (m.groupValues[4].uppercase().first()) { 'M' -> "M"; 'F' -> "F"; else -> "T" },
                status = status,
            )
        }.toList()
        if (pnr == null && train == null && passengers.isEmpty()) return null
        return IrctcTicket(
            pnr = pnr,
            trainNumber = train?.groupValues?.get(1),
            trainName = train?.groupValues?.get(2)?.trim()?.replace(Regex("\\s+"), " "),
            date = DATE.find(text)?.groupValues?.get(1),
            from = FROM.find(text)?.let { "${it.groupValues[1].trim()} (${it.groupValues[2]})" },
            to = TO.find(text)?.let { "${it.groupValues[1].trim()} (${it.groupValues[2]})" },
            travelClass = CLASS.find(text)?.groupValues?.get(1)?.uppercase(),
            passengers = passengers,
        )
    }
}

/**
 * The ticket told like a person would (Hindi): the journey first, then each passenger with their seat
 * or waiting number, then honestly what could not be read.
 */
fun spokenTicket(t: IrctcTicket): String = buildString {
    val train = listOfNotNull(t.trainNumber, t.trainName?.lowercase()?.split(' ')?.joinToString(" ") { w -> w.replaceFirstChar { it.uppercase() } })
        .joinToString(" ")
    append("टिकट देख लिया है")
    if (t.pnr != null) append(", PNR ${t.pnr.chunked(3).joinToString(" ")}")
    append("। ")
    if (train.isNotBlank()) append("ट्रेन $train")
    if (t.from != null && t.to != null) append(if (train.isNotBlank()) ", ${t.from} से ${t.to}" else "${t.from} से ${t.to}")
    if (t.date != null) append(", तारीख ${t.date}")
    if (t.travelClass != null) append(", क्लास ${t.travelClass}")
    if (train.isNotBlank() || t.from != null || t.date != null) append("। ")
    when (t.passengers.size) {
        0 -> append("यात्रियों की लिस्ट पढ़ी नहीं जा सकी। ")
        else -> {
            val confirmed = t.passengers.count { it.confirmed }
            append("कुल ${t.passengers.size} यात्री हैं")
            append(if (confirmed == t.passengers.size) ", सबकी सीट कन्फर्म है। " else ", $confirmed की सीट कन्फर्म है। ")
            t.passengers.forEach { p ->
                append(p.name.lowercase().split(' ').joinToString(" ") { w -> w.replaceFirstChar { it.uppercase() } })
                p.age?.let { append(", $it साल") }
                if (p.confirmed) {
                    val (coach, seat, berth) = p.seat
                    append(", कन्फर्म")
                    coach?.let { append(", कोच $it") }
                    seat?.let { append(", सीट $it") }
                    berth?.let { append(" ${berthName(it)}") }
                    if (coach == null) append(" (कोच नंबर टिकट में साफ़ नहीं लिखा)")
                } else {
                    append(", ${statusName(p.status)}")
                }
                append("। ")
            }
        }
    }
    val missing = listOfNotNull(
        "PNR".takeIf { t.pnr == null },
        "ट्रेन नंबर".takeIf { t.trainNumber == null },
        "यात्रा की तारीख".takeIf { t.date == null },
        "स्टेशन".takeIf { t.from == null || t.to == null },
    )
    if (missing.isNotEmpty()) append("${missing.joinToString(", ")} टिकट में साफ़ नहीं मिला, एक बार खुद देख लेना।")
}.trim()

private fun berthName(code: String): String = when (code.uppercase()) {
    "LB", "LOWER" -> "लोअर"
    "MB", "MIDDLE" -> "मिडिल"
    "UB", "UPPER" -> "अपर"
    "SL", "SIDE LOWER", "SIDELOWER" -> "साइड लोअर"
    "SU", "SIDE UPPER", "SIDEUPPER" -> "साइड अपर"
    "WS", "WINDOW" -> "विंडो"
    else -> code
}

private fun statusName(status: String): String {
    val parts = status.uppercase().split('/')
    val no = parts.getOrNull(1)
    return when {
        parts[0] == "RAC" -> "RAC" + (no?.let { " $it" } ?: "")
        parts[0].endsWith("WL") -> "वेटिंग" + (no?.let { " $it" } ?: "")
        parts[0] == "CAN" -> "कैंसल"
        else -> status
    }
}
