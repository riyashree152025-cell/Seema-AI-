package com.raju.aiassistant.domain.tools

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class AppNameMatchTest {
    private val apps = listOf("Chrome", "YouTube", "WhatsApp", "Camera", "Gallery", "Clock", "Settings", "Calculator", "Google Maps", "Play Store", "Messages")

    @Test
    fun appNamesInHindiOrEnglishFindTheApp() {
        assertEquals("Chrome", AppNameMatch.best("chrome", apps))
        assertEquals("Chrome", AppNameMatch.best("क्रोम", apps))
        assertEquals("YouTube", AppNameMatch.best("यूट्यूब", apps))
        assertEquals("WhatsApp", AppNameMatch.best("व्हाट्सएप", apps))
        assertEquals("Camera", AppNameMatch.best("कैमरा", apps))
        assertEquals("Calculator", AppNameMatch.best("कैलकुलेटर", apps))
        assertEquals("Clock", AppNameMatch.best("घड़ी", apps))
        assertEquals("Play Store", AppNameMatch.best("प्ले स्टोर", apps))
        assertEquals("Google Maps", AppNameMatch.best("maps", apps))
        assertNull(AppNameMatch.best("instagram", apps))
    }
}
