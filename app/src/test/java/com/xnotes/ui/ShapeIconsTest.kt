package com.xnotes.ui

import com.xnotes.core.shapes.ConnectorPresets
import com.xnotes.core.shapes.ShapePresets
import org.junit.Assert.assertNotNull
import org.junit.Test

/** Smoke tests for the geometry-backed icons composed as soon as the shape picker opens. */
class ShapeIconsTest {
    @Test
    fun everyShapePresetCanBuildItsPickerIcon() {
        ShapePresets.all.forEach { preset ->
            assertNotNull("Shape icon failed for ${preset.key}", ShapeIcons.of(preset))
        }
    }

    @Test
    fun everyConnectorPresetCanBuildItsPickerIcon() {
        ConnectorPresets.all.forEach { preset ->
            assertNotNull("Connector icon failed for ${preset.key}", ShapeIcons.connector(preset))
        }
    }
}
