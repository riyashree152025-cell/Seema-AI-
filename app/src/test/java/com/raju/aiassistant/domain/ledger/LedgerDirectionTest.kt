package com.raju.aiassistant.domain.ledger

import org.junit.Assert.assertEquals
import org.junit.Test

class LedgerDirectionTest {

    @Test
    fun direction1_owedToMe_mapsToGave_Receivable() {
        // "से ... लेना है / बाकी है / देगा" -> GAVE (Udhaar/Receivable)
        assertEquals(
            LedgerCommand.Record("राहुल", 50_000, LedgerKind.GAVE),
            parseLedger("राहुल से 500 रुपये लेना है")
        )
        assertEquals(
            LedgerCommand.Record("Rahul", 50_000, LedgerKind.GAVE),
            parseLedger("Rahul se 500 lena hai")
        )
        assertEquals(
            LedgerCommand.Record("आनंद पटेल", 150_000, LedgerKind.GAVE),
            parseLedger("आनंद पटेल से 1500 बाकी है")
        )
        assertEquals(
            LedgerCommand.Record("Mohan", 150_000, LedgerKind.GAVE),
            parseLedger("Mohan se 1500 lene baki hain")
        )
        assertEquals(
            LedgerCommand.Record("तारीकुल", 250_000, LedgerKind.GAVE),
            parseLedger("तारीकुल 2500 रुपये देगा")
        )
        assertEquals(
            LedgerCommand.Record("Zaid", 500_000, LedgerKind.GAVE),
            parseLedger("Zaid 5 hazar dega")
        )
    }

    @Test
    fun direction2_gotReceived_mapsToGot() {
        // "से ... मिल गए / मिले हैं / आ गए / जमा" -> GOT (Received)
        assertEquals(
            LedgerCommand.Record("राहुल", 50_000, LedgerKind.GOT),
            parseLedger("राहुल से 500 रुपये मिल गए")
        )
        assertEquals(
            LedgerCommand.Record("Rahul", 50_000, LedgerKind.GOT),
            parseLedger("Rahul se 500 mile")
        )
        assertEquals(
            LedgerCommand.Record("आनंद", 100_000, LedgerKind.GOT),
            parseLedger("आनंद से 1000 जमा")
        )
        assertEquals(
            LedgerCommand.Record("Jalal Bhai", 200_000, LedgerKind.GOT),
            parseLedger("Jalal Bhai se 2000 jama karo")
        )
        assertEquals(
            LedgerCommand.Record("Rizwan", 300_000, LedgerKind.GOT),
            parseLedger("Rizwan se 3000 wapas mile")
        )
    }

    @Test
    fun direction3_iOwe_mapsToGot_Payable() {
        // "को ... देना है / बाकी है" -> GOT (Payable)
        assertEquals(
            LedgerCommand.Record("मोहन", 50_000, LedgerKind.GOT),
            parseLedger("मोहन को 500 रुपये देना है")
        )
        assertEquals(
            LedgerCommand.Record("Mohan", 50_000, LedgerKind.GOT),
            parseLedger("Mohan ko 500 dena hai")
        )
        assertEquals(
            LedgerCommand.Record("राकेश", 30_000, LedgerKind.GOT),
            parseLedger("राकेश को 300 बाकी है")
        )
        assertEquals(
            LedgerCommand.Record("Amit", 120_000, LedgerKind.GOT),
            parseLedger("Amit ko 1200 dene baki hain")
        )
    }

    @Test
    fun direction4_paidGiven_mapsToGave() {
        // "को ... दे दिया / दिए हैं / पेमेंट कर दिया" -> GAVE (Paid)
        assertEquals(
            LedgerCommand.Record("मोहन", 50_000, LedgerKind.GAVE),
            parseLedger("मोहन को 500 दे दिए")
        )
        assertEquals(
            LedgerCommand.Record("Mohan", 50_000, LedgerKind.GAVE),
            parseLedger("Mohan ko 500 diye")
        )
        assertEquals(
            LedgerCommand.Record("रिजवान", 120_000, LedgerKind.GAVE),
            parseLedger("रिजवान को 1200 पेमेंट कर दिया")
        )
        assertEquals(
            LedgerCommand.Record("Rizwan Travels", 450_000, LedgerKind.GAVE),
            parseLedger("Rizwan Travels ko 4500 pay kar diya")
        )
    }

    @Test
    fun hindiNumbersParsedInLedgerCommands() {
        // "सात हज़ार पचास" -> 7050 -> 705000 paise
        assertEquals(
            LedgerCommand.Record("राहुल", 705_000, LedgerKind.GAVE),
            parseLedger("राहुल से सात हज़ार पचास रुपये लेने बाकी हैं")
        )
        // "सात हज़ार पचासी" -> 7085 -> 708500 paise
        assertEquals(
            LedgerCommand.Record("आनंद", 708_500, LedgerKind.GAVE),
            parseLedger("आनंद से सात हज़ार पचासी लेना है")
        )
        // "पच्चीस सौ" -> 2500 -> 250000 paise
        assertEquals(
            LedgerCommand.Record("तारीकुल", 250_000, LedgerKind.GOT),
            parseLedger("तारीकुल से पच्चीस सौ मिल गए")
        )
        // "डेढ़ हज़ार" -> 1500 -> 150000 paise
        assertEquals(
            LedgerCommand.Record("जलाल", 150_000, LedgerKind.GAVE),
            parseLedger("जलाल को डेढ़ हज़ार दे दिए")
        )
        // "ढाई हज़ार" -> 2500 -> 250000 paise
        assertEquals(
            LedgerCommand.Record("समीर", 250_000, LedgerKind.GOT),
            parseLedger("समीर को ढाई हज़ार देना है")
        )
    }
}
