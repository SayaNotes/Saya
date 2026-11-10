package com.xnotes.canvas

/**
 * Pure timing for an ephemeral laser trail. It stays fully visible for its configured hold, then
 * eases out quadratically over the fade interval before it is removed from the overlay.
 */
internal object LaserTrailTiming {
    fun alpha(nowMs: Long, fadeAtMs: Long, fadeDurationMs: Long): Double {
        if (nowMs < fadeAtMs) return 1.0
        if (fadeDurationMs <= 0L) return 0.0
        val elapsed = (nowMs - fadeAtMs).toDouble()
        val progress = (elapsed / fadeDurationMs.toDouble()).coerceIn(0.0, 1.0)
        val remaining = 1.0 - progress
        return remaining * remaining
    }

    fun expired(nowMs: Long, fadeAtMs: Long, fadeDurationMs: Long): Boolean =
        nowMs >= fadeAtMs && (fadeDurationMs <= 0L || nowMs - fadeAtMs >= fadeDurationMs)
}
