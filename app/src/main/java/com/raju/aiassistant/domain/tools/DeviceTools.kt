package com.raju.aiassistant.domain.tools

import com.raju.aiassistant.domain.assistant.SettingsPage
import com.raju.aiassistant.domain.model.MusicService

data class Contact(val name: String, val number: String)

/** Real outcome of a device action. */
sealed interface ToolOutcome {
    data class Done(val message: String) : ToolOutcome
    data class Failed(val message: String) : ToolOutcome
    data class NeedsPermission(val permission: String, val reason: String) : ToolOutcome
}

/** A photo from the phone's camera (Seema's "photo lo" / "dekho kya dikh raha hai"). */
sealed interface PhotoResult {
    class Taken(val jpeg: ByteArray, val savedAs: String?) : PhotoResult
    data class Failed(val message: String) : PhotoResult
    data class NeedsPermission(val permission: String, val reason: String) : PhotoResult
}

sealed interface ContactLookup {
    data class Found(val contacts: List<Contact>) : ContactLookup
    data class NeedsPermission(val permission: String, val reason: String) : ContactLookup
    data class Failed(val message: String) : ContactLookup
}

/** Phone capabilities the assistant may use. Implemented with public Android APIs/intents only. */
interface DeviceTools {
    suspend fun findContacts(query: String): ContactLookup
    fun dial(contact: Contact, direct: Boolean): ToolOutcome
    fun openApp(name: String): ToolOutcome
    fun openSettings(page: SettingsPage): ToolOutcome
    fun batteryStatus(): ToolOutcome
    fun playMusic(query: String, service: MusicService): ToolOutcome
    fun openWhatsApp(number: String?, text: String): ToolOutcome
    fun openWhatsAppCall(number: String?, video: Boolean = false): ToolOutcome = ToolOutcome.Failed("WhatsApp call is not supported here.")

