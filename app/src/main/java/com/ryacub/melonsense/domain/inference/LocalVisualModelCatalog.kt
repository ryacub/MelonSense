package com.ryacub.melonsense.domain.inference

import org.json.JSONObject

data class LocalVisualModelTrack(
    val id: String,
    val assetPath: String,
    val assetByteSize: Long,
    val labels: List<String>,
    val weight: Float,
)

data class LocalVisualModelCatalog(
    val id: String,
    val version: String,
    val tracks: List<LocalVisualModelTrack>,
) {
    companion object {
        const val ASSET_PATH: String = "models/visual-models.json"

        val fallback: LocalVisualModelCatalog =
            LocalVisualModelCatalog(
                id = "melonsense-visual-runtime-v0",
                version = "runtime-v0",
                tracks =
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
                    ),
            )

        fun loadFromAssets(readAsset: (String) -> String): LocalVisualModelCatalog =
            runCatching { parse(readAsset(ASSET_PATH)) }.getOrDefault(fallback)

        fun parse(json: String): LocalVisualModelCatalog {
            val root = JSONObject(json)
            val tracksJson = root.getJSONArray("tracks")
            val tracks =
                List(tracksJson.length()) { index ->
                    val track = tracksJson.getJSONObject(index)
                    val labelsJson = track.getJSONArray("labels")
                    LocalVisualModelTrack(
                        id = track.getString("id"),
                        assetPath = track.getString("asset"),
                        assetByteSize = track.getLong("byteSize"),
                        labels =
                            List(labelsJson.length()) { labelIndex ->
                                labelsJson.getString(labelIndex)
                            },
                        weight = track.getDouble("weight").toFloat(),
                    )
                }
            return LocalVisualModelCatalog(
                id = root.optString("id", "melonsense-visual-${root.getString("version")}"),
                version = root.getString("version"),
                tracks = tracks,
            ).validated()
        }
    }

    init {
        validated()
    }

    private fun validated(): LocalVisualModelCatalog {
        require(id.isNotBlank()) { "Catalog id must not be blank" }
        require(version.isNotBlank()) { "Catalog version must not be blank" }
        require(tracks.isNotEmpty()) { "Catalog must include at least one track" }
        tracks.forEach { track ->
            require(track.id.isNotBlank()) { "Track id must not be blank" }
            require(track.assetPath.isNotBlank()) { "Track asset path must not be blank" }
            require(track.assetByteSize > 0) { "Track asset byte size must be positive" }
            require(track.labels.isNotEmpty()) { "Track labels must not be empty" }
            require(track.labels.all { label -> label.isNotBlank() }) { "Track labels must not be blank" }
            require(track.weight > 0f) { "Track weight must be positive" }
        }
        return this
    }
}
