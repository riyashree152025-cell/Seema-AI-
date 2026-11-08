package com.raju.aiassistant.data.tools

import android.Manifest
import android.content.ActivityNotFoundException
import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.pm.PackageManager
import android.hardware.camera2.CameraAccessException
import android.hardware.camera2.CameraCharacteristics
import android.hardware.camera2.CameraManager
import android.media.AudioManager
import android.media.RingtoneManager
import android.media.AudioAttributes
import android.os.Build
import android.provider.AlarmClock
import android.provider.CalendarContract
import android.telephony.SmsManager
import android.telecom.TelecomManager
import android.view.KeyEvent
import android.net.Uri
import android.os.BatteryManager
import android.provider.ContactsContract
import android.provider.MediaStore
import android.provider.Settings
import androidx.core.content.ContextCompat
import com.raju.aiassistant.domain.assistant.SettingsPage
import com.raju.aiassistant.domain.model.MusicService
import com.raju.aiassistant.domain.tools.Contact
import com.raju.aiassistant.domain.tools.ContactMatch
import com.raju.aiassistant.domain.tools.ContactLookup
import com.raju.aiassistant.domain.tools.DeviceTools
import com.raju.aiassistant.domain.tools.MediaCommand
import com.raju.aiassistant.domain.tools.ToolOutcome
import com.raju.aiassistant.domain.tools.TravelMode
import com.raju.aiassistant.domain.tools.normalizeForWhatsApp
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull
import java.net.URLEncoder
import java.text.DateFormat
import java.util.Date

/** Phone actions via public APIs and visible system UI only (dialer, launcher intents, settings screens). */
private const val A11Y_READ_MS = 3_000L
private const val A11Y_ACT_MS = 3_000L
// Each step may take ~14 s on a slow phone or network; the whole flow still has a hard ceiling.
private const val WHATSAPP_FLOW_MS = 60_000L
private const val WHATSAPP_DOCUMENT_FLOW_MS = 90_000L

class AndroidDeviceTools(private val context: Context) : DeviceTools {

    private fun granted(permission: String) =
        ContextCompat.checkSelfPermission(context, permission) == PackageManager.PERMISSION_GRANTED

    override suspend fun findContacts(query: String): ContactLookup {
        if (!granted(Manifest.permission.READ_CONTACTS)) {
            return ContactLookup.NeedsPermission(Manifest.permission.READ_CONTACTS, "Seema AI needs Contacts access to find \"$query\". Contacts are read on this phone only and never uploaded.")
        }
        return withContext(Dispatchers.IO) {
            try {
                val result = mutableListOf<Contact>()
                context.contentResolver.query(
                    ContactsContract.CommonDataKinds.Phone.CONTENT_URI,
                    arrayOf(ContactsContract.CommonDataKinds.Phone.DISPLAY_NAME, ContactsContract.CommonDataKinds.Phone.NUMBER),
                    "${ContactsContract.CommonDataKinds.Phone.DISPLAY_NAME} LIKE ?",
                    arrayOf("%${query.trim()}%"),
                    "${ContactsContract.CommonDataKinds.Phone.DISPLAY_NAME} ASC",
                )?.use { c ->
                    while (c.moveToNext() && result.size < 20) {
                        val name = c.getString(0) ?: continue
                        val number = c.getString(1) ?: continue
                        result += Contact(name, number)
                    }
                }
                // Prefer exact name matches when there are any.
                val exact = result.filter { it.name.equals(query.trim(), ignoreCase = true) }
                val direct = exact.ifEmpty { result }
                // Nothing with that exact spelling ("Vodafone 0 1" vs "Vodafone 01", other word order,
                // a spoken number): compare every contact ignoring spaces, case and punctuation.
                ContactLookup.Found(direct.ifEmpty { ContactMatch.best(query, allPhoneContacts()).take(20) })
            } catch (e: SecurityException) {
                ContactLookup.NeedsPermission(Manifest.permission.READ_CONTACTS, "Contacts permission is required.")
            } catch (e: Exception) {
                ContactLookup.Failed("Could not read contacts: ${e.message}")
            }
        }
    }

    /** Every contact with a phone number (name + number), at most [limit] rows. Caller holds READ_CONTACTS. */
    private fun allPhoneContacts(limit: Int = 5_000): List<Contact> {
        val all = mutableListOf<Contact>()
        context.contentResolver.query(
            ContactsContract.CommonDataKinds.Phone.CONTENT_URI,
            arrayOf(ContactsContract.CommonDataKinds.Phone.DISPLAY_NAME, ContactsContract.CommonDataKinds.Phone.NUMBER),
            null, null, null,
        )?.use { c ->
            while (c.moveToNext() && all.size < limit) {
                val name = c.getString(0) ?: continue
                val number = c.getString(1) ?: continue
                all += Contact(name, number)
            }
        }
        return all
    }

