package com.xnotes.core.stroke

import com.xnotes.core.geometry.Pt
import com.xnotes.core.model.Stroke
import kotlin.math.PI
import kotlin.math.atan2
import kotlin.math.ceil
import kotlin.math.hypot
import kotlin.math.max

/** A robust dominant writing axis estimated from a bounded sample of the selected strokes. */
data class HandwritingAxis(val center: Pt, val angle: Double)
data class HandwritingLine(val strokes: List<Stroke>, val axis: HandwritingAxis)

object HandwritingPolish {
    private const val MAX_AXIS_POINTS = 4096
    private const val CORNER_COS = 0.6427876096865394 // cos(50°)
    private const val SMOOTH_MIX = 0.55
    private const val PASSES = 2

    /**
     * Estimate the selected handwriting's dominant baseline. [mapPoint] converts each stroke's
     * local sample into the shared coordinate space in which all selected strokes should align.
     */
    fun principalAxis(
        strokes: List<Stroke>,
        mapPoint: (Stroke, Pt) -> Pt,
    ): HandwritingAxis? {
        if (strokes.isEmpty()) return null
        val perStrokeBudget = max(2, MAX_AXIS_POINTS / strokes.size)
        val points = ArrayList<Pt>(minOf(MAX_AXIS_POINTS, strokes.size * perStrokeBudget))
        for (stroke in strokes) {
            val count = stroke.sampleCount
            if (count < 2) continue
            val stride = max(1, ceil(count.toDouble() / perStrokeBudget).toInt())
            var lastAdded = -1
            var i = 0
            while (i < count) {
                points.add(mapPoint(stroke, Pt(stroke.xAt(i), stroke.yAt(i))))
                lastAdded = i
                i += stride
            }
            if (lastAdded != count - 1) {
                points.add(mapPoint(stroke, Pt(stroke.xAt(count - 1), stroke.yAt(count - 1))))
            }
        }
        if (points.size < 2) return null

        var meanX = 0.0
        var meanY = 0.0
        for (p in points) { meanX += p.x; meanY += p.y }
        meanX /= points.size
        meanY /= points.size

        var xx = 0.0
        var yy = 0.0
        var xy = 0.0
        for (p in points) {
            val dx = p.x - meanX
            val dy = p.y - meanY
            xx += dx * dx
            yy += dy * dy
            xy += dx * dy
        }
        if (xx + yy < 1e-8) return null

        var angle = 0.5 * atan2(2.0 * xy, xx - yy)
        // Choose the nearest horizontal baseline; a vertical principal axis is equivalent after
        // a quarter turn, while ordinary handwriting only needs a small correction.
        while (angle > PI / 4.0) angle -= PI / 2.0
        while (angle < -PI / 4.0) angle += PI / 2.0
        return HandwritingAxis(Pt(meanX, meanY), angle)
    }

    /**
     * Groups selected strokes into separate writing lines and estimates each line's baseline.
     * This avoids rotating a multi-line paragraph as one rigid block.
     */
    fun lineAxes(
        strokes: List<Stroke>,
        mapPoint: (Stroke, Pt) -> Pt,
    ): List<HandwritingLine> {
        data class Bounds(val stroke: Stroke, val centerY: Double, val centerX: Double, val height: Double)
        val bounds = strokes.mapNotNull { stroke ->
            if (stroke.sampleCount < 2) return@mapNotNull null
            var minX = Double.POSITIVE_INFINITY
            var maxX = Double.NEGATIVE_INFINITY
            var minY = Double.POSITIVE_INFINITY
            var maxY = Double.NEGATIVE_INFINITY
            for (i in 0 until stroke.sampleCount) {
                val point = mapPoint(stroke, Pt(stroke.xAt(i), stroke.yAt(i)))
                minX = minOf(minX, point.x)
                maxX = maxOf(maxX, point.x)
                minY = minOf(minY, point.y)
                maxY = maxOf(maxY, point.y)
            }
            Bounds(stroke, (minY + maxY) / 2.0, (minX + maxX) / 2.0, maxY - minY)
        }
        if (bounds.isEmpty()) return emptyList()
        // Project stroke centres onto the normal of the overall writing direction. This keeps
        // long, slightly sloped lines together even when their strokes begin at different x positions.
        val overallAngle = principalAxis(strokes, mapPoint)?.angle ?: 0.0
        val sinAngle = kotlin.math.sin(overallAngle)
        val cosAngle = kotlin.math.cos(overallAngle)
        val ordered = bounds.map { it.copy(centerY = -it.centerX * sinAngle + it.centerY * cosAngle) }.sortedBy { it.centerY }
        val heights = ordered.map { it.height }.filter { it > 1.0 }.sorted()
        val typicalHeight = heights.getOrNull((heights.size * 3 / 4).coerceAtMost(heights.lastIndex)) ?: 20.0
        val lineTolerance = (typicalHeight * 1.6).coerceIn(10.0, 40.0)
        val clusters = mutableListOf<MutableList<Bounds>>()
        val centers = mutableListOf<Double>()
        for (entry in ordered) {
            val nearest = centers.indices.minByOrNull { kotlin.math.abs(centers[it] - entry.centerY) }
            if (nearest != null && kotlin.math.abs(centers[nearest] - entry.centerY) <= lineTolerance) {
                val cluster = clusters[nearest]
                cluster.add(entry)
                centers[nearest] = cluster.map { it.centerY }.average()
            } else {
                clusters.add(mutableListOf(entry))
                centers.add(entry.centerY)
            }
        }
        return clusters.mapNotNull { cluster ->
            val members = cluster.map { it.stroke }
            principalAxis(members, mapPoint)?.let { HandwritingLine(members, it) }
        }
    }

    /**
     * Gently smooths local tremor while retaining the user's own stroke path, pressure and timing.
     * Strong corners and endpoints are preserved; this never performs handwriting-to-text OCR.
     */
    fun beautify(samples: List<Sample>): List<Sample> {
        if (samples.size < 5) return samples
        var current = samples.toList()
        repeat(PASSES) {
            val next = current.toMutableList()
            for (i in 1 until current.lastIndex) {
                val before = current[i - 1]
                val point = current[i]
                val after = current[i + 1]
                val ax = point.x - before.x
                val ay = point.y - before.y
                val bx = after.x - point.x
                val by = after.y - point.y
                val al = hypot(ax, ay)
                val bl = hypot(bx, by)
                if (al < 1e-6 || bl < 1e-6) continue
                val cosine = ((ax * bx + ay * by) / (al * bl)).coerceIn(-1.0, 1.0)
                if (cosine < CORNER_COS) continue

                val targetX = (before.x + 2.0 * point.x + after.x) / 4.0
                val targetY = (before.y + 2.0 * point.y + after.y) / 4.0
                next[i] = Sample(
                    point.x + (targetX - point.x) * SMOOTH_MIX,
                    point.y + (targetY - point.y) * SMOOTH_MIX,
                    point.pressure,
                    point.t,
                )
            }
            current = next
        }
        return current
    }
}
