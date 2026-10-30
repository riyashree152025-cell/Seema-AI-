package com.raju.aiassistant.domain.power

import com.raju.aiassistant.ui.home.visuals.motionProfileFor
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class PowerTest {
    @Test
    fun androidThermalStatusMapsToFourLevels() {
        assertEquals(ThermalLevel.NORMAL, thermalLevelFor(0))
        assertEquals(ThermalLevel.WARM, thermalLevelFor(1))
        assertEquals(ThermalLevel.WARM, thermalLevelFor(2))
        assertEquals(ThermalLevel.HOT, thermalLevelFor(3))
        assertEquals(ThermalLevel.CRITICAL, thermalLevelFor(4))
        assertEquals(ThermalLevel.CRITICAL, thermalLevelFor(6))
    }

    @Test
    fun screenGoesIdleThenSleepsOnlyWhenNothingHappens() {
        assertEquals(ActivityPhase.ACTIVE, activityPhaseFor(smartPower = true, busy = false, quietForMs = 5_000))
        assertEquals(ActivityPhase.IDLE, activityPhaseFor(true, false, IDLE_AFTER_MS))
        assertEquals(ActivityPhase.SLEEP, activityPhaseFor(true, false, SLEEP_AFTER_MS))
        assertEquals(ActivityPhase.ACTIVE, activityPhaseFor(true, busy = true, quietForMs = SLEEP_AFTER_MS)) // talking: never idle
        assertEquals(ActivityPhase.ACTIVE, activityPhaseFor(smartPower = false, busy = false, quietForMs = SLEEP_AFTER_MS))
    }

    @Test
    fun heatAndIdleOnlyEverReduceTheAnimationWork() {
        val normal = motionProfileFor(false, false, true)
        val warm = motionProfileFor(false, false, true, ThermalLevel.WARM)
        val hot = motionProfileFor(false, false, true, ThermalLevel.HOT)
        val critical = motionProfileFor(false, false, true, ThermalLevel.CRITICAL)
        assertTrue(warm.maxFps < normal.maxFps && warm.particleCount < normal.particleCount)
        assertTrue(hot.maxFps < warm.maxFps && hot.particleCount == 0 && !hot.richEffects && hot.backgroundDensity == 0f)
        assertFalse(critical.animate)

        val idle = motionProfileFor(false, false, true, phase = ActivityPhase.IDLE)
        assertTrue(idle.animate && idle.maxFps <= 20)
        // Sleep only slows the reactor down: it keeps turning and beating, it never freezes.
        val sleep = motionProfileFor(false, false, true, phase = ActivityPhase.SLEEP)
        assertTrue(sleep.animate && sleep.maxFps <= 12 && !sleep.richEffects)
        // Battery saver + hot + idle together never exceed the smallest limit.
        val all = motionProfileFor(true, false, true, ThermalLevel.HOT, ActivityPhase.IDLE)
        assertTrue(all.maxFps <= 8)
    }
}
