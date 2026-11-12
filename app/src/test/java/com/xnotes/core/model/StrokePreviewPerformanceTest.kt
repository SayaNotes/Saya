package com.xnotes.core.model

import com.xnotes.core.stroke.Sample
import com.xnotes.core.tools.Tool
import com.xnotes.core.tools.ToolDefaults
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Regression guard for the long-live-stroke path: expensive non-cacheable pen previews must stay
 * bounded while the pointer is down, but the committed stroke must retain every captured sample.
 */
class StrokePreviewPerformanceTest {

    @Test fun liveTaperPreviewIsBoundedAndFinishedGeometryKeepsEverySample() {
        val samples = (0 until 4096).map { i ->
            Sample(i * 1.25, kotlin.math.sin(i / 17.0) * 24.0, 0.7, i.toDouble())
        }
        val stroke = Stroke(Tool.TAPER, ToolDefaults.configFor(Tool.TAPER), samples)
        stroke.finished = false

        val preview = stroke.geometryForRendering()
        assertTrue("live preview must not rebuild thousands of taper samples", preview.pointCount <= 512)
        assertEquals("raw document samples must never be discarded", samples.size, stroke.sampleCount)

        stroke.finished = true
        val committed = stroke.geometryForRendering()
        assertEquals("pen-up geometry must retain the full stroke", samples.size, committed.pointCount)
    }
}