    @android.annotation.SuppressLint("MissingPermission") // CALL_PHONE is checked (useDirect)
    override fun dial(contact: Contact, direct: Boolean): ToolOutcome {
        val uri = Uri.parse("tel:" + contact.number.filter { it.isDigit() || it == '+' })
        val useDirect = direct && granted(Manifest.permission.CALL_PHONE)
        if (useDirect) {
            val am = context.getSystemService(AudioManager::class.java)
            am?.let {
                it.mode = AudioManager.MODE_IN_COMMUNICATION
                it.isSpeakerphoneOn = true
            }
        }
        if (useDirect) {
            // Telecom places the call without opening a screen, so it also works while another
            // app (WhatsApp, YouTube…) is on top; ACTION_CALL from behind would be ignored.
            val placed = runCatching {
                context.getSystemService(TelecomManager::class.java)?.placeCall(uri, android.os.Bundle())
                    ?: error("no telecom")
            }.isSuccess
            if (placed) return ToolOutcome.Done("कॉल लगाई जा रही है ${contact.name} को (लाउडस्पीकर चालू)।")
        }
        val intent = Intent(if (useDirect) Intent.ACTION_CALL else Intent.ACTION_DIAL, uri)
        return start(intent,
            success = if (useDirect) "कॉल लगाई जा रही है ${contact.name} को (लाउडस्पीकर चालू)।" else "Dialer opened for ${contact.name} (${contact.number}). Tap the call button to call.",
            failure = "No phone app found to make the call.")
    }

    private fun isFinancialOrBankingApp(packageName: String, label: String): Boolean {
        val lowerPkg = packageName.lowercase()
        val lowerLabel = label.lowercase()
        val financialKeywords = listOf(
            "paisa", "phonepe", "paytm", "bhim", "gpay", "google pay",
            "yono", "bank", "banking", "wallet", "cred", "hdfc",
            "icici", "axis mobile", "kotak", "canara", "union bank",
            "pnb one", "bob world", "mobikwik", "freecharge", "zerodha", "groww"
        )
        return financialKeywords.any { lowerPkg.contains(it) || lowerLabel.contains(it) } ||
            com.raju.aiassistant.domain.screen.SensitiveScreens.isFinancialApp(packageName, label)
    }

    override fun openApp(name: String): ToolOutcome {
        val wanted = name.trim().lowercase()
        if (wanted in listOf("home", "home screen", "homescreen", "launcher", "होम स्क्रीन", "होम")) {
            return goHome()
        }
        if (wanted in listOf("seema", "seema ai", "seema-ai", "assistant", "apni screen", "apna screen", "सीमा", "सीमा स्क्रीन")) {
            return openSeema()
        }

        val pm = context.packageManager
        val launcher = Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_LAUNCHER)
        val apps = pm.queryIntentActivities(launcher, 0)
        val match = apps.firstOrNull { it.loadLabel(pm).toString().lowercase() == wanted }
            ?: apps.firstOrNull { it.loadLabel(pm).toString().lowercase().startsWith(wanted) }
            ?: apps.firstOrNull { it.loadLabel(pm).toString().lowercase().contains(wanted) }
            ?: return ToolOutcome.Failed("No installed app called \"$name\" was found.")
        val label = match.loadLabel(pm).toString()
        val packageName = match.activityInfo.packageName

        // Strict Non-Banking & Financial Security Shield
        if (isFinancialOrBankingApp(packageName, label)) {
            return ToolOutcome.Failed("सुरक्षा कारणों से बैंकिंग और UPI ऐप्स ($label) को Seema AI द्वारा नियंत्रित करना पूरी तरह ब्लॉक है।")
        }

