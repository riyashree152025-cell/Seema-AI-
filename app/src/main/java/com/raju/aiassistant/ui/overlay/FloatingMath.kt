package com.raju.aiassistant.ui.overlay

import com.raju.aiassistant.domain.model.AssistantState

/*
 * Floating Seema's decisions, kept pure so they are unit-tested without a phone: when the popup may
 * appear, when the glass card opens or folds into the eye bubble, what its one line says, and which
 * edge the bubble rests on.
 */

/** The glass card (eye, status, buttons) or the small eye bubble at the edge of the screen. */
enum class FloatMode { CARD, BUBBLE }

/**
 * The popup appears over other apps only when the user wants it ([enabled]), Seema is switched on
 * ([paused] false), Android lets the app draw over others ([canDrawOverlays]), the microphone service
 * runs (without it a background app hears only silence, and Android may close it), and the app's own
 * screen is not locked behind the app lock.
 */
fun mayShowFloating(enabled: Boolean, paused: Boolean, canDrawOverlays: Boolean, micServiceRunning: Boolean, appLocked: Boolean): Boolean =
    enabled && !paused && canDrawOverlays && micServiceRunning && !appLocked

/** She is doing something the user should see: the bubble opens into the card by itself. */
fun opensCard(state: AssistantState, hasPending: Boolean): Boolean =
    hasPending || state == AssistantState.Listening || state == AssistantState.Thinking ||
        state == AssistantState.Executing || state == AssistantState.Speaking || state == AssistantState.WaitingPermission

/** How long the card stays open once she is quiet again before folding into the bubble (null: stays open). */
fun foldAfterMs(state: AssistantState, hasPending: Boolean): Long? = when {
    opensCard(state, hasPending) -> null
    state == AssistantState.Error -> 9_000L
    else -> 6_000L
}

/** The status word next to her name. */
fun floatingStatus(state: AssistantState): String = when (state) {
    AssistantState.Idle -> "तैयार हूँ"
    AssistantState.Listening -> "सुन रही हूँ"
    AssistantState.Thinking -> "सोच रही हूँ"
    AssistantState.Executing -> "काम कर रही हूँ"
    AssistantState.Speaking -> "बोल रही हूँ"
    AssistantState.Success -> "हो गया"
    AssistantState.Error -> "दिक्कत आई"
    AssistantState.Offline -> "इंटरनेट नहीं है"
    AssistantState.Sleeping -> "आराम कर रही हूँ"
    AssistantState.WaitingPermission -> "अनुमति चाहिए"
}

/** Longest line shown on the card (the rest is in the app). */
const val FLOATING_LINE_MAX = 140

/**
 * The card's one line: what she hears while listening, the question she is waiting on, her last
 * reply, the problem, or a hint — never empty. [hideWords] (app lock on) shows no words at all.
 */
fun floatingLine(
    state: AssistantState,
    heardSoFar: String,
    pendingQuestion: String?,
    lastReply: String?,
    lastError: String?,
    hideWords: Boolean,
): String {
    if (hideWords) return if (state == AssistantState.Listening) "सुन रही हूँ…" else "बात करने के लिए 🎤 दबाइए"
    val text = when {
        pendingQuestion != null -> pendingQuestion
        state == AssistantState.Listening -> heardSoFar.ifBlank { "बोलिए, सुन रही हूँ…" }
        state == AssistantState.Thinking -> heardSoFar.takeIf { it.isNotBlank() }?.let { "“$it”" } ?: "एक पल…"
        state == AssistantState.Error && !lastError.isNullOrBlank() -> lastError
        state == AssistantState.Sleeping -> "“हे सीमा” बोलिए या 🎤 दबाइए"
        !lastReply.isNullOrBlank() -> lastReply
        else -> "बात करने के लिए 🎤 दबाइए"
    }
    val flat = text.replace(Regex("\\s+"), " ").trim()
    return if (flat.length <= FLOATING_LINE_MAX) flat else flat.take(FLOATING_LINE_MAX - 1).trimEnd() + "…"
}

/** Where the bubble rests after a drag: the nearer side edge, [margin] in from it. */
fun bubbleRestX(centerX: Int, screenWidth: Int, bubbleWidth: Int, margin: Int): Int =
    if (centerX < screenWidth / 2) margin else (screenWidth - bubbleWidth - margin).coerceAtLeast(margin)

/** Keeps the bubble's top inside the screen (between [top] and [bottom] minus its height). */
fun bubbleRestY(y: Int, bubbleHeight: Int, top: Int, bottom: Int): Int =
    y.coerceIn(top, (bottom - bubbleHeight).coerceAtLeast(top))
