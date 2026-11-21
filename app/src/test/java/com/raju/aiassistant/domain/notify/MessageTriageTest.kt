package com.raju.aiassistant.domain.notify

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class MessageTriageTest {
    private fun msg(text: String, who: String = "Unknown", pkg: String = "com.whatsapp") = IncomingItem("k", pkg, "WhatsApp", who, null, text)

    @Test
    fun scamsAreRecognised() {
        listOf(
            "Dear customer your KYC will expire today, update now http://bit.ly/x",
            "Congratulations! You have won lottery of 25 lakh",
            "Your SBI account will be blocked today. Click https://sbi-kyc.xyz to verify",
            "बिजली कनेक्शन आज रात कट जाएगा, तुरंत 9876543210 पर कॉल करें",
            "Please share the OTP to receive your refund",
            "Part time job, daily earn 5000 from home",
        ).forEach { assertTrue(it, MessageTriage.looksLikeFraud(it)) }
    }

    @Test
    fun ordinaryMessagesAreNotScams() {
        listOf("kal milte hain", "Rs 4500 credited to your a/c XX1234", "PNR 6840828103 chart prepared", "OTP for login is 482913", "bijli aa gayi").forEach {
            assertFalse(it, MessageTriage.looksLikeFraud(it))
        }
    }

    @Test
    fun moneyRailAndClientsComeFirst() {
        assertTrue(MessageTriage.isPriority(msg("Rs 4500 credited to your a/c")))
        assertTrue(MessageTriage.isPriority(msg("PNR chart prepared, coach S4")))
        assertTrue(MessageTriage.isPriority(msg("kal aata hoon", who = "Rahul"), knownPeople = setOf("rahul")))
        assertFalse(MessageTriage.isPriority(msg("good morning", who = "Group")))
        assertTrue(MessageTriage.fraudWarning(msg("x", who = "Bank")).contains("सावधान"))
    }
}