        val intent = pm.getLaunchIntentForPackage(packageName)
            ?: return ToolOutcome.Failed("$label cannot be opened.")
        return start(intent, "Opened $label.", "$label could not be opened.")
    }

    override fun openSettings(page: SettingsPage): ToolOutcome {
        val intent = when (page) {
            SettingsPage.WIFI -> Intent(Settings.ACTION_WIFI_SETTINGS)
            SettingsPage.BLUETOOTH -> Intent(Settings.ACTION_BLUETOOTH_SETTINGS)
            SettingsPage.DISPLAY -> Intent(Settings.ACTION_DISPLAY_SETTINGS)
            SettingsPage.SOUND -> Intent(Settings.ACTION_SOUND_SETTINGS)
            SettingsPage.APPS -> Intent(Settings.ACTION_APPLICATION_SETTINGS)
            SettingsPage.NOTIFICATION_ACCESS -> Intent(Settings.ACTION_NOTIFICATION_LISTENER_SETTINGS)
            SettingsPage.BATTERY -> Intent(Intent.ACTION_POWER_USAGE_SUMMARY)
            SettingsPage.APP_PERMISSIONS -> Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, Uri.parse("package:${context.packageName}"))
            SettingsPage.GENERAL -> Intent(Settings.ACTION_SETTINGS)
        }
        return start(intent, "Opened ${page.name.lowercase().replace('_', ' ')} settings.", "That settings page is not available on this phone.")
    }

    override fun batteryStatus(): ToolOutcome {
        val bm = context.getSystemService(BatteryManager::class.java)
        val level = bm?.getIntProperty(BatteryManager.BATTERY_PROPERTY_CAPACITY) ?: -1
        val status = context.registerReceiver(null, IntentFilter(Intent.ACTION_BATTERY_CHANGED))
            ?.getIntExtra(BatteryManager.EXTRA_STATUS, -1) ?: -1
        val charging = status == BatteryManager.BATTERY_STATUS_CHARGING || status == BatteryManager.BATTERY_STATUS_FULL
        if (level < 0) return ToolOutcome.Failed("Battery level is not available.")
        return ToolOutcome.Done("Battery is at $level%" + if (charging) " and charging." else ".")
    }

    override fun playMusic(query: String, service: MusicService): ToolOutcome {
        val encoded = URLEncoder.encode(query, "UTF-8")
        val (pkg, deepLink) = when (service) {
            MusicService.YOUTUBE_MUSIC -> "com.google.android.apps.youtube.music" to "https://music.youtube.com/search?q=$encoded"
            MusicService.SPOTIFY -> "com.spotify.music" to "spotify:search:$encoded"
        }
        // 1) Ask the app to search and play (supported by both apps), 2) open its search, 3) web fallback.
        val playFromSearch = Intent(MediaStore.INTENT_ACTION_MEDIA_PLAY_FROM_SEARCH).apply {
            setPackage(pkg)
            putExtra(android.app.SearchManager.QUERY, query)
            putExtra(MediaStore.EXTRA_MEDIA_FOCUS, "vnd.android.cursor.item/*")
        }
        if (isInstalled(pkg)) {
            if (!canOpenScreens()) return blockedInBackground()
            if (tryStart(playFromSearch)) return ToolOutcome.Done("Asked ${service.label} to play \"$query\". Playback depends on the app and your subscription.")
            if (tryStart(Intent(Intent.ACTION_VIEW, Uri.parse(deepLink)).setPackage(pkg))) return ToolOutcome.Done("Opened ${service.label} search for \"$query\".")
        }
        val web = if (service == MusicService.SPOTIFY) "https://open.spotify.com/search/$encoded" else "https://music.youtube.com/search?q=$encoded"
        return start(Intent(Intent.ACTION_VIEW, Uri.parse(web)),
            "${service.label} app is not installed — opened its website search for \"$query\".",
            "Could not open ${service.label}.")
    }

    override fun openWhatsApp(number: String?, text: String): ToolOutcome {
        val pkg = listOf("com.whatsapp", "com.whatsapp.w4b").firstOrNull { isInstalled(it) }
            ?: return ToolOutcome.Failed("WhatsApp is not installed.")
        val normalized = number?.let { normalizeForWhatsApp(it) }
        val intent = if (normalized != null) {
            Intent(Intent.ACTION_VIEW, Uri.parse("https://wa.me/$normalized?text=${URLEncoder.encode(text, "UTF-8")}")).setPackage(pkg)
        } else {
            Intent(Intent.ACTION_SEND).setType("text/plain").putExtra(Intent.EXTRA_TEXT, text).setPackage(pkg)
        }
        // Auto-send only into the chat of a known number; without one WhatsApp shows its chat list and
        // pressing "send" there could reach the wrong person.
        val autoSendActive = normalized != null && com.raju.aiassistant.accessibility.SeemaAccessibilityService.isRunning()
        if (autoSendActive) {
            com.raju.aiassistant.accessibility.SeemaAccessibilityService.armWhatsAppAutoSend(text)
        }
        val msg = when {
            autoSendActive -> "WhatsApp संदेश सीधे भेजा जा रहा है (ऑटो-सेंड सक्रिय)।"
            normalized == null -> "WhatsApp खुल गया है। चैट चुनें और Send दबाएँ — संदेश अभी भेजा नहीं गया है।"
            else -> "WhatsApp में संदेश तैयार है — Send दबाएँ, अभी भेजा नहीं गया है। बिना छुए भेजने के लिए फ़ोन की Settings → Accessibility में Seema AI चालू करें।"
        }
        return start(intent, msg, "Could not open WhatsApp.")
    }

    override fun openWhatsAppCall(number: String?, video: Boolean): ToolOutcome {
        val pkg = listOf("com.whatsapp", "com.whatsapp.w4b").firstOrNull { isInstalled(it) }
            ?: return ToolOutcome.Failed("WhatsApp is not installed.")
        val normalized = number?.let { normalizeForWhatsApp(it) }
            ?: return ToolOutcome.Failed("No valid WhatsApp phone number found.")
        val autoCallActive = com.raju.aiassistant.accessibility.SeemaAccessibilityService.isRunning()
        if (autoCallActive) {
            com.raju.aiassistant.accessibility.SeemaAccessibilityService.armWhatsAppCall(video)
        }
        val intent = Intent(Intent.ACTION_VIEW, Uri.parse("https://api.whatsapp.com/send?phone=$normalized")).setPackage(pkg)
        val callType = if (video) "वीडियो कॉल" else "वॉइस कॉल"
        val msg = if (autoCallActive) "WhatsApp $callType सीधे लगाई जा रही है।"
                  else "WhatsApp खुल गया है। बिना छुए ऑटो-कॉल के लिए Accessibility चालू करें।"
        return start(intent, msg, "Could not open WhatsApp.")
    }

    override fun endCall(): ToolOutcome = telecomCall(answer = false)

    override fun answerCall(): ToolOutcome = telecomCall(answer = true)

    /** Ends or answers the ringing/active call (Android 9+, needs ANSWER_PHONE_CALLS). */
    @Suppress("DEPRECATION") // endCall/acceptRingingCall are deprecated but still the only API for non-dialer apps
    private fun telecomCall(answer: Boolean): ToolOutcome {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.P) {
            return ToolOutcome.Failed(if (answer) "कॉल उठाने के लिए Android 9+ आवश्यक है।" else "कॉल कट करने के लिए Android 9+ आवश्यक है।")
        }
        val tm = context.getSystemService(TelecomManager::class.java)
            ?: return ToolOutcome.Failed("Telecom service is not available.")
        if (ContextCompat.checkSelfPermission(context, Manifest.permission.ANSWER_PHONE_CALLS) != PackageManager.PERMISSION_GRANTED) {
            return ToolOutcome.NeedsPermission(
                Manifest.permission.ANSWER_PHONE_CALLS,
                if (answer) "कॉल उठाने के लिए अनुमति आवश्यक है।" else "कॉल कट करने के लिए अनुमति आवश्यक है।",
            )
        }
        return try {
            if (answer) {
                // Ringing is visible in the audio mode (no extra permission needed).
                val ringing = context.getSystemService(android.media.AudioManager::class.java)?.mode == android.media.AudioManager.MODE_RINGTONE
                if (!ringing) return ToolOutcome.Failed("अभी कोई कॉल नहीं आ रही है।")
                tm.acceptRingingCall()
                ToolOutcome.Done("कॉल उठा ली है।")
            } else {
                if (tm.endCall()) ToolOutcome.Done("फोन कॉल कट कर दिया गया है।") else ToolOutcome.Failed("कोई एक्टिव कॉल नहीं मिली।")
            }
        } catch (e: SecurityException) {
            ToolOutcome.NeedsPermission(Manifest.permission.ANSWER_PHONE_CALLS, "फोन ने कॉल कंट्रोल की अनुमति नहीं दी।")
        } catch (e: Exception) {
            ToolOutcome.Failed("कॉल में समस्या आई: ${e.message}")
        }
    }

    override fun hasPermission(permission: String): Boolean = granted(permission)

    override suspend fun takePhoto(front: Boolean, save: Boolean): com.raju.aiassistant.domain.tools.PhotoResult {
        if (ContextCompat.checkSelfPermission(context, Manifest.permission.CAMERA) != PackageManager.PERMISSION_GRANTED) {
            return com.raju.aiassistant.domain.tools.PhotoResult.NeedsPermission(Manifest.permission.CAMERA, "फोटो लेने के लिए कैमरा की अनुमति चाहिए।")
        }
        if (!appOnScreen()) {
            // Android lets only the app on screen use the camera: bring Seema forward first.
            if (openSeema() !is ToolOutcome.Done) {
                return com.raju.aiassistant.domain.tools.PhotoResult.Failed(BACKGROUND_BLOCKED)
            }
            var waited = 0
            while (!appOnScreen() && waited < 2_500) { kotlinx.coroutines.delay(100); waited += 100 }
            kotlinx.coroutines.delay(300)
        }
        val jpeg = try {
            CameraCapture.capture(context, front)
        } catch (e: SecurityException) {
            return com.raju.aiassistant.domain.tools.PhotoResult.Failed("कैमरा तभी चलता है जब सीमा स्क्रीन पर खुली हो।")
        } catch (e: Exception) {
            return com.raju.aiassistant.domain.tools.PhotoResult.Failed("कैमरा नहीं चल पाया (${e.message ?: e.javaClass.simpleName})।")
        }
        val saved = if (save) savePhoto(jpeg) else null
        return com.raju.aiassistant.domain.tools.PhotoResult.Taken(jpeg, saved)
    }

    /** Saves into the gallery (Pictures/Seema); no storage permission is needed for this on Android 10+. */
    private fun savePhoto(jpeg: ByteArray): String? = runCatching {
        val name = "Seema_" + java.text.SimpleDateFormat("yyyyMMdd_HHmmss", java.util.Locale.US).format(java.util.Date()) + ".jpg"
        val values = android.content.ContentValues().apply {
            put(android.provider.MediaStore.Images.Media.DISPLAY_NAME, name)
            put(android.provider.MediaStore.Images.Media.MIME_TYPE, "image/jpeg")
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) put(android.provider.MediaStore.Images.Media.RELATIVE_PATH, "Pictures/Seema")
        }
        val uri = context.contentResolver.insert(android.provider.MediaStore.Images.Media.EXTERNAL_CONTENT_URI, values) ?: return@runCatching null
        context.contentResolver.openOutputStream(uri)?.use { it.write(jpeg) }
        name
    }.getOrNull()

    override fun setTimer(seconds: Int, note: String): ToolOutcome {
        val intent = Intent(AlarmClock.ACTION_SET_TIMER)
            .putExtra(AlarmClock.EXTRA_LENGTH, seconds.coerceIn(1, 86_400))
            .putExtra(AlarmClock.EXTRA_MESSAGE, note)
            .putExtra(AlarmClock.EXTRA_SKIP_UI, true)
        val shown = if (seconds >= 60) "${seconds / 60} min" + if (seconds % 60 != 0) " ${seconds % 60} sec" else "" else "$seconds sec"
        return start(intent, "Timer set for $shown.", "No clock app accepted the timer.")
    }

    override fun setAlarm(hour: Int, minute: Int, note: String): ToolOutcome {
        val intent = Intent(AlarmClock.ACTION_SET_ALARM)
            .putExtra(AlarmClock.EXTRA_HOUR, hour)
            .putExtra(AlarmClock.EXTRA_MINUTES, minute)
            .putExtra(AlarmClock.EXTRA_MESSAGE, note)
            .putExtra(AlarmClock.EXTRA_SKIP_UI, true)
        return start(intent, "Alarm set for %02d:%02d.".format(hour, minute), "No clock app accepted the alarm.")
    }

    override fun setFlashlight(on: Boolean): ToolOutcome {
        val cm = context.getSystemService(CameraManager::class.java) ?: return ToolOutcome.Failed("Camera service is not available.")
        return try {
            val id = cm.cameraIdList.firstOrNull { cm.getCameraCharacteristics(it).get(CameraCharacteristics.FLASH_INFO_AVAILABLE) == true }
                ?: return ToolOutcome.Failed("This phone has no flashlight.")
            cm.setTorchMode(id, on)
            ToolOutcome.Done("Flashlight is " + if (on) "on." else "off.")
        } catch (e: CameraAccessException) {
            ToolOutcome.Failed("Flashlight could not be changed (camera is in use).")
        } catch (e: IllegalArgumentException) {
            ToolOutcome.Failed("Flashlight could not be changed.")
        }
    }

    override fun setVolume(percent: Int): ToolOutcome {
        val am = context.getSystemService(AudioManager::class.java) ?: return ToolOutcome.Failed("Audio service is not available.")
        val max = am.getStreamMaxVolume(AudioManager.STREAM_MUSIC)
        val target = (max * percent.coerceIn(0, 100) / 100f).toInt().coerceIn(0, max)
        return try {
            am.setStreamVolume(AudioManager.STREAM_MUSIC, target, AudioManager.FLAG_SHOW_UI)
            ToolOutcome.Done("Media volume set to about ${percent.coerceIn(0, 100)}%.")
        } catch (e: SecurityException) {
            ToolOutcome.Failed("Android did not allow changing the volume right now (Do Not Disturb?).")
        }
    }

    override fun mediaControl(command: MediaCommand): ToolOutcome {
        val am = context.getSystemService(AudioManager::class.java) ?: return ToolOutcome.Failed("Audio service is not available.")
        val code = when (command) {
            MediaCommand.PLAY -> KeyEvent.KEYCODE_MEDIA_PLAY
            MediaCommand.PAUSE -> KeyEvent.KEYCODE_MEDIA_PAUSE
            MediaCommand.NEXT -> KeyEvent.KEYCODE_MEDIA_NEXT
            MediaCommand.PREVIOUS -> KeyEvent.KEYCODE_MEDIA_PREVIOUS
        }
        am.dispatchMediaKeyEvent(KeyEvent(KeyEvent.ACTION_DOWN, code))
        am.dispatchMediaKeyEvent(KeyEvent(KeyEvent.ACTION_UP, code))
        return ToolOutcome.Done("Sent \"${command.name.lowercase()}\" to the active music app. It only works if some app is playing or paused.")
    }

    @Suppress("DEPRECATION")
    override fun sendSms(contact: Contact, text: String): ToolOutcome {
        if (!granted(Manifest.permission.SEND_SMS)) return ToolOutcome.NeedsPermission(Manifest.permission.SEND_SMS, "SMS permission is required.")
        val number = contact.number.filter { it.isDigit() || it == '+' }
        if (number.length < 3) return ToolOutcome.Failed("\"${contact.number}\" is not a valid phone number.")
        return try {
            val sms = if (Build.VERSION.SDK_INT >= 31) context.getSystemService(SmsManager::class.java) else SmsManager.getDefault()
            sms.sendMultipartTextMessage(number, null, sms.divideMessage(text), null, null)
            ToolOutcome.Done("SMS handed to Android for ${contact.name} ($number). Delivery is not confirmed.")
        } catch (e: Exception) {
            ToolOutcome.Failed("SMS could not be sent: ${e.message ?: e.javaClass.simpleName}")
        }
    }

    override fun addCalendarEvent(title: String, startMillis: Long, durationMinutes: Int, location: String?, rrule: String?): ToolOutcome {
        val intent = Intent(Intent.ACTION_INSERT)
            .setData(CalendarContract.Events.CONTENT_URI)
            .putExtra(CalendarContract.Events.TITLE, title)
            .putExtra(CalendarContract.EXTRA_EVENT_BEGIN_TIME, startMillis)
            .putExtra(CalendarContract.EXTRA_EVENT_END_TIME, startMillis + durationMinutes.coerceIn(1, 24 * 60) * 60_000L)
        if (!location.isNullOrBlank()) intent.putExtra(CalendarContract.Events.EVENT_LOCATION, location)
        if (!rrule.isNullOrBlank()) intent.putExtra(CalendarContract.Events.RRULE, rrule)
        val shown = DateFormat.getDateTimeInstance(DateFormat.MEDIUM, DateFormat.SHORT).format(Date(startMillis))
        val repeats = when {
            rrule.isNullOrBlank() -> ""
            rrule.contains("BYDAY") -> " (Mon–Fri)"
            rrule.contains("DAILY") -> " (every day)"
            else -> " (every week)"
        }
        return start(intent, "Calendar opened with \"$title\" on $shown$repeats. Tap Save there to add it (a reminder is added by your calendar app).", "No calendar app is installed.")
    }

    override fun navigate(destination: String, mode: TravelMode): ToolOutcome {
        val q = Uri.encode(destination)
        val mapsMode = when (mode) { TravelMode.DRIVE -> "d"; TravelMode.WALK -> "w"; TravelMode.BIKE -> "b"; TravelMode.TRANSIT -> "d" }
        val webMode = when (mode) { TravelMode.DRIVE -> "driving"; TravelMode.WALK -> "walking"; TravelMode.BIKE -> "bicycling"; TravelMode.TRANSIT -> "transit" }
        if (!canOpenScreens()) return blockedInBackground()
        val ok = (mode != TravelMode.TRANSIT && isInstalled(MAPS_PKG) &&
            tryStart(Intent(Intent.ACTION_VIEW, Uri.parse("google.navigation:q=$q&mode=$mapsMode")).setPackage(MAPS_PKG))) ||
            tryStart(Intent(Intent.ACTION_VIEW, Uri.parse("https://www.google.com/maps/dir/?api=1&destination=$q&travelmode=$webMode"))) ||
            tryStart(Intent(Intent.ACTION_VIEW, Uri.parse("geo:0,0?q=$q")))
        return if (ok) ToolOutcome.Done("Directions to \"$destination\" opened.") else ToolOutcome.Failed("No maps app or browser could open directions.")
    }

    override fun copyToClipboard(text: String): ToolOutcome {
        val cm = context.getSystemService(ClipboardManager::class.java) ?: return ToolOutcome.Failed("Clipboard is not available.")
        return try {
            cm.setPrimaryClip(ClipData.newPlainText("Seema AI", text))
            ToolOutcome.Done("Copied to clipboard (${text.length} characters).")
        } catch (e: RuntimeException) {
            ToolOutcome.Failed("Could not copy to the clipboard.")
        }
    }

    private fun isInstalled(pkg: String): Boolean = try {
        context.packageManager.getPackageInfo(pkg, 0); true
    } catch (e: PackageManager.NameNotFoundException) {
        false
    }

    private fun tryStart(intent: Intent): Boolean = try {
        context.startActivity(intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)); true
    } catch (e: ActivityNotFoundException) {
        false
    } catch (e: SecurityException) {
        false
    }

    /** Seema's own screen is visible (not only her background microphone service). */
    private fun appOnScreen(): Boolean = runCatching {
        val info = android.app.ActivityManager.RunningAppProcessInfo()
        android.app.ActivityManager.getMyMemoryState(info)
        info.importance <= android.app.ActivityManager.RunningAppProcessInfo.IMPORTANCE_FOREGROUND
    }.getOrDefault(true)

    /**
     * Android 10+ silently ignores screens opened by an app that is behind another app — Seema
     * would say "khol diya" and nothing opens. A running Seema Accessibility service is the
     * exemption that lets her open apps from anywhere.
     */
    private fun canOpenScreens(): Boolean =
        appOnScreen() || com.raju.aiassistant.accessibility.SeemaAccessibilityService.isRunning()

    private fun blockedInBackground(): ToolOutcome.Failed = ToolOutcome.Failed(BACKGROUND_BLOCKED)

    private fun start(intent: Intent, success: String, failure: String): ToolOutcome = when {
        !canOpenScreens() -> blockedInBackground()
        tryStart(intent) -> ToolOutcome.Done(success)
        else -> ToolOutcome.Failed(failure)
    }

    override fun openWebPage(url: String): ToolOutcome =
        start(Intent(Intent.ACTION_VIEW, Uri.parse(url)), "Opened $url", "No browser could open $url")

    override fun shareTextFile(name: String, content: String): ToolOutcome = try {
        val dir = java.io.File(context.cacheDir, "shared").apply { mkdirs() }
        val file = java.io.File(dir, name)
        file.writeText(content)
        val uri = androidx.core.content.FileProvider.getUriForFile(context, "${context.packageName}.files", file)
        val mime = android.webkit.MimeTypeMap.getSingleton().getMimeTypeFromExtension(name.substringAfterLast('.', "").lowercase()) ?: "text/plain"
        val send = Intent(Intent.ACTION_SEND)
            .setType(mime)
            .putExtra(Intent.EXTRA_STREAM, uri)
            .putExtra(Intent.EXTRA_SUBJECT, name)
            .addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        val chooser = Intent.createChooser(send, "Save or share $name").addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        start(chooser, "File \"$name\" ready (${content.length} characters) — choose where to save or share it.", "Could not open the share sheet for $name.")
    } catch (e: Exception) {
        ToolOutcome.Failed("Could not save $name: ${e.message}")
    }

    override suspend fun getLastCall(): ToolOutcome {
        if (!granted(Manifest.permission.READ_CALL_LOG)) {
            return ToolOutcome.NeedsPermission(
                Manifest.permission.READ_CALL_LOG,
                "Seema AI needs Call Log permission to see your recent calls."
            )
        }
        return withContext(Dispatchers.IO) {
            try {
                val projection = arrayOf(
                    android.provider.CallLog.Calls.NUMBER,
                    android.provider.CallLog.Calls.CACHED_NAME,
                    android.provider.CallLog.Calls.TYPE,
                    android.provider.CallLog.Calls.DATE,
                    android.provider.CallLog.Calls.DURATION,
                )
                context.contentResolver.query(
                    android.provider.CallLog.Calls.CONTENT_URI,
                    projection,
                    null,
                    null,
                    "${android.provider.CallLog.Calls.DATE} DESC LIMIT 1"
                )?.use { c ->
                    if (c.moveToFirst()) {
                        val number = c.getString(0) ?: "Unknown"
                        val name = c.getString(1)?.takeIf { it.isNotBlank() }
                        val type = c.getInt(2)
                        val date = c.getLong(3)
                        val caller = name ?: number
                        val typeStr = when (type) {
                            android.provider.CallLog.Calls.INCOMING_TYPE -> "Incoming call"
                            android.provider.CallLog.Calls.OUTGOING_TYPE -> "Outgoing call"
                            android.provider.CallLog.Calls.MISSED_TYPE -> "Missed call"
                            android.provider.CallLog.Calls.REJECTED_TYPE -> "Rejected call"
                            android.provider.CallLog.Calls.VOICEMAIL_TYPE -> "Voicemail"
                            else -> "Call"
                        }
                        val timeAgo = android.text.format.DateUtils.getRelativeTimeSpanString(
                            date,
                            System.currentTimeMillis(),
                            android.text.format.DateUtils.MINUTE_IN_MILLIS
                        ).toString()

                        ToolOutcome.Done("लास्ट कॉल $caller की थी ($typeStr), $timeAgo।")
                    } else {
                        ToolOutcome.Done("कॉल लॉग में कोई कॉल नहीं मिली।")
                    }
                } ?: ToolOutcome.Failed("Could not access call log.")
            } catch (e: SecurityException) {
                ToolOutcome.NeedsPermission(Manifest.permission.READ_CALL_LOG, "Call log permission is required.")
            } catch (e: Exception) {
                ToolOutcome.Failed("Could not read call log: ${e.message}")
            }
        }
    }

    /**
     * Accessibility calls are IPC into the other app and can block when it hangs. They run on their
     * own threads and are waited for at most [ms]: a stuck app gives [fallback], never a frozen Seema.
     */
    private suspend fun <T> bounded(ms: Long, fallback: T, block: () -> T): T {
        val job = a11yScope.async { block() }
        return try {
            withTimeoutOrNull(ms) { job.await() } ?: fallback.also { job.cancel() }
        } catch (e: Exception) {
            if (e is kotlinx.coroutines.CancellationException) throw e
            fallback
        }
    }

    private val a11yScope = kotlinx.coroutines.CoroutineScope(kotlinx.coroutines.SupervisorJob() + Dispatchers.Default)

    override suspend fun readScreen(): com.raju.aiassistant.domain.screen.ScreenRead =
        bounded(A11Y_READ_MS, com.raju.aiassistant.domain.screen.ScreenRead.Unavailable("स्क्रीन पढ़ने में ऐप ने जवाब नहीं दिया — थोड़ी देर बाद फिर कहिए।")) {
            com.raju.aiassistant.accessibility.SeemaAccessibilityService.readScreen()
        }

    override suspend fun screenAction(op: com.raju.aiassistant.domain.screen.ScreenOp): ToolOutcome =
        bounded<ToolOutcome>(A11Y_ACT_MS, ToolOutcome.Failed("ऐप ने समय पर जवाब नहीं दिया, काम नहीं हो पाया।")) {
            val (ok, message) = com.raju.aiassistant.accessibility.SeemaAccessibilityService.perform(op)
            if (ok) ToolOutcome.Done(message) else ToolOutcome.Failed(message)
        }

    override suspend fun whatsAppSearchAndSend(chat: String, text: String) =
        whatsAppFlow(WHATSAPP_FLOW_MS) { it.send(chat, text) }

    override suspend fun whatsAppSendDocument(chat: String, fileQuery: String) =
        whatsAppFlow(WHATSAPP_DOCUMENT_FLOW_MS) { it.sendDocument(chat, fileQuery) }

    /** Runs a WhatsApp flow with Seema's Accessibility as its hands; every step and the whole flow are time-bounded. */
    private suspend fun whatsAppFlow(
        limitMs: Long,
        run: suspend (com.raju.aiassistant.domain.screen.WhatsAppSearchFlow) -> com.raju.aiassistant.domain.screen.WhatsAppFlowResult,
    ): com.raju.aiassistant.domain.screen.WhatsAppFlowResult {
        val service = com.raju.aiassistant.accessibility.SeemaAccessibilityService
        val pkg = listOf("com.whatsapp", "com.whatsapp.w4b").firstOrNull { isInstalled(it) }
        if (pkg == null || !service.isRunning()) {
            return com.raju.aiassistant.domain.screen.WhatsAppFlowResult(com.raju.aiassistant.domain.screen.FlowOutcome.UNAVAILABLE)
        }
        val hands = object : com.raju.aiassistant.domain.screen.ScreenHands {
            override suspend fun openWhatsApp(): Boolean {
                val intent = context.packageManager.getLaunchIntentForPackage(pkg)?.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK) ?: return false
                return runCatching { context.startActivity(intent) }.isSuccess
            }
            override suspend fun read() = bounded(A11Y_READ_MS, null) { service.snapshot() }
            override suspend fun tap(element: com.raju.aiassistant.domain.screen.ScreenElement) = bounded(A11Y_ACT_MS, false) { service.tap(element) }
            override suspend fun type(element: com.raju.aiassistant.domain.screen.ScreenElement, text: String) = bounded(A11Y_ACT_MS, false) { service.type(element, text) }
            override suspend fun back() = bounded(A11Y_ACT_MS, false) { service.goBack() }
        }
        // The whole flow has a hard ceiling too, whatever WhatsApp does.
        return withTimeoutOrNull(limitMs) { run(com.raju.aiassistant.domain.screen.WhatsAppSearchFlow(hands)) }
            ?: com.raju.aiassistant.domain.screen.WhatsAppFlowResult(com.raju.aiassistant.domain.screen.FlowOutcome.STUCK, log = listOf("STUCK: flow took over ${limitMs / 1000} s"))
    }

    override suspend fun awaitAutoAction(timeoutMs: Long): Boolean? {
        if (!com.raju.aiassistant.accessibility.SeemaAccessibilityService.isRunning()) return null
        var waited = 0L
        var step = 200L
        while (waited < timeoutMs) {
            com.raju.aiassistant.accessibility.SeemaAccessibilityService.autoResult?.let { return it }
            // Quick checks first (most presses land within a second), then every 500 ms.
            kotlinx.coroutines.delay(step)
            waited += step
            step = minOf(500L, step + 100L)
        }
        return com.raju.aiassistant.accessibility.SeemaAccessibilityService.autoResult
    }

    override suspend fun awaitCallStarted(timeoutMs: Long): Boolean? {
        val am = context.getSystemService(AudioManager::class.java) ?: return null
        var waited = 0L
        while (waited < timeoutMs) {
            // The phone switches its audio to "in call" once a real call is dialling or connected.
            if (am.mode == AudioManager.MODE_IN_CALL) return true
            kotlinx.coroutines.delay(250)
            waited += 250
        }
        // No call: undo the call-style audio set for the loudspeaker, so Seema's voice is not
        // left on the earpiece.
        if (am.mode == AudioManager.MODE_IN_COMMUNICATION) runCatching { am.mode = AudioManager.MODE_NORMAL }
        return false
    }

    override fun takeScreenshot(): ToolOutcome {
        if (com.raju.aiassistant.accessibility.SeemaAccessibilityService.isRunning()) {
            val taken = com.raju.aiassistant.accessibility.SeemaAccessibilityService.takeScreenshot()
            return if (taken) {
                ToolOutcome.Done("स्क्रीनशॉट ले लिया गया है।")
            } else {
                ToolOutcome.Failed("स्क्रीनशॉट लेने में समस्या आई।")
            }
        } else {
            try {
                val intent = Intent(android.provider.Settings.ACTION_ACCESSIBILITY_SETTINGS).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                context.startActivity(intent)
            } catch (_: Exception) {}
            return ToolOutcome.Failed("स्क्रीनशॉट के लिए Seema AI की Accessibility Service चालू करें (सेटिंग्स खुल गई हैं)।")
        }
    }

    override fun beaconHardware(): com.raju.aiassistant.domain.tools.BeaconHardware = AndroidBeaconHardware(context)

    override fun findMyPhone(): ToolOutcome {
        val am = context.getSystemService(AudioManager::class.java) ?: return ToolOutcome.Failed("Audio service is not available.")
        return try {
            // Unmute silent mode & maximize ring, alarm, and music volume
            runCatching { am.ringerMode = AudioManager.RINGER_MODE_NORMAL }
            val maxAlarm = am.getStreamMaxVolume(AudioManager.STREAM_ALARM)
            val maxMusic = am.getStreamMaxVolume(AudioManager.STREAM_MUSIC)
            val maxRing = am.getStreamMaxVolume(AudioManager.STREAM_RING)
            am.setStreamVolume(AudioManager.STREAM_ALARM, maxAlarm, 0)
            am.setStreamVolume(AudioManager.STREAM_MUSIC, maxMusic, 0)
            am.setStreamVolume(AudioManager.STREAM_RING, maxRing, 0)

            val uri = RingtoneManager.getDefaultUri(RingtoneManager.TYPE_ALARM)
                ?: RingtoneManager.getDefaultUri(RingtoneManager.TYPE_RINGTONE)
            val ringtone = RingtoneManager.getRingtone(context, uri)
            ringtone?.let {
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
                    it.audioAttributes = AudioAttributes.Builder()
                        .setUsage(AudioAttributes.USAGE_ALARM)
                        .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
                        .build()
                }
                it.play()
            }
            ToolOutcome.Done("फोन पर 100% फुल वॉल्यूम में अलार्म और घंटी बजा दी गई है!")
        } catch (e: Exception) {
            ToolOutcome.Failed("घंटी बजाने में समस्या आई: ${e.message}")
        }
    }

    override fun goHome(): ToolOutcome {
        if (com.raju.aiassistant.accessibility.SeemaAccessibilityService.isRunning() &&
            com.raju.aiassistant.accessibility.SeemaAccessibilityService.goHome()) {
            return ToolOutcome.Done("Navigated to Home screen.")
        }
        if (!canOpenScreens()) return blockedInBackground()
        return try {
            val intent = Intent(Intent.ACTION_MAIN).apply {
                addCategory(Intent.CATEGORY_HOME)
                flags = Intent.FLAG_ACTIVITY_NEW_TASK
            }
            context.startActivity(intent)
            ToolOutcome.Done("Navigated to Home screen.")
        } catch (e: Exception) {
            ToolOutcome.Failed("Could not navigate to Home screen: ${e.message}")
        }
    }

    override fun openSeema(): ToolOutcome {
        if (!canOpenScreens()) return blockedInBackground()
        return try {
            val intent = context.packageManager.getLaunchIntentForPackage(context.packageName)?.apply {
                flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_REORDER_TO_FRONT or Intent.FLAG_ACTIVITY_SINGLE_TOP
            }
            if (intent != null) {
                context.startActivity(intent)
                ToolOutcome.Done("Opened Seema.")
            } else {
                ToolOutcome.Failed("Could not find Seema activity.")
            }
        } catch (e: Exception) {
            ToolOutcome.Failed("Could not open Seema: ${e.message}")
        }
    }

    private companion object {
        const val MAPS_PKG = "com.google.android.apps.maps"
        const val BACKGROUND_BLOCKED = "सीमा पीछे से (दूसरे ऐप के ऊपर) स्क्रीन नहीं खोल सकती। Settings → Accessibility में Seema AI चालू कीजिए, या सीमा की स्क्रीन खोलकर फिर बोलिए।"
    }
}
