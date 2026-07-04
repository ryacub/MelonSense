package com.ryacub.melonsense.domain.inference

data class LocalVisualModelTrack(
    val id: String,
    val assetPath: String,
    val assetByteSize: Long,
    val labels: List<String>,
    val weight: Float,
)

object LocalVisualModelCatalog {
    const val ID: String = "melonsense-visual-runtime-v0"
    const val VERSION: String = "runtime-v0"

    val tracks: List<LocalVisualModelTrack> =
        listOf(
            LocalVisualModelTrack(
                id = "ripeness",
                assetPath = "models/visual-runtime-ripeness-v0.ptl",
                assetByteSize = 568_133L,
                labels = listOf("ripe", "unripe"),
                weight = 0.7f,
            ),
            LocalVisualModelTrack(
                id = "sweetness",
                assetPath = "models/visual-sweetness-fa99cb0.ptl",
                assetByteSize = 568_323L,
                labels = listOf("not_sweet", "sweet"),
                weight = 0.3f,
            ),
        )
}
