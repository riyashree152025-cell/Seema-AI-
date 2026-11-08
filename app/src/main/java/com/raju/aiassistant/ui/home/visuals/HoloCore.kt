package com.raju.aiassistant.ui.home.visuals

import com.raju.aiassistant.domain.model.AssistantState

/*
 * The colours and motion of the "Modern" core appearance, per state. The ring-based holographic
 * core that used them was replaced by Home's Living Eye; the styles stay for the settings tint.
 */

// Neon-blue identity of the holographic core; each state adds its own accent like the Arc Reactor.
private const val NEON = 0xFF2EA8FF
private const val CYAN = 0xFF4DF3FF
private const val ICE = 0xFFB8ECFF
private const val ROYAL = 0xFF1E5BFF
private const val VIOLET = 0xFF9A7BFF
private const val MAGENTA = 0xFFFF4FD8
private const val GREEN = 0xFF4CE0A0
private const val AMBER = 0xFFFFC857
private const val ORANGE = 0xFFFF8A3D
private const val RED = 0xFFFF5C7A
private const val GREY = 0xFF5D7083
private const val GREY_LIGHT = 0xFF8FA9BF

/** How the 3D core looks in each assistant state (same rhythm and effects as the Arc Reactor, blue palette). */
fun holoCoreStyleFor(state: AssistantState): ReactorStyle = when (state) {
    AssistantState.Idle -> ReactorStyle(listOf(NEON, CYAN, ICE, ROYAL, NEON), glow = 0.7f, rotationSpeed = 0.06f, bpm = 64f)
    AssistantState.Listening -> ReactorStyle(listOf(CYAN, NEON, ICE, GREEN, CYAN), glow = 0.85f, rotationSpeed = 0.09f, bpm = 76f, ripples = true)
    AssistantState.Thinking -> ReactorStyle(listOf(VIOLET, NEON, CYAN, MAGENTA, VIOLET), glow = 0.8f, rotationSpeed = 0.25f, bpm = 96f, spinner = true)
    AssistantState.Speaking -> ReactorStyle(listOf(ICE, NEON, CYAN, ROYAL, ICE), glow = 0.9f, rotationSpeed = 0.12f, bpm = 72f, speechReactive = true)
    AssistantState.Executing -> ReactorStyle(listOf(AMBER, NEON, ORANGE, CYAN, AMBER), glow = 0.8f, rotationSpeed = 0.18f, bpm = 90f, sweep = true)
    AssistantState.Success -> ReactorStyle(listOf(GREEN, CYAN, NEON, ICE, GREEN), glow = 0.85f, rotationSpeed = 0.07f, bpm = 68f)
    AssistantState.Error -> ReactorStyle(listOf(RED, ORANGE, MAGENTA, ORANGE, RED), glow = 0.7f, rotationSpeed = 0.03f, bpm = 112f, flicker = true)
    AssistantState.Offline -> ReactorStyle(listOf(GREY, GREY_LIGHT, GREY, GREY_LIGHT, GREY), glow = 0.2f, rotationSpeed = 0f, bpm = 40f)
    AssistantState.Sleeping -> ReactorStyle(listOf(ROYAL, VIOLET, GREY_LIGHT, ROYAL, VIOLET), glow = 0.35f, rotationSpeed = 0.015f, bpm = 34f)
    // Amber, slow and steady: waiting for the user to allow a permission.
    AssistantState.WaitingPermission -> ReactorStyle(listOf(AMBER, ORANGE, AMBER, CYAN, AMBER), glow = 0.6f, rotationSpeed = 0.04f, bpm = 56f)
}

/** Tilt, size and speed of the three holographic rings (radians, units, revolutions per second). */
