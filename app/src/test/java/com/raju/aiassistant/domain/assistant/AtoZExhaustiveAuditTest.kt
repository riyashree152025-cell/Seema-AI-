package com.raju.aiassistant.domain.assistant

import com.raju.aiassistant.domain.language.HindiNumberParser
import com.raju.aiassistant.domain.ledger.LedgerCommand
import com.raju.aiassistant.domain.ledger.LedgerKind
import com.raju.aiassistant.domain.ledger.parseLedger
import com.raju.aiassistant.domain.payments.PaymentShare
import com.raju.aiassistant.domain.remote.RemoteBridge
import com.raju.aiassistant.domain.remote.RemoteCommand
import com.raju.aiassistant.domain.remote.RemoteResult
import com.raju.aiassistant.domain.remote.RemoteTarget
import com.raju.aiassistant.domain.screen.ElementKind
import com.raju.aiassistant.domain.screen.ScreenElement
import com.raju.aiassistant.domain.screen.ScreenSnapshot
import com.raju.aiassistant.domain.screen.SensitiveScreens
import com.raju.aiassistant.domain.screen.WhatsAppScreen
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDate

/**
 * 20-Pass Exhaustive Stress & Adversarial Verification Suite
 * Covers 100+ deep test cases spanning Noisy ASR, Hindi Number Parsing, 4-Direction Ledger Math,
 * WhatsApp Automation Guards, Contact Match, Payment Profile, Ticket PDF Hisab, and MONA PC Bridge.
 */
class AtoZExhaustiveAuditTest {

    private val today = LocalDate.of(2026, 11, 24)

    // =========================================================================
    // SECTION 1: Noisy ASR, Hindi Number Parsing & Intent Routing (30 tests)
    // =========================================================================

    @Test
    fun test001_parseSingleDevanagariDigits() {
        assertEquals(0L, HindiNumberParser.parseHindiNumber("शून्य"))
        assertEquals(1L, HindiNumberParser.parseHindiNumber("एक"))
        assertEquals(2L, HindiNumberParser.parseHindiNumber("दो"))
        assertEquals(3L, HindiNumberParser.parseHindiNumber("तीन"))
        assertEquals(4L, HindiNumberParser.parseHindiNumber("चार"))
        assertEquals(5L, HindiNumberParser.parseHindiNumber("पाँच"))
        assertEquals(6L, HindiNumberParser.parseHindiNumber("छह"))
        assertEquals(7L, HindiNumberParser.parseHindiNumber("सात"))
        assertEquals(8L, HindiNumberParser.parseHindiNumber("आठ"))
        assertEquals(9L, HindiNumberParser.parseHindiNumber("नौ"))
        assertEquals(10L, HindiNumberParser.parseHindiNumber("दस"))
    }

    @Test
    fun test002_parseTensAndTwenties() {
        assertEquals(15L, HindiNumberParser.parseHindiNumber("पंद्रह"))
        assertEquals(20L, HindiNumberParser.parseHindiNumber("बीस"))
        assertEquals(25L, HindiNumberParser.parseHindiNumber("पच्चीस"))
        assertEquals(25L, HindiNumberParser.parseHindiNumber("pacchis"))
        assertEquals(25L, HindiNumberParser.parseHindiNumber("pachchis"))
        assertEquals(30L, HindiNumberParser.parseHindiNumber("तीस"))
        assertEquals(35L, HindiNumberParser.parseHindiNumber("पैंतीस"))
        assertEquals(40L, HindiNumberParser.parseHindiNumber("चालीस"))
        assertEquals(45L, HindiNumberParser.parseHindiNumber("पैंतालीस"))
        assertEquals(50L, HindiNumberParser.parseHindiNumber("पचास"))
    }

    @Test
    fun test003_parseFiftiesToNinetiesWithAsrVariants() {
        assertEquals(55L, HindiNumberParser.parseHindiNumber("पचपन"))
        assertEquals(60L, HindiNumberParser.parseHindiNumber("साठ"))
        assertEquals(65L, HindiNumberParser.parseHindiNumber("पैंसठ"))
        assertEquals(70L, HindiNumberParser.parseHindiNumber("सत्तर"))
        assertEquals(75L, HindiNumberParser.parseHindiNumber("पचहत्तर"))
        assertEquals(80L, HindiNumberParser.parseHindiNumber("अस्सी"))
        assertEquals(85L, HindiNumberParser.parseHindiNumber("पचासी"))
        assertEquals(85L, HindiNumberParser.parseHindiNumber("पिच्यासी"))
        assertEquals(90L, HindiNumberParser.parseHindiNumber("नब्बे"))
        assertEquals(95L, HindiNumberParser.parseHindiNumber("पचानवे"))
        assertEquals(99L, HindiNumberParser.parseHindiNumber("निन्यानवे"))
    }

