package com.raju.aiassistant.ui.navigation

import com.raju.aiassistant.domain.model.UserSettings
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class NavigationAndProfileTest {

    @Test
    fun destinationHasExactlyFiveBottomTabs() {
        val bottomTabs = Destination.entries
        assertEquals(5, bottomTabs.size)
        assertEquals(listOf(Destination.Home, Destination.Chat, Destination.Apps, Destination.Settings, Destination.Profile), bottomTabs)
    }

    @Test
    fun destinationRoutesAndLabelsMatchReference() {
        assertEquals("home", Destination.Home.route)
        assertEquals("Home", Destination.Home.label)

        assertEquals("chat", Destination.Chat.route)
        assertEquals("Chat", Destination.Chat.label)

        assertEquals("apps", Destination.Apps.route)
        assertEquals("Apps", Destination.Apps.label)

        assertEquals("settings", Destination.Settings.route)
        assertEquals("Settings", Destination.Settings.label)

        assertEquals("profile", Destination.Profile.route)
        assertEquals("Profile", Destination.Profile.label)
    }

    @Test
    fun subRoutesAreDefined() {
        assertEquals("control_panel", SubRoutes.CONTROL_PANEL)
        assertEquals("ai_brain", SubRoutes.AI_BRAIN)
        assertEquals("voice_control", SubRoutes.VOICE_CONTROL)
        assertEquals("seema_pro", SubRoutes.SEEMA_PRO)
        assertEquals("tools", SubRoutes.TOOLS)
    }

    @Test
    fun userSettingsDefaultsMatchProfileReference() {
        val settings = UserSettings()
        assertEquals("Raju", settings.userName)
        assertEquals("ruhulamin16121998@gmail.com", settings.userEmail)
        assertTrue(settings.isProUser)
    }

    @Test
    fun userSettingsProfileCustomization() {
        val settings = UserSettings(userName = "Test User", userEmail = "test@example.com", isProUser = false)
        assertEquals("Test User", settings.userName)
        assertEquals("test@example.com", settings.userEmail)
        assertEquals(false, settings.isProUser)
    }
}
