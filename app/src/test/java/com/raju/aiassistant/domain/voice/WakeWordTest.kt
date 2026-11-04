package com.raju.aiassistant.domain.voice

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class WakeWordTest {
    @Test
    fun nameAtTheStartWakesAndKeepsTheRequest() {
        assertEquals("time kya hua?", afterWakeWord("Seema, time kya hua?"))
        assertEquals("", afterWakeWord("hey seema"))
        assertEquals("", afterWakeWord("heyseema"))
        assertEquals("", afterWakeWord("\u0939\u0947 \u0938\u0940\u092e\u093e")) // हे सीमा
        assertEquals("", afterWakeWord("\u0939\u0947\u0938\u0940\u092e\u093e")) // हेसीमा
        assertEquals("\u0915\u094d\u092f\u093e \u0939\u093e\u0932 \u0939\u0948", afterWakeWord("\u0939\u0947 \u0938\u0940\u092e\u093e \u0915\u094d\u092f\u093e \u0939\u093e\u0932 \u0939\u0948")) // हे सीमा क्या हाल है
        assertEquals("home screen par aao ya apna screen par aao.", afterWakeWord("\u0939\u0948 \u0924\u094b \u092e\u0948\u0902 \u0939\u0947\u0938\u0940\u092e\u093e \u092c\u094b\u0932 \u0930\u0939\u093e \u0939\u0942\u0902 \u0915\u093f home screen par aao ya apna screen par aao."))
        assertEquals("batao", afterWakeWord("Sima batao"))
        assertEquals("", afterWakeWord("\u0938\u0940\u092e\u093e")) // सीमा
        assertEquals("", afterWakeWord("\u0938\u0940\u092e\u093e \u091c\u0940")) // सीमा जी
    }

    @Test
    fun otherTalkDoesNotWake() {
        assertNull(afterWakeWord("TV pe news chal rahi hai"))
        assertNull(afterWakeWord("maine kal bazaar mein Seema ko dekha")) // her name buried in other talk
        assertNull(afterWakeWord("seemant aaya")) // a different word that starts the same
    }
}
