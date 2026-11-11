package com.raju.aiassistant.notifications

import android.app.Notification
import android.app.RemoteInput
import android.content.Context
import android.content.Intent
import android.os.Bundle
import com.raju.aiassistant.domain.notify.ReplyTarget

/**
 * The reply fields of the notifications that are showing now, kept in memory only. A reply goes through
 * the app's own notification action (the same as typing in the notification), never through a private API.
 */
object NotificationReplies {
    private const val MAX = 30
    private val actions = LinkedHashMap<String, Pair<ReplyTarget, Notification.Action>>()

    @Synchronized
    fun remember(key: String, who: String, app: String, action: Notification.Action) {
        actions.remove(key)
        actions[key] = ReplyTarget(key, who, app) to action
        while (actions.size > MAX) actions.remove(actions.keys.first())
    }

    @Synchronized
    fun forget(key: String) { actions.remove(key) }

    /** Newest first. */
    @Synchronized
    fun targets(): List<ReplyTarget> = actions.values.map { it.first }.reversed()

    /** Sends [text] into that notification's reply field. False when the notification is gone or the app refused. */
    fun send(context: Context, key: String, text: String): Boolean {
        val action = synchronized(this) { actions[key]?.second } ?: return false
        val inputs = action.remoteInputs?.takeIf { it.isNotEmpty() } ?: return false
        return runCatching {
            val results = Bundle()
            inputs.forEach { results.putCharSequence(it.resultKey, text) }
            val fill = Intent()
            RemoteInput.addResultsToIntent(inputs, fill, results)
            action.actionIntent.send(context, 0, fill)
            forget(key)
            true
        }.getOrDefault(false)
    }
}
