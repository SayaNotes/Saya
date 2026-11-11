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

    @Test fun resizingSnapsWidthAndHeightToAnotherShape() {
        val result = ShapeAlignment.snapDrag(
            Pt(0.0, 0.0),
            Pt(96.0, 96.0),
            listOf(Rect(200.0, 200.0, 100.0, 100.0)),
            6.0,
            matchDimensions = true,
        )

        assertEquals(Pt(100.0, 100.0), result.end)
        assertTrue("matching dimensions should show guides even when boxes are apart", result.guides.isNotEmpty())
    }

    @Test fun resizingMatchesWidthIndependentlyAndShowsBothBoxes() {
        val target = Rect(200.0, 200.0, 100.0, 60.0)
        val result = ShapeAlignment.snapDrag(
            Pt(0.0, 0.0), Pt(96.0, 30.0), listOf(target), 6.0, matchDimensions = true,
        )

        assertEquals(Pt(100.0, 30.0), result.end)
        assertTrue(result.guides.any { it.vertical && it.coordinate == 200.0 })
        assertTrue(result.guides.any { it.vertical && it.coordinate == 300.0 })
    }

    @Test fun resizeDimensionMatchWinsOverNearbyEdgeMatch() {
        val result = ShapeAlignment.snapDrag(
            Pt(0.0, 0.0), Pt(98.0, 40.0),
            listOf(Rect(99.0, 200.0, 100.0, 60.0)),
            6.0,
            matchDimensions = true,
        )

        // The target's left edge is closer than the 2px width difference, but resize
        // should still magnetize the dragged edge to the matching 100px width.
        assertEquals(100.0, result.end.x, 0.0)
        assertTrue(result.guides.any { it.vertical && it.coordinate == 100.0 })
    }

    @Test fun resizingMatchesHeightIndependentlyAndShowsBothBoxes() {
        val target = Rect(200.0, 200.0, 60.0, 100.0)
        val result = ShapeAlignment.snapDrag(
            Pt(0.0, 0.0), Pt(30.0, 96.0), listOf(target), 6.0, matchDimensions = true,
        )

        assertEquals(Pt(30.0, 100.0), result.end)
        assertTrue(result.guides.any { !it.vertical && it.coordinate == 200.0 })
        assertTrue(result.guides.any { !it.vertical && it.coordinate == 300.0 })
    }

    @Test fun leftAndRightResizeSnappingNeverChangesHeight() {
        val result = ShapeAlignment.snapResizeHandle(
            anchor = Pt(200.0, 50.0),
            moving = Pt(95.0, 50.0),
            targets = listOf(Rect(300.0, 0.0, 100.0, 100.0)),
            tolerance = 6.0,
            handle = HandleId.L,
        )

        assertEquals(Pt(100.0, 50.0), result.end)
        assertTrue(result.guides.isNotEmpty())
        assertTrue(result.guides.all { it.vertical })
    }

    @Test fun topAndBottomResizeSnappingNeverChangesWidth() {
        val result = ShapeAlignment.snapResizeHandle(
            anchor = Pt(50.0, 200.0),
            moving = Pt(50.0, 95.0),
            targets = listOf(Rect(0.0, 300.0, 100.0, 100.0)),
            tolerance = 6.0,
            handle = HandleId.T,
        )

        assertEquals(Pt(50.0, 100.0), result.end)
        assertTrue(result.guides.isNotEmpty())
        assertTrue(result.guides.none { it.vertical })
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

    @Test fun gridSnapMovesDraggedEdgeToNearestGridLine() {
        val result = ShapeAlignment.snapGrid(Pt(10.0, 10.0), Pt(63.0, 126.0), 64.0, 5.0)
        assertEquals(Pt(64.0, 128.0), result.end)
        assertTrue(result.guides.any { it.vertical })
        assertTrue(result.guides.any { !it.vertical })
    }

    @Test fun gridSnapLeavesCoordinatesOutsideToleranceUntouched() {
        val end = Pt(59.0, 123.0)
        val result = ShapeAlignment.snapGrid(Pt.ZERO, end, 64.0, 4.0)
        assertEquals(end, result.end)
        assertTrue(result.guides.isEmpty())
    }

}
