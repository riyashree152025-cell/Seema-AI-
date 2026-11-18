package com.raju.aiassistant.notifications

import android.app.Notification
import android.service.notification.NotificationListenerService
import android.service.notification.StatusBarNotification
import androidx.core.app.NotificationCompat
import com.raju.aiassistant.RajuApp
import com.raju.aiassistant.domain.model.AnnounceApp
import com.raju.aiassistant.domain.notify.NotificationFields
import com.raju.aiassistant.domain.notify.parseNotification
import com.raju.aiassistant.domain.notify.voipCallText
import kotlinx.coroutines.launch

/**
 * Runs only after the user enables Notification Access in system settings. Reads only what the
 * notification itself shows (sender, preview) for apps the user allowed, and hands it to the
 * [com.raju.aiassistant.domain.notify.AnnouncementCoordinator], which asks before reading.
 * Message content is never logged or stored.
 */
class RajuNotificationListener : NotificationListenerService() {

    private companion object {
        /** One payment = one line, however many apps post it. */
        val moneyDeduper = com.raju.aiassistant.domain.sms.MoneyAlertDeduper()
    }

    private val container get() = (applicationContext as? RajuApp)?.container

    override fun onListenerConnected() {
        container?.listenerConnected?.value = true
    }

    override fun onListenerDisconnected() {
        container?.listenerConnected?.value = false
    }

    override fun onNotificationPosted(sbn: StatusBarNotification) {
        // A ringing call's notification: keep its Answer / Decline buttons for "uthao" / "kaato" (before any
        // other check — this must work whatever the announcement settings are).
        sbn.notification?.let { n -> if (IncomingCallActions.isCallNotification(n, sbn.packageName)) IncomingCallActions.remember(sbn.key, n) }
        val c = container ?: return
        val settings = c.currentSettings.value
        announceMoneyIn(c, sbn)
        if (!settings.announcementsActive) return
        if (sbn.packageName !in settings.announceApps) return
        val n = sbn.notification ?: return
        val extras = n.extras ?: return

        val style = runCatching { NotificationCompat.MessagingStyle.extractMessagingStyleFromNotification(n) }.getOrNull()
        val fields = NotificationFields(
            packageName = sbn.packageName,
            key = sbn.key,
            title = extras.getCharSequence(Notification.EXTRA_TITLE)?.toString(),
            text = extras.getCharSequence(Notification.EXTRA_TEXT)?.toString(),
            bigText = extras.getCharSequence(Notification.EXTRA_BIG_TEXT)?.toString(),
            conversationTitle = style?.conversationTitle?.toString(),
            isGroupConversation = style?.isGroupConversation == true,
            messages = style?.messages.orEmpty().map { m -> m.person?.name?.toString() to m.text?.toString() },
            category = n.category,
            isGroupSummary = n.flags and Notification.FLAG_GROUP_SUMMARY != 0,
            isOngoing = n.flags and Notification.FLAG_ONGOING_EVENT != 0,
        )
        val label = AnnounceApp.entries.firstOrNull { it.packageName == sbn.packageName }?.label ?: "Message"
        val item = parseNotification(fields, label) ?: return
        val now = System.currentTimeMillis()
        c.recentNotifications.add(item, now)
        // A reply field offered by the app (WhatsApp, Messages …): remembered so "reply karo ki …" can answer.
        if (!item.isCall) {
            n.actions?.firstOrNull { a -> a.remoteInputs?.any { it.allowFreeFormInput } == true }?.let { action ->
                NotificationReplies.remember(item.key, item.sender ?: item.group ?: item.appLabel, item.appLabel, action)
            }
        }
        if (!c.announcementDeduper.shouldAnnounce(item.packageName, item.sender.orEmpty() + item.group.orEmpty(), (item.preview ?: "") + item.isCall, now)) return

        if (item.isCall) {
            if (settings.announceAppCalls && !settings.kidMode && !settings.assistantPaused) c.appScope.launch {
                // "Raju, Mummy ka WhatsApp call aa raha hai. Uthaun ki nahi?" — and "uthao / kaato" presses the call
                // notification's own Answer / Decline button (WhatsApp, Telegram… are not phone calls for Android).
                val canAnswer = com.raju.aiassistant.notifications.IncomingCallActions.ringing()
                c.speaker.speak(voipCallText(item, settings.userName) + if (canAnswer) " " + com.raju.aiassistant.domain.notify.ASK_TO_ANSWER else "")
                if (canAnswer && com.raju.aiassistant.notifications.IncomingCallActions.ringing()) c.engine.listenForCallCommand(null, item.sender)
            }
        } else {
            c.announcements.onMessage(item)
        }
    }

    /**
     * Money came in (a bank SMS, PhonePe, GPay…): "राजू, आपके खाते में अभी 500 रुपये आए हैं". Independent of the
     * message announcements; never while Seema is OFF, in safety mode or during a phone call. Said after
     * whatever she is saying now, once per payment. Nothing is logged.
     */
    private fun announceMoneyIn(c: com.raju.aiassistant.di.AppContainer, sbn: StatusBarNotification) {
        val s = c.currentSettings.value
        if (!s.announceMoneyIn || s.assistantPaused || s.safetyMode || s.kidMode) return
        if (!com.raju.aiassistant.domain.sms.isMoneyApp(sbn.packageName)) return
        val n = sbn.notification ?: return
        if (n.flags and Notification.FLAG_GROUP_SUMMARY != 0) return
        val extras = n.extras ?: return
        val title = extras.getCharSequence(Notification.EXTRA_TITLE)?.toString()
        val text = (extras.getCharSequence(Notification.EXTRA_BIG_TEXT) ?: extras.getCharSequence(Notification.EXTRA_TEXT))?.toString()
        val money = com.raju.aiassistant.domain.sms.moneyCreditIn(title, text) ?: return
        if (!moneyDeduper.shouldSay(money.paise, System.currentTimeMillis())) return
        val audio = getSystemService(android.media.AudioManager::class.java)
        if (audio?.mode == android.media.AudioManager.MODE_IN_CALL || audio?.mode == android.media.AudioManager.MODE_IN_COMMUNICATION) return
        val language = com.raju.aiassistant.domain.language.SeemaLanguage.fromTag(s.speechLanguage) ?: c.conversationLanguage.value
        val line = com.raju.aiassistant.domain.sms.spokenMoneyIn(money, s.userName, language)
        c.appScope.launch {
            // After her current sentence, not over it.
            kotlinx.coroutines.withTimeoutOrNull(20_000) { while (c.speaker.speaking.value) kotlinx.coroutines.delay(300) }
            c.speaker.speak(line)
        }
    }

    override fun onNotificationRemoved(sbn: StatusBarNotification) {
        IncomingCallActions.forget(sbn.key)
        container?.announcements?.onRemoved(sbn.key)
        NotificationReplies.forget(sbn.key)
    }
}
