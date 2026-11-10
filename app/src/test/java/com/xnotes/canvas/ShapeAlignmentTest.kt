package com.xnotes.canvas

import com.xnotes.core.geometry.Pt
import com.xnotes.core.geometry.Rect
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class ShapeAlignmentTest {
    @Test fun translationSnapsEdgesWithoutChangingShapeDimensions() {
        val candidate = Rect(10.0, 10.0, 20.0, 30.0)
        val target = Rect(35.0, 5.0, 20.0, 40.0)
        val result = ShapeAlignment.snapTranslation(candidate, listOf(target), 6.0)

        assertEquals(Pt(5.0, 0.0), result.delta)
        assertEquals(20.0, candidate.w, 0.0)
        assertEquals(30.0, candidate.h, 0.0)
        assertTrue(result.guides.any { it.vertical })
    }

    @Test fun equalSizeRectanglesShowBothEdgeAndCentreGuides() {
        val candidate = Rect(10.0, 10.0, 20.0, 20.0)
        val target = Rect(15.0, 15.0, 20.0, 20.0)
        val result = ShapeAlignment.snapTranslation(candidate, listOf(target), 6.0)

        assertEquals(Pt(5.0, 5.0), result.delta)
        assertEquals(3, result.guides.count { it.vertical })
        assertEquals(3, result.guides.count { !it.vertical })
    }

    @Test fun dragSnapsTheMovingEdgesAndKeepsTheAnchorFixed() {
        val result = ShapeAlignment.snapDrag(
            Pt(0.0, 0.0),
            Pt(95.0, 95.0),
            listOf(Rect(100.0, 100.0, 50.0, 50.0)),
            6.0,
        )

        assertEquals(Pt(100.0, 100.0), result.end)
        assertTrue(result.guides.any { it.vertical })
        assertTrue(result.guides.any { !it.vertical })
    }

    @Test fun nearbyFixedAnchorDoesNotBlockSnappingTheDraggedEdge() {
        val result = ShapeAlignment.snapDrag(
            Pt(99.0, 0.0),
            Pt(194.0, 40.0),
            listOf(
                Rect(100.0, -10.0, 20.0, 100.0),
                Rect(200.0, -10.0, 20.0, 100.0),
            ),
            6.0,
        )

        // The fixed x anchor is already within tolerance of x=100, but the moving x edge
        // should still snap to x=200 rather than choosing a zero correction at the anchor.
        assertEquals(200.0, result.end.x, 0.0)
    }

    @Test fun centreAlignmentSnapsTheCentreRatherThanTheAnchor() {
        val result = ShapeAlignment.snapDrag(
            Pt(0.0, 0.0),
            Pt(96.0, 96.0),
            listOf(Rect(20.0, 20.0, 60.0, 60.0)),
            6.0,
        )

        assertEquals(Pt(100.0, 100.0), result.end)
    }
}
