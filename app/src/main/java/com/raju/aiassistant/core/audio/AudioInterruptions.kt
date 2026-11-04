package com.raju.aiassistant.core.audio

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.media.AudioManager
import android.os.Build
import android.telephony.PhoneStateListener
import android.telephony.TelephonyManager
import androidx.core.content.ContextCompat

/** Where a phone call is. */
enum class CallPhase { NONE, RINGING, ACTIVE }

/**
 * Phone calls and headphones taking over the audio:
 * - [onCall] reports RINGING (Seema frees the microphone; the caller announcement may still play),
 *   ACTIVE (answered or dialling: all of Seema's sound stops) and NONE (call over);
 * - wired or Bluetooth headphones are unplugged / disconnected → [onBecomingNoisy] (she stops
 *   talking instead of suddenly playing out of the loudspeaker).
 * No extra permission: the audio mode (Android 12+) or the call state (older phones) is enough.
 * Seema's own hands-free mode uses the "communication" audio mode, so only ringtone / in-call count
 * as a phone call. Internet (VoIP) calls cannot be told apart from it and are not detected here.
 */
class AudioInterruptions(
    private val context: Context,
    private val onCall: (CallPhase) -> Unit,
    private val onBecomingNoisy: () -> Unit,
) {
    private val audio = context.getSystemService(AudioManager::class.java)
    private var phase = CallPhase.NONE
    private var started = false

    private val noisy = object : BroadcastReceiver() {
        override fun onReceive(c: Context?, intent: Intent?) {
            if (intent?.action == AudioManager.ACTION_AUDIO_BECOMING_NOISY) onBecomingNoisy()
        }
    }

    private val modeListener: Any? = if (Build.VERSION.SDK_INT >= 31) AudioManager.OnModeChangedListener { mode -> onMode(mode) } else null

    @Suppress("DEPRECATION")
    private val callListener: PhoneStateListener? = if (Build.VERSION.SDK_INT < 31) object : PhoneStateListener() {
        override fun onCallStateChanged(state: Int, phoneNumber: String?) {
            update(
                when (state) {
                    TelephonyManager.CALL_STATE_RINGING -> CallPhase.RINGING
                    TelephonyManager.CALL_STATE_OFFHOOK -> CallPhase.ACTIVE
                    else -> CallPhase.NONE
                },
            )
        }
    } else null

    fun start() {
        if (started) return
        started = true
        ContextCompat.registerReceiver(context, noisy, IntentFilter(AudioManager.ACTION_AUDIO_BECOMING_NOISY), ContextCompat.RECEIVER_NOT_EXPORTED)
        if (Build.VERSION.SDK_INT >= 31) {
            runCatching { audio?.addOnModeChangedListener(ContextCompat.getMainExecutor(context), modeListener as AudioManager.OnModeChangedListener) }
            audio?.mode?.let(::onMode)
        } else {
            @Suppress("DEPRECATION")
            runCatching { context.getSystemService(TelephonyManager::class.java)?.listen(callListener, PhoneStateListener.LISTEN_CALL_STATE) }
        }
    }

    private fun onMode(mode: Int) = update(callPhaseForMode(mode))

    private fun update(next: CallPhase) {
        if (next == phase) return
        phase = next
        onCall(next)
    }

    companion object {
        /** Ringing or a cellular call; MODE_IN_COMMUNICATION is not a call (it is also Seema's own hands-free mode). */
        fun callPhaseForMode(mode: Int): CallPhase = when (mode) {
            AudioManager.MODE_RINGTONE, MODE_CALL_SCREENING -> CallPhase.RINGING
            AudioManager.MODE_IN_CALL -> CallPhase.ACTIVE
            else -> CallPhase.NONE
        }

        /** AudioManager.MODE_CALL_SCREENING (Android 10+). */
        private const val MODE_CALL_SCREENING = 4
    }
}