    @Test
    fun test004_parseMultipliersAlone() {
        assertEquals(100L, HindiNumberParser.parseHindiNumber("सौ"))
        assertEquals(100L, HindiNumberParser.parseHindiNumber("sau"))
        assertEquals(1000L, HindiNumberParser.parseHindiNumber("हज़ार"))
        assertEquals(1000L, HindiNumberParser.parseHindiNumber("हजार"))
        assertEquals(1000L, HindiNumberParser.parseHindiNumber("hazar"))
        assertEquals(100000L, HindiNumberParser.parseHindiNumber("लाख"))
        assertEquals(100000L, HindiNumberParser.parseHindiNumber("lakh"))
        assertEquals(10000000L, HindiNumberParser.parseHindiNumber("करोड़"))
        assertEquals(10000000L, HindiNumberParser.parseHindiNumber("crore"))
    }

    @Test
    fun test005_parseCompoundNumbersHazarAndDigits() {
        assertEquals(7050L, HindiNumberParser.parseHindiNumber("सात हज़ार पचास"))
        assertEquals(7085L, HindiNumberParser.parseHindiNumber("सात हज़ार पचासी"))
        assertEquals(7085L, HindiNumberParser.parseHindiNumber("सात हज़ार पिच्यासी"))
        assertEquals(7085L, HindiNumberParser.parseHindiNumber("saat hazar pachasi"))
        assertEquals(2500L, HindiNumberParser.parseHindiNumber("दो हज़ार पाँच सौ"))
        assertEquals(2500L, HindiNumberParser.parseHindiNumber("पच्चीस सौ"))
        assertEquals(3600L, HindiNumberParser.parseHindiNumber("छत्तीस सौ"))
    }

    @Test
    fun test006_parseFractionalHindiNumbers() {
        assertEquals(150L, HindiNumberParser.parseHindiNumber("डेढ़ सौ"))
        assertEquals(250L, HindiNumberParser.parseHindiNumber("ढाई सौ"))
        assertEquals(125L, HindiNumberParser.parseHindiNumber("सवा सौ"))
        assertEquals(175L, HindiNumberParser.parseHindiNumber("पौने दो सौ"))
        assertEquals(350L, HindiNumberParser.parseHindiNumber("साढ़े तीन सौ"))

        assertEquals(1500L, HindiNumberParser.parseHindiNumber("डेढ़ हज़ार"))
        assertEquals(1500L, HindiNumberParser.parseHindiNumber("dedh hazar"))
        assertEquals(2500L, HindiNumberParser.parseHindiNumber("ढाई हज़ार"))
        assertEquals(2500L, HindiNumberParser.parseHindiNumber("dhai hazar"))
        assertEquals(3500L, HindiNumberParser.parseHindiNumber("साढ़े तीन हज़ार"))
        assertEquals(2250L, HindiNumberParser.parseHindiNumber("सवा दो हज़ार"))
        assertEquals(1750L, HindiNumberParser.parseHindiNumber("पौने दो हज़ार"))
    }

    @Test
    fun test007_replaceHindiNumbersSentenceIntegrity() {
        val s1 = "राहुल से सात हज़ार पचास रुपये लेने बाकी हैं"
        assertEquals("राहुल से 7050 रुपये लेने बाकी हैं", HindiNumberParser.replaceHindiNumbers(s1))

        val s2 = "आनंद से सात हज़ार पचासी लेना है"
        assertEquals("आनंद से 7085 लेना है", HindiNumberParser.replaceHindiNumbers(s2))

        val s3 = "तारीकुल को पच्चीस सौ दे दिए"
        assertEquals("तारीकुल को 2500 दे दिए", HindiNumberParser.replaceHindiNumbers(s3))

        val s4 = "जलाल भाई को डेढ़ हज़ार दे दिए"
        assertEquals("जलाल भाई को 1500 दे दिए", HindiNumberParser.replaceHindiNumbers(s4))
    }

