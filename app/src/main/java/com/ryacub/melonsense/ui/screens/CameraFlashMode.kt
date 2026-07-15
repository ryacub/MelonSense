package com.ryacub.melonsense.ui.screens

import androidx.annotation.StringRes
import androidx.camera.core.ImageCapture
import com.ryacub.melonsense.R

enum class CameraFlashMode(
    val imageCaptureMode: Int,
    @get:StringRes val labelRes: Int,
) {
    Auto(ImageCapture.FLASH_MODE_AUTO, R.string.scan_flash_auto),
    On(ImageCapture.FLASH_MODE_ON, R.string.scan_flash_on),
    Off(ImageCapture.FLASH_MODE_OFF, R.string.scan_flash_off),
    ;

    companion object {
        val default: CameraFlashMode = Auto
    }
}
