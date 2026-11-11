package com.raju.aiassistant.domain.tools

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.withTimeoutOrNull

/** Why the "find my phone" beacon stopped. */
enum class BeaconStop { PICKED_UP, UNLOCKED, VOLUME_KEY, FOUND_SAID, STOPPED, TIMEOUT }

/** The phone's side of the beacon: loudness, a chime, and the signals that the phone was found. */
interface BeaconHardware {
    /** Ringer to normal, alarm and media volume to full; remembers the old values. False = no audio. */
    fun loud(): Boolean
    /** Puts back the volumes and ringer mode [loud] changed. Called exactly once per beacon. */
    fun restore()
    /** One short loud chime (returns when it has played). */
    suspend fun chime()
    /**
     * Emits when the user has the phone: the proximity sensor covered after it was uncovered, the
     * screen unlocked, a volume key pressed. Collected only while the beacon runs.
     */
    fun foundSignals(): Flow<BeaconStop>
}

/**
 * "Mera phone dhundo": a chime every [intervalMs] and every [spokenEvery]-th time a spoken line,
 * at full volume, until the phone is found (or [maxMs] passes). Volumes are always put back.
 */
class PhoneBeacon(
    private val scope: CoroutineScope,
    private val speakLine: suspend (String) -> Unit,
    private val intervalMs: Long = 1_200,
    /** Silent window after each chime so the microphone can hear "mil gaya / band karo". */
    private val listenGapMs: Long = 2_600,
    private val maxMs: Long = 3 * 60_000,
    /** The spoken line is Seema's own loud voice over the mic: said only at the start and then rarely. */
    private val spokenEvery: Int = 8,
    private val onStopped: (BeaconStop) -> Unit = {},
) {
    private val _active = MutableStateFlow(false)
    val active: StateFlow<Boolean> = _active.asStateFlow()

    private var job: Job? = null
    @Volatile private var requested: BeaconStop? = null

    /** Starts ringing; false when the phone cannot make sound. A running beacon keeps running. */
    fun start(hardware: BeaconHardware, line: String): Boolean {
        if (job?.isActive == true) return true
        if (!runCatching { hardware.loud() }.getOrDefault(false)) return false
        requested = null
        _active.value = true
        job = scope.launch {
            var reason = BeaconStop.TIMEOUT
            try {
                coroutineScope {
                    val ringing = launch {
                        withTimeoutOrNull(maxMs) {
                            var n = 0
                            while (true) {
                                runCatching { hardware.chime() }
                                // Say the line only the first time (then every spokenEvery-th), so most
                                // cycles are chime + silence — a clear window to say "band karo".
                                if (n == 0 || n % spokenEvery == 0) runCatching { speakLine(line) }
                                n++
                                delay(intervalMs)
                                delay(listenGapMs) // quiet: the mic listens here
                            }
                        }
                    }
                    val watch = launch {
                        reason = runCatching { hardware.foundSignals().first() }.getOrDefault(BeaconStop.TIMEOUT)
                        ringing.cancel()
                    }
                    ringing.join()
                    watch.cancel()
                }
            } finally {
                runCatching { hardware.restore() }
                _active.value = false
                onStopped(requested ?: reason)
            }
        }
        return true
    }

    /** Stops ringing at once (the user said "mil gaya", pressed Stop …). */
    fun stop(reason: BeaconStop = BeaconStop.STOPPED) {
        if (job?.isActive != true) return
        requested = reason
        job?.cancel()
    }
}

/** "Mil gaya", "mil gaya phone", "bas ruk jao", "बंद करो" … while the beacon rings. */
fun saysPhoneFound(text: String): Boolean = FOUND.containsMatchIn(text.lowercase())

private val FOUND = Regex(
    "\\b(mil gaya|mil gya|mil gayi|milgaya|mil gay[ae] hai|mil gai|found|found it|got it|bas|ruk|ruk jao|ruk ja|ruko|ruko|band|band karo|band kar do|band kardo|chup|stop|off karo|chup ho ja|thik hai bas|theek hai|ho gaya)\\b|" +
        "मिल\\s*गया|मिल\\s*गई|मिल\\s*गयी|मिल\\s*गे|बस\\s*करो|बस|रुको|रुक\\s*जाओ|बंद\\s*करो|बंद\\s*कर|चुप|हो\\s*गया|ठीक\\s*है|পেয়েছি|পেয়ে\\s*গেছি|থামো|বন্ধ\\s*কর",
)
