package com.raju.aiassistant

import android.app.Application
import com.raju.aiassistant.di.AppContainer
import kotlinx.coroutines.launch

class RajuApp : Application() {
    lateinit var container: AppContainer
        private set

    override fun onCreate() {
        super.onCreate()
        container = AppContainer(this)
        container.consentNotifier.createChannel()
        com.raju.aiassistant.notifications.BusinessNotifier(this).createChannel()
        // Morning briefing and weekly report; they check the setting themselves each time.
        runCatching { com.raju.aiassistant.notifications.BusinessScheduler.schedule(this) }
        runCatching { container.cleanSharedFiles() }
        // The old offline voice models (up to 150 MB) are no longer used: their storage is given back.
        container.appScope.launch(kotlinx.coroutines.Dispatchers.IO) { runCatching { container.deleteOldVoiceModels() } }
        // Calls and headphone disconnects pause / silence Seema (no extra permission needed).
        // Volumes a killed process left lowered (ring at 10 %) or raised are given back first.
        runCatching { container.ringDucker.restoreAfterRestart() }
        runCatching { container.audioInterruptions.start() }
        runCatching { container.watchAudioDevices() }
        // Saved places' arrival alerts (Android drops them on a restart).
        runCatching { com.raju.aiassistant.notifications.PlaceAlerts.arm(this) }
        // The 9 AM / 9 PM check-in: when he unlocks the phone within two hours after it, Seema says the briefing
        // once (only while the app's process is alive; the WorkManager notification covers the rest).
        runCatching {
            androidx.core.content.ContextCompat.registerReceiver(
                this, unlockReceiver, android.content.IntentFilter(android.content.Intent.ACTION_USER_PRESENT),
                androidx.core.content.ContextCompat.RECEIVER_NOT_EXPORTED,
            )
        }
        // Wired headphones plugged in: a short "बोलिए" (Bluetooth earbuds are seen through the same audio route change).
        runCatching {
            androidx.core.content.ContextCompat.registerReceiver(
                this, headsetReceiver, android.content.IntentFilter(android.media.AudioManager.ACTION_HEADSET_PLUG),
                androidx.core.content.ContextCompat.RECEIVER_NOT_EXPORTED,
            )
        }
        // Android's own "battery low" moment (about 15 %): she says it once, softly.
        runCatching {
            androidx.core.content.ContextCompat.registerReceiver(
                this, batteryLowReceiver, android.content.IntentFilter(android.content.Intent.ACTION_BATTERY_LOW),
                androidx.core.content.ContextCompat.RECEIVER_NOT_EXPORTED,
            )
        }
    }

    private var headsetIn: Boolean? = null

    private val headsetReceiver = object : android.content.BroadcastReceiver() {
        override fun onReceive(context: android.content.Context?, intent: android.content.Intent?) {
            val plugged = intent?.getIntExtra("state", 0) == 1
            val before = headsetIn
            headsetIn = plugged
            // The first broadcast only tells the current state (sticky): not a new plug-in.
            if (before == null || !plugged || before) return
            val s = container.currentSettings.value
            if (s.assistantPaused || s.safetyMode) return
            container.engine.onHeadphonesConnected()
        }
    }

    private val batteryLowReceiver = object : android.content.BroadcastReceiver() {
        override fun onReceive(context: android.content.Context?, intent: android.content.Intent?) {
            val s = container.currentSettings.value
            if (s.assistantPaused || s.safetyMode) return
            val pct = runCatching { getSystemService(android.os.BatteryManager::class.java).getIntProperty(android.os.BatteryManager.BATTERY_PROPERTY_CAPACITY) }.getOrDefault(15)
            container.engine.onBatteryLow(pct)
        }
    }

    private val unlockReceiver = object : android.content.BroadcastReceiver() {
        override fun onReceive(context: android.content.Context?, intent: android.content.Intent?) {
            val s = container.currentSettings.value
            if (!s.businessReminders || s.assistantPaused || s.safetyMode) return
            val prefs = getSharedPreferences("daily_briefing", MODE_PRIVATE)
            val now = System.currentTimeMillis()
            val due = com.raju.aiassistant.domain.business.DailyBriefing.dueCheckIn(now, java.time.ZoneId.systemDefault(), prefs.getLong("last_spoken", 0L)) ?: return
            prefs.edit().putLong("last_spoken", now).apply()
            android.util.Log.i("SeemaVoice", "briefing: unlocked after the ${java.time.Instant.ofEpochMilli(due).atZone(java.time.ZoneId.systemDefault()).hour}:00 check-in")
            container.engine.speakDailyBriefing()
        }
    }

    @Suppress("DEPRECATION") // the level constants are still what Android delivers
    override fun onTrimMemory(level: Int) {
        super.onTrimMemory(level)
        // BACKGROUND and above: the app has been in the background for a while.
        if (level >= android.content.ComponentCallbacks2.TRIM_MEMORY_BACKGROUND) runCatching { container.cleanSharedFiles() }
    }
}
