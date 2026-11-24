package com.raju.aiassistant.domain.automation

import org.junit.Assert.assertTrue
import org.junit.Test

class BatteryCareTest {

    @Test
    fun batteryAlertLinesFormatWarmlyInHindiAndBengali() {
        val lowHi = CallAutomation.lowBatteryLine(15, bengali = false)
        assertTrue(lowHi.contains("15") && lowHi.contains("बैटरी"))

        val lowBn = CallAutomation.lowBatteryLine(15, bengali = true)
        assertTrue(lowBn.contains("15") && lowBn.contains("ব্যাটারি"))

        val fullHi = CallAutomation.fullBatteryLine(100, bengali = false)
        assertTrue(fullHi.contains("100") && fullHi.contains("चार्ज"))

        val fullBn = CallAutomation.fullBatteryLine(99, bengali = true)
        assertTrue(fullBn.contains("99") && fullBn.contains("চার্জ"))
    }
}
