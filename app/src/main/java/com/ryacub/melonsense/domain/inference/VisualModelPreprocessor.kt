package com.ryacub.melonsense.domain.inference

data class VisualModelCrop(
    val left: Float,
    val top: Float,
    val right: Float,
    val bottom: Float,
)

object VisualModelPreprocessor {
    const val INPUT_LAYOUT: String = "rgb_chw"
    const val NORMALIZATION: String = "float32_0_1"
    const val RESIZE_MODE: String = "nearest"
    const val DEFAULT_IMAGE_SIZE: Int = 96

    fun normalizeRgbBytesToChwFloatArray(
        rgbBytes: ByteArray,
        width: Int,
        height: Int,
        crop: VisualModelCrop? = null,
        imageSize: Int = DEFAULT_IMAGE_SIZE,
    ): FloatArray {
        require(width > 0) { "width must be positive" }
        require(height > 0) { "height must be positive" }
        require(imageSize > 0) { "imageSize must be positive" }
        require(rgbBytes.size == width * height * CHANNEL_COUNT) {
            "rgbBytes must contain width * height * 3 bytes"
        }

        val cropBox = cropBox(crop, width, height)
        val cropWidth = cropBox.right - cropBox.left
        val cropHeight = cropBox.bottom - cropBox.top
        val output = FloatArray(CHANNEL_COUNT * imageSize * imageSize)
        val planeSize = imageSize * imageSize

        for (outputY in 0 until imageSize) {
            val sourceY = cropBox.top + ((outputY * cropHeight) / imageSize).coerceIn(0, cropHeight - 1)
            for (outputX in 0 until imageSize) {
                val sourceX = cropBox.left + ((outputX * cropWidth) / imageSize).coerceIn(0, cropWidth - 1)
                val sourceOffset = ((sourceY * width) + sourceX) * CHANNEL_COUNT
                val outputOffset = (outputY * imageSize) + outputX
                output[outputOffset] = unsignedByteToUnitFloat(rgbBytes[sourceOffset])
                output[planeSize + outputOffset] = unsignedByteToUnitFloat(rgbBytes[sourceOffset + 1])
                output[(planeSize * 2) + outputOffset] = unsignedByteToUnitFloat(rgbBytes[sourceOffset + 2])
            }
        }
        return output
    }

    private fun cropBox(
        crop: VisualModelCrop?,
        width: Int,
        height: Int,
    ): PixelCropBox {
        val sourceCrop = crop ?: VisualModelCrop(0f, 0f, 1f, 1f)
        val left = (sourceCrop.left.coerceIn(0f, 1f) * width).toInt()
        val top = (sourceCrop.top.coerceIn(0f, 1f) * height).toInt()
        val right = (sourceCrop.right.coerceIn(0f, 1f) * width).toInt().coerceAtLeast(left + 1)
        val bottom = (sourceCrop.bottom.coerceIn(0f, 1f) * height).toInt().coerceAtLeast(top + 1)
        return PixelCropBox(
            left = left.coerceIn(0, width - 1),
            top = top.coerceIn(0, height - 1),
            right = right.coerceIn(1, width),
            bottom = bottom.coerceIn(1, height),
        )
    }

    private fun unsignedByteToUnitFloat(value: Byte): Float = (value.toInt() and 0xff) / 255f

    private const val CHANNEL_COUNT = 3
}

private data class PixelCropBox(
    val left: Int,
    val top: Int,
    val right: Int,
    val bottom: Int,
)
