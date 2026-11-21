package com.raju.aiassistant.ui

import android.app.PendingIntent
import android.appwidget.AppWidgetManager
import android.appwidget.AppWidgetProvider
import android.content.Context
import android.content.Intent
import android.widget.RemoteViews
import com.raju.aiassistant.MainActivity
import com.raju.aiassistant.R

/**
 * Home-screen widget: "🎤 बोलो" opens Seema listening, "📋 आज का हाल" opens her with today's update. Both open the app
 * (Android lets only the app on screen use the microphone), nothing runs in the background from here.
 */
class SeemaWidget : AppWidgetProvider() {
    override fun onUpdate(context: Context, manager: AppWidgetManager, ids: IntArray) {
        val views = RemoteViews(context.packageName, R.layout.seema_widget)
        views.setOnClickPendingIntent(R.id.widget_talk, open(context, LaunchRequest.ACTION_VOICE, 1))
        views.setOnClickPendingIntent(R.id.widget_today, open(context, LaunchRequest.ACTION_TODAY, 2))
        ids.forEach { manager.updateAppWidget(it, views) }
    }

    private fun open(context: Context, action: String, code: Int): PendingIntent = PendingIntent.getActivity(
        context, 7300 + code,
        Intent(context, MainActivity::class.java).setAction(action).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_SINGLE_TOP),
        PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
    )
}
