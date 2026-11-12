package com.xnotes.core.stroke

import com.xnotes.core.geometry.Pt
import kotlin.math.PI
import kotlin.math.hypot

/** Adaptive One Euro pointer filter for per-pen hand stabilisation, operating in viewport pixels. */
class StrokeStabilizer(strength: Double) {
    private val level = strength.coerceIn(0.0, 100.0) / 100.0
    private val minCutoff = 8.0 - 7.25 * level
    private val beta = 0.003 + 0.009 * level
    private var initialized = false
    private var lastRaw = Pt.ZERO
    private var filtered = Pt.ZERO
    private var filteredDx = 0.0
    private var filteredDy = 0.0
    private var lastTimeMs = 0L

    fun filter(point: Pt, timeMs: Long): Pt {
        if (level <= 0.0) return point
        if (!initialized) {
            initialized = true; lastRaw = point; filtered = point; lastTimeMs = timeMs
            return point
        }
        val dt = (timeMs - lastTimeMs).coerceIn(1L, 50L) / 1000.0
        val rawDx = (point.x - lastRaw.x) / dt
        val rawDy = (point.y - lastRaw.y) / dt
        val derivativeAlpha = alpha(1.0, dt)
        filteredDx += derivativeAlpha * (rawDx - filteredDx)
        filteredDy += derivativeAlpha * (rawDy - filteredDy)
        val positionAlpha = alpha(minCutoff + beta * hypot(filteredDx, filteredDy), dt)
        filtered = Pt(filtered.x + positionAlpha * (point.x - filtered.x), filtered.y + positionAlpha * (point.y - filtered.y))
        lastRaw = point; lastTimeMs = timeMs
        return filtered
    }

    private fun alpha(cutoff: Double, dt: Double): Double {
        val tau = 1.0 / (2.0 * PI * cutoff.coerceAtLeast(0.01))
        return 1.0 / (1.0 + tau / dt)
    }
}