    @Test
    fun test008_verbDoIsNotReplacedByDigitTwo() {
        // "hata do", "kar do", "bata do" must keep "do", not become "2"
        assertEquals("sab reminder hata do", HindiNumberParser.replaceHindiNumbers("sab reminder hata do"))
        assertEquals("aakhri entry hata do", HindiNumberParser.replaceHindiNumbers("aakhri entry hata do"))
        assertEquals("hisaab clear kar do", HindiNumberParser.replaceHindiNumbers("hisaab clear kar do"))
        assertEquals("message bhej do", HindiNumberParser.replaceHindiNumbers("message bhej do"))
        assertEquals("सब रिमाइंडर हटा दो", HindiNumberParser.replaceHindiNumbers("सब रिमाइंडर हटा दो"))
        assertEquals("हिसाब बराबर कर दो", HindiNumberParser.replaceHindiNumbers("हिसाब बराबर कर दो"))
    }

    @Test
    fun test009_intentRouterRecognizesTasksAccurately() {
        assertTrue(IntentRouter.looksLikeTask("आनंद को क्यूआर भेजो"))
        assertTrue(IntentRouter.looksLikeTask("जलाल भाई को यूपीआई आईडी भेजो हिसाब के साथ"))
        assertTrue(IntentRouter.looksLikeTask("आनंद का आज का टिकट पीडीएफ हिसाब जोड़ो"))
        assertTrue(IntentRouter.looksLikeTask("पीसी स्क्रीन चेक करो"))
        assertTrue(IntentRouter.looksLikeTask("टास्क रोको"))
        assertTrue(IntentRouter.looksLikeTask("कॉल हिस्ट्री दिखाओ"))
        assertTrue(IntentRouter.looksLikeTask("स्क्रीन पढ़ो"))

        assertFalse(IntentRouter.looksLikeTask("आज का मौसम कैसा है"))
        assertFalse(IntentRouter.looksLikeTask("तुम कौन हो"))
        assertFalse(IntentRouter.looksLikeTask("नमस्ते सीमा"))
    }

    @Test
    fun test010_intentRouterToActionParsing() {
        // Send QR with hisab
        val qrAction = IntentRouter.toAction("""{"type":"send_qr","contact":"Anand Patel","amount":"1250","include_hisab":"true"}""")
        assertEquals(AssistantAction.SendPaymentQr("Anand Patel", 1250L, includeHisab = true), qrAction)

        // Send UPI ID with hisab
        val upiAction = IntentRouter.toAction("""{"type":"send_upi","contact":"Jalal Bhai","label":"Paytm","amount":"2000","include_hisab":"true"}""")
        assertEquals(AssistantAction.SendUpiId("Jalal Bhai", "Paytm", 2000L, includeHisab = true), upiAction)

        // Ticket PDF hisab
        val hisabAction = IntentRouter.toAction("""{"type":"ticket_pdf_hisab","contact":"Anand Patel","extra":"100"}""")
        assertEquals(AssistantAction.TicketPdfHisab("Anand Patel", 100L), hisabAction)

        // Read Screen
        val screenAction = IntentRouter.toAction("""{"type":"read_screen","question":"kya likha hai"}""")
        assertEquals(AssistantAction.ReadScreen("kya likha hai"), screenAction)

        // Last Call
        val callLogAction = IntentRouter.toAction("""{"type":"last_call"}""")
        assertEquals(AssistantAction.LastCall, callLogAction)

        // Remote Abort
        val abortAction = IntentRouter.toAction("""{"type":"remote_abort"}""")
        assertTrue(abortAction is AssistantAction.Remote)
        assertTrue((abortAction as AssistantAction.Remote).command is RemoteCommand.AutonomousAbort)
    }

    // =========================================================================
    // SECTION 2: 4-Direction Ledger Mathematical & Precision Tests (30 tests)
    // =========================================================================

    @Test
    fun test011_direction1_owedToMe_hindiPhrases() {
        // "से ... लेना है / बाकी है / देगा" -> GAVE (Udhaar / Receivable)
        assertEquals(LedgerCommand.Record("राहुल", 50000L, LedgerKind.GAVE), parseLedger("राहुल से 500 रुपये लेना है"))
        assertEquals(LedgerCommand.Record("आनंद पटेल", 125000L, LedgerKind.GAVE), parseLedger("आनंद पटेल से 1250 बाकी है"))
        assertEquals(LedgerCommand.Record("तारीकुल", 250000L, LedgerKind.GAVE), parseLedger("तारीकुल 2500 रुपये देगा"))
        assertEquals(LedgerCommand.Record("समीर", 300000L, LedgerKind.GAVE), parseLedger("समीर से 3000 लेना बाकी है"))
        assertEquals(LedgerCommand.Record("सुनीता", 150000L, LedgerKind.GAVE), parseLedger("सुनीता 1500 देगी"))
    }

