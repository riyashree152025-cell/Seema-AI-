package com.raju.aiassistant.ui.home.visuals

import com.raju.aiassistant.domain.power.ActivityPhase
import com.raju.aiassistant.domain.power.ThermalLevel

/** How much animation work the home screen may do. Tuned for budget phones (Galaxy A14). */
data class MotionProfile(
    /** False = draw a single static frame (reduced motion, deep idle, critical heat). */
    val animate: Boolean,
    val maxFps: Int,
    val particleCount: Int,
    /** Heartbeat shock-waves and the extra outer rings. */
    val richEffects: Boolean,
    /** 0..1 how many code-rain columns and motes the background draws. */
    val backgroundDensity: Float = 0.62f,
)

/**
 * The animation budget from the user's settings, the phone's heat and whether anybody is using the
 * screen. Every input can only make the work smaller, never larger.
 */
fun motionProfileFor(
    lowPowerMode: Boolean,
    reducedMotion: Boolean,
    systemAnimationsEnabled: Boolean,
    thermal: ThermalLevel = ThermalLevel.NORMAL,
    phase: ActivityPhase = ActivityPhase.ACTIVE,
): MotionProfile {
    val still = MotionProfile(animate = false, maxFps = 0, particleCount = 0, richEffects = true, backgroundDensity = 0.35f)
    if (reducedMotion || !systemAnimationsEnabled) return still
    if (thermal == ThermalLevel.CRITICAL) return still.copy(richEffects = false, backgroundDensity = 0f)
    if (phase == ActivityPhase.SLEEP) return still
    var p = if (lowPowerMode) MotionProfile(animate = true, maxFps = 20, particleCount = 12, richEffects = false, backgroundDensity = 0.3f)
    // 30 fps keeps the heartbeat smooth while halving the drawing work (and battery) of 60 fps.
    else MotionProfile(animate = true, maxFps = 30, particleCount = 36, richEffects = true, backgroundDensity = 0.62f)
    if (phase == ActivityPhase.IDLE) {
        p = p.copy(maxFps = minOf(p.maxFps, 12), particleCount = p.particleCount / 2, richEffects = false, backgroundDensity = p.backgroundDensity * 0.5f)
    }
    p = when (thermal) {
        ThermalLevel.WARM -> p.copy(maxFps = minOf(p.maxFps, 15), particleCount = p.particleCount / 2, backgroundDensity = p.backgroundDensity * 0.6f)
        ThermalLevel.HOT -> p.copy(maxFps = minOf(p.maxFps, 8), particleCount = 0, richEffects = false, backgroundDensity = 0f)
        else -> p
    }
    return p
}
