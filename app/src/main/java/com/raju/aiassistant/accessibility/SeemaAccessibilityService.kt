package com.raju.aiassistant.accessibility

import android.accessibilityservice.AccessibilityService
import android.accessibilityservice.GestureDescription
import android.content.Intent
import android.graphics.Path
import android.graphics.Rect
import android.os.Build
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.os.SystemClock
import android.view.accessibility.AccessibilityEvent
import android.view.accessibility.AccessibilityNodeInfo
import android.view.accessibility.AccessibilityWindowInfo
import com.raju.aiassistant.domain.screen.ElementKind
import com.raju.aiassistant.domain.screen.ScreenElement
import com.raju.aiassistant.domain.screen.ScreenMatch
import com.raju.aiassistant.domain.screen.ScreenOp
import com.raju.aiassistant.domain.screen.ScreenRead
import com.raju.aiassistant.domain.screen.ScreenSnapshot
import com.raju.aiassistant.domain.screen.SensitiveScreens
import com.raju.aiassistant.domain.screen.WhatsAppButtons

/**
 * Seema's eyes and hands on other apps — only when the user asks:
 * - reads the app in front (texts, buttons, input boxes), never password or banking/UPI screens;
 * - taps a named button, goes back, scrolls, types — the engine asks the user first;
 * - presses WhatsApp's Send / Call button for a chat Seema opened, and reports whether it worked.
 */
class SeemaAccessibilityService : AccessibilityService() {

    private val handler = Handler(Looper.getMainLooper())

    override fun onServiceConnected() {
        super.onServiceConnected()
        instance = this
    }

    override fun onAccessibilityEvent(event: AccessibilityEvent?) {
        if (event == null) return
        val pkg = event.packageName?.toString() ?: return
        if (pkg == "com.whatsapp" || pkg == "com.whatsapp.w4b") {
            if (pendingWhatsAppAutoSend) {
                attemptWhatsAppSend(pkg)
            } else if (pendingWhatsAppCall) {
                attemptWhatsAppCall(pkg)
            }
        }
    }

    // ------------------------------------------------------------------ WhatsApp send

    private val sendWords = listOf("send", "भेजें", "भेजो", "পাঠান").map { ScreenMatch.normalize(it) }

    private fun attemptWhatsAppSend(pkg: String) {
        val root = rootInActiveWindow ?: return
        try {
            // 1. WhatsApp's own Send button (the id carries the package, also for WhatsApp Business).
            var clicked = root.findAccessibilityNodeInfosByViewId("$pkg:id/send").any { clickNode(it) }
            // 2. A button described exactly as "Send" / "भेजें" — not anything merely containing "send".
            if (!clicked) {
                clicked = walk(root).any { n ->
                    val d = ScreenMatch.normalize(n.contentDescription?.toString() ?: "")
                    d.isNotEmpty() && d in sendWords && clickNode(n)
                }
            }
            if (!clicked) return
            pendingWhatsAppAutoSend = false // never press twice
            // Verify: after a real send WhatsApp empties its message box.
            handler.postDelayed({
                val text = pendingSendText
                val box = rootInActiveWindow?.findAccessibilityNodeInfosByViewId("$pkg:id/entry")?.firstOrNull()
                val stillThere = text != null && box?.text?.toString()?.trim()?.startsWith(text.trim().take(20)) == true
                finishAuto(!stillThere)
                if (!stillThere) returnToSeema()
            }, 800)
        } catch (_: Exception) {}
    }

    // ------------------------------------------------------------------ WhatsApp call

    private var callButtonPressedAt = 0L

    private fun attemptWhatsAppCall(pkg: String) {
        val root = rootInActiveWindow ?: return
        try {
            if (callButtonPressedAt > 0) {
                // Only a dialog about starting this call is confirmed; "Block?", "Delete?" etc. never.
                val confirm = root.findAccessibilityNodeInfosByViewId("android:id/button1")
                if (confirm.isNotEmpty() && WhatsAppButtons.isCallDialog(texts(root))) {
                    if (confirm.any { clickNode(it) }) finishCall(true)
                }
                return
            }
            val ids = if (pendingWhatsAppVideo) listOf("video_call", "menuitem_video_call") else listOf("voice_call", "menuitem_call", "audio_call")
            var pressed = ids.any { id -> root.findAccessibilityNodeInfosByViewId("$pkg:id/$id").any { clickNode(it) } }
            if (!pressed) {
                pressed = walk(root).any { n ->
                    val label = n.contentDescription?.toString() ?: n.text?.toString() ?: ""
                    WhatsAppButtons.isCallButton(label, pendingWhatsAppVideo) && clickNode(n)
                }
            }
            if (pressed) {
                callButtonPressedAt = SystemClock.elapsedRealtime()
                // No confirm dialog came: the call button press was enough.
                handler.postDelayed({ if (pendingWhatsAppCall) finishCall(true) }, 2_500)
            }
        } catch (_: Exception) {}
    }

