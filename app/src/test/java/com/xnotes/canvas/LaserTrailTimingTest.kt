package com.xnotes.canvas

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class LaserTrailTimingTest {
    @Test fun trailStaysFullyVisibleUntilItsConfiguredFadeTime() {
        assertEquals(1.0, LaserTrailTiming.alpha(999L, 1000L, 1500L), 0.0)
        assertEquals(1.0, LaserTrailTiming.alpha(1000L, 1000L, 1500L), 0.0)
        assertFalse(LaserTrailTiming.expired(2499L, 1000L, 1500L))
    }

    @Test fun trailPulsesThreeTimesWithAProgressivelySofterFade() {
        // Each 500ms cycle dims to zero, returns as a softer flash, then dims again.
        assertEquals(0.0, LaserTrailTiming.alpha(1250L, 1000L, 1500L), 1e-9)
        assertEquals(2.0 / 3.0, LaserTrailTiming.alpha(1500L, 1000L, 1500L), 1e-9)
        assertEquals(0.0, LaserTrailTiming.alpha(1750L, 1000L, 1500L), 1e-9)
        assertEquals(1.0 / 3.0, LaserTrailTiming.alpha(2000L, 1000L, 1500L), 1e-9)
        assertEquals(0.0, LaserTrailTiming.alpha(2250L, 1000L, 1500L), 1e-9)
        assertEquals(0.0, LaserTrailTiming.alpha(2500L, 1000L, 1500L), 0.0)
        assertTrue(LaserTrailTiming.expired(2500L, 1000L, 1500L))
    }

    @Test fun zeroLengthFadeExpiresImmediatelyAtTheFadeTime() {
        assertEquals(1.0, LaserTrailTiming.alpha(999L, 1000L, 0L), 0.0)
        assertEquals(0.0, LaserTrailTiming.alpha(1000L, 1000L, 0L), 0.0)
        assertTrue(LaserTrailTiming.expired(1000L, 1000L, 0L))
    }
}