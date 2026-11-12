package com.xnotes.core.stroke

import com.xnotes.core.geometry.Pt
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class StrokeStabilizerTest {
    @Test fun disabledStabilizationLeavesInputUntouched() {
        val filter = StrokeStabilizer(0.0)
        val point = Pt(12.5, -7.25)
        assertEquals(point, filter.filter(point, 10L))
        val next = Pt(20.0, 3.0)
        assertEquals(next, filter.filter(next, 26L))
    }

    @Test fun strongStabilizationReducesSlowHandJitter() {
        val filter = StrokeStabilizer(100.0)
        val points = (0..80).map { i ->
            val sign = if (i % 2 == 0) 1.0 else -1.0
            Pt(100.0 + sign * 6.0, 200.0 - sign * 4.0)
        }
        val filtered = points.mapIndexed { i, point -> filter.filter(point, i * 16L) }
        val rawError = points.drop(1).map { it.distanceTo(Pt(100.0, 200.0)) }.average()
        val filteredError = filtered.drop(1).map { it.distanceTo(Pt(100.0, 200.0)) }.average()
        assertTrue("strong stabilization should reduce alternating hand tremor", filteredError < rawError * 0.65)
    }
}
