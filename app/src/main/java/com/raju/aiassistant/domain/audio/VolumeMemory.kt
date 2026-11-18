package com.raju.aiassistant.domain.audio

/**
 * Volumes Seema changed for a moment and must give back: kept on disk (not only in memory), so a process killed
 * mid-call or mid-alarm still gets them back the next time the app starts.
 */
interface VolumeMemory {
    fun load(key: String): Int?
    fun save(key: String, value: Int?)
}

class InMemoryVolumeMemory : VolumeMemory {
    private val values = HashMap<String, Int>()
    override fun load(key: String): Int? = synchronized(values) { values[key] }
    override fun save(key: String, value: Int?) {
        synchronized(values) { if (value == null) values.remove(key) else values[key] = value }
    }
}

/**
 * The ringtone turned down while Seema announces a call: the volume before it is saved first, and given back
 * once ([endDuck]) when the call is answered, cut or over — or at the next app start if the process died.
 */
class RingDuckPolicy(private val memory: VolumeMemory) {
    /** The volume to set now, or null to leave it (already ducked, or nothing to lower). Saves [current] first. */
    fun beginDuck(current: Int, target: Int?): Int? {
        if (memory.load(RING) != null || target == null) return null
        memory.save(RING, current)
        return target
    }

    /** The volume to give back (and forgets it), or null when nothing was lowered. */
    fun endDuck(): Int? = memory.load(RING)?.also { memory.save(RING, null) }

    val ducked: Boolean get() = memory.load(RING) != null

    companion object { const val RING = "ring_before_duck" }
}

/**
 * "Phone dhundo" turns alarm, music and ring to full: the volumes before are saved and given back when it stops,
 * or at the next app start.
 */
class LoudModePolicy(private val memory: VolumeMemory) {
    fun remember(alarm: Int, music: Int, ring: Int) {
        if (memory.load(ALARM) != null) return // already loud: keep the first, real values
        memory.save(ALARM, alarm); memory.save(MUSIC, music); memory.save(RING, ring)
    }

    /** The volumes to give back (alarm, music, ring), or null when nothing was raised. Forgets them. */
    fun restore(): Triple<Int, Int, Int>? {
        val a = memory.load(ALARM) ?: return null
        val m = memory.load(MUSIC) ?: a
        val r = memory.load(RING) ?: a
        memory.save(ALARM, null); memory.save(MUSIC, null); memory.save(RING, null)
        return Triple(a, m, r)
    }

    companion object {
        const val ALARM = "loud_alarm"
        const val MUSIC = "loud_music"
        const val RING = "loud_ring"
    }
}
