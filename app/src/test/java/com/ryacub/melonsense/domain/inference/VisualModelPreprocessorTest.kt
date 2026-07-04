package com.ryacub.melonsense.domain.inference

import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Test

class VisualModelPreprocessorTest {
    @Test
    fun normalizeRgbBytes_matchesTrainingCropResizeAndChwNormalization() {
        val rgbBytes = ByteArray(4 * 4 * 3)
        for (y in 0 until 4) {
            for (x in 0 until 4) {
                val offset = ((y * 4) + x) * 3
                rgbBytes[offset] = (x * 10).toByte()
                rgbBytes[offset + 1] = (y * 20).toByte()
                rgbBytes[offset + 2] = ((x + y) * 30).toByte()
            }
        }

        val output =
            VisualModelPreprocessor.normalizeRgbBytesToChwFloatArray(
                rgbBytes = rgbBytes,
                width = 4,
                height = 4,
                crop = VisualModelCrop(left = 0f, top = 0f, right = 0.5f, bottom = 0.5f),
                imageSize = 2,
            )

        assertEquals("rgb_chw", VisualModelPreprocessor.INPUT_LAYOUT)
        assertEquals(96, VisualModelPreprocessor.DEFAULT_IMAGE_SIZE)
        assertArrayEquals(
            floatArrayOf(
                0f / 255f,
                10f / 255f,
                0f / 255f,
                10f / 255f,
                0f / 255f,
                0f / 255f,
                20f / 255f,
                20f / 255f,
                0f / 255f,
                30f / 255f,
                30f / 255f,
                60f / 255f,
            ),
            output,
            0.000001f,
        )
    }
}
