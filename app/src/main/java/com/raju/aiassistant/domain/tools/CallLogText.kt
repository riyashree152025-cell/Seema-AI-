package com.raju.aiassistant.domain.tools

/** How a call in the call log happened. */
enum class CallKind { INCOMING, OUTGOING, MISSED, REJECTED, OTHER }

/** "5 मिनट पहले", "2 घंटे पहले", "कल", "3 दिन पहले" — said the way people say it. */
fun agoInHindi(nowMs: Long, thenMs: Long): String {
    val min = ((nowMs - thenMs).coerceAtLeast(0) / 60_000L).toInt()
    return when {
        min < 1 -> "अभी-अभी"
        min < 60 -> "$min मिनट पहले"
        min < 24 * 60 -> "${min / 60} घंटे पहले"
        min < 48 * 60 -> "कल"
        else -> "${min / (24 * 60)} दिन पहले"
    }
}

/**
 * "आख़िरी कॉल Mummy की थी — आई हुई कॉल, 5 मिनट पहले।" The name is the contact's when known,
 * otherwise the number read out digit by digit.
 */
fun lastCallSentence(name: String?, number: String?, kind: CallKind, ago: String): String {
    val who = name?.takeIf { it.isNotBlank() }
        ?: number?.filter { it.isDigit() || it == '+' }?.takeIf { it.isNotEmpty() }?.toList()?.joinToString(" ")
        ?: "छुपे हुए नंबर"
    val how = when (kind) {
        CallKind.INCOMING -> "आई हुई कॉल"
        CallKind.OUTGOING -> "आपने की थी"
        CallKind.MISSED -> "मिस्ड कॉल"
        CallKind.REJECTED -> "काटी गई कॉल"
        CallKind.OTHER -> "कॉल"
    }
    return "आख़िरी कॉल $who की थी — $how, $ago।"
}
