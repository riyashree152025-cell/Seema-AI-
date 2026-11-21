package com.raju.aiassistant.domain.automation

import com.raju.aiassistant.domain.remote.RemoteResult

/** Today's weather in one line for the morning briefing (only when it matters: rain, heat, cold). */
object WeatherLine {
    fun line(rainChancePercent: Int?, maxTempC: Double?, minTempC: Double?): String? {
        val parts = buildList {
            if (rainChancePercent != null && rainChancePercent >= 50) add("आज बारिश की संभावना $rainChancePercent प्रतिशत है — छाता साथ रखना")
            if (maxTempC != null && maxTempC >= 38) add("आज बहुत गर्मी है, ${maxTempC.toInt()} डिग्री तक — पानी पीते रहना")
            if (minTempC != null && minTempC <= 10) add("आज ठंड ${minTempC.toInt()} डिग्री तक जाएगी — गर्म कपड़े पहन लेना")
        }
        return if (parts.isEmpty()) null else parts.joinToString(", ") + "।"
    }
}

/**
 * PC / VPS check every half hour (only when the bridge is set up): two failed checks in a row → "PC / VPS से संपर्क नहीं",
 * less than [LOW_DISK_GB] free → "डिस्क भर रही है". Each alert once until it is fine again.
 */
object BridgeHealth {
    const val LOW_DISK_GB = 5.0

    data class State(val failures: Int = 0, val alertedDown: Boolean = false, val alertedDisk: Boolean = false)

    /** The new state and the alert to show (null: nothing new). */
    fun check(result: RemoteResult, before: State): Pair<State, String?> = when (result) {
        RemoteResult.NotConfigured -> State() to null
        is RemoteResult.Done -> {
            val free = result.fields["disk_free_gb"]?.toDoubleOrNull()
            val low = free != null && free < LOW_DISK_GB
            val alert = when {
                low && !before.alertedDisk -> "PC / VPS की डिस्क भर रही है — सिर्फ़ ${"%.1f".format(java.util.Locale.US, free)} GB खाली बचा है।"
                before.alertedDown -> "PC / VPS फिर से चालू है।"
                else -> null
            }
            State(failures = 0, alertedDown = false, alertedDisk = low) to alert
        }
        else -> {
            val failures = before.failures + 1
            if (failures >= 2 && !before.alertedDown) before.copy(failures = failures, alertedDown = true) to "PC / VPS से संपर्क नहीं हो रहा — शायद बंद है या इंटरनेट नहीं है।"
            else before.copy(failures = failures) to null
        }
    }
}

/**
 * Phone lost: an SMS "SEEMA FIND <secret word>" (the word Raju saved in Settings) from any phone makes this phone ring
 * loudly and answers with its location link. Without the exact word nothing happens, and the word is never sent back.
 */
object FindPhoneSms {
    private val CMD = Regex("^\\s*seema\\s+find\\s+(\\S{4,40})\\s*$", RegexOption.IGNORE_CASE)

    /** True when [body] is the find command with exactly [secret] (case-sensitive word, at least 4 characters). */
    fun matches(body: String, secret: String?): Boolean {
        val s = secret?.trim()?.takeIf { it.length >= 4 } ?: return false
        val word = CMD.matchEntire(body)?.groupValues?.get(1) ?: return false
        return java.security.MessageDigest.isEqual(word.toByteArray(), s.toByteArray())
    }

    fun reply(lat: Double?, lon: Double?): String =
        if (lat != null && lon != null) "Seema: phone is ringing. Location: https://maps.google.com/?q=${"%.5f".format(java.util.Locale.US, lat)},${"%.5f".format(java.util.Locale.US, lon)}"
        else "Seema: phone is ringing. Location is not available (location off or no permission)."
}