    private fun finishCall(ok: Boolean) {
        pendingWhatsAppCall = false
        callButtonPressedAt = 0
        finishAuto(ok)
    }

    private fun returnToSeema() {
        // Back to Seema shortly after sending, so the user stays hands-free.
        handler.postDelayed({
            val launchIntent = packageManager.getLaunchIntentForPackage(packageName)?.apply {
                flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_REORDER_TO_FRONT or Intent.FLAG_ACTIVITY_SINGLE_TOP
            }
            if (launchIntent != null) runCatching { startActivity(launchIntent) } else performGlobalAction(GLOBAL_ACTION_HOME)
        }, 400)
    }

    // ------------------------------------------------------------------ screen reading

    /** The app the user is working in: the active window, or — when Seema is in front — another app window. */
    private fun targetRoot(): AccessibilityNodeInfo? {
        val active = rootInActiveWindow
        if (active != null && active.packageName?.toString() != packageName) return active
        return runCatching {
            windows.filter { it.type == AccessibilityWindowInfo.TYPE_APPLICATION }
                .mapNotNull { it.root }
                .firstOrNull { it.packageName?.toString() != packageName }
        }.getOrNull()
    }

    private fun label(pkg: String): String = runCatching {
        packageManager.getApplicationLabel(packageManager.getApplicationInfo(pkg, 0)).toString()
    }.getOrDefault(pkg)

    /** Elements of the screen with the node each came from (for tapping / typing). */
    private fun collect(root: AccessibilityNodeInfo): List<Pair<ScreenElement, AccessibilityNodeInfo>> {
        val out = ArrayList<Pair<ScreenElement, AccessibilityNodeInfo>>()
        // Depth first, children in order: the elements come out in reading order (top to bottom,
        // a row's texts together), not level by level.
        val stack = ArrayDeque<Pair<AccessibilityNodeInfo, Boolean>>()
        stack.addLast(root to false)
        var visited = 0
        while (stack.isNotEmpty() && visited < MAX_NODES && out.size < MAX_ELEMENTS) {
            val (n, parentClickable) = stack.removeLast()
            visited++
            if (!n.isVisibleToUser) continue
            val clickable = n.isClickable || parentClickable
            val cls = n.className?.toString().orEmpty()
            val password = n.isPassword
            val text = if (password) "" else (n.text?.toString() ?: n.contentDescription?.toString() ?: "").trim().take(200)
            val hint = n.hintText?.toString()?.trim().orEmpty()
            val kind = when {
                n.isEditable -> ElementKind.INPUT
                n.isScrollable && text.isEmpty() -> ElementKind.LIST
                cls.contains("Button") || (clickable && text.isNotEmpty()) -> ElementKind.BUTTON
                cls.contains("Image") -> ElementKind.IMAGE
                else -> ElementKind.TEXT
            }
            if (text.isNotEmpty() || n.isEditable || password || n.isScrollable) {
                out += ScreenElement(text, kind, clickable = clickable, editable = n.isEditable, scrollable = n.isScrollable, password = password, hint = hint, id = n.viewIdResourceName.orEmpty()) to n
            }
            for (i in n.childCount - 1 downTo 0) n.getChild(i)?.let { stack.addLast(it to clickable) }
        }
        return out
    }

    private fun read(): Pair<ScreenSnapshot, List<Pair<ScreenElement, AccessibilityNodeInfo>>>? {
        val root = targetRoot() ?: return null
        val pkg = root.packageName?.toString() ?: return null
        val items = collect(root)
        return ScreenSnapshot(pkg, label(pkg), items.map { it.first }) to items
    }

