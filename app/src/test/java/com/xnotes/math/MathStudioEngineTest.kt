package com.xnotes.math

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class MathStudioEngineTest {
    @Test fun evaluatesWithPrecedenceAndFunctions() {
        assertEquals(14.0, MathStudioEngine.evaluate("2 + 3 * 4"), 1e-9)
        assertEquals(1.0, MathStudioEngine.evaluate("sin(pi / 2)"), 1e-9)
        assertEquals(9.0, MathStudioEngine.evaluate("3x", x = 3.0), 1e-9)
    }
    @Test fun solvesSimpleQuadraticRoots() {
        val roots = MathStudioEngine.solveEquation("x^2 - 4 = 0")
        assertEquals(2, roots.size)
        assertEquals(-2.0, roots[0], 1e-3)
        assertEquals(2.0, roots[1], 1e-3)
    }
    @Test fun computesNumericalCalculus() {
        assertEquals(6.0, MathStudioEngine.derivative("x^2", 3.0), 1e-4)
        assertEquals(0.5, MathStudioEngine.integrate("x", 0.0, 1.0), 1e-8)
    }
    @Test fun samplesGraph() {
        val points = MathStudioEngine.graph("x^2", -2.0, 2.0)
        assertTrue(points.size > 300)
        assertEquals(0.0, points.minBy { it.y }.y, 1e-8)
    }
}
