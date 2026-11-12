package com.xnotes.core.model

import com.xnotes.core.geometry.Pt
import com.xnotes.core.shapes.ShapeKind
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.math.sqrt

class CenterRadiusGuideTest {
    private val ink = Rgba(30, 90, 180)

    @Test fun circleUsesItsTrueCentreAndRadius() {
        val shape = ShapeItem(ShapeKind.CIRCLE, Pt(0.0, 0.0), Pt(100.0, 100.0), ink)
        val guide = shape.centerRadiusGuide()
        assertNotNull(guide)
        assertEquals(Pt(50.0, 50.0), guide!!.first)
        assertEquals(50.0, guide.first.distanceTo(guide.second), 0.35)
    }

    @Test fun rectangleUsesAreaCentroidAndFarthestCorner() {
        val shape = ShapeItem(ShapeKind.RECTANGLE, Pt(0.0, 0.0), Pt(100.0, 50.0), ink)
        val guide = shape.centerRadiusGuide()
        assertNotNull(guide)
        assertEquals(Pt(50.0, 25.0), guide!!.first)
        assertEquals(sqrt(3125.0), guide.first.distanceTo(guide.second), 1e-6)
    }

    @Test fun rotatedClosedShapeKeepsItsGuideInPageSpace() {
        val shape = ShapeItem(ShapeKind.RECTANGLE, Pt(0.0, 0.0), Pt(100.0, 50.0), ink)
        shape.rotation = Math.PI / 4.0
        val guide = shape.centerRadiusGuide()
        assertNotNull(guide)
        assertEquals(Pt(50.0, 25.0), guide!!.first)
        assertEquals(sqrt(3125.0), guide.first.distanceTo(guide.second), 1e-6)
    }

    @Test fun openShapesDoNotOfferACentreRadiusGuide() {
        val shape = ShapeItem(ShapeKind.LINE, Pt(0.0, 0.0), Pt(100.0, 50.0), ink)
        assertNull(shape.centerRadiusGuide())
        assertFalse(shape.showCenterRadius)
    }

    @Test fun theGuideIsTransientAndOffByDefault() {
        val shape = ShapeItem(ShapeKind.CIRCLE, Pt(0.0, 0.0), Pt(100.0, 100.0), ink)
        assertTrue(shape.centerRadiusGuide() != null)
        assertFalse(shape.showCenterRadius)
    }
}
