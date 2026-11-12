package com.xnotes.core.stroke

import com.xnotes.core.geometry.Pt
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class StrokeStabilizerTest {

    @Test fun zeroStrengthIsExactPassThrough() {
        val filter = StrokeStabilizer(0.0)
        val points = listOf(Pt(0.0, 0.0), Pt(2.0, 1.0), Pt(4.0, -1.0))
        points.forEachIndexed { i, point ->
            assertEquals(point, filter.filter(point, i * 16L))
        }
    }

    @Test fun strongStabilizationReducesSlowAlternatingJitter() {
        val filter = StrokeStabilizer(100.0)
        val xs = listOf(0.0, 3.0, -3.0, 3.0, -3.0, 3.0, -3.0)
        val out = xs.mapIndexed { i, x -> filter.filter(Pt(x, 0.0), i * 16L).x }
        val rawRange = xs.maxOrNull()!! - xs.minOrNull()!!
        val filteredRange = out.maxOrNull()!! - out.minOrNull()!!
        assertTrue("stabilization should damp slow hand tremor", filteredRange < rawRange)
    }

    @Test fun filterKeepsFiniteCoordinates() {
        val filter = StrokeStabilizer(75.0)
        repeat(100) { i ->
            val p = filter.filter(Pt(i * 1.25, kotlin.math.sin(i / 8.0) * 12.0), i * 8L)
            assertTrue(p.x.isFinite())
            assertTrue(p.y.isFinite())
        }
    }
}
