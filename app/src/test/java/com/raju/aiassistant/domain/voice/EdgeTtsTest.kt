package com.raju.aiassistant.domain.voice

import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class EdgeTtsTest {
    @Test
    fun swaraForHindiAndTanishaaForBengali() {
        assertEquals("hi-IN-SwaraNeural", EdgeTts.voiceFor("hi-IN").shortName)
        assertEquals("hi-IN-SwaraNeural", EdgeTts.voiceFor("en-IN").shortName) // Hinglish / English: Swara reads it naturally
        assertEquals("bn-IN-TanishaaNeural", EdgeTts.voiceFor("bn-IN").shortName)
        assertEquals("Microsoft Server Speech Text to Speech Voice (hi-IN, SwaraNeural)", EdgeTts.SWARA.ssmlName)
        assertEquals("Microsoft Server Speech Text to Speech Voice (bn-IN, TanishaaNeural)", EdgeTts.TANISHAA.ssmlName)
    }

    @Test
    fun theWarmMoodGetsExactlyTheAskedProsody() {
        // Swara rate -6 %, pitch +2 Hz; Tanishaa rate -5 %, pitch +2 Hz (at the user's normal speed).
        assertEquals(-6, EdgeTts.ratePercent(EdgeTts.SWARA, VoiceMood.WARM.rate))
        assertEquals(-5, EdgeTts.ratePercent(EdgeTts.TANISHAA, VoiceMood.WARM.rate))
        assertEquals(2, EdgeTts.pitchHz(EdgeTts.SWARA, VoiceMood.WARM))
        assertEquals(2, EdgeTts.pitchHz(EdgeTts.TANISHAA, null))
        // Late at night / upset: slower and a little lower, never higher.
        assertTrue(EdgeTts.ratePercent(EdgeTts.SWARA, VoiceMood.WHISPER.rate) < -6)
        assertTrue(EdgeTts.pitchHz(EdgeTts.SWARA, VoiceMood.CONTROLLED_UPSET) < 2)
    }

    @Test
    fun theSsmlCarriesVoiceProsodyAndEscapedText() {
        val ssml = EdgeTts.ssml("राजू & <सीमा> 'हाँ'", EdgeTts.SWARA, -6, 2)
        assertTrue(ssml, ssml.startsWith("<speak version='1.0' xmlns='http://www.w3.org/2001/10/synthesis' xml:lang='hi-IN'>"))
        assertTrue(ssml, ssml.contains("<voice name='Microsoft Server Speech Text to Speech Voice (hi-IN, SwaraNeural)'>"))
        assertTrue(ssml, ssml.contains("<prosody pitch='+2Hz' rate='-6%' volume='+0%'>राजू &amp; &lt;सीमा&gt; &apos;हाँ&apos;</prosody>"))
        assertTrue(EdgeTts.ssml("আজ", EdgeTts.TANISHAA, -5, 2).contains("xml:lang='bn-IN'"))
    }

    @Test
    fun secMsGecIsTheWindowsFiveMinuteTickHash() {
        // 2026-11-20 06:24:32 UTC and 06:21:00 are in the same 5-minute window; the value is 64 upper-case hex digits.
        val a = EdgeTts.secMsGec(1_795_242_272_000L)
        assertEquals(a, EdgeTts.secMsGec(1_795_242_060_000L))
        assertTrue(a.matches(Regex("[0-9A-F]{64}")))
        // Known value: Unix 0 → Windows ticks 116444736000000000.
        val expected = java.security.MessageDigest.getInstance("SHA-256")
            .digest("116444736000000000${EdgeTts.TRUSTED_CLIENT_TOKEN}".toByteArray()).joinToString("") { "%02X".format(it) }
        assertEquals(expected, EdgeTts.secMsGec(0L))
        // A clock 6 minutes off is corrected by the learned skew.
        assertEquals(EdgeTts.secMsGec(360_000L), EdgeTts.secMsGec(0L, skewSeconds = 360))
    }

    @Test
    fun theConnectionAndMessagesLookLikeTheBrowsers() {
        val url = EdgeTts.connectUrl("abc123", 0L)
        assertTrue(url, url.startsWith("wss://speech.platform.bing.com/consumer/speech/synthesize/readaloud/edge/v1?TrustedClientToken=6A5AA1D4EAFF4E9FB37E23D68491D6F4&Sec-MS-GEC="))
        assertTrue(url, url.endsWith("&Sec-MS-GEC-Version=${EdgeTts.SEC_MS_GEC_VERSION}&ConnectionId=abc123"))
        assertEquals("Thu Jan 01 1970 00:00:00 GMT+0000 (Coordinated Universal Time)", EdgeTts.timestamp(0L))
        val config = EdgeTts.configMessage(0L)
        assertTrue(config.contains("Path:speech.config\r\n\r\n") && config.contains("\"outputFormat\":\"audio-24khz-48kbitrate-mono-mp3\""))
        val ssml = EdgeTts.ssmlMessage("req1", 0L, "<speak/>")
        assertTrue(ssml.startsWith("X-RequestId:req1\r\nContent-Type:application/ssml+xml\r\n") && ssml.endsWith("Path:ssml\r\n\r\n<speak/>"))
    }

    @Test
    fun audioFramesAndTheEndOfTheTurnAreRecognised() {
        val header = "X-RequestId:req1\r\nContent-Type:audio/mpeg\r\nPath:audio\r\n".toByteArray()
        val mp3 = byteArrayOf(0xFF.toByte(), 0xFB.toByte(), 1, 2, 3)
        val frame = byteArrayOf((header.size shr 8).toByte(), header.size.toByte()) + header + mp3
        assertArrayEquals(mp3, EdgeTts.audioOf(frame))
        val other = "Path:audio.metadata\r\n".toByteArray()
        assertNull(EdgeTts.audioOf(byteArrayOf(0, other.size.toByte()) + other + mp3))
        assertNull(EdgeTts.audioOf(byteArrayOf(0, 99, 1))) // malformed
        assertEquals("turn.end", EdgeTts.pathOf("X-RequestId:req1\r\nContent-Type:application/json\r\nPath:turn.end\r\n\r\n{}"))
        assertEquals("turn.start", EdgeTts.pathOf("Path:turn.start\r\n\r\n{}"))
    }

    @Test
    fun theServersClockFixesAWrongPhoneClock() {
        assertEquals(300L, EdgeTts.skewSeconds("Thu, 01 Jan 1970 00:05:00 GMT", 0L))
        assertNull(EdgeTts.skewSeconds(null, 0L))
    }
}
