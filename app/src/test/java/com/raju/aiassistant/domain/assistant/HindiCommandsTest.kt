package com.raju.aiassistant.domain.assistant

import org.junit.Assert.assertEquals
import org.junit.Test

/**
 * Speech recognition is Hindi: commands arrive in Devanagari. Every basic phone command must still be
 * understood on the phone (instant, no AI, works offline). One sentence per capability, as the recogniser
 * writes it; a failure names the sentences.
 */
class HindiCommandsTest {
    private val expected = mapOf(
        "टॉर्च जलाओ" to "Flashlight", "टॉर्च बंद करो" to "Flashlight", "आवाज़ 50 करो" to "Volume", "वॉल्यूम कम करो" to "Volume",
        "वॉल्यूम बढ़ाओ" to "Volume", "5 मिनट का टाइमर लगाओ" to "SetTimer", "सुबह 6 बजे का अलार्म लगाओ" to "SetAlarm", "फोटो खींचो" to "TakePhoto",
        "सेल्फी लो" to "TakePhoto", "मम्मी को कॉल करो" to "Call", "पापा को फोन लगाओ" to "Call", "कॉल काटो" to "EndCall", "कॉल उठाओ" to "AnswerCall",
        "बैटरी कितनी है" to "Battery", "होम स्क्रीन पर जाओ" to "GoHome", "फोन लॉक करो" to "LockScreen", "गाना बंद करो" to "Media", "अगला गाना" to "Media",
        "स्क्रीनशॉट लो" to "Screenshot", "मेरा फोन ढूंढो" to "FindPhone", "आखिरी कॉल किसकी थी" to "LastCall", "वाईफाई सेटिंग खोलो" to "OpenSettings",
        "ब्लूटूथ चालू करो" to "OpenSettings", "पीछे जाओ" to "ScreenBack", "टाइम क्या हुआ" to "TellTime", "क्रोम खोलो" to "OpenApp", "यूट्यूब खोलो" to "OpenApp",
        "राहुल को व्हाट्सएप पर बोलो कि मैं आ रहा हूं" to "WhatsApp", "राहुल को मैसेज भेजो कि कल मिलते हैं" to "Sms", "मैसेज पढ़ो" to "ReadNotification",
        "10 मिनट बाद याद दिलाना कि दवाई लेनी है" to "ReminderAdd", "सब रिमाइंडर बताओ" to "ReminderList", "राहुल से 500 मिले" to "LedgerRecord",
        "राहुल का हिसाब बताओ" to "LedgerBalance", "तत्काल अलार्म 9:58 एसी" to "SetTatkalAlarm", "तत्काल कब खुलेगा" to "TatkalWhen",
        "इनबॉक्स चेक करो" to "InboxCheck", "3750 का रिफंड आया कि नहीं" to "PaymentCheck", "स्क्रीन पढ़ो" to "ReadScreen",
        "कैमरा से देख के बताओ ये क्या है" to "LookCamera", "लाइव कैमरा चालू करो" to "LiveCamera", "बुकिंग नोट बनाओ" to "BookingNoteStart",
        "यूट्यूब पर किशोर कुमार के गाने सर्च करो" to "AppSearch", "कल का कैलेंडर बताओ" to "CalendarRead",
    )

    @Test
    fun basicCommandsInDevanagariAreUnderstoodOnThePhone() {
        val wrong = expected.filter { (said, kind) -> LocalCommands.parse(said)?.let { it::class.simpleName } != kind }
            .map { (said, kind) -> "$said → ${LocalCommands.parse(said)?.let { it::class.simpleName }} (want $kind)" }
        assertEquals(emptyList<String>(), wrong)
    }
}
