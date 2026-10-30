package com.raju.aiassistant.core.power

import android.app.ActivityManager
import android.content.Context
import android.os.BatteryManager
import android.os.Environment
import android.os.StatFs

/**
 * Real phone status for the holographic HUD. Every value comes from an Android API at the moment
 * it is read; anything Android does not provide stays null and is shown as "N/A".
 */
data class DeviceStats(
    val batteryPercent: Int?,
    val charging: Boolean?,
    val ramUsedGb: Float?,
    val ramTotalGb: Float?,
    val storageFreeGb: Float?,
    val storageTotalGb: Float?,
    val cpuCores: Int,
) {
    /** Apps cannot read the CPU load on Android 8+, so it is never shown as a number. */
    val cpuLoadAvailable: Boolean get() = false

    companion object {
        private const val GB = 1024f * 1024f * 1024f

        fun read(context: Context): DeviceStats {
            val battery = context.getSystemService(BatteryManager::class.java)
            val am = context.getSystemService(ActivityManager::class.java)
            val mem = ActivityManager.MemoryInfo().also { info -> runCatching { am?.getMemoryInfo(info) } }
            val fs = runCatching { StatFs(Environment.getDataDirectory().path) }.getOrNull()
            return DeviceStats(
                batteryPercent = battery?.getIntProperty(BatteryManager.BATTERY_PROPERTY_CAPACITY)?.takeIf { it in 0..100 },
                charging = battery?.isCharging,
                ramUsedGb = mem.totalMem.takeIf { it > 0 }?.let { (it - mem.availMem) / GB },
                ramTotalGb = mem.totalMem.takeIf { it > 0 }?.let { it / GB },
                storageFreeGb = fs?.let { it.availableBytes / GB },
                storageTotalGb = fs?.let { it.totalBytes / GB },
                cpuCores = Runtime.getRuntime().availableProcessors(),
            )
        }

        /** Light phones (or Battery Saver) get the Arc Reactor in Auto mode, others the 3D core. */
        fun prefersLightCore(context: Context): Boolean {
            val am = context.getSystemService(ActivityManager::class.java) ?: return true
            val mem = ActivityManager.MemoryInfo().also { am.getMemoryInfo(it) }
            return am.isLowRamDevice || mem.totalMem < 3L * 1024 * 1024 * 1024
        }
    }
}
