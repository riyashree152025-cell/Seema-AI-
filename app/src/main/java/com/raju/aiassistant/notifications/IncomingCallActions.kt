package com.raju.aiassistant.notifications

import android.app.Notification
import android.app.PendingIntent
import com.raju.aiassistant.domain.notify.CallButtons

/**
 * The ringing call's own notification (Samsung, Google, Xiaomi… dialers all post one) carries "Answer" and
 * "Decline" buttons. Seema keeps them while the call rings and presses them when "uthao" / "kaato" is said and
 * TelecomManager did not do it — this works whatever the phone's answer style is (tap or swipe), and without
 * the screen. Nothing about the call is stored; the buttons are forgotten when the notification goes.
 */
object IncomingCallActions {
    @Volatile private var answer: PendingIntent? = null
    @Volatile private var decline: PendingIntent? = null
    @Volatile private var key: String? = null
    /** When the buttons were last seen: older than [STALE_MS] they are not trusted (a missed "removed"). */
    @Volatile private var rememberedAt = 0L
    private const val STALE_MS = 90_000L

    /** A call notification was posted (or updated): its Answer / Decline buttons, found by their titles. */
    fun remember(notificationKey: String, n: Notification) {
        val actions = n.actions ?: return
        var a: PendingIntent? = null
        var d: PendingIntent? = null
        actions.forEach { act ->
            val title = act.title?.toString().orEmpty()
            when {
                a == null && CallButtons.isAnswer(title) -> a = act.actionIntent
                d == null && CallButtons.isDecline(title) -> d = act.actionIntent
            }
        }
        if (a == null && d == null) return
        // The call was answered: the same notification now offers "Hang up" (no Answer) — no longer ringing.
        answer = a
        decline = d
        key = notificationKey
        rememberedAt = System.currentTimeMillis()
    }

    /** A call notification with an Answer button is showing: the phone is ringing. */
    fun ringing(): Boolean = answer != null && fresh()

    private fun fresh(): Boolean = System.currentTimeMillis() - rememberedAt < STALE_MS

    fun forget(notificationKey: String) {
        if (notificationKey != key) return
        answer = null
        decline = null
        key = null
    }

    /** Presses the notification's Answer (or Decline) button; false when there is none. */
    fun press(answerCall: Boolean): Boolean {
        if (!fresh()) return false
        val pi = (if (answerCall) answer else decline) ?: return false
        return runCatching { pi.send() }.isSuccess
    }

    /** True for a ringing call's notification. */
    fun isCallNotification(n: Notification, packageName: String): Boolean =
        n.category == Notification.CATEGORY_CALL ||
            Regex("incallui|dialer|telecom|\\.phone", RegexOption.IGNORE_CASE).containsMatchIn(packageName) && (n.actions?.size ?: 0) >= 2
}
