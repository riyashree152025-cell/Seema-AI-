package com.raju.aiassistant.domain.automation

import com.raju.aiassistant.domain.remote.RemoteResult
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class GuardiansTest {
    @Test
    fun weatherOnlyWhenItMatters() {
        assertNull(WeatherLine.line(20, 31.0, 22.0))
        assertTrue(WeatherLine.line(70, 31.0, 22.0)!!.contains("बारिश की संभावना 70 प्रतिशत"))
        assertTrue(WeatherLine.line(null, 41.5, 30.0)!!.contains("41 डिग्री"))
        assertTrue(WeatherLine.line(0, 20.0, 8.0)!!.contains("ठंड"))
    }

    @Test
    fun bridgeAlertsOncePerTrouble() {
        var st = BridgeHealth.State()
        var r = BridgeHealth.check(RemoteResult.Unreachable, st); st = r.first
        assertNull(r.second) // one miss is not "down"
        r = BridgeHealth.check(RemoteResult.Unreachable, st); st = r.first
        assertTrue(r.second!!.contains("संपर्क नहीं"))
        r = BridgeHealth.check(RemoteResult.Unreachable, st); st = r.first
        assertNull(r.second) // said once
        r = BridgeHealth.check(RemoteResult.Done(mapOf("disk_free_gb" to "120.0")), st); st = r.first
        assertTrue(r.second!!.contains("फिर से चालू"))
        r = BridgeHealth.check(RemoteResult.Done(mapOf("disk_free_gb" to "3.2")), st); st = r.first
        assertTrue(r.second!!.contains("3.2 GB"))
        r = BridgeHealth.check(RemoteResult.Done(mapOf("disk_free_gb" to "3.0")), st)
        assertNull(r.second)
        assertEquals(BridgeHealth.State() to null, BridgeHealth.check(RemoteResult.NotConfigured, st))
    }

    @Test
    fun findPhoneOnlyWithTheExactWord() {
        assertTrue(FindPhoneSms.matches("SEEMA FIND tiger42", "tiger42"))
        assertTrue(FindPhoneSms.matches("seema find tiger42 ", "tiger42"))
        assertFalse(FindPhoneSms.matches("SEEMA FIND tiger43", "tiger42"))
        assertFalse(FindPhoneSms.matches("SEEMA FIND tiger42", null))
        assertFalse(FindPhoneSms.matches("SEEMA FIND abc", "abc")) // too short a word is never accepted
        assertFalse(FindPhoneSms.matches("hello SEEMA FIND tiger42", "tiger42"))
        assertTrue(FindPhoneSms.reply(22.5726, 88.3639).contains("maps.google.com/?q=22.57260,88.36390"))
        assertFalse(FindPhoneSms.reply(null, null).contains("tiger"))
    }
}
