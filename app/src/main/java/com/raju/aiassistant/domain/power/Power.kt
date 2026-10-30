package com.raju.aiassistant.domain.power

/** How hot Android says the phone is, reduced to what the app acts on. */
enum class ThermalLevel(val label: String) {
    NORMAL("normal"),
    WARM("warm"),
    HOT("hot"),
    CRITICAL("critical"),
}

/**
 * Maps PowerManager.THERMAL_STATUS_* (0 none, 1 light, 2 moderate, 3 severe, 4 critical,
 * 5 emergency, 6 shutdown). "Light" already counts as warm: on a budget phone it is better to
 * slow the visuals early than to let the phone throttle everything later.
 */
fun thermalLevelFor(status: Int): ThermalLevel = when {
    status <= 0 -> ThermalLevel.NORMAL
    status <= 2 -> ThermalLevel.WARM
    status == 3 -> ThermalLevel.HOT
    else -> ThermalLevel.CRITICAL
}

/** What the home screen is doing right now, from the power point of view. */
enum class ActivityPhase(val label: String) {
    /** The user is talking to Seema or she is working: full visuals. */
    ACTIVE("active"),
    /** Nothing happening for a while: slower, lighter visuals. */
    IDLE("idle"),
    /** Nothing happening for a long time: slowest visuals (still turning and beating). */
    SLEEP("sleep"),
}

/** After this long without any touch or assistant activity the screen counts as idle… */
const val IDLE_AFTER_MS = 30_000L
/** …and after this long it runs at its slowest until the next touch or event. */
const val SLEEP_AFTER_MS = 120_000L

/** [busy]: listening, thinking, speaking or working (never idle then). */
fun activityPhaseFor(smartPower: Boolean, busy: Boolean, quietForMs: Long): ActivityPhase = when {
    !smartPower || busy -> ActivityPhase.ACTIVE
    quietForMs >= SLEEP_AFTER_MS -> ActivityPhase.SLEEP
    quietForMs >= IDLE_AFTER_MS -> ActivityPhase.IDLE
    else -> ActivityPhase.ACTIVE
}
