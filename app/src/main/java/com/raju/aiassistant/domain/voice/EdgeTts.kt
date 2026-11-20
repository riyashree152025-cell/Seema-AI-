package com.raju.aiassistant.domain.voice

import java.security.MessageDigest
import java.time.Instant
import java.time.ZoneOffset
import java.time.format.DateTimeFormatter
import java.util.Locale
import kotlin.math.roundToInt

/**
 * Microsoft Edge's "Read aloud" neural voices, as the Edge browser asks for them: a WebSocket to
 * speech.platform.bing.com with the browser's public TrustedClientToken and the Sec-MS-GEC check value, one
 * speech.config message, one SSML message, MP3 audio back in binary frames until "turn.end". No key and no
 * account — Seema's always-there voice when Sarvam or Cartesia has no credits left or fails.
 *
 * Note: this is the browser's own (unofficial, undocumented) endpoint; Microsoft can change or block it.
 * Everything here is pure text and bytes, so it is unit-tested; the socket lives in data/tts.
 */
object EdgeTts {
    const val TRUSTED_CLIENT_TOKEN = "6A5AA1D4EAFF4E9FB37E23D68491D6F4"
    /** The Edge (Chromium) version the requests present; Sec-MS-GEC-Version is "1-" + it. */
    const val CHROMIUM_FULL_VERSION = "130.0.2849.68"
    const val SEC_MS_GEC_VERSION = "1-$CHROMIUM_FULL_VERSION"
    const val WSS_BASE = "wss://speech.platform.bing.com/consumer/speech/synthesize/readaloud/edge/v1"
    const val ORIGIN = "chrome-extension://jdiccldimpdaibmpdkjnbmckianbfold"
    val USER_AGENT = "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) " +
        "Chrome/${CHROMIUM_FULL_VERSION.substringBefore('.')}.0.0.0 Safari/537.36 Edg/${CHROMIUM_FULL_VERSION.substringBefore('.')}.0.0.0"
    /** 24 kHz mono MP3: the format the read-aloud service serves. */
    const val OUTPUT_FORMAT = "audio-24khz-48kbitrate-mono-mp3"

    /** One neural voice and the soft, unhurried base prosody Raju asked for. */
    data class Voice(val shortName: String, val locale: String, val ratePercent: Int, val pitchHz: Int) {
        /** The full name the service expects in SSML. */
        val ssmlName: String get() = "Microsoft Server Speech Text to Speech Voice ($locale, ${shortName.substringAfter("$locale-")})"
    }

    /** Hindi and Hinglish: Swara, a little slower (-6 %) and a touch brighter (+2 Hz). */
    val SWARA = Voice("hi-IN-SwaraNeural", "hi-IN", ratePercent = -6, pitchHz = 2)
    /** Bengali: Tanishaa, a little slower (-5 %) and a touch brighter (+2 Hz). */
    val TANISHAA = Voice("bn-IN-TanishaaNeural", "bn-IN", ratePercent = -5, pitchHz = 2)

    /** Bengali (bn-…) → Tanishaa; everything else (Hindi, Hinglish) → Swara. */
    fun voiceFor(languageTag: String): Voice = if (languageTag.startsWith("bn", ignoreCase = true)) TANISHAA else SWARA

    /**
     * The rate for this sentence: the voice's base rate, moved by how much slower or faster her mood and the
     * user's speech-rate setting ask than the warm mood's normal pace (warm at speed 1.0 = exactly the base).
     */
    fun ratePercent(voice: Voice, rate: Float): Int =
        (voice.ratePercent + ((rate / VoiceMood.WARM.rate) - 1f) * 100f).roundToInt().coerceIn(-50, 50)

    /** The pitch for this sentence: the base, a little lower when she is upset or whispering. */
    fun pitchHz(voice: Voice, mood: VoiceMood?): Int =
        voice.pitchHz + (((mood ?: VoiceMood.WARM).pitch - VoiceMood.WARM.pitch) * 30f).roundToInt()

    private fun signed(n: Int) = if (n >= 0) "+$n" else "$n"

    private fun escapeXml(text: String): String = buildString(text.length) {
        for (c in text) when (c) {
            '&' -> append("&amp;")
            '<' -> append("&lt;")
            '>' -> append("&gt;")
            '"' -> append("&quot;")
            '\'' -> append("&apos;")
            else -> if (c.code >= 0x20 || c == '\n' || c == '\t') append(c) // no control characters
        }
    }

