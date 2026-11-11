package com.raju.aiassistant.domain.notify

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class NotificationDigestTest {
    private fun msg(key: String, app: String, sender: String, text: String?) = IncomingItem(key, "pkg.$app", app, sender, null, text)

    @Test
    fun countsPerAppAndPersonAndReadsTheNewest() {
        val items = listOf(
            msg("1", "WhatsApp", "Rahul", "hello"),
            msg("2", "WhatsApp", "Rahul", "kahan ho"),
            msg("3", "WhatsApp", "Mummy", "khana kha liya"),
            msg("4", "Messages", "Airtel", "Recharge successful"),
        )
        val d = notificationDigest(items)
        assertTrue(d, d.startsWith("WhatsApp पर 3 संदेश: Rahul 2, Mummy 1"))
        assertTrue(d, d.contains("Messages पर 1 संदेश"))
        assertTrue(d, d.contains("सबसे नया, Airtel: Recharge successful"))
        assertTrue(d, d.contains("उससे पहले, Mummy: khana kha liya"))
    }

    @Test
    fun codesAndPasswordsAreNeverSpoken() {
        assertNull(speakablePreview("Your OTP is 482913"))
        assertNull(speakablePreview("482913 is your code"))
        assertNull(speakablePreview("आपका ओटीपी"))
        assertEquals("kal milte hain", speakablePreview(" kal milte hain "))
        val d = notificationDigest(listOf(msg("1", "Messages", "HDFC", "OTP 482913 for txn")))
        assertFalse(d, d.contains("482913"))
        assertTrue(d, d.contains("Messages पर 1 संदेश"))
    }

    @Test
    fun nothingNewIsSaidPlainly() {
        assertEquals("पिछले कुछ घंटों में कोई नया संदेश नहीं आया।", notificationDigest(emptyList()))
    }

    @Test
    fun oldItemsFallOutAndAnUpdatedNotificationIsNotCountedTwice() {
        val store = RecentNotifications(max = 3, maxAgeMs = 1_000)
        store.add(msg("a", "WhatsApp", "Rahul", "hi"), 0)
        store.add(msg("a", "WhatsApp", "Rahul", "hi"), 10) // re-posted: once
        store.add(msg("b", "WhatsApp", "Mummy", "ok"), 500)
        assertEquals(2, store.since(600).size)
        assertEquals(1, store.since(1_200).size) // "a" is too old now
    }
}