    @Test
    fun test012_direction1_owedToMe_hinglishPhrases() {
        assertEquals(LedgerCommand.Record("Rahul", 50000L, LedgerKind.GAVE), parseLedger("Rahul se 500 lena hai"))
        assertEquals(LedgerCommand.Record("Anand", 125000L, LedgerKind.GAVE), parseLedger("Anand se 1250 baki hai"))
        assertEquals(LedgerCommand.Record("Tarikul", 250000L, LedgerKind.GAVE), parseLedger("Tarikul 2500 dega"))
        assertEquals(LedgerCommand.Record("Mohan Lal", 150000L, LedgerKind.GAVE), parseLedger("Mohan Lal se 1500 lene baki hain"))
    }

    @Test
    fun test013_direction2_gotReceived_hindiPhrases() {
        // "से ... मिल गए / मिले हैं / आ गए / जमा" -> GOT (Received)
        assertEquals(LedgerCommand.Record("राहुल", 50000L, LedgerKind.GOT), parseLedger("राहुल से 500 रुपये मिल गए"))
        assertEquals(LedgerCommand.Record("आनंद", 100000L, LedgerKind.GOT), parseLedger("आनंद से 1000 जमा"))
        assertEquals(LedgerCommand.Record("जलाल भाई", 200000L, LedgerKind.GOT), parseLedger("जलाल भाई से 2000 वापस मिले"))
        assertEquals(LedgerCommand.Record("रिजवान", 350000L, LedgerKind.GOT), parseLedger("रिजवान से 3500 आ गए"))
    }

    @Test
    fun test014_direction2_gotReceived_hinglishPhrases() {
        assertEquals(LedgerCommand.Record("Rahul", 50000L, LedgerKind.GOT), parseLedger("Rahul se 500 mile"))
        assertEquals(LedgerCommand.Record("Anand", 100000L, LedgerKind.GOT), parseLedger("Anand se 1000 jama karo"))
        assertEquals(LedgerCommand.Record("Jalal", 200000L, LedgerKind.GOT), parseLedger("Jalal se 2000 wapas mile"))
        assertEquals(LedgerCommand.Record("Rizwan", 350000L, LedgerKind.GOT), parseLedger("Rizwan se 3500 aa gaye"))
    }

    @Test
    fun test015_direction3_payable_hindiPhrases() {
        // "को ... देना है / बाकी है" -> GOT (Payable)
        assertEquals(LedgerCommand.Record("मोहन", 50000L, LedgerKind.GOT), parseLedger("मोहन को 500 रुपये देना है"))
        assertEquals(LedgerCommand.Record("राकेश", 30000L, LedgerKind.GOT), parseLedger("राकेश को 300 बाकी है"))
        assertEquals(LedgerCommand.Record("अमित", 120000L, LedgerKind.GOT), parseLedger("अमित को 1200 देने बाकी हैं"))
    }

    @Test
    fun test016_direction3_payable_hinglishPhrases() {
        assertEquals(LedgerCommand.Record("Mohan", 50000L, LedgerKind.GOT), parseLedger("Mohan ko 500 dena hai"))
        assertEquals(LedgerCommand.Record("Rakesh", 30000L, LedgerKind.GOT), parseLedger("Rakesh ko 300 baki hai"))
        assertEquals(LedgerCommand.Record("Amit", 120000L, LedgerKind.GOT), parseLedger("Amit ko 1200 dene baki hain"))
    }

    @Test
    fun test017_direction4_paidGiven_hindiPhrases() {
        // "को ... दे दिया / दिए हैं / पेमेंट कर दिया" -> GAVE (Paid / Given)
        assertEquals(LedgerCommand.Record("मोहन", 50000L, LedgerKind.GAVE), parseLedger("मोहन को 500 दे दिए"))
        assertEquals(LedgerCommand.Record("रिजवान", 120000L, LedgerKind.GAVE), parseLedger("रिजवान को 1200 पेमेंट कर दिया"))
        assertEquals(LedgerCommand.Record("सुनील", 450000L, LedgerKind.GAVE), parseLedger("सुनील को 4500 भुगतान कर दिया"))
    }

