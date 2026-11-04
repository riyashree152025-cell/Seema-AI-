package com.raju.aiassistant.accessibility

import android.accessibilityservice.AccessibilityService
import android.content.Intent
import android.os.Build
import android.os.Handler
import android.os.Looper
import android.view.accessibility.AccessibilityEvent
import android.view.accessibility.AccessibilityNodeInfo

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
                attemptWhatsAppSend()
            } else if (pendingWhatsAppCall) {
                attemptWhatsAppCall()
            }
        } else if (pendingSpeakerphone && (pkg.contains("incall") || pkg.contains("dialer") || pkg.contains("telecom"))) {
            attemptSpeakerphone()
        } else if (pendingYouTubePlay && pkg == "com.google.android.youtube") {
            attemptYouTubePlay()
        }
    }

    private fun attemptWhatsAppSend() {
        val root = rootInActiveWindow ?: return
        try {
            // 1. By view ID
            val byId = root.findAccessibilityNodeInfosByViewId("com.whatsapp:id/send")
            for (node in byId) {
                if (clickNode(node)) {
                    finishAutoSend()
                    return
                }
            }
            // 2. By content description ("Send", "भेजें", "পাঠান", "bheje")
            val targetDescs = listOf("send", "\u092d\u0947\u091c\u0947\u0902", "\u09aa\u09be\u09a0\u09be\u09a8", "bheje")
            val queue = ArrayDeque<AccessibilityNodeInfo>()
            queue.add(root)
            while (queue.isNotEmpty()) {
                val node = queue.removeFirst()
                val desc = node.contentDescription?.toString()?.lowercase() ?: ""
                if (targetDescs.any { desc.contains(it) }) {
                    if (clickNode(node)) {
                        finishAutoSend()
                        return
                    }
                }
                for (i in 0 until node.childCount) {
                    node.getChild(i)?.let { queue.add(it) }
                }
            }
        } catch (_: Exception) {}
    }

    private fun attemptWhatsAppCall() {
        val root = rootInActiveWindow ?: return
        try {
            // First check if a confirmation dialog is visible (e.g. "Start voice call? Call / Cancel")
            val confirmButtons = root.findAccessibilityNodeInfosByViewId("android:id/button1")
            for (btn in confirmButtons) {
                if (clickNode(btn)) {
                    pendingWhatsAppCall = false
                    return
                }
            }

            // Now look for Voice Call or Video Call buttons in WhatsApp action bar
            val targetIds = if (pendingWhatsAppVideo) {
                listOf("com.whatsapp:id/video_call", "com.whatsapp:id/menuitem_video_call")
            } else {
                listOf("com.whatsapp:id/voice_call", "com.whatsapp:id/menuitem_call", "com.whatsapp:id/audio_call")
            }

            for (id in targetIds) {
                val nodes = root.findAccessibilityNodeInfosByViewId(id)
                for (n in nodes) {
                    if (clickNode(n)) {
                        handler.postDelayed({ attemptDialogConfirm() }, 600)
                        return
                    }
                }
            }

            // Fallback by content descriptions
            val targetDescs = if (pendingWhatsAppVideo) {
                listOf("video call", "\u0935\u0940\u0921\u093f\u092f\u094b \u0915\u0949\u0932", "\u09ad\u09bf\u09a1\u09bf\u0993 \u0995\u09b2")
            } else {
                listOf("voice call", "call", "\u0915\u0949\u0932", "\u09ad\u09af\u09bc\u09c7\u09b8 \u0995\u09b2", "\u0995\u09b2", "audio call")
            }

            val queue = ArrayDeque<AccessibilityNodeInfo>()
            queue.add(root)
            while (queue.isNotEmpty()) {
                val node = queue.removeFirst()
                val desc = node.contentDescription?.toString()?.lowercase() ?: ""
                if (targetDescs.any { desc.contains(it) }) {
                    if (clickNode(node)) {
                        handler.postDelayed({ attemptDialogConfirm() }, 600)
                        return
                    }
                }
                for (i in 0 until node.childCount) {
                    node.getChild(i)?.let { queue.add(it) }
                }
            }
        } catch (_: Exception) {}
    }

    private fun attemptDialogConfirm() {
        val root = rootInActiveWindow ?: return
        val confirm = root.findAccessibilityNodeInfosByViewId("android:id/button1")
        for (btn in confirm) {
            clickNode(btn)
        }
        pendingWhatsAppCall = false
    }

    private fun attemptSpeakerphone() {
        val root = rootInActiveWindow ?: return
        try {
            val speakerKeywords = listOf("speaker", "स्पीकर", "লাউডস্পিকার", "speakerphone")
            val queue = ArrayDeque<AccessibilityNodeInfo>()
            queue.add(root)
            while (queue.isNotEmpty()) {
                val node = queue.removeFirst()
                val desc = (node.contentDescription?.toString() ?: node.text?.toString() ?: "").lowercase()
                if (speakerKeywords.any { desc.contains(it) } && node.isClickable) {
                    clickNode(node)
                    pendingSpeakerphone = false
                    return
                }
                for (i in 0 until node.childCount) {
                    node.getChild(i)?.let { queue.add(it) }
                }
            }
        } catch (_: Exception) {}
    }

    private fun attemptYouTubePlay() {
        val root = rootInActiveWindow ?: return
        try {
            val videoItems = root.findAccessibilityNodeInfosByViewId("com.google.android.youtube:id/thumbnail_layout")
            for (item in videoItems) {
                if (clickNode(item)) {
                    pendingYouTubePlay = false
                    return
                }
            }
        } catch (_: Exception) {}
    }

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

    private fun finishAutoSend() {
        pendingWhatsAppAutoSend = false
        // Return back to Seema AI after brief delay so user stays hands-free
        handler.postDelayed({
            val launchIntent = packageManager.getLaunchIntentForPackage(packageName)?.apply {
                flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_REORDER_TO_FRONT or Intent.FLAG_ACTIVITY_SINGLE_TOP
            }
            if (launchIntent != null) {
                startActivity(launchIntent)
            } else {
                performGlobalAction(GLOBAL_ACTION_HOME)
            }
        }, 1200)
    }

    override fun onInterrupt() {
        pendingWhatsAppAutoSend = false
        pendingWhatsAppCall = false
        pendingSpeakerphone = false
        pendingYouTubePlay = false
    }

    override fun onDestroy() {
        super.onDestroy()
        if (instance === this) instance = null
        pendingWhatsAppAutoSend = false
        pendingWhatsAppCall = false
        pendingSpeakerphone = false
        pendingYouTubePlay = false
    }

    companion object {
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
        var pendingSpeakerphone: Boolean = false
            private set

        @Volatile
        var pendingYouTubePlay: Boolean = false
            private set

        fun isRunning(): Boolean = instance != null

        fun armWhatsAppAutoSend() {
            pendingWhatsAppAutoSend = true
            Handler(Looper.getMainLooper()).postDelayed({
                pendingWhatsAppAutoSend = false
            }, 15_000)
        }

        fun armWhatsAppCall(video: Boolean = false) {
            pendingWhatsAppCall = true
            pendingWhatsAppVideo = video
            Handler(Looper.getMainLooper()).postDelayed({
                pendingWhatsAppCall = false
                pendingWhatsAppVideo = false
            }, 15_000)
        }

        fun armSpeakerphone() {
            pendingSpeakerphone = true
            Handler(Looper.getMainLooper()).postDelayed({
                pendingSpeakerphone = false
            }, 10_000)
        }

        fun armYouTubePlay() {
            pendingYouTubePlay = true
            Handler(Looper.getMainLooper()).postDelayed({
                pendingYouTubePlay = false
            }, 10_000)
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
    }
}
