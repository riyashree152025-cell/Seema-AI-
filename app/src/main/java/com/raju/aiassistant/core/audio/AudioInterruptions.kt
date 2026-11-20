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

    /**
     * Android 12+: the SIM call state too, once the Phone permission is granted. It decides for SIM calls
     * (the audio mode alone stayed "ringtone" on a Samsung after the call was answered, so Seema neither
     * paused for the call nor believed it was answered).
     */
    private var telephonyRegistered = false
    private val telephonyCallback: Any? = if (Build.VERSION.SDK_INT >= 31) object : android.telephony.TelephonyCallback(), android.telephony.TelephonyCallback.CallStateListener {
        override fun onCallStateChanged(state: Int) = onSimState(simStateOf(state))
    } else null

    /** The SIM call state now (UNKNOWN without the Phone permission). */
    @android.annotation.SuppressLint("MissingPermission") // READ_PHONE_STATE is checked by hasPhonePermission()
    fun simState(): com.raju.aiassistant.domain.notify.SimCallState {
        if (!hasPhonePermission()) return com.raju.aiassistant.domain.notify.SimCallState.UNKNOWN
        return runCatching { simStateOf(context.getSystemService(TelephonyManager::class.java)?.callState ?: -1) }
            .getOrDefault(com.raju.aiassistant.domain.notify.SimCallState.UNKNOWN)
    }

    private fun hasPhonePermission(): Boolean =
        ContextCompat.checkSelfPermission(context, android.Manifest.permission.READ_PHONE_STATE) == android.content.pm.PackageManager.PERMISSION_GRANTED

    private fun simStateOf(state: Int) = when (state) {
        TelephonyManager.CALL_STATE_RINGING -> com.raju.aiassistant.domain.notify.SimCallState.RINGING
        TelephonyManager.CALL_STATE_OFFHOOK -> com.raju.aiassistant.domain.notify.SimCallState.OFFHOOK
        TelephonyManager.CALL_STATE_IDLE -> com.raju.aiassistant.domain.notify.SimCallState.IDLE
        else -> com.raju.aiassistant.domain.notify.SimCallState.UNKNOWN
    }

    private fun onSimState(sim: com.raju.aiassistant.domain.notify.SimCallState) {
        when (sim) {
            com.raju.aiassistant.domain.notify.SimCallState.RINGING -> update(CallPhase.RINGING)
            com.raju.aiassistant.domain.notify.SimCallState.OFFHOOK -> update(CallPhase.ACTIVE)
            // The SIM call is over: what is left is an internet call (by the audio mode) or nothing.
            com.raju.aiassistant.domain.notify.SimCallState.IDLE -> update(audio?.mode?.let { m ->
                callPhaseForMode(m).takeIf { m != AudioManager.MODE_IN_COMMUNICATION }
            } ?: CallPhase.NONE)
            com.raju.aiassistant.domain.notify.SimCallState.UNKNOWN -> Unit
        }
    }

    /** Listens to the SIM call state once the Phone permission is there (it may be granted after start). */
    @android.annotation.SuppressLint("MissingPermission") // READ_PHONE_STATE is checked by hasPhonePermission()
    fun ensureCallStateListener() {
        if (Build.VERSION.SDK_INT < 31) return
        if (telephonyRegistered || !hasPhonePermission()) return
        runCatching {
            context.getSystemService(TelephonyManager::class.java)?.registerTelephonyCallback(
                ContextCompat.getMainExecutor(context), telephonyCallback as android.telephony.TelephonyCallback,
            )
            telephonyRegistered = true
        }
    }

    fun start() {
        if (started) return
        started = true
        ensureCallStateListener()
        ContextCompat.registerReceiver(context, noisy, IntentFilter(AudioManager.ACTION_AUDIO_BECOMING_NOISY), ContextCompat.RECEIVER_NOT_EXPORTED)
        if (Build.VERSION.SDK_INT >= 31) {
            runCatching { audio?.addOnModeChangedListener(ContextCompat.getMainExecutor(context), modeListener as AudioManager.OnModeChangedListener) }
            audio?.mode?.let(::onMode)
        } else {
            @Suppress("DEPRECATION")
            runCatching { context.getSystemService(TelephonyManager::class.java)?.listen(callListener, PhoneStateListener.LISTEN_CALL_STATE) }
        }
    }

    private fun onMode(mode: Int) {
        // Seema's own call-style audio (MODE_IN_COMMUNICATION) during a ringing or active phone call is not
        // the end of that call: ending it there stopped her listening for "utha lo / kaat do".
        if (mode == AudioManager.MODE_IN_COMMUNICATION && phase != CallPhase.NONE) return
        // A SIM call is ringing or up: its own state decides, not the audio mode.
        if (telephonyRegistered) {
            when (simState()) {
                com.raju.aiassistant.domain.notify.SimCallState.RINGING -> return update(CallPhase.RINGING)
                com.raju.aiassistant.domain.notify.SimCallState.OFFHOOK -> return update(CallPhase.ACTIVE)
                else -> Unit
            }
        }
        update(callPhaseForMode(mode))
    }

    /** A call is ringing right now (not yet answered or ended). */
    fun ringing(): Boolean = phase == CallPhase.RINGING

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
