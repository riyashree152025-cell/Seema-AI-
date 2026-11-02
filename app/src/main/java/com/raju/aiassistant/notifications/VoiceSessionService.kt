package com.raju.aiassistant.notifications

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.os.Build
import android.os.IBinder
import androidx.core.app.NotificationCompat
import androidx.core.app.ServiceCompat
import androidx.core.content.ContextCompat
import com.raju.aiassistant.MainActivity
import com.raju.aiassistant.R
import com.raju.aiassistant.RajuApp
import com.raju.aiassistant.domain.model.AssistantState
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.launch

/**
 * Keeps the existing hands-free voice session running when the screen turns off or another app is
 * opened ("Keep listening when the screen is off"). It does not record anything itself: the
 * assistant engine keeps using its own recogniser / wake word. Android requires a visible,
 * ongoing notification while the microphone is used in the background — it shows what Seema is
 * doing and has a Stop button. Started only while the app is open and hands-free is on; it stops
 * itself when hands-free ends, the option is turned off, or Stop is tapped.
 */
class VoiceSessionService : Service() {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main)
    private var watching = false

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        val container = (application as RajuApp).container
        if (intent?.action == ACTION_STOP) {
            container.engine.stop()
            stopSelf()
            return START_NOT_STICKY
        }
        createChannel(this)
        val started = runCatching {
            ServiceCompat.startForeground(
                this, NOTIFICATION_ID, notification(this, AssistantState.Listening),
                if (Build.VERSION.SDK_INT >= 30) ServiceInfo.FOREGROUND_SERVICE_TYPE_MICROPHONE else 0,
            )
        }
        if (started.isFailure) {
            // Android refused (no microphone permission, or the app was not in the foreground).
            android.util.Log.w("SeemaVoice", "voice service not started: ${started.exceptionOrNull()?.javaClass?.simpleName}")
            _running.value = false
            stopSelf()
            return START_NOT_STICKY
        }
        _running.value = true
        if (!watching) {
            watching = true
            scope.launch {
                combine(container.engine.state, container.engine.handsFreeActive, container.currentSettings) { state, handsFree, s ->
                    Triple(state, handsFree, s.screenOffListening && !s.assistantPaused)
                }.distinctUntilChanged().collect { (state, handsFree, allowed) ->
                    if (!handsFree || !allowed) {
                        stopSelf()
                    } else {
                        getSystemService(NotificationManager::class.java).notify(NOTIFICATION_ID, notification(this@VoiceSessionService, state))
                    }
                }
            }
        }
        // Not restarted by Android after the process dies: the user starts listening again from the app.
        return START_NOT_STICKY
    }

    override fun onDestroy() {
        _running.value = false
        scope.cancel()
        super.onDestroy()
    }

    companion object {
        private const val CHANNEL_ID = "voice_session"
        private const val NOTIFICATION_ID = 7301
        private const val ACTION_STOP = "com.raju.aiassistant.action.STOP_VOICE"

        private val _running = MutableStateFlow(false)
        /** True while the background voice session (and its notification) is active. */
        val running: StateFlow<Boolean> = _running.asStateFlow()

        /** Call only while the app is on screen (Android forbids starting a microphone service from the background). */
        fun start(context: Context) {
            runCatching { ContextCompat.startForegroundService(context, Intent(context, VoiceSessionService::class.java)) }
                .onFailure { android.util.Log.w("SeemaVoice", "voice service start refused: ${it.javaClass.simpleName}") }
        }

        fun stop(context: Context) {
            context.stopService(Intent(context, VoiceSessionService::class.java))
        }

        /** What the notification says for each state (also used by tests). */
        fun statusText(state: AssistantState): String = when (state) {
            AssistantState.Listening -> "Microphone ON — listening"
            AssistantState.Sleeping -> "Microphone ON — waiting for \"Hey Seema\" (nothing is recorded or sent)"
            AssistantState.Thinking, AssistantState.Executing -> "Thinking…"
            AssistantState.Speaking -> "Speaking — talk to interrupt"
            else -> "Microphone ON — hands-free"
        }

        private fun createChannel(context: Context) {
            val channel = NotificationChannel(CHANNEL_ID, "Voice assistant running", NotificationManager.IMPORTANCE_LOW).apply {
                description = "Shown while Seema keeps listening with the screen off. Tap Stop to turn the microphone off."
                setShowBadge(false)
                setSound(null, null)
            }
            context.getSystemService(NotificationManager::class.java).createNotificationChannel(channel)
        }

        private fun notification(context: Context, state: AssistantState): Notification {
            val open = PendingIntent.getActivity(
                context, 0, Intent(context, MainActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_SINGLE_TOP),
                PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
            )
            val stop = PendingIntent.getService(
                context, 1, Intent(context, VoiceSessionService::class.java).setAction(ACTION_STOP),
                PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
            )
            return NotificationCompat.Builder(context, CHANNEL_ID)
                .setSmallIcon(R.drawable.ic_notification)
                .setContentTitle("Seema is listening")
                .setContentText(statusText(state))
                .setOngoing(true)
                .setOnlyAlertOnce(true)
                .setSilent(true)
                .setCategory(NotificationCompat.CATEGORY_SERVICE)
                .setForegroundServiceBehavior(NotificationCompat.FOREGROUND_SERVICE_IMMEDIATE)
                .setContentIntent(open)
                .addAction(0, "Stop", stop)
                .build()
        }
    }
}
