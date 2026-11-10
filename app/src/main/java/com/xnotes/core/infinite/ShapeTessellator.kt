package com.xnotes.core.infinite

import com.xnotes.core.vector.VectorContour

import com.xnotes.core.geometry.Pt
import com.xnotes.core.geometry.Rect
import com.xnotes.core.model.ConnectorItem
import com.xnotes.core.model.Rgba
import com.xnotes.core.model.ShapeItem
import com.xnotes.core.model.ShapeLabel
import com.xnotes.core.pal.FillRule
import com.xnotes.core.pal.Pen
import com.xnotes.core.pal.FontFace
import com.xnotes.core.pal.FontSpec
import com.xnotes.core.pal.HAlign
import com.xnotes.core.pal.TextFlags
import com.xnotes.core.pal.TextMeasurer
import com.xnotes.core.shapes.PathRole
import com.xnotes.core.shapes.ShapePainter
import com.xnotes.core.shapes.ShapePath
import com.xnotes.core.vector.PathFlattener
import com.xnotes.core.vector.Triangulator
import kotlin.math.abs
import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.sin
import kotlin.math.hypot

/**
 * Turns a [ShapeItem] into triangles: the fills first, then the outline over them, each with its
 * own colour, exactly as the paged renderer paints the same [ShapePath]s.
 *
 * Curves are flattened at the canvas tolerance, so a circle stays round at the deepest zoom. The
 * outline is a constant-width ribbon with a disc wherever it really turns, which draws the same
 * silhouette a round-capped, round-joined pen would without a disc on every step of a curve. A
 * convex fill is a fan; anything else ear-clips, or falls back to a stencil when it is too big or
 * too tangled for that.
 *
 * Pure Kotlin, so the geometry is unit-testable without a canvas.
 */
object ShapeTessellator {

    fun tessellate(shape: ShapeItem, tolerance: Double = StrokeTessellator.DEFAULT_TOLERANCE): List<MeshPart> =
        tessellate(shape.paths(), shape.fillRgba, shape.pen(), tolerance)

    /** A connector's line and heads, drawn like an open shape's. */
    fun tessellate(connector: ConnectorItem, tolerance: Double = StrokeTessellator.DEFAULT_TOLERANCE): List<MeshPart> =
        tessellate(connector.paths(), null, connector.pen(), tolerance)

    /** [paths] filled with [fill] when there is one, then stroked with [pen]: a shape's parts. */
    internal fun tessellate(paths: List<ShapePath>, fill: Rgba?, pen: Pen, tolerance: Double): List<MeshPart> {
        val flat = flatten(paths, tolerance)
        val parts = ArrayList<MeshPart>(2)
        for (path in flat) {
            val color = ShapePainter.fillColor(path.role, fill, pen.color) ?: continue
            fillPart(path, color)?.let(parts::add)
        }
        val outline = outlineMesh(flat, pen, tolerance)
        if (!outline.isEmpty) parts.add(MeshPart(outline, pen.color, InkPass.OPAQUE))
        return parts
    }

