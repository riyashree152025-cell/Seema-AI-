package com.raju.aiassistant.domain.memory

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDateTime

class CallMemoryExtractorTest {
    private val now = LocalDateTime.of(2026, 11, 20, 12, 0)

    @Test
    fun aMoneyPromiseWithPersonAndDay() {
        val c = CallMemoryExtractor.extract("Rahul kal 5000 rupaye dega", now).single()
        assertEquals(CommitmentKind.MONEY_IN, c.kind)
        assertEquals("Rahul", c.person)
        assertEquals(500_000L, c.amountPaise)
        assertEquals(LocalDateTime.of(2026, 11, 21, 10, 0), c.due)
        assertEquals("Rahul से 5,000 रुपये आने हैं", CallMemoryExtractor.reminderText(c, bengali = false))
    }

    @Test
    fun hindiThousandWordsAndEveningTime() {
        val c = CallMemoryExtractor.extract("अमित ने कहा परसों शाम 6 बजे दो हज़ार भेज देगा", now).single()
        assertEquals("अमित", c.person)
        assertEquals(200_000L, c.amountPaise)
        assertEquals(LocalDateTime.of(2026, 11, 22, 18, 0), c.due)
    }

    @Test
    fun bengaliPromise() {
        val c = CallMemoryExtractor.extract("মিঠু কাল সকালে দুই হাজার টাকা দেবে", now).single()
        assertEquals(CommitmentKind.MONEY_IN, c.kind)
        assertEquals("মিঠু", c.person)
        assertEquals(200_000L, c.amountPaise)
        assertEquals(LocalDateTime.of(2026, 11, 21, 9, 30), c.due)
        assertEquals("মিঠু থেকে 2,000 টাকা আসার কথা", CallMemoryExtractor.reminderText(c, bengali = true))
    }

    @Test
    fun aCallBackAndAPaymentIOwe() {
        val call = CallMemoryExtractor.extract("shaam ko Amit ko call karna hai", now).single()
        assertEquals(CommitmentKind.CALL_BACK, call.kind)
        assertEquals("Amit", call.person)
        assertEquals(LocalDateTime.of(2026, 11, 20, 18, 0), call.due)
        val owe = CallMemoryExtractor.extract("mujhe Sunil ko 3 din mein 1200 dena hai", now).single()
        assertEquals(CommitmentKind.MONEY_OUT, owe.kind)
        assertEquals(120_000L, owe.amountPaise)
        assertEquals(LocalDateTime.of(2026, 11, 23, 10, 0), owe.due)
    }

    @Test
    fun nothingPromisedMeansNothingSaid() {
        assertTrue(CallMemoryExtractor.extract("theek hai, baad mein baat karte hain", now).isEmpty())
        assertTrue(CallMemoryExtractor.extract("aaj mausam accha hai", now).isEmpty())
        assertTrue(CallMemoryExtractor.extract("Rahul dega", now).isEmpty()) // no amount: not a money promise
    }

    @Test
    fun aPassedTimeTodayBecomesAnHourFromNow() {
        assertEquals(LocalDateTime.of(2026, 11, 20, 13, 0), CallMemoryExtractor.dueOf("aaj subah 10 baje", now))
    }
}
