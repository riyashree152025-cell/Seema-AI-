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
        val am = audio ?: return
        if (saved != null) return
        val current = runCatching { am.getStreamVolume(AudioManager.STREAM_RING) }.getOrNull() ?: return
        val target = duckedRingVolume(current, am.getStreamMaxVolume(AudioManager.STREAM_RING), fraction) ?: return
        if (runCatching { am.setStreamVolume(AudioManager.STREAM_RING, target, 0) }.isSuccess) saved = current
    }

    fun restore() {
        val v = saved ?: return
        saved = null
        runCatching { audio?.setStreamVolume(AudioManager.STREAM_RING, v, 0) }
    }
}

/** The lowered ring volume, or null when it is already that low (or silent: left alone). */
fun duckedRingVolume(current: Int, max: Int, fraction: Float): Int? {
    if (current <= 0 || max <= 0) return null
    val target = (max * fraction).roundToInt().coerceAtLeast(1)
    return if (current > target) target else null
}