    // Personal-use extras. Defaults keep older fakes compiling; AndroidDeviceTools overrides all of them.
    fun setTimer(seconds: Int, note: String): ToolOutcome = ToolOutcome.Failed("Timer is not supported here.")
    fun setAlarm(hour: Int, minute: Int, note: String): ToolOutcome = ToolOutcome.Failed("Alarm is not supported here.")
    /** Daily Tatkal alert. A Done message containing [TATKAL_INEXACT] means it may ring late (exact alarms off). */
    fun setTatkalAlarm(hour: Int, minute: Int, label: String): ToolOutcome = ToolOutcome.Failed("Tatkal alarm is not supported here.")
    fun listTatkalAlarms(): List<com.raju.aiassistant.domain.tatkal.TatkalAlarm> = emptyList()
    /** Seema's own reminder at [atMillis]. A Done message containing [TATKAL_INEXACT] means it may come late. */
    fun addReminder(atMillis: Long, text: String): ToolOutcome = ToolOutcome.Failed("Reminders are not supported here.")
    fun listReminders(): List<com.raju.aiassistant.domain.reminder.Reminder> = emptyList()
    /** The calendar entries of today (+[dayOffset] days). */
    fun calendarEvents(dayOffset: Int): com.raju.aiassistant.domain.calendar.CalendarLookup =
        com.raju.aiassistant.domain.calendar.CalendarLookup.Failed("Calendar is not supported here.")
    /** Removes the newest reminder ([all] false) or every reminder. Done message carries the count. */
    fun cancelReminders(all: Boolean): ToolOutcome = ToolOutcome.Failed("Reminders are not supported here.")
    /** Removes the alarms at that time ([kind] null: either class). Done message carries the count. */
    fun cancelTatkalAlarms(hour: Int, minute: Int, kind: String?): ToolOutcome = ToolOutcome.Failed("Tatkal alarm is not supported here.")
    fun cancelAllTatkalAlarms(): ToolOutcome = ToolOutcome.Failed("Tatkal alarm is not supported here.")
    fun setFlashlight(on: Boolean): ToolOutcome = ToolOutcome.Failed("Flashlight is not supported here.")
    fun setVolume(percent: Int): ToolOutcome = ToolOutcome.Failed("Volume control is not supported here.")
    fun mediaControl(command: MediaCommand): ToolOutcome = ToolOutcome.Failed("Media control is not supported here.")
    fun hasPermission(permission: String): Boolean = true
    fun sendSms(contact: Contact, text: String): ToolOutcome = ToolOutcome.Failed("SMS is not supported here.")
    /** [rrule] is an iCalendar repeat rule such as "FREQ=DAILY", or null for a one-time event. */
    fun addCalendarEvent(title: String, startMillis: Long, durationMinutes: Int, location: String?, rrule: String?): ToolOutcome =
        ToolOutcome.Failed("Calendar is not supported here.")
    fun navigate(destination: String, mode: TravelMode): ToolOutcome = ToolOutcome.Failed("Navigation is not supported here.")
    fun copyToClipboard(text: String): ToolOutcome = ToolOutcome.Failed("Clipboard is not supported here.")
    fun openWebPage(url: String): ToolOutcome = ToolOutcome.Failed("Opening web pages is not supported here.")
    /** Writes [content] to a file named [name] and opens the share sheet (save to Files/Drive, send). */
    fun shareTextFile(name: String, content: String): ToolOutcome = ToolOutcome.Failed("Saving files is not supported here.")
    suspend fun getLastCall(): ToolOutcome = ToolOutcome.Failed("Call log is not supported here.")
    /**
     * Who rang a moment ago (an incoming, missed or rejected call within [withinMs]), from the call
     * log: used to reply by SMS when Android gave no number while it rang. Null without the Call
     * Log permission or when there is no such call.
     */
    suspend fun recentCaller(withinMs: Long = 120_000): Contact? = null
    fun takeScreenshot(): ToolOutcome = ToolOutcome.Failed("Screenshot is not supported here.")
    /** The app on screen, read by Seema's Accessibility service (never a password, banking or UPI screen). */
    suspend fun readScreen(): com.raju.aiassistant.domain.screen.ScreenRead =
        com.raju.aiassistant.domain.screen.ScreenRead.Unavailable("Screen reading is not supported here.")
    /** Taps, goes back, scrolls or types on the app in front. Only called after the user said yes. */
    suspend fun screenAction(op: com.raju.aiassistant.domain.screen.ScreenOp): ToolOutcome =
        ToolOutcome.Failed("Screen control is not supported here.")
    /**
     * Waits for the result of the WhatsApp Send/Call button press Seema armed: true = pressed (and the
     * message left the box), false = it did not happen, null = cannot be checked here.
     */
    suspend fun awaitAutoAction(timeoutMs: Long): Boolean? = null
    /**
     * WhatsApp for a name that is not in the phone's contacts (a group, a WhatsApp-only name): opens
     * WhatsApp, searches [chat], opens the one matching chat, types [text] and presses Send — only
     * after the user said yes. Never hangs: a stuck step ends the flow with a reason.
     */
    suspend fun whatsAppSearchAndSend(chat: String, text: String): com.raju.aiassistant.domain.screen.WhatsAppFlowResult =
        com.raju.aiassistant.domain.screen.WhatsAppFlowResult(com.raju.aiassistant.domain.screen.FlowOutcome.UNAVAILABLE)
    /** Like [whatsAppSearchAndSend], with a document from the phone (📎 → Document → the matching file). */
    suspend fun whatsAppSendDocument(chat: String, fileQuery: String): com.raju.aiassistant.domain.screen.WhatsAppFlowResult =
        com.raju.aiassistant.domain.screen.WhatsAppFlowResult(com.raju.aiassistant.domain.screen.FlowOutcome.UNAVAILABLE)
    /**
     * Waits until the phone reports a call in progress (the call Seema placed has started):
     * true = started, false = not within [timeoutMs], null = cannot be checked here.
     */
    suspend fun awaitCallStarted(timeoutMs: Long): Boolean? = null
    fun findMyPhone(): ToolOutcome = ToolOutcome.Failed("Finding phone is not supported here.")
    /** Loudness, chime and "found" signals for the continuous find-my-phone beacon; null = not here. */
    fun beaconHardware(): BeaconHardware? = null
    fun endCall(): ToolOutcome = ToolOutcome.Failed("Ending call is not supported here.")
    fun answerCall(): ToolOutcome = ToolOutcome.Failed("Answering a call is not supported here.")
    /** Loudspeaker on or off for the current call. */
    fun setSpeakerphone(on: Boolean): ToolOutcome = ToolOutcome.Failed("Speaker control is not supported here.")
    /** Front or back camera; [save] keeps the photo in the gallery (Pictures/Seema). */
    suspend fun takePhoto(front: Boolean, save: Boolean): PhotoResult = PhotoResult.Failed("Camera is not supported here.")
    /** A picture of the screen as it is now (not saved), for "screen dekh ke batao". */
    suspend fun captureScreen(): PhotoResult = PhotoResult.Failed("Screen capture is not supported here.")
    fun goHome(): ToolOutcome = ToolOutcome.Failed("Going home is not supported here.")
    fun lockScreen(): ToolOutcome = ToolOutcome.Failed("Locking is not supported here.")
    fun openSeema(): ToolOutcome = ToolOutcome.Failed("Opening Seema is not supported here.")
}

enum class TravelMode { DRIVE, WALK, BIKE, TRANSIT }

enum class MediaCommand { PLAY, PAUSE, NEXT, PREVIOUS }

/** Digits-only number for wa.me links; 10-digit Indian mobile numbers get the 91 prefix. */
fun normalizeForWhatsApp(number: String): String? {
    val digits = number.filter { it.isDigit() }
    return when {
        digits.length == 10 -> "91$digits"
        digits.length == 11 && digits.startsWith("0") -> "91" + digits.drop(1)
        digits.length in 11..15 -> digits
        else -> null
    }
}

/** Deduplicates contacts by number (last 10 digits). */
fun distinctContacts(contacts: List<Contact>): List<Contact> =
    contacts.distinctBy { it.number.filter(Char::isDigit).takeLast(10) }

/** Added to a Tatkal alarm's reply when exact alarms are off: it may ring a little late. */
const val TATKAL_INEXACT = "(exact alarm off: may ring late)"
