package com.xnotes.canvas

import com.xnotes.core.geometry.Pt
import com.xnotes.core.geometry.Rect
import kotlin.math.abs
import kotlin.math.min
import kotlin.math.max

/**
 * Temporary alignment guides and non-destructive translation/drag snapping for shapes.
 *
 * The helper is pure so its geometry can be tested independently of pointer input and rendering.
 * X features make vertical guides; Y features make horizontal guides.
 */
internal object ShapeAlignment {
    data class Guide(
        val vertical: Boolean,
        val coordinate: Double,
        val from: Double,
        val to: Double,
    )

    data class DragResult(val end: Pt, val guides: List<Guide>)
    data class MoveResult(val delta: Pt, val guides: List<Guide>)

    private data class Match(
        val delta: Double,
        val coordinate: Double,
        val target: Rect,
        val vertical: Boolean,
        val distance: Double,
    )

    private data class DragFeature(val value: Double, val endpointFactor: Double, val priority: Int)

    /** Snap a translated bounding box to the nearest matching edge or centre without resizing it. */
    fun snapTranslation(candidate: Rect, targets: List<Rect>, tolerance: Double): MoveResult {
        if (targets.isEmpty() || tolerance <= 0.0) return MoveResult(Pt.ZERO, emptyList())
        val x = bestMoveMatch(candidate, targets, tolerance, vertical = true)
        val y = bestMoveMatch(candidate, targets, tolerance, vertical = false)
        val dx = x?.delta ?: 0.0
        val dy = y?.delta ?: 0.0
        val placed = candidate.translate(dx, dy)
        return MoveResult(
            Pt(dx, dy),
            listOfNotNull(x?.guide(placed), y?.guide(placed)),
        )
    }

    /**
     * Snap the dragged endpoint of a newly drawn box. The fixed endpoint stays anchored; matching
     * an edge adjusts the dragged edge, while matching the centre doubles that centre correction.
     */
    fun snapDrag(start: Pt, end: Pt, targets: List<Rect>, tolerance: Double): DragResult {
        if (targets.isEmpty() || tolerance <= 0.0) return DragResult(end, emptyList())
        val box = Rect.fromPoints(start, end)
        val x = bestDragMatch(start.x, end.x, box, targets, tolerance, vertical = true)
        val y = bestDragMatch(start.y, end.y, box, targets, tolerance, vertical = false)
        val snappedEnd = Pt(end.x + (x?.delta ?: 0.0), end.y + (y?.delta ?: 0.0))
        val placed = Rect.fromPoints(start, snappedEnd)
        return DragResult(
            snappedEnd,
            listOfNotNull(x?.guide(placed), y?.guide(placed)),
        )
    }

    private fun bestMoveMatch(candidate: Rect, targets: List<Rect>, tolerance: Double, vertical: Boolean): Match? {
        val values = if (vertical) {
            listOf(candidate.left, candidate.centerX, candidate.right)
        } else {
            listOf(candidate.top, candidate.centerY, candidate.bottom)
        }
        var best: Match? = null
        for (value in values) {
            for (target in targets) {
                val targetValues = if (vertical) {
                    listOf(target.left, target.centerX, target.right)
                } else {
                    listOf(target.top, target.centerY, target.bottom)
                }
                for (targetValue in targetValues) {
                    val delta = targetValue - value
                    if (abs(delta) > tolerance) continue
                    if (best == null || abs(delta) < abs(best.delta)) {
                        best = Match(delta, targetValue, target, vertical, abs(delta))
                    }
                }
            }
        }
        return best
    }

    private fun bestDragMatch(
        fixed: Double,
        moving: Double,
        candidate: Rect,
        targets: List<Rect>,
        tolerance: Double,
        vertical: Boolean,
    ): Match? {
        val features = listOf(
            DragFeature(moving, 1.0, 0),
            DragFeature((fixed + moving) / 2.0, 2.0, 1),
            DragFeature(fixed, 0.0, 2),
        )
        var best: Match? = null
        var bestPriority = Int.MAX_VALUE
        for (feature in features) {
            for (target in targets) {
                val values = if (vertical) {
                    listOf(target.left, target.centerX, target.right)
                } else {
                    listOf(target.top, target.centerY, target.bottom)
                }
                for (targetValue in values) {
                    val difference = targetValue - feature.value
                    if (abs(difference) > tolerance) continue
                    if (best == null || abs(difference) < best.distance ||
                        (abs(difference) == best.distance && feature.priority < bestPriority)
                    ) {
                        best = Match(difference * feature.endpointFactor, targetValue, target, vertical, abs(difference))
                        bestPriority = feature.priority
                    }
                }
            }
        }
        return best
    }

    private fun Match.guide(candidate: Rect): Guide = if (vertical) {
        Guide(true, coordinate, min(candidate.top, target.top), max(candidate.bottom, target.bottom))
    } else {
        Guide(false, coordinate, min(candidate.left, target.left), max(candidate.right, target.right))
    }
}