    /**
     * Angle markers and labels as ordinary mesh parts, so the infinite GL canvas renders the same
     * annotations as the immediate-mode page renderer. No screen-space overlay or per-frame work:
     * the geometry is built with the shape and follows its normal invalidation/cache lifecycle.
     */
    fun angleParts(
        shape: ShapeItem,
        measurer: TextMeasurer?,
        tolerance: Double = StrokeTessellator.DEFAULT_TOLERANCE,
    ): List<MeshPart> {
        val measure = measurer ?: return emptyList()
        if (!shape.shape.isClosed || shape.shape == com.xnotes.core.shapes.ShapeKind.GLYPHS ||
            shape.shape == com.xnotes.core.shapes.ShapeKind.FILL ||
            shape.shape == com.xnotes.core.shapes.ShapeKind.CIRCLE ||
            shape.shape == com.xnotes.core.shapes.ShapeKind.ELLIPSE
        ) return emptyList()

        val corners = shape.localGeometry().corners.takeIf { it.size >= 3 }
            ?: shape.vertices()?.let { points -> points.map(shape::toLocal) }?.takeIf { it.size >= 3 }
            ?: return emptyList()
        val area = corners.indices.sumOf { i ->
            val a = corners[i]
            val b = corners[(i + 1) % corners.size]
            a.x * b.y - b.x * a.y
        } * 0.5
        if (!area.isFinite() || abs(area) < 1e-6) return emptyList()
        val winding = if (area > 0.0) 1.0 else -1.0
        val ink = shape.strokeRgba
        val markerHalfWidth = maxOf(0.45, shape.strokeWidth * 0.275)
        val font = FontSpec(9.0, FontFace.SANS)
        val flags = TextFlags(wordWrap = false, alignLeft = false, alignTop = false, hAlign = HAlign.CENTER)
        val wrapWidth = 36.0
        val out = ArrayList<MeshPart>(corners.size * 2)

        for (i in corners.indices) {
            val vertex = corners[i]
            val previous = corners[(i - 1 + corners.size) % corners.size]
            val next = corners[(i + 1) % corners.size]
            val prevLength = previous.distanceTo(vertex)
            val nextLength = next.distanceTo(vertex)
            if (prevLength < 1e-3 || nextLength < 1e-3) continue
            val toPrevious = (previous - vertex) / prevLength
            val toNext = (next - vertex) / nextLength
            val dot = (toPrevious.x * toNext.x + toPrevious.y * toNext.y).coerceIn(-1.0, 1.0)
            val cross = toPrevious.x * toNext.y - toPrevious.y * toNext.x
            val small = Math.toDegrees(kotlin.math.acos(dot))
            val reflex = cross * winding > 1e-8
            val degrees = if (reflex) 360.0 - small else small
            if (!degrees.isFinite() || degrees < 1.0 || degrees > 359.0) continue

            val radius = minOf(12.0, prevLength * 0.20, nextLength * 0.20)
            if (radius < 2.0) continue
            val startAngle = atan2(toPrevious.y, toPrevious.x)
            val sweep = -winding * Math.toRadians(degrees)
            val marker = if (abs(degrees - 90.0) <= 1.0) {
                val a = vertex + toPrevious * radius
                val b = a + toNext * radius
                listOf(shape.toPage(a), shape.toPage(b), shape.toPage(vertex + toNext * radius))
            } else {
                (0..12).map { step ->
                    val angle = startAngle + sweep * step / 12.0
                    shape.toPage(vertex + Pt(cos(angle) * radius, sin(angle) * radius))
                }
            }
            val markerBuilder = MeshBuilder()
            markerBuilder.polylineRibbon(marker, markerHalfWidth, false, tolerance)
            if (!markerBuilder.isEmpty) out += MeshPart(markerBuilder.build(), ink, InkPass.OPAQUE)

            val bisector = startAngle + sweep / 2.0
            val labelDistance = radius + 9.0
            val center = shape.toPage(vertex + Pt(cos(bisector) * labelDistance, sin(bisector) * labelDistance))
            val text = "${Math.round(degrees)}°"
            val block = measure.measure(text, font, wrapWidth, flags)
            val top = center.y - block.h / 2.0
            val left = center.x - wrapWidth / 2.0
            val glyphs = measure.outline(text, font, wrapWidth, flags)
            textPart(glyphs, ink) { Pt(left + it.x, top + it.y) }?.let(out::add)
        }
        return out
    }

    /**
     * The label as filled glyphs in its own colour, set where the note sets it and turned with the
     * shape; null without one, or while it is being typed and the editor shows it instead.
     */
    fun labelPart(shape: ShapeItem): MeshPart? {
        if (shape.labelHidden) return null
        return glyphPart(shape.label ?: return null, shape.labelRect() ?: return null, shape::toPage)
    }

    /** A connector's label as filled glyphs, upright where it sits on the line; null without one or while typed. */
    fun labelPart(connector: ConnectorItem): MeshPart? {
        if (connector.labelHidden) return null
        return glyphPart(connector.label ?: return null, connector.labelRect() ?: return null) { it }
    }

    /** [l]'s glyphs set in [frame] and moved onto the page by [toPage], in the label's colour. */
    private fun glyphPart(l: ShapeLabel, frame: Rect, toPage: (Pt) -> Pt): MeshPart? =
        textPart(l.outline(frame.w), l.style.rgba) { toPage(Pt(frame.left + it.x, frame.top + it.y)) }

    /** Glyph outlines as filled triangles in [color], each point put in place by [place]; null for none. */
    fun textPart(glyphs: List<VectorContour>, color: Rgba, place: (Pt) -> Pt): MeshPart? {
        if (glyphs.isEmpty()) return null
        val rings = glyphs.map { c -> PathFlattener.flatten(c, GLYPH_FLATTEN).map(place) }.filter { it.size >= 3 }
        if (rings.isEmpty()) return null
        Triangulator.triangulate(rings, FillRule.NONZERO)?.let { mesh ->
            val b = MeshBuilder(mesh.points.size, mesh.indices.size)
            into(b, mesh)
            if (!b.isEmpty) return MeshPart(b.build(), color, InkPass.OPAQUE)
        }
        return stencilPart(rings, color)
    }

    /** The triangles of every fill the shape's own fill colour paints; empty for open kinds. */
    fun fillMesh(shape: ShapeItem, tolerance: Double): MeshData {
        val b = MeshBuilder()
        for (path in flatten(shape.paths(), tolerance)) {
            if (path.role != PathRole.OUTLINE && path.role != PathRole.FILL_ONLY) continue
            val rings = path.rings()
            if (rings.size == 1 && isConvex(rings[0])) fan(b, rings[0])
            else Triangulator.triangulate(rings, path.rule)?.let { into(b, it) }
        }
        return b.build()
    }

    /** The shape's stroked outline, an arrow's head included. */
    fun outlineMesh(shape: ShapeItem, tolerance: Double): MeshData =
        outlineMesh(flatten(shape.paths(), tolerance), shape.pen(), tolerance)

