package com.raju.aiassistant.notifications

import android.app.admin.DeviceAdminReceiver
import android.app.admin.DevicePolicyManager
import android.content.ComponentName
import android.content.Context
import android.content.Intent

/**
 * The one device-admin power Seema uses: lock the screen (DevicePolicyManager.lockNow). Unlike the
 * accessibility lock it works from the background and needs no accessibility service — "screen lock
 * karo" while another app is open, or straight after the wake word, still locks the phone. The screen
 * is only ever LOCKED here; opening it again always needs the phone's own fingerprint / PIN / pattern.
 * Seema never stores or enters that. The user grants this in Settings and can revoke it any time.
 */
class SeemaDeviceAdmin : DeviceAdminReceiver()

object DeviceLock {
    private fun component(context: Context) = ComponentName(context, SeemaDeviceAdmin::class.java)
    private fun dpm(context: Context) = context.getSystemService(Context.DEVICE_POLICY_SERVICE) as? DevicePolicyManager

    fun isActive(context: Context): Boolean =
        runCatching { dpm(context)?.isAdminActive(component(context)) == true }.getOrDefault(false)

    /** Locks the screen now. Returns false when the admin power is not granted or the call failed. */
    fun lockNow(context: Context): Boolean {
        val dpm = dpm(context) ?: return false
        if (!dpm.isAdminActive(component(context))) return false
        return runCatching { dpm.lockNow(); true }.getOrDefault(false)
    }

    /** The system screen that asks the user to grant (or shows) the lock power. */
    fun requestIntent(context: Context): Intent =
        Intent(DevicePolicyManager.ACTION_ADD_DEVICE_ADMIN)
            .putExtra(DevicePolicyManager.EXTRA_DEVICE_ADMIN, component(context))
            .putExtra(
                DevicePolicyManager.EXTRA_ADD_EXPLANATION,
                "इससे Seema आपके कहने पर ('screen lock karo') फ़ोन लॉक कर पाएगी — बैकग्राउंड में भी। " +
                    "फ़ोन खोलने के लिए आपका अपना fingerprint / PIN / pattern ही चलेगा; Seema उसे कभी सेव या दर्ज नहीं करती।",
            )
}