    private fun doPerform(op: ScreenOp): Outcome {
        if (op is ScreenOp.Back) {
            return if (performGlobalAction(GLOBAL_ACTION_BACK)) Outcome(true, "पीछे गई।") else Outcome(false, "पीछे नहीं जा पाई।")
        }
        val (snap, items) = read() ?: return Outcome(false, NO_APP)
        SensitiveScreens.reason(snap)?.let { return Outcome(false, "ये $it है — सुरक्षा के लिए मैं यहाँ कुछ नहीं करूँगी।") }
        return when (op) {
            is ScreenOp.Tap -> {
                val target = ScreenMatch.candidates(snap, op.target).firstOrNull()
                    ?: return Outcome(false, "स्क्रीन पर \"${op.target}\" नहीं मिला।")
                val node = items.firstOrNull { it.first == target }?.second ?: return Outcome(false, "स्क्रीन बदल गई, \"${op.target}\" नहीं मिला।")
                if (clickNode(node)) Outcome(true, "\"${target.text.ifBlank { target.hint }}\" दबाया।") else Outcome(false, "\"${op.target}\" दबाया नहीं जा सका।")
            }
            is ScreenOp.Scroll -> scroll(items, op.down)
            is ScreenOp.Type -> {
                val box = ScreenMatch.inputFor(snap, op.field) ?: return Outcome(false, "स्क्रीन पर लिखने की कोई जगह नहीं मिली।")
                val node = items.firstOrNull { it.first == box }?.second ?: return Outcome(false, "लिखने की जगह नहीं मिली।")
                if (node.isPassword) return Outcome(false, "पासवर्ड वाली जगह में मैं नहीं लिखती।")
                node.performAction(AccessibilityNodeInfo.ACTION_FOCUS)
                val args = Bundle().apply { putCharSequence(AccessibilityNodeInfo.ACTION_ARGUMENT_SET_TEXT_CHARSEQUENCE, op.text) }
                if (node.performAction(AccessibilityNodeInfo.ACTION_SET_TEXT, args)) Outcome(true, "लिख दिया।") else Outcome(false, "यहाँ लिखा नहीं जा सका।")
            }
            ScreenOp.Back -> Outcome(false, "")
        }
    }

    /** The node of [element] on the screen as it is now (null when the screen changed meanwhile). */
    private fun nodeOf(element: ScreenElement): AccessibilityNodeInfo? = read()?.second?.firstOrNull { it.first == element }?.second

    private fun tapElement(element: ScreenElement): Boolean {
        val node = nodeOf(element) ?: return false
        return clickNode(node)
    }

    private fun typeInto(element: ScreenElement, text: String): Boolean {
        val node = nodeOf(element) ?: return false
        if (node.isPassword || !node.isEditable) return false
        node.performAction(AccessibilityNodeInfo.ACTION_FOCUS)
        val args = Bundle().apply { putCharSequence(AccessibilityNodeInfo.ACTION_ARGUMENT_SET_TEXT_CHARSEQUENCE, text) }
        return node.performAction(AccessibilityNodeInfo.ACTION_SET_TEXT, args)
    }

    private fun scroll(items: List<Pair<ScreenElement, AccessibilityNodeInfo>>, down: Boolean): Outcome {
        val list = items.filter { it.first.scrollable }.maxByOrNull { val r = Rect(); it.second.getBoundsInScreen(r); r.width() * r.height() }?.second
        val action = if (down) AccessibilityNodeInfo.ACTION_SCROLL_FORWARD else AccessibilityNodeInfo.ACTION_SCROLL_BACKWARD
        if (list != null && list.performAction(action)) return Outcome(true, if (down) "नीचे स्क्रॉल किया।" else "ऊपर स्क्रॉल किया।")
        // No scrollable list reported: swipe like a finger.
        val dm = resources.displayMetrics
        val x = dm.widthPixels / 2f
        val (from, to) = if (down) dm.heightPixels * 0.7f to dm.heightPixels * 0.3f else dm.heightPixels * 0.3f to dm.heightPixels * 0.7f
        val path = Path().apply { moveTo(x, from); lineTo(x, to) }
        val gesture = GestureDescription.Builder().addStroke(GestureDescription.StrokeDescription(path, 0, 350)).build()
        return if (dispatchGesture(gesture, null, null)) Outcome(true, "स्क्रॉल किया।") else Outcome(false, "स्क्रॉल नहीं हो पाया।")
    }

    // ------------------------------------------------------------------ helpers

    private fun walk(root: AccessibilityNodeInfo): Sequence<AccessibilityNodeInfo> = sequence {
        val queue = ArrayDeque<AccessibilityNodeInfo>()
        queue.add(root)
        var visited = 0
        while (queue.isNotEmpty() && visited < MAX_NODES) {
            val n = queue.removeFirst()
            visited++
            yield(n)
            for (i in 0 until n.childCount) n.getChild(i)?.let { queue.add(it) }
        }
    }

    private fun texts(root: AccessibilityNodeInfo): List<String> =
        walk(root).mapNotNull { (it.text ?: it.contentDescription)?.toString() }.take(40).toList()

    private fun clickNode(node: AccessibilityNodeInfo): Boolean {
        var current: AccessibilityNodeInfo? = node
        while (current != null) {
            if (current.isClickable) {
                return current.performAction(AccessibilityNodeInfo.ACTION_CLICK)
            }
            current = current.parent
        }
        return false
    }

    override fun onInterrupt() {
        pendingWhatsAppAutoSend = false
        pendingWhatsAppCall = false
    }

    override fun onDestroy() {
        super.onDestroy()
        if (instance === this) instance = null
        pendingWhatsAppAutoSend = false
        pendingWhatsAppCall = false
    }

    private data class Outcome(val ok: Boolean, val message: String)

    companion object {
        private const val MAX_NODES = 800
        private const val MAX_ELEMENTS = 150
        private const val NO_APP = "अभी Seema की अपनी स्क्रीन सामने है। जिस ऐप पर काम करना है उसे खोलकर \"हे सीमा …\" बोलिए।"

        @Volatile
        var instance: SeemaAccessibilityService? = null
            private set

        @Volatile
        var pendingWhatsAppAutoSend: Boolean = false
            private set

        @Volatile
        var pendingWhatsAppCall: Boolean = false
            private set

        @Volatile
        var pendingWhatsAppVideo: Boolean = false
            private set

        @Volatile
        private var pendingSendText: String? = null

        /** Result of the last armed WhatsApp send/call: true = done, false = not done, null = still waiting. */
        @Volatile
        var autoResult: Boolean? = null
            private set

        private var armSeq = 0

        private fun finishAuto(ok: Boolean) {
            autoResult = ok
        }

        fun isRunning(): Boolean = instance != null

        /** Presses Send in the WhatsApp chat Seema opens next with [text]; gives up after 15 s. */
        fun armWhatsAppAutoSend(text: String? = null) {
            val seq = ++armSeq
            pendingSendText = text
            autoResult = null
            pendingWhatsAppAutoSend = true
            Handler(Looper.getMainLooper()).postDelayed({
                if (seq == armSeq && pendingWhatsAppAutoSend) {
                    pendingWhatsAppAutoSend = false
                    autoResult = false
                }
            }, 15_000)
        }

        fun armWhatsAppCall(video: Boolean = false) {
            val seq = ++armSeq
            autoResult = null
            instance?.callButtonPressedAt = 0
            pendingWhatsAppCall = true
            pendingWhatsAppVideo = video
            Handler(Looper.getMainLooper()).postDelayed({
                if (seq == armSeq && pendingWhatsAppCall) {
                    pendingWhatsAppCall = false
                    pendingWhatsAppVideo = false
                    instance?.callButtonPressedAt = 0
                    autoResult = false
                }
            }, 15_000)
        }

        /** Locks the screen through the system (Android 9+). Opening it again still needs the phone's own PIN or pattern. */
        fun lockScreen(): Boolean {
            val service = instance ?: return false
            return Build.VERSION.SDK_INT >= Build.VERSION_CODES.P && service.performGlobalAction(GLOBAL_ACTION_LOCK_SCREEN)
        }

        fun goHome(): Boolean {
            val service = instance ?: return false
            return service.performGlobalAction(GLOBAL_ACTION_HOME)
        }

        fun goBack(): Boolean {
            val service = instance ?: return false
            return service.performGlobalAction(GLOBAL_ACTION_BACK)
        }

        fun takeScreenshot(): Boolean {
            val service = instance ?: return false
            return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
                service.performGlobalAction(GLOBAL_ACTION_TAKE_SCREENSHOT)
            } else {
                false
            }
        }

        /** What is on the screen of the app in front (Seema's own screen excluded). */
        fun readScreen(): ScreenRead {
            val service = instance ?: return ScreenRead.Unavailable(NOT_ON)
            return runCatching { service.read()?.first }.getOrNull()?.let { ScreenRead.Ok(it) }
                ?: ScreenRead.Unavailable(NO_APP)
        }

        /** The app in front as data, or null (service off, nothing readable). */
        fun snapshot(): ScreenSnapshot? = instance?.let { s -> runCatching { s.read()?.first }.getOrNull() }

        /** Taps exactly [element] (as read a moment ago); false when it is gone or does not react. */
        fun tap(element: ScreenElement): Boolean = instance?.let { s -> runCatching { s.tapElement(element) }.getOrDefault(false) } ?: false

        /** Writes [text] into the input box [element]; never into a password box. */
        fun type(element: ScreenElement, text: String): Boolean = instance?.let { s -> runCatching { s.typeInto(element, text) }.getOrDefault(false) } ?: false

        /** Does [op] on the app in front. Returns (done, message). The engine has already asked the user. */
        fun perform(op: ScreenOp): Pair<Boolean, String> {
            val service = instance ?: return false to NOT_ON
            return runCatching { service.doPerform(op) }.getOrElse { Outcome(false, "स्क्रीन पर काम नहीं हो पाया: ${it.javaClass.simpleName}") }
                .let { it.ok to it.message }
        }

        const val NOT_ON = "Seema की Accessibility चालू नहीं है। फ़ोन की Settings → Accessibility → Installed apps → Seema AI → On कीजिए।"
    }
}
