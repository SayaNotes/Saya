package com.xnotes.core.model

import com.xnotes.core.stroke.Sample
import com.xnotes.core.tools.Tool
import com.xnotes.core.tools.ToolDefaults
import org.junit.Assert.assertTrue
import org.junit.Test

class StrokeLivePreviewTest {
    @Test fun taperPreviewRemainsBoundedForVeryLongLiveStrokes() {
        val samples = (0 until 12_000).map { i ->
            Sample(i * 0.75, kotlin.math.sin(i * 0.035) * 12.0, 0.7, i.toDouble())
        }
        val stroke = Stroke(Tool.TAPER, ToolDefaults.configFor(Tool.TAPER), samples)
        stroke.finished = false

        val preview = stroke.geometryForRendering()

        assertTrue("live preview should stay bounded, got ${preview.pointCount} points", preview.pointCount <= 256)
        assertTrue("preview should retain a useful path", preview.pointCount >= 200)
    }
}
