package com.raju.aiassistant.accessibility

import android.accessibilityservice.AccessibilityService
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
            }
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
            // 2. By content description ("Send", "भेजें", "পাঠান")
            val targetDescs = listOf("send", "भेजें", "পাঠান", "bheje")
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
            performGlobalAction(GLOBAL_ACTION_BACK)
        }, 800)
    }

    override fun onInterrupt() {
        pendingWhatsAppAutoSend = false
    }

    override fun onDestroy() {
        super.onDestroy()
        if (instance === this) instance = null
        pendingWhatsAppAutoSend = false
    }

    companion object {
        @Volatile
        var instance: SeemaAccessibilityService? = null
            private set

        @Volatile
        var pendingWhatsAppAutoSend: Boolean = false
            private set

        fun isRunning(): Boolean = instance != null

        fun armWhatsAppAutoSend() {
            pendingWhatsAppAutoSend = true
            Handler(Looper.getMainLooper()).postDelayed({
                pendingWhatsAppAutoSend = false
            }, 15_000)
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