package com.raju.aiassistant.domain.assistant

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class WhatsAppCommandTest {
    @Test
    fun oneShotRequestGivesNameAndMessage() {
        assertEquals(
            WhatsAppRequest("Rizwan Travels", "दादा पेमेंट नहीं आया, पेमेंट भेजो, ज़रूरत है"),
            WhatsAppRequest.parse("सीमा, Rizwan Travels को मैसेज भेजो कि दादा पेमेंट नहीं आया, पेमेंट भेजो, ज़रूरत है"),
        )
        assertEquals(
            WhatsAppRequest("रिजवान ट्रैवल्स", "दादा पेमेंट नहीं आया"),
            WhatsAppRequest.parse("रिजवान ट्रैवल्स को व्हाट्सएप में मैसेज भेजो कि दादा पेमेंट नहीं आया"),
        )
        assertEquals(
            WhatsAppRequest("Rizwan Travels", "dada payment nahi aaya"),
            WhatsAppRequest.parse("Rizwan Travels ko whatsapp pe message bhejo ki dada payment nahi aaya"),
        )
    }

    @Test
    fun aRequestedLanguageIsTakenOutOfTheMessage() {
        assertEquals(
            WhatsAppRequest("Rizwan Travels", "दादा पेमेंट नहीं आया", MessageStyle.HINGLISH),
            WhatsAppRequest.parse("Rizwan Travels को WhatsApp में मैसेज भेजो कि दादा पेमेंट नहीं आया Hinglish में"),
        )
    }

    @Test
    fun conversationalStartsAskWhoAndWhat() {
        assertEquals(WhatsAppRequest(null, null), WhatsAppRequest.parse("सीमा, किसी को WhatsApp करना है"))
        assertEquals(WhatsAppRequest(null, null), WhatsAppRequest.parse("WhatsApp पर मैसेज भेजना है"))
        assertEquals(WhatsAppRequest(null, null), WhatsAppRequest.parse("kisi ko whatsapp karna hai"))
        assertEquals(WhatsAppRequest("Rizwan Travels", null), WhatsAppRequest.parse("Rizwan Travels को WhatsApp करना है"))
    }

    @Test
    fun otherSentencesAreNotWhatsAppRequests() {
        assertNull(WhatsAppRequest.parse("मम्मी को कॉल करो"))
        assertNull(WhatsAppRequest.parse("Rahul ko SMS bhejo ki late hoga"))
        assertNull(WhatsAppRequest.parse("आज मौसम कैसा है"))
        assertNull(WhatsAppRequest.parse("राहुल को बोलो कि कल आना"))
    }

    @Test
    fun theDictatedAnswerIsCleanedUp() {
        assertEquals("कल का बुकिंग का पैसा नहीं आया है" to null, WhatsAppRequest.message("ठीक है, उसको बोलो कि कल का बुकिंग का पैसा नहीं आया है"))
        assertEquals("kal aaunga" to null, WhatsAppRequest.message("likho ki kal aaunga"))
    }

    @Test
    fun shortAnswersAreUnderstood() {
        assertEquals(true, VoiceReply.yesNo("हाँ"))
        assertEquals(true, VoiceReply.yesNo("हाँ, भेज दो।"))
        assertEquals(true, VoiceReply.yesNo("ok"))
        assertEquals(false, VoiceReply.yesNo("नहीं"))
        assertEquals(false, VoiceReply.yesNo("रहने दो"))
        assertNull(VoiceReply.yesNo("कल आऊँगा"))
        assertEquals("लिखो कि कल आऊँगा", VoiceReply.afterNo("नहीं, लिखो कि कल आऊँगा"))
        assertEquals(1, VoiceReply.choice("दूसरा वाला", 3))
        assertEquals(0, VoiceReply.choice("पहला", 2))
        assertNull(VoiceReply.choice("तीसरा", 2))
    }
}
