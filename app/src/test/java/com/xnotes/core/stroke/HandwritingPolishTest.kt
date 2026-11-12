package com.xnotes.core.stroke

import com.xnotes.core.geometry.Pt
import com.xnotes.core.model.Stroke
import com.xnotes.core.tools.Tool
import com.xnotes.core.tools.ToolDefaults
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.math.atan

class HandwritingPolishTest {
    @Test fun beautifyDampsGentleWobbleWithoutChangingPressureOrTime() {
        val input = listOf(
            Sample(0.0, 0.0, 0.2, 0.0),
            Sample(10.0, 0.3, 0.4, 10.0),
            Sample(20.0, -0.3, 0.6, 20.0),
            Sample(30.0, 0.3, 0.8, 30.0),
            Sample(40.0, 0.0, 1.0, 40.0),
        )
        val output = HandwritingPolish.beautify(input)
        assertEquals(input.size, output.size)
        assertTrue(output[2].y in -0.3..0.3)
        assertTrue(kotlin.math.abs(output[2].y) < kotlin.math.abs(input[2].y))
        input.indices.forEach { i ->
            assertEquals(input[i].pressure, output[i].pressure, 0.0)
            assertEquals(input[i].t, output[i].t, 0.0)
        }
        assertEquals(input.first(), output.first())
        assertEquals(input.last(), output.last())
    }

    @Test fun lineAxesKeepsSlopedLinesSeparateAndTogether() {
        fun stroke(x0: Double, y0: Double) = Stroke(
            Tool.PEN,
            ToolDefaults.configFor(Tool.PEN),
            (0..10).map { i -> Sample(x0 + i * 10.0, y0 + i * 1.5, 1.0) },
        )
        val strokes = listOf(
            stroke(0.0, 0.0), stroke(120.0, 18.0),
            stroke(0.0, 100.0), stroke(120.0, 118.0),
        )
        val lines = HandwritingPolish.lineAxes(strokes) { _, point -> point }
        assertEquals(2, lines.size)
        assertTrue(lines.all { it.strokes.size == 2 })
        lines.forEach { assertEquals(atan(0.15), it.axis.angle, 0.02) }
    }

    @Test fun principalAxisFindsSlopedWritingBaseline() {
        val samples = (0..20).map { i -> Sample(i * 10.0, i * 1.5, 1.0) }
        val stroke = Stroke(Tool.PEN, ToolDefaults.configFor(Tool.PEN), samples)
        val axis = HandwritingPolish.principalAxis(listOf(stroke)) { _, p -> p }
        assertNotNull(axis)
        assertEquals(atan(0.15), axis!!.angle, 0.02)
    }
}
