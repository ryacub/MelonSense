package com.ryacub.melonsense.ui.screens

import androidx.camera.core.ImageCapture
import org.junit.Assert.assertEquals
import org.junit.Test

class CameraFlashModeTest {
    @Test
    fun defaultMode_usesCameraMeteredAutoFlash() {
        assertEquals(CameraFlashMode.Auto, CameraFlashMode.default)
        assertEquals(ImageCapture.FLASH_MODE_AUTO, CameraFlashMode.default.imageCaptureMode)
    }

    @Test
    fun explicitModes_mapToCameraXFlashModes() {
        assertEquals(ImageCapture.FLASH_MODE_ON, CameraFlashMode.On.imageCaptureMode)
        assertEquals(ImageCapture.FLASH_MODE_OFF, CameraFlashMode.Off.imageCaptureMode)
    }
}
