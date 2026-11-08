package com.raju.aiassistant.core.audio

import android.content.Context
import android.media.AudioManager
import kotlin.math.roundToInt

/**
 * The ringtone at full volume drowns the user's "kaat do / bol do …": while Seema announces a
 * ringing call and listens, the ring volume goes down to about 20 %, and back to exactly what it
 * was when the call is answered, rejected or ends. Never to 0 (that would switch the phone to silent).
 */
class RingDucker(context: Context) {
    private val audio = context.getSystemService(AudioManager::class.java)
    @Volatile private var saved: Int? = null

    fun duck(fraction: Float = 0.2f) {
        try {
            val am = audio ?: return
            if (saved != null) return
            // Silent / vibrate / Do-not-disturb: the ring is not loud anyway, and changing the ring
            // volume there can throw or switch the profile. Leave it alone.
            val normal = am.ringerMode == AudioManager.RINGER_MODE_NORMAL
            val current = am.getStreamVolume(AudioManager.STREAM_RING)
            val target = duckedRingVolume(current, am.getStreamMaxVolume(AudioManager.STREAM_RING), fraction, normal) ?: return
            am.setStreamVolume(AudioManager.STREAM_RING, target, 0)
            saved = current
        } catch (e: Exception) {
            // SecurityException (DND policy) or anything else: no ducking, the call is not affected.
            saved = null
        }
    }

    fun restore() {
        val v = saved ?: return
        saved = null
        runCatching { audio?.setStreamVolume(AudioManager.STREAM_RING, v, 0) }
    }
}

/** The lowered ring volume, or null when it is already that low, silent, or the ringer is not in normal mode. */
fun duckedRingVolume(current: Int, max: Int, fraction: Float, ringerNormal: Boolean = true): Int? {
    if (!ringerNormal || current <= 0 || max <= 0) return null
    val target = (max * fraction).roundToInt().coerceAtLeast(1)
    return if (current > target) target else null
}