    private fun outlineMesh(flat: List<FlatPath>, pen: Pen, tolerance: Double): MeshData {
        val b = MeshBuilder()
        val half = pen.width / 2.0
        for (path in flat) {
            val use = ShapePainter.strokePen(path.role, pen) ?: continue
            for (c in path.contours) {
                if (c.points.isEmpty()) continue
                if (!use.dashed || c.points.size < 2) {
                    b.polylineRibbon(c.points, half, c.closed, tolerance, StrokeTessellator.JOIN_DISC_ANGLE)
                    continue
                }
                for (run in MeshBuilder.dashRuns(c.points, use.dashOn, use.dashGap, c.closed)) {
                    b.polylineRibbon(run, half, closed = false, tolerance, StrokeTessellator.JOIN_DISC_ANGLE)
                }
            }
        }
        return b.build()
    }

    private class FlatContour(val points: List<Pt>, val closed: Boolean)

    private class FlatPath(val role: PathRole, val rule: FillRule, val contours: List<FlatContour>) {
        /** Every contour as a ring to fill: an open one fills as if closed, as SVG does. */
        fun rings(): List<List<Pt>> = contours.map { it.points }.filter { it.size >= 3 }
    }

    private fun flatten(paths: List<ShapePath>, tolerance: Double): List<FlatPath> = paths.map { p ->
        FlatPath(p.role, p.fillRule, p.contours.map { FlatContour(PathFlattener.flatten(it, tolerance), it.closed) })
    }

    /** One filled path: a fan when convex, an ear clip when small enough, else a stencil cover. */
    private fun fillPart(path: FlatPath, color: Rgba): MeshPart? {
        val rings = path.rings()
        if (rings.isEmpty()) return null
        if (rings.size == 1 && isConvex(rings[0])) {
            val b = MeshBuilder(rings[0].size, 3 * rings[0].size)
            fan(b, rings[0])
            return if (b.isEmpty) null else MeshPart(b.build(), color, InkPass.OPAQUE)
        }
        if (rings.sumOf { it.size } <= MAX_CLIPPED_VERTICES) {
            Triangulator.triangulate(rings, path.rule)?.let { mesh ->
                val b = MeshBuilder(mesh.points.size, mesh.indices.size)
                into(b, mesh)
                if (!b.isEmpty) return MeshPart(b.build(), color, InkPass.OPAQUE)
            }
        }
        return stencilPart(rings, color)
    }

    /** A fan from one point over every edge covers by parity, which is exact for any outline. */
    private fun stencilPart(rings: List<List<Pt>>, color: Rgba): MeshPart? {
        val b = MeshBuilder(64, 96)
        val pivot = b.vertex(rings[0][0].x, rings[0][0].y)
        for (ring in rings) {
            val first = b.vertex(ring[0].x, ring[0].y)
            var prev = first
            for (k in 1 until ring.size) {
                val v = b.vertex(ring[k].x, ring[k].y)
                b.triangle(pivot, prev, v)
                prev = v
            }
            b.triangle(pivot, prev, first)
        }
        return if (b.isEmpty) null else MeshPart(b.build(), color, InkPass.EVEN_ODD)
    }

    private fun fan(b: MeshBuilder, ring: List<Pt>) {
        val v = IntArray(ring.size) { b.vertex(ring[it].x, ring[it].y) }
        for (i in 1 until ring.size - 1) b.triangle(v[0], v[i], v[i + 1])
    }

    private fun into(b: MeshBuilder, mesh: Triangulator.Mesh) {
        if (mesh.points.isEmpty()) return
        val first = b.vertex(mesh.points[0].x, mesh.points[0].y)
        for (k in 1 until mesh.points.size) b.vertex(mesh.points[k].x, mesh.points[k].y)
        var i = 0
        while (i < mesh.indices.size) {
            b.triangle(first + mesh.indices[i], first + mesh.indices[i + 1], first + mesh.indices[i + 2])
            i += 3
        }
    }

    /** True when [ring] turns one way only and goes round once, so a fan from any vertex tiles it. */
    private fun isConvex(ring: List<Pt>): Boolean {
        val n = ring.size
        if (n < 3) return false
        var sign = 0
        var turning = 0.0
        for (i in 0 until n) {
            val a = ring[i]
            val b = ring[(i + 1) % n]
            val c = ring[(i + 2) % n]
            val ux = b.x - a.x
            val uy = b.y - a.y
            val vx = c.x - b.x
            val vy = c.y - b.y
            val cross = ux * vy - uy * vx
            if (abs(cross) > 1e-12) {
                val s = if (cross > 0.0) 1 else -1
                if (sign == 0) sign = s else if (s != sign) return false
            }
            turning += atan2(cross, ux * vx + uy * vy)
        }
        return sign != 0 && abs(abs(turning) - 2.0 * Math.PI) < 1e-6
    }

    /** Past this many vertices a curved fill takes the stencil rather than a quadratic ear clip. */
    private const val MAX_CLIPPED_VERTICES = 512

    /** Glyph outlines arrive as polylines already; this only guards a host that hands back curves. */
    private const val GLYPH_FLATTEN = 0.05
}
