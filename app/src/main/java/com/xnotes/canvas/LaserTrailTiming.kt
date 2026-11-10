package com.xnotes.canvas

import kotlin.math.PI
import kotlin.math.cos

/**
 * Pure timing for an ephemeral laser trail. It stays fully visible for its configured hold, then
 * gives three progressively softer light pulses before disappearing from the overlay.
 */
internal object LaserTrailTiming {
    fun alpha(nowMs: Long, fadeAtMs: Long, fadeDurationMs: Long): Double {
        if (nowMs < fadeAtMs) return 1.0
        if (fadeDurationMs <= 0L) return 0.0
        val elapsed = (nowMs - fadeAtMs).toDouble()
        val progress = (elapsed / fadeDurationMs.toDouble()).coerceIn(0.0, 1.0)
        val envelope = 1.0 - progress
        val pulse = (1.0 + cos(2.0 * PI * BLINK_CYCLES * progress)) / 2.0
        return (envelope * pulse).coerceIn(0.0, 1.0)
    }

    fun expired(nowMs: Long, fadeAtMs: Long, fadeDurationMs: Long): Boolean =
        nowMs >= fadeAtMs && (fadeDurationMs <= 0L || nowMs - fadeAtMs >= fadeDurationMs)

    private const val BLINK_CYCLES = 3.0
}