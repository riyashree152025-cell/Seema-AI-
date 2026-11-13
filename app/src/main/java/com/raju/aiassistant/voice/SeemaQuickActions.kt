package com.raju.aiassistant.voice

import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import com.raju.aiassistant.RajuApp

/**
 * The standby notification's own buttons, "🎤 बोलो" and "⏹ रोको": the always-available way to talk to
 * Seema from any app, even without the floating popup's permission. Works while the microphone
 * service runs (it was started with the app on screen, so Android lets it keep the microphone).
 */
class SeemaQuickActionReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        val c = (context.applicationContext as? RajuApp)?.container ?: return
        if (c.currentSettings.value.assistantPaused) return
        when (intent.action) {
            ACTION_TALK -> when {
                c.engine.state.value == com.raju.aiassistant.domain.model.AssistantState.Sleeping -> c.engine.wakeUp(greet = false)
                c.engine.listening.value -> c.stopEverything()
                else -> {
                    c.speaker.stop()
                    c.engine.startVoice()
                }
            }
            ACTION_STOP -> c.stopEverything()
        }
    }

    companion object {
        const val ACTION_TALK = "com.raju.aiassistant.action.TALK"
        const val ACTION_STOP = "com.raju.aiassistant.action.STOP"

        fun pending(context: Context, action: String, code: Int): PendingIntent = PendingIntent.getBroadcast(
            context, code,
            Intent(context, SeemaQuickActionReceiver::class.java).setAction(action),
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
        )
    }
}
