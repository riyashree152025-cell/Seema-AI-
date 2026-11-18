package com.raju.aiassistant.core.audio

import android.content.Context
import android.media.AudioManager
import com.raju.aiassistant.domain.audio.LoudModePolicy
import com.raju.aiassistant.domain.audio.RingDuckPolicy
import com.raju.aiassistant.domain.audio.VolumeMemory
import kotlin.math.roundToInt

/** Volumes to give back, kept in the app's private preferences (they survive the process being killed). */
class PrefsVolumeMemory(context: Context) : VolumeMemory {
    private val prefs = context.getSharedPreferences("saved_volumes", Context.MODE_PRIVATE)
    override fun load(key: String): Int? = if (prefs.contains(key)) prefs.getInt(key, 0) else null
    override fun save(key: String, value: Int?) {
        // commit(): it must be on disk before the volume changes (a kill right after must not lose it).
        if (value == null) prefs.edit().remove(key).commit() else prefs.edit().putInt(key, value).commit()
    }
}

/**
 * The ringtone at full volume drowns the user's "kaat do / bol do …": while Seema announces a
 * ringing call and listens, the ring volume goes down to about 10 %, and back to exactly what it
 * was when the call is answered, rejected or ends. Never to 0 (that would switch the phone to silent).
 * The volume before is saved on disk first, so a process killed mid-call gives it back at the next start
 * ([restoreAfterRestart]) instead of leaving the ringer at 10 %.
 */
class RingDucker(context: Context) {
    private val audio = context.getSystemService(AudioManager::class.java)
    private val memory = PrefsVolumeMemory(context)
    private val policy = RingDuckPolicy(memory)
    private val loud = LoudModePolicy(memory)

    fun duck(fraction: Float = 0.1f) {
        try {
            val am = audio ?: return
            // Silent / vibrate / Do-not-disturb: the ring is not loud anyway, and changing the ring
            // volume there can throw or switch the profile. Leave it alone.
            val normal = am.ringerMode == AudioManager.RINGER_MODE_NORMAL
            val current = am.getStreamVolume(AudioManager.STREAM_RING)
            val target = policy.beginDuck(current, duckedRingVolume(current, am.getStreamMaxVolume(AudioManager.STREAM_RING), fraction, normal)) ?: return
            am.setStreamVolume(AudioManager.STREAM_RING, target, 0)
        } catch (e: Exception) {
            // SecurityException (DND policy) or anything else: give the saved value back, the call is not affected.
            policy.endDuck()
        }
    }

    fun restore() {
        val v = policy.endDuck() ?: return
        runCatching { audio?.setStreamVolume(AudioManager.STREAM_RING, v, 0) }
    }

    /** App start: volumes left lowered (ring) or raised ("phone dhundo") by a process that was killed are given back. */
    fun restoreAfterRestart() {
        restore()
        val am = audio ?: return
        loud.restore()?.let { (a, m, r) ->
            runCatching { am.setStreamVolume(AudioManager.STREAM_ALARM, a, 0) }
            runCatching { am.setStreamVolume(AudioManager.STREAM_MUSIC, m, 0) }
            runCatching { am.setStreamVolume(AudioManager.STREAM_RING, r, 0) }
        }
    }
}

/** The lowered ring volume, or null when it is already that low, silent, or the ringer is not in normal mode. */
fun duckedRingVolume(current: Int, max: Int, fraction: Float, ringerNormal: Boolean = true): Int? {
    if (!ringerNormal || current <= 0 || max <= 0) return null
    val target = (max * fraction).roundToInt().coerceAtLeast(1)
    return if (current > target) target else null
}
