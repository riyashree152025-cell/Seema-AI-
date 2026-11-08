package com.raju.aiassistant.domain.tools

import org.junit.Assert.assertEquals
import org.junit.Test

class CallLogTextTest {
    @Test
    fun theLastCallIsSaidNaturally() {
        assertEquals("आख़िरी कॉल Mummy की थी — आई हुई कॉल, 5 मिनट पहले।", lastCallSentence("Mummy", "+919800000001", CallKind.INCOMING, agoInHindi(600_000, 300_000)))
        assertEquals("आख़िरी कॉल 9 8 0 0 0 की थी — मिस्ड कॉल, अभी-अभी।", lastCallSentence(null, "98000", CallKind.MISSED, agoInHindi(1_000, 0)))
        assertEquals("आख़िरी कॉल छुपे हुए नंबर की थी — काटी गई कॉल, कल।", lastCallSentence(null, null, CallKind.REJECTED, agoInHindi(30 * 3_600_000L, 0)))
        assertEquals("2 घंटे पहले", agoInHindi(2 * 3_600_000L + 5, 0))
        assertEquals("3 दिन पहले", agoInHindi(3 * 86_400_000L, 0))
    }
}