    fun ssml(text: String, voice: Voice, ratePercent: Int, pitchHz: Int): String =
        "<speak version='1.0' xmlns='http://www.w3.org/2001/10/synthesis' xml:lang='${voice.locale}'>" +
            "<voice name='${voice.ssmlName}'>" +
            "<prosody pitch='${signed(pitchHz)}Hz' rate='${signed(ratePercent)}%' volume='+0%'>${escapeXml(text)}</prosody>" +
            "</voice></speak>"

    /** "Thu Nov 20 2026 06:24:32 GMT+0000 (Coordinated Universal Time)", as the browser writes X-Timestamp. */
    fun timestamp(nowMillis: Long): String =
        DateTimeFormatter.ofPattern("EEE MMM dd yyyy HH:mm:ss", Locale.US).withZone(ZoneOffset.UTC).format(Instant.ofEpochMilli(nowMillis)) +
            " GMT+0000 (Coordinated Universal Time)"

    private const val WIN_EPOCH_SECONDS = 11_644_473_600L

    /**
     * Sec-MS-GEC: SHA-256 (upper-case hex) of the Windows file time, rounded down to 5 minutes, followed by the
     * client token. [skewSeconds] corrects a phone clock that is off (learned from the server's Date on a 403).
     */
    fun secMsGec(nowMillis: Long, skewSeconds: Long = 0): String {
        var seconds = nowMillis / 1000 + skewSeconds + WIN_EPOCH_SECONDS
        seconds -= seconds % 300
        val ticks = seconds * 10_000_000L // 100-ns intervals
        val digest = MessageDigest.getInstance("SHA-256").digest("$ticks$TRUSTED_CLIENT_TOKEN".toByteArray(Charsets.US_ASCII))
        return digest.joinToString("") { "%02X".format(it) }
    }

    fun connectUrl(connectionId: String, nowMillis: Long, skewSeconds: Long = 0): String =
        "$WSS_BASE?TrustedClientToken=$TRUSTED_CLIENT_TOKEN&Sec-MS-GEC=${secMsGec(nowMillis, skewSeconds)}" +
            "&Sec-MS-GEC-Version=$SEC_MS_GEC_VERSION&ConnectionId=$connectionId"

    fun configMessage(nowMillis: Long): String =
        "X-Timestamp:${timestamp(nowMillis)}\r\nContent-Type:application/json; charset=utf-8\r\nPath:speech.config\r\n\r\n" +
            "{\"context\":{\"synthesis\":{\"audio\":{\"metadataoptions\":{\"sentenceBoundaryEnabled\":\"false\",\"wordBoundaryEnabled\":\"false\"}," +
            "\"outputFormat\":\"$OUTPUT_FORMAT\"}}}}\r\n"

    fun ssmlMessage(requestId: String, nowMillis: Long, ssml: String): String =
        "X-RequestId:$requestId\r\nContent-Type:application/ssml+xml\r\nX-Timestamp:${timestamp(nowMillis)}Z\r\nPath:ssml\r\n\r\n$ssml"

    /** The "Path:" of a text frame ("turn.start", "audio.metadata", "turn.end"), or null. */
    fun pathOf(textFrame: String): String? =
        textFrame.substringBefore("\r\n\r\n").lineSequence().map { it.trim() }.firstOrNull { it.startsWith("Path:", ignoreCase = true) }?.substringAfter(':')?.trim()

    /**
     * The audio of a binary frame: 2 bytes (big-endian) header length, the header ("Path:audio" …), then the
     * audio bytes. null for a frame that is not audio or is malformed; an empty array for an audio frame without data.
     */
    fun audioOf(frame: ByteArray): ByteArray? {
        if (frame.size < 2) return null
        val headerLength = ((frame[0].toInt() and 0xFF) shl 8) or (frame[1].toInt() and 0xFF)
        if (2 + headerLength > frame.size) return null
        val header = String(frame, 2, headerLength, Charsets.UTF_8)
        if (pathOf(header + "\r\n\r\n") != "audio") return null
        return frame.copyOfRange(2 + headerLength, frame.size)
    }

    /** The server's clock vs the phone's, from a 403's Date header, in seconds (to fix Sec-MS-GEC). */
    fun skewSeconds(serverDate: String?, nowMillis: Long): Long? {
        val server = serverDate?.let { runCatching { java.time.ZonedDateTime.parse(it, DateTimeFormatter.RFC_1123_DATE_TIME) }.getOrNull() } ?: return null
        return server.toEpochSecond() - nowMillis / 1000
    }
}
