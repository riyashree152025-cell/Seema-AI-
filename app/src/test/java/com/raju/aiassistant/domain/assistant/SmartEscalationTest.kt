package com.raju.aiassistant.domain.assistant

import com.raju.aiassistant.domain.sms.SmsMessage
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class SmartEscalationTest {
    @Test
    fun aReplyThatGivesUpIsRecognisedInEveryLanguage() {
        listOf(
            "हम्म… मुझे समझ नहीं आया।", "सॉरी, मुझे नहीं पता।", "pata nahi yaar", "আমি বুঝতে পারিনি।", "এটা জানি না।",
            "Sorry, I don't know that.", "I'm not sure about this one.",
        ).forEach { assertTrue(it, SmartEscalation.isUnsure(it)) }
    }

    @Test
    fun realAnswersAreNotEscalated() {
        assertFalse(SmartEscalation.isUnsure("हाँ जान, कल धूप रहेगी।"))
        assertFalse(SmartEscalation.isUnsure(""))
        // A long, real answer that mentions "पता नहीं" in passing.
        assertFalse(SmartEscalation.isUnsure("ब्लैक होल " + "बहुत भारी तारा होता है जो सिकुड़ जाता है, ".repeat(6) + "कितने हैं, किसी को पता नहीं।"))
    }

    @Test
    fun bankSmsAreSentOnlyRedacted() {
        val r = SmartEscalation.redact("UPI txn Rs 2400 into A/c XX3587 from rahul@ybl RRN 632354656706 on 20-11-26")
        assertEquals("UPI txn Rs 2400 into A/c XXXX from [UPI ID] RRN #### on 20-11-26", r)
        assertEquals("Acct XXXX debited Rs.500", SmartEscalation.redact("Acct 1234567 debited Rs.500"))
    }

    @Test
    fun onlyBankLikeMessagesWithoutOtpGo() {
        val sms = listOf(
            SmsMessage("XY-BANK", "Rs 2400 into A/c XX3587", 1),
            SmsMessage("XY-BANK", "123456 is your OTP for Rs 500", 2),
            SmsMessage("Mom", "khana kha liya?", 3),
        )
        assertEquals(listOf(1L), SmartEscalation.bankLike(sms).map { it.atMillis })
    }

    @Test
    fun theMoneyPromptHasTheQuestionAndRedactedMessagesNewestFirst() {
        val p = SmartEscalation.moneyPrompt(
            "आज कितना पैसा आया",
            listOf(SmsMessage("A", "Rs 100 into A/c XX1111", 1), SmsMessage("B", "Rs 200 into A/c XX2222", 2)),
        ) { "t$it" }
        assertTrue(p.startsWith("Raju asked: \"आज कितना पैसा आया\""))
        assertTrue(p.indexOf("[t2]") < p.indexOf("[t1]"))
        assertTrue(!p.contains("1111") && !p.contains("2222"))
        assertTrue(p.contains("never decimals"))
    }
}
