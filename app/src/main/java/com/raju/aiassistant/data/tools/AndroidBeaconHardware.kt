package com.raju.aiassistant.data.tools

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.hardware.Sensor
import android.hardware.SensorEvent
import android.hardware.SensorEventListener
import android.hardware.SensorManager
import android.media.AudioManager
import android.media.ToneGenerator
import androidx.core.content.ContextCompat
import com.raju.aiassistant.domain.tools.BeaconHardware
import com.raju.aiassistant.domain.tools.BeaconStop
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow

/**
 * The phone's side of "mera phone dhundo": full alarm + media volume, a loud tone on the alarm
 * stream, and the signs that the user has the phone in hand (proximity, unlock, volume key).
 */
class AndroidBeaconHardware(private val context: Context) : BeaconHardware {
    private val audio = context.getSystemService(AudioManager::class.java)
    private var savedRinger: Int? = null
    private var savedAlarm: Int? = null
    private var savedMusic: Int? = null
    private var tone: ToneGenerator? = null

    override fun loud(): Boolean {
        val am = audio ?: return false
        savedRinger = am.ringerMode
        savedAlarm = am.getStreamVolume(AudioManager.STREAM_ALARM)
        savedMusic = am.getStreamVolume(AudioManager.STREAM_MUSIC)
        // Silent / Do-not-disturb may refuse the ringer change; the alarm stream still sounds.
        runCatching { am.ringerMode = AudioManager.RINGER_MODE_NORMAL }
        runCatching { am.setStreamVolume(AudioManager.STREAM_ALARM, am.getStreamMaxVolume(AudioManager.STREAM_ALARM), 0) }
        runCatching { am.setStreamVolume(AudioManager.STREAM_MUSIC, am.getStreamMaxVolume(AudioManager.STREAM_MUSIC), 0) }
        tone = runCatching { ToneGenerator(AudioManager.STREAM_ALARM, ToneGenerator.MAX_VOLUME) }.getOrNull()
        return true
    }

    override fun restore() {
        runCatching { tone?.release() }
        tone = null
        val am = audio ?: return
        savedAlarm?.let { v -> runCatching { am.setStreamVolume(AudioManager.STREAM_ALARM, v, 0) } }
        savedMusic?.let { v -> runCatching { am.setStreamVolume(AudioManager.STREAM_MUSIC, v, 0) } }
        savedRinger?.let { m -> runCatching { am.ringerMode = m } }
        savedAlarm = null; savedMusic = null; savedRinger = null
    }

    override suspend fun chime() {
        val t = tone ?: return
        runCatching { t.startTone(ToneGenerator.TONE_CDMA_HIGH_L, CHIME_MS) }
        delay(CHIME_MS + 100L)
    }

    override fun foundSignals(): Flow<BeaconStop> = callbackFlow {
        // Proximity: covered after having been uncovered (a phone already under a pillow does not count).
        val sensors = context.getSystemService(SensorManager::class.java)
        val proximity = sensors?.getDefaultSensor(Sensor.TYPE_PROXIMITY)
        var sawFar = false
        val listener = object : SensorEventListener {
            override fun onSensorChanged(event: SensorEvent) {
                val near = event.values.firstOrNull()?.let { it < minOf(proximity?.maximumRange ?: 5f, 5f) } ?: return
                if (!near) sawFar = true else if (sawFar) trySend(BeaconStop.PICKED_UP)
            }
            override fun onAccuracyChanged(sensor: Sensor?, accuracy: Int) {}
        }
        if (proximity != null) sensors.registerListener(listener, proximity, SensorManager.SENSOR_DELAY_NORMAL)

        val receiver = object : BroadcastReceiver() {
            override fun onReceive(c: Context, intent: Intent) {
                when (intent.action) {
                    Intent.ACTION_USER_PRESENT -> trySend(BeaconStop.UNLOCKED)
                    VOLUME_CHANGED -> {
                        val now = intent.getIntExtra(EXTRA_VALUE, -1)
                        val before = intent.getIntExtra(EXTRA_PREV, -1)
                        // The beacon set everything to full, so a key press shows up as a lower volume.
                        if (now >= 0 && before >= 0 && now < before) trySend(BeaconStop.VOLUME_KEY)
                    }
                }
            }
        }
        val filter = IntentFilter().apply { addAction(Intent.ACTION_USER_PRESENT); addAction(VOLUME_CHANGED) }
        ContextCompat.registerReceiver(context, receiver, filter, ContextCompat.RECEIVER_NOT_EXPORTED)
        awaitClose {
            runCatching { context.unregisterReceiver(receiver) }
            runCatching { sensors?.unregisterListener(listener) }
        }
    }

    private companion object {
        const val CHIME_MS = 700
        const val VOLUME_CHANGED = "android.media.VOLUME_CHANGED_ACTION"
        const val EXTRA_VALUE = "android.media.EXTRA_VOLUME_STREAM_VALUE"
        const val EXTRA_PREV = "android.media.EXTRA_PREV_VOLUME_STREAM_VALUE"
    }
}