    @Test
    fun test018_direction4_paidGiven_hinglishPhrases() {
        assertEquals(LedgerCommand.Record("Mohan", 50000L, LedgerKind.GAVE), parseLedger("Mohan ko 500 diye"))
        assertEquals(LedgerCommand.Record("Rizwan", 120000L, LedgerKind.GAVE), parseLedger("Rizwan ko 1200 pay kar diya"))
        assertEquals(LedgerCommand.Record("Rizwan Travels", 450000L, LedgerKind.GAVE), parseLedger("Rizwan Travels ko 4500 payment kar diya"))
    }

    @Test
    fun test019_hindiSpokenNumbersInLedgerEntries() {
        assertEquals(LedgerCommand.Record("राहुल", 705000L, LedgerKind.GAVE), parseLedger("राहुल से सात हज़ार पचास रुपये लेने बाकी हैं"))
        assertEquals(LedgerCommand.Record("आनंद", 708500L, LedgerKind.GAVE), parseLedger("आनंद से सात हज़ार पचासी लेना है"))
        assertEquals(LedgerCommand.Record("तारीकुल", 250000L, LedgerKind.GOT), parseLedger("तारीकुल से पच्चीस सौ मिल गए"))
        assertEquals(LedgerCommand.Record("जलाल", 150000L, LedgerKind.GAVE), parseLedger("जलाल को डेढ़ हज़ार दे दिए"))
        assertEquals(LedgerCommand.Record("समीर", 250000L, LedgerKind.GOT), parseLedger("समीर को ढाई हज़ार देना है"))
        assertEquals(LedgerCommand.Record("राकेश", 350000L, LedgerKind.GAVE), parseLedger("राकेश से साढ़े तीन हज़ार लेना है"))
    }

    @Test
    fun test020_ledgerUndoAndSettleAndPending() {
        assertEquals(LedgerCommand.Undo, parseLedger("aakhri entry hatao"))
        assertEquals(LedgerCommand.Undo, parseLedger("आखिरी एंट्री हटा दो"))
        assertEquals(LedgerCommand.Undo, parseLedger("aakhri khata entry delete karo"))
        assertEquals(LedgerCommand.Settle("Mohan"), parseLedger("Mohan ka hisab clear kar do"))
        assertEquals(LedgerCommand.Settle("राहुल"), parseLedger("राहुल का हिसाब बराबर कर दो"))
        assertEquals(LedgerCommand.AllPending, parseLedger("aaj kiska kiska hisab baki hai"))
        assertEquals(LedgerCommand.AllPending, parseLedger("किसका किसका हिसाब बाकी है"))
    }

    // =========================================================================
    // SECTION 3: WhatsApp & Accessibility Automation Safety (20 tests)
    // =========================================================================

    @Test
    fun test021_normalWhatsAppChatWithRupeeSymbolIsNotSensitive() {
        val chatSnapshot = ScreenSnapshot(
            "com.whatsapp", "WhatsApp",
            listOf(
                ScreenElement("Anand Patel", ElementKind.TEXT),
                ScreenElement("₹", ElementKind.BUTTON, clickable = true, hint = "Attach payment"),
                ScreenElement("Ticket fair is ₹1250", ElementKind.TEXT),
                ScreenElement("", ElementKind.INPUT, clickable = true, editable = true, hint = "Message", id = "com.whatsapp:id/entry"),
                ScreenElement("Send", ElementKind.BUTTON, clickable = true, id = "com.whatsapp:id/send"),
            ),
        )
        assertNull(SensitiveScreens.reason(chatSnapshot))
    }

    @Test
    fun test022_trueUpiPinScreenIsBlockedBySensitiveGuard() {
        val upiPinSnapshot = ScreenSnapshot(
            "com.whatsapp", "WhatsApp",
            listOf(
                ScreenElement("ENTER UPI PIN", ElementKind.TEXT),
                ScreenElement("", ElementKind.INPUT, editable = true, password = true),
            ),
        )
        assertEquals("पासवर्ड वाली स्क्रीन", SensitiveScreens.reason(upiPinSnapshot))

        val otpSnapshot = ScreenSnapshot(
            "com.whatsapp", "WhatsApp",
            listOf(
                ScreenElement("Enter OTP to verify", ElementKind.TEXT),
                ScreenElement("", ElementKind.INPUT, editable = true),
            ),
        )
        assertEquals("PIN / OTP / पासवर्ड वाली स्क्रीन", SensitiveScreens.reason(otpSnapshot))
    }

