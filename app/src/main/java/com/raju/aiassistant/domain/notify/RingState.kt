package com.raju.aiassistant.domain.notify

/** The SIM call state as Android's TelephonyManager reports it (UNKNOWN without the Phone permission). */
enum class SimCallState { UNKNOWN, IDLE, RINGING, OFFHOOK }

/**
 * Is a call still ringing? The SIM call state decides whenever Android gives it: OFFHOOK means the call
 * was answered (or one is in progress), whatever the audio mode or a call notification still say — the
 * audio mode can stay "ringtone" and an answered call's notification can keep its old buttons for a while,
 * which made Seema say "फ़ोन अब भी बज रहा है" about a call that was already up. Without a SIM state
 * (WhatsApp and other internet calls, or no Phone permission) the ringtone audio mode or a call
 * notification with an Answer button are the signs.
 */
object RingState {
    fun ringing(sim: SimCallState, audioRinging: Boolean, notificationRinging: Boolean): Boolean = when (sim) {
        SimCallState.RINGING -> true
        SimCallState.OFFHOOK -> false
        SimCallState.IDLE, SimCallState.UNKNOWN -> audioRinging || notificationRinging
    }

    /** A SIM call is up (answered or dialled): Seema must not listen to it or answer what she hears. */
    fun inCall(sim: SimCallState): Boolean = sim == SimCallState.OFFHOOK
}
