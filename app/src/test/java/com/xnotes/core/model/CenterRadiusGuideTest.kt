package com.xnotes.core.model

import com.xnotes.core.geometry.Pt
import com.xnotes.core.shapes.ShapeKind
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.math.sqrt

class CenterRadiusGuideTest {
    private val ink = Rgba(30, 90, 180)

    @Test fun circleUsesItsTrueCentreAndRadius() {
        val shape = ShapeItem(ShapeKind.CIRCLE, Pt(0.0, 0.0), Pt(100.0, 100.0), ink)
        val (center, rim) = shape.centerRadiusGuide() ?: error("circle guide missing")
        assertEquals(50.0, center.x, 1e-6)
        assertEquals(50.0, center.y, 1e-6)
        assertEquals(50.0, center.distanceTo(rim), 0.35)
    }

    @Test fun rectangleUsesAreaCentroidAndFarthestCorner() {
        val shape = ShapeItem(ShapeKind.RECTANGLE, Pt(0.0, 0.0), Pt(100.0, 50.0), ink)
        val (center, rim) = shape.centerRadiusGuide() ?: error("rectangle guide missing")
        assertEquals(50.0, center.x, 1e-6)
        assertEquals(25.0, center.y, 1e-6)
        assertEquals(sqrt(3125.0), center.distanceTo(rim), 1e-6)
    }

    @Test fun rotatedClosedShapeKeepsItsGuideInPageSpace() {
        val shape = ShapeItem(ShapeKind.RECTANGLE, Pt(0.0, 0.0), Pt(100.0, 50.0), ink)
        shape.rotation = Math.PI / 4.0
        val (center, rim) = shape.centerRadiusGuide() ?: error("rotated guide missing")
        assertEquals(Pt(50.0, 25.0), center)
        assertEquals(sqrt(3125.0), center.distanceTo(rim), 1e-6)
    }

    @Test fun openShapesDoNotOfferACentreRadiusGuide() {
        val shape = ShapeItem(ShapeKind.LINE, Pt(0.0, 0.0), Pt(100.0, 50.0), ink)
        assertNull(shape.centerRadiusGuide())
        assertFalse(shape.showCenter)
        assertFalse(shape.showRadius)
    }

    @Test fun theGuideIsTransientAndOffByDefault() {
        val shape = ShapeItem(ShapeKind.CIRCLE, Pt(0.0, 0.0), Pt(100.0, 100.0), ink)
        assertTrue(shape.centerRadiusGuide() != null)
        assertFalse(shape.showCenter)
        assertFalse(shape.showRadius)
    }
}