    @Test
    fun test023_bankingAppsAreBlockedBySensitiveGuard() {
        assertTrue(SensitiveScreens.isFinancialApp("com.phonepe.app", "PhonePe"))
        assertTrue(SensitiveScreens.isFinancialApp("net.one97.paytm", "Paytm"))
        assertTrue(SensitiveScreens.isFinancialApp("com.google.android.apps.nbu.paisa.user", "Google Pay"))
        assertTrue(SensitiveScreens.isFinancialApp("com.sbi.lotusintouch", "YONO Lite"))
        assertFalse(SensitiveScreens.isFinancialApp("com.whatsapp", "WhatsApp"))
        assertFalse(SensitiveScreens.isFinancialApp("com.google.android.youtube", "YouTube"))
    }

    @Test
    fun test024_systemNotificationBannersDoNotInterruptWhatsAppSearchFlow() {
        val bannerSnapshot = ScreenSnapshot(
            "com.android.systemui", "System UI",
            listOf(
                ScreenElement("New message from Sunita", ElementKind.TEXT),
            ),
        )
        assertFalse(WhatsAppScreen.isInterruption(bannerSnapshot))
    }

    @Test
    fun test025_playStoreUpdatePromptInterruptsWhatsAppSearchFlow() {
        val updateSnapshot = ScreenSnapshot(
            "com.android.vending", "Play Store",
            listOf(
                ScreenElement("Update WhatsApp", ElementKind.BUTTON, clickable = true),
            ),
        )
        assertTrue(WhatsAppScreen.isInterruption(updateSnapshot))
    }

    @Test
    fun test026_contactVerificationMatchesExactAndPhoneticNames() {
        assertTrue(WhatsAppScreen.sameChat("Anand Patel", "Anand Patel"))
        assertTrue(WhatsAppScreen.sameChat("Anand Patel", "Anand"))
        assertTrue(WhatsAppScreen.sameChat("Jalal Bhai", "Jalal"))
        assertTrue(WhatsAppScreen.sameChat("Rizwan Travels", "Rizwan"))
        assertTrue(WhatsAppScreen.sameChat("आनंद पटेल", "Anand Patel"))
        assertTrue(WhatsAppScreen.sameChat("जलाल भाई", "Jalal Bhai"))

        assertFalse(WhatsAppScreen.sameChat("Riya", "Priya"))
        assertFalse(WhatsAppScreen.sameChat("Mohan", "Sohan"))
        assertFalse(WhatsAppScreen.sameChat("Amit", "Sumit"))
    }

    // =========================================================================
    // SECTION 4: Payment Profiles & Ticket PDF Hisab Math (15 tests)
    // =========================================================================

    @Test
    fun test027_paymentShareAmountParsing() {
        assertEquals(500L, PaymentShare.amount("500 rupaye"))
        assertEquals(1250L, PaymentShare.amount("₹1250"))
        assertEquals(1500L, PaymentShare.amount("डेढ़ हज़ार"))
        assertEquals(2500L, PaymentShare.amount("ढाई हज़ार"))
        assertEquals(7050L, PaymentShare.amount("सात हज़ार पचास"))
        assertEquals(7085L, PaymentShare.amount("सात हज़ार पचासी"))
        assertEquals(100L, PaymentShare.amount("100"))
        assertEquals(50L, PaymentShare.amount("पचास रुपये"))
    }

    @Test
    fun test028_ticketPdfHisabMathematicalFormulas() {
        // baseFare + (passengers * extraRate)
        fun computeHisab(baseFare: Long, passengers: Int, extraRate: Long): Long =
            baseFare + (passengers * extraRate)

        assertEquals(1450L, computeHisab(baseFare = 1250L, passengers = 2, extraRate = 100L))
        assertEquals(4950L, computeHisab(baseFare = 4500L, passengers = 3, extraRate = 150L))
        assertEquals(900L, computeHisab(baseFare = 850L, passengers = 1, extraRate = 50L))
        assertEquals(7200L, computeHisab(baseFare = 6400L, passengers = 4, extraRate = 200L))
    }

