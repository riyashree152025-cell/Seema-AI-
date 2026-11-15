package com.raju.aiassistant.domain.tatkal

/**
 * What Seema says when a Tatkal alarm rings: how many minutes are left until booking opens (AC at
 * 10:00, Sleeper at 11:00 — the next full hour after the alarm) and how fast the network reaches IRCTC
 * right now, measured, never guessed. [pingMs] null: IRCTC could not be reached.
 */
fun tatkalReadyLine(label: String, hour: Int, minute: Int, pingMs: Long?): String {
    val left = if (minute == 0) 0 else 60 - minute
    val opens = if (left == 0) "$label Tatkal अभी खुल रहा है" else "$label Tatkal खुलने में $left मिनट बचे हैं"
    val network = when {
        pingMs == null -> "पर नेटवर्क अभी IRCTC तक नहीं पहुँच रहा, एक बार इंटरनेट देख लो।"
        pingMs > SLOW_PING_MS -> "नेटवर्क थोड़ा धीमा है, ping $pingMs ms है… Wi-Fi या डेटा एक बार बदल कर देख लो।"
        else -> "नेटवर्क ping $pingMs ms है, सब एकदम तैयार है!"
    }
    return "जान, $opens… $network"
}

/** Above this the booking page usually lags in the first seconds of Tatkal. */
const val SLOW_PING_MS = 800L
