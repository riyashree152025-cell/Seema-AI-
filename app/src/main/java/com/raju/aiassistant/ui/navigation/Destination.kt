package com.raju.aiassistant.ui.navigation

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Chat
import androidx.compose.material.icons.filled.GridView
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.Settings
import androidx.compose.ui.graphics.vector.ImageVector

/** Top-level destinations shown in the 5-item bottom navigation bar. */
enum class Destination(val route: String, val label: String, val icon: ImageVector) {
    Home("home", "Home", Icons.Filled.Home),
    Chat("chat", "Chat", Icons.AutoMirrored.Filled.Chat),
    Apps("apps", "Apps", Icons.Filled.GridView),
    Settings("settings", "Settings", Icons.Filled.Settings),
    Profile("profile", "Profile", Icons.Filled.Person);

    companion object {
        val start = Home
        fun fromRoute(route: String?): Destination? = entries.firstOrNull { it.route == route }
    }
}

/** Internal navigation routes for the other 4 reference screens. */
object SubRoutes {
    const val CONTROL_PANEL = "control_panel"
    const val AI_BRAIN = "ai_brain"
    const val VOICE_CONTROL = "voice_control"
    const val SEEMA_PRO = "seema_pro"
    const val TOOLS = "tools"
}
