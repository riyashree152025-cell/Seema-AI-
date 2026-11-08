package com.raju.aiassistant.ui.home.visuals

import com.raju.aiassistant.domain.model.AssistantState


/** The tone of her last reply, shown by the colour of the glowing rim. */
enum class TaporiMood { CALM, NORMAL, FUNNY, SHARP }

private val FUNNY_MARKS = Regex("haha|hehe|hihi|lol|😂|🤣|😜|😆|😄|😁|हाहा|हेहे|हीही|mazaa|maza aa|मज़ा|मजा", RegexOption.IGNORE_CASE)
private val SHARP_MARKS = Regex(
    "\\b(arre|are|abe|bhai|bhidu|naatak|natak|nautanki|hawabaazi|hawabazi|bakwaas|bakwas|scene mat|seriously)\\b|" +
        "अरे|अबे|भाई|भिड़ू|नाटक|नौटंकी|हवाबाजी|हवाबाज़ी|बकवास|सीन मत",
    RegexOption.IGNORE_CASE,
)

/** Funny (laughs, 😂) or sharp (a roast: "arre bhai", "kya nautanki") reply; NORMAL otherwise, CALM without one. */
fun taporiMoodFor(reply: String?): TaporiMood = when {
    reply.isNullOrBlank() -> TaporiMood.CALM
    FUNNY_MARKS.containsMatchIn(reply) -> TaporiMood.FUNNY
    SHARP_MARKS.containsMatchIn(reply) || reply.count { it == '!' } >= 2 -> TaporiMood.SHARP
    else -> TaporiMood.NORMAL
}

/**
 * Colour of the glowing rim: cyan listening, purple thinking; while speaking it follows the
 * reply's tone (hot orange for a sharp roast, pink for a joke, red otherwise); calm cyan when quiet.
 */
fun taporiRimArgb(state: AssistantState, mood: TaporiMood): Long = when (state) {
    AssistantState.Listening, AssistantState.Thinking -> taporiAccentArgb(state)
    AssistantState.Speaking -> when (mood) {
        TaporiMood.SHARP -> 0xFFFF6A00
        TaporiMood.FUNNY -> 0xFFFF4FD8
        else -> taporiAccentArgb(state)
    }
    else -> taporiAccentArgb(state)
}

/** What the Tapori face shows for an assistant state (pure, unit-testable). */
enum class TaporiExpression { SMIRK, LISTEN, EYE_ROLL, TALK, WINK, SLEEP, OOPS }

fun taporiExpressionFor(state: AssistantState): TaporiExpression = when (state) {
    AssistantState.Listening -> TaporiExpression.LISTEN
    AssistantState.Thinking, AssistantState.Executing -> TaporiExpression.EYE_ROLL
    AssistantState.Speaking -> TaporiExpression.TALK
    AssistantState.Success -> TaporiExpression.WINK
    AssistantState.Sleeping, AssistantState.Offline -> TaporiExpression.SLEEP
    AssistantState.Error, AssistantState.WaitingPermission -> TaporiExpression.OOPS
    AssistantState.Idle -> TaporiExpression.SMIRK
}
