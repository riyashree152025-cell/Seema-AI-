package com.raju.aiassistant.domain.model

/**
 * Visual / behavioural state of the assistant. The home screen (Phase 2) renders a
 * different reactor + hologram look for each state.
 */
enum class AssistantState(val label: String) {
    Idle("Ready"),
    Listening("Listening"),
    Thinking("Thinking"),
    Speaking("Speaking"),
    /** Carrying out a task (phone action, web search, research): "Processing". */
    Executing("Processing"),
    Success("Done"),
    Error("Error"),
    Offline("Offline"),
    /** Hands-free mode after a quiet while: the mic only waits (on the phone) for the user's voice. */
    Sleeping("Sleeping"),
    /** Something needs a permission from the user (e.g. the microphone) before Seema can continue. */
    WaitingPermission("Permission needed");

    /** States in which a Stop action is meaningful. */
    val isBusy: Boolean
        get() = this == Listening || this == Thinking || this == Speaking || this == Executing
}