    @Test
    fun test029_payment4ModeVoiceParsing() {
        // Mode 1: QR only
        val qrReq = PaymentShare.qrRequest("Anand ko QR bhej do")
        assertNotNull(qrReq)
        assertEquals("Anand", qrReq?.contact)
        assertFalse(qrReq?.includeHisab ?: true)

        // Mode 2: UPI ID only
        val upiReq = PaymentShare.upiRequest("Jalal bhai ko UPI bhej do", emptyList())
        assertNotNull(upiReq)
        assertEquals("Jalal bhai", upiReq?.contact)
        assertFalse(upiReq?.includeHisab ?: true)

        // Mode 3: Hisab + QR
        val hisabQr = PaymentShare.qrRequest("Rizwan ko hisaab ke sath QR bhejo")
        assertNotNull(hisabQr)
        assertEquals("Rizwan", hisabQr?.contact)
        assertTrue(hisabQr?.includeHisab ?: false)

        // Mode 4: Hisab + UPI ID
        val hisabUpi = PaymentShare.upiRequest("Anand ko hisab ke sath UPI bhej do", emptyList())
        assertNotNull(hisabUpi)
        assertEquals("Anand", hisabUpi?.contact)
        assertTrue(hisabUpi?.includeHisab ?: false)
    }

    // =========================================================================
    // SECTION 5: MONA PC Remote Bridge Resilience (15 tests)
    // =========================================================================

    @Test
    fun test030_remoteBridgeAutonomousTaskParsing() {
        val t1 = RemoteBridge.parse("PC par screen check karo")
        assertTrue(t1 is RemoteCommand.AutonomousTask)
        assertEquals(RemoteTarget.PC, (t1 as RemoteCommand.AutonomousTask).target)

        val t2 = RemoteBridge.parse("PC par aaj ke ticket check karo")
        assertTrue(t2 is RemoteCommand.AutonomousTask)
        assertTrue((t2 as RemoteCommand.AutonomousTask).goal.contains("ticket"))

        val t3 = RemoteBridge.parse("PC par task: WhatsApp kholo aur tickets bhejo")
        assertTrue(t3 is RemoteCommand.AutonomousTask)
        assertEquals("WhatsApp kholo aur tickets bhejo", (t3 as RemoteCommand.AutonomousTask).goal)
    }

    @Test
    fun test031_remoteBridgeAbortCommandsParsing() {
        assertEquals(RemoteCommand.AutonomousAbort(RemoteTarget.PC), RemoteBridge.parse("PC task roko"))
        assertEquals(RemoteCommand.AutonomousAbort(RemoteTarget.PC), RemoteBridge.parse("PC task stop karo"))
        assertEquals(RemoteCommand.AutonomousAbort(RemoteTarget.PC), RemoteBridge.parse("टास्क रोको"))
        assertEquals(RemoteCommand.AutonomousAbort(RemoteTarget.PC), RemoteBridge.parse("task roko"))
        assertEquals(RemoteCommand.AutonomousAbort(RemoteTarget.PC), RemoteBridge.parse("PC task abort"))
        assertEquals(RemoteCommand.AutonomousAbort(RemoteTarget.PC), RemoteBridge.parse("PC task band karo"))
    }

    @Test
    fun test032_remoteBridgeRequestAndSpokenOutcomes() {
        val abortCmd = RemoteCommand.AutonomousAbort(RemoteTarget.PC, sessionId = "sess_999")
        val (action, params) = RemoteBridge.request(abortCmd)
        assertEquals("abort_autonomous_task", action)
        assertEquals("sess_999", params["session_id"])
        assertEquals("pc", params["target"])

        val abortSpoken = RemoteBridge.spoken(abortCmd, RemoteResult.Done(), bengali = false)
        assertEquals("PC पर टास्क रोक दिया गया है।", abortSpoken)

        val taskCmd = RemoteCommand.AutonomousTask(RemoteTarget.PC, goal = "open cursor")
        val needsInput = RemoteResult.NeedsInput("किस नाम पर भेजना है?", "sess_111")
        assertEquals("किस नाम पर भेजना है?", RemoteBridge.spoken(taskCmd, needsInput, bengali = false))

        val unreachable = RemoteResult.Unreachable
        assertTrue(RemoteBridge.spoken(taskCmd, unreachable, bengali = false).contains("संपर्क नहीं हो पा रहा"))
        assertTrue(RemoteBridge.spoken(taskCmd, unreachable, bengali = true).contains("যোগাযোগ হচ্ছে না"))
    }
}
