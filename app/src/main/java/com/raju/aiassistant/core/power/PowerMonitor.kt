package com.raju.aiassistant.core.power

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.os.BatteryManager
import android.os.Build
import android.os.Debug
import android.os.PowerManager
import androidx.core.content.ContextCompat
import com.raju.aiassistant.domain.power.ThermalLevel
import com.raju.aiassistant.domain.power.thermalLevelFor
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/** Real power facts from Android (never estimated): thermal status, battery saver, battery, memory. */
data class PowerState(val thermal: ThermalLevel = ThermalLevel.NORMAL, val thermalStatus: Int = 0, val systemPowerSave: Boolean = false)

/** One on-demand reading for the resource dashboard. Null fields are not available on this phone. */
data class ResourceSnapshot(
    val appMemoryMb: Int,
    val javaHeapUsedMb: Int,
    val javaHeapMaxMb: Int,
    val batteryPercent: Int?,
    val charging: Boolean?,
    val thermalHeadroom: Float?,
)

/**
 * Listens to Android's thermal status (event-driven, no polling) and Battery Saver changes.
 * Thermal status needs Android 10+; older phones always report NORMAL.
 */
class PowerMonitor(context: Context) {
    private val app = context.applicationContext
    private val pm = app.getSystemService(PowerManager::class.java)
    private val _state = MutableStateFlow(PowerState())
    val state: StateFlow<PowerState> = _state.asStateFlow()

    init {
        if (Build.VERSION.SDK_INT >= 29 && pm != null) {
            update(thermalStatus = pm.currentThermalStatus)
            runCatching { pm.addThermalStatusListener(app.mainExecutor) { status -> update(thermalStatus = status) } }
        }
        update(powerSave = pm?.isPowerSaveMode == true)
        val receiver = object : BroadcastReceiver() {
            override fun onReceive(c: Context, i: Intent) { update(powerSave = pm?.isPowerSaveMode == true) }
        }
        runCatching { ContextCompat.registerReceiver(app, receiver, IntentFilter(PowerManager.ACTION_POWER_SAVE_MODE_CHANGED), ContextCompat.RECEIVER_NOT_EXPORTED) }
    }

    private fun update(thermalStatus: Int? = null, powerSave: Boolean? = null) {
        val s = _state.value
        val status = thermalStatus ?: s.thermalStatus
        _state.value = s.copy(thermal = thermalLevelFor(status), thermalStatus = status, systemPowerSave = powerSave ?: s.systemPowerSave)
    }

    /** Read only when the dashboard asks (no background sampling). */
    fun snapshot(): ResourceSnapshot {
        val rt = Runtime.getRuntime()
        val battery = app.getSystemService(BatteryManager::class.java)
        val percent = battery?.getIntProperty(BatteryManager.BATTERY_PROPERTY_CAPACITY)?.takeIf { it in 0..100 }
        val charging = if (Build.VERSION.SDK_INT >= 23) battery?.isCharging else null
        val headroom = if (Build.VERSION.SDK_INT >= 30) pm?.getThermalHeadroom(0)?.takeIf { !it.isNaN() } else null
        return ResourceSnapshot(
            appMemoryMb = (Debug.getPss() / 1024).toInt(),
            javaHeapUsedMb = ((rt.totalMemory() - rt.freeMemory()) / (1024 * 1024)).toInt(),
            javaHeapMaxMb = (rt.maxMemory() / (1024 * 1024)).toInt(),
            batteryPercent = percent,
            charging = charging,
            thermalHeadroom = headroom,
        )
    }
}
