package com.xnotes.canvas

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class LaserTrailTimingTest {
    @Test fun trailStaysFullyVisibleUntilItsConfiguredFadeTime() {
        assertEquals(1.0, LaserTrailTiming.alpha(999L, 1000L, 400L), 0.0)
        assertEquals(1.0, LaserTrailTiming.alpha(1000L, 1000L, 400L), 0.0)
        assertFalse(LaserTrailTiming.expired(1399L, 1000L, 400L))
    }

    @Test fun trailFadesSmoothlyAndIsRemovedAtTheEnd() {
        assertEquals(0.5625, LaserTrailTiming.alpha(1100L, 1000L, 400L), 1e-9)
        assertEquals(0.25, LaserTrailTiming.alpha(1200L, 1000L, 400L), 1e-9)
        assertEquals(0.0, LaserTrailTiming.alpha(1400L, 1000L, 400L), 0.0)
        assertTrue(LaserTrailTiming.expired(1400L, 1000L, 400L))
    }

    @Test fun zeroLengthFadeExpiresImmediatelyAtTheFadeTime() {
        assertEquals(1.0, LaserTrailTiming.alpha(999L, 1000L, 0L), 0.0)
        assertEquals(0.0, LaserTrailTiming.alpha(1000L, 1000L, 0L), 0.0)
        assertTrue(LaserTrailTiming.expired(1000L, 1000L, 0L))
    }
}
