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

    private data class Match(val delta: Double, val distance: Double)

    private data class DragFeature(val value: Double, val endpointFactor: Double, val priority: Int)

    /** Snap a translated bounding box to the nearest matching edge or centre without resizing it. */
    fun snapTranslation(candidate: Rect, targets: List<Rect>, tolerance: Double): MoveResult {
        if (targets.isEmpty() || tolerance <= 0.0) return MoveResult(Pt.ZERO, emptyList())
        val x = bestMoveMatch(candidate, targets, tolerance, vertical = true)
        val y = bestMoveMatch(candidate, targets, tolerance, vertical = false)
        val dx = x?.delta ?: 0.0
        val dy = y?.delta ?: 0.0
        val placed = candidate.translate(dx, dy)
        return MoveResult(Pt(dx, dy), guidesFor(placed, targets))
    }

    /**
     * Snap the dragged endpoint of a newly drawn box. The fixed endpoint stays anchored; matching
     * an edge adjusts the dragged edge, while matching the centre doubles that centre correction.
     */
    fun snapDrag(
        start: Pt,
        end: Pt,
        targets: List<Rect>,
        tolerance: Double,
        matchDimensions: Boolean = false,
    ): DragResult {
        if (targets.isEmpty() || tolerance <= 0.0) return DragResult(end, emptyList())
        // During a resize, optionally magnetize the resulting width/height as well as edges/centres.
        val xEdge = bestDragMatch(start.x, end.x, targets, tolerance, vertical = true)
        val yEdge = bestDragMatch(start.y, end.y, targets, tolerance, vertical = false)
        val xSize = if (matchDimensions) bestDimensionMatch(start.x, end.x, targets, tolerance, vertical = true) else null
        val ySize = if (matchDimensions) bestDimensionMatch(start.y, end.y, targets, tolerance, vertical = false) else null
        val x = closerMatch(xEdge, xSize)
        val y = closerMatch(yEdge, ySize)
        val snappedEnd = Pt(end.x + (x?.delta ?: 0.0), end.y + (y?.delta ?: 0.0))
        val placed = Rect.fromPoints(start, snappedEnd)
        return DragResult(snappedEnd, guidesFor(placed, targets))
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
                        best = Match(delta, abs(delta))
                    }
                }
            }
        }
        return best
    }

    private fun bestDragMatch(
        fixed: Double,
        moving: Double,
        targets: List<Rect>,
        tolerance: Double,
        vertical: Boolean,
    ): Match? {
        // Only the moving edge and centre may influence the endpoint. The fixed anchor is
        // deliberately excluded: a nearby guide at the anchor must never block a useful snap.
        val features = listOf(
            DragFeature(moving, 1.0, 0),
            DragFeature((fixed + moving) / 2.0, 2.0, 1),
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
                        best = Match(difference * feature.endpointFactor, abs(difference))
                        bestPriority = feature.priority
                    }
                }
            }
        }
        return best
    }

    /** Return every edge/centre guide that is aligned after the chosen snap is applied. */
    private fun guidesFor(candidate: Rect, targets: List<Rect>): List<Guide> {
        val guides = mutableListOf<Guide>()
        fun collect(vertical: Boolean) {
            val candidateValues = if (vertical) {
                listOf(candidate.left, candidate.centerX, candidate.right)
            } else {
                listOf(candidate.top, candidate.centerY, candidate.bottom)
            }
            for (target in targets) {
                val targetValues = if (vertical) {
                    listOf(target.left, target.centerX, target.right)
                } else {
                    listOf(target.top, target.centerY, target.bottom)
                }
                for (candidateValue in candidateValues) for (targetValue in targetValues) {
                    if (abs(candidateValue - targetValue) > GUIDE_EPSILON) continue
                    val guide = if (vertical) {
                        Guide(true, targetValue, min(candidate.top, target.top), max(candidate.bottom, target.bottom))
                    } else {
                        Guide(false, targetValue, min(candidate.left, target.left), max(candidate.right, target.right))
                    }
                    val duplicate = guides.indexOfFirst { it.vertical == guide.vertical && abs(it.coordinate - guide.coordinate) <= GUIDE_EPSILON }
                    if (duplicate < 0) guides.add(guide)
                    else {
                        val previous = guides[duplicate]
                        guides[duplicate] = previous.copy(from = min(previous.from, guide.from), to = max(previous.to, guide.to))
                    }
                }
            }
        }
        collect(vertical = true)
        collect(vertical = false)
        return guides
    }

    private const val GUIDE_EPSILON = 0.5
}
