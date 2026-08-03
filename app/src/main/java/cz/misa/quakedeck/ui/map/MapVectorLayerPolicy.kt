package cz.misa.quakedeck.ui.map

/** The one administrative vector layer rendered for a settled map zoom. */
internal enum class MapVectorLayer {
    N03_PREFECTURES,
    JMA_QUAKE_AREAS,
    MUNICIPALITIES
}

/**
 * Public zoom units are normalized so the former physical 1.5× view is 1×.
 * Camera transforms deliberately retain their proven physical magnification.
 */
internal const val CAMERA_ZOOM_PER_DISPLAY_ZOOM = 1.5f
internal const val MIN_DISPLAY_MAP_ZOOM = 1f
internal const val MAX_DISPLAY_MAP_ZOOM = 128f
internal const val MIN_CAMERA_MAP_ZOOM =
    MIN_DISPLAY_MAP_ZOOM * CAMERA_ZOOM_PER_DISPLAY_ZOOM
internal const val MAX_CAMERA_MAP_ZOOM =
    MAX_DISPLAY_MAP_ZOOM * CAMERA_ZOOM_PER_DISPLAY_ZOOM

internal const val JMA_QUAKE_LAYER_ZOOM = 6.5f
internal const val MUNICIPALITY_LAYER_ZOOM = 21f

internal fun displayZoomForCameraZoom(cameraZoom: Float): Float =
    cameraZoom / CAMERA_ZOOM_PER_DISPLAY_ZOOM

internal fun cameraZoomForDisplayZoom(displayZoom: Float): Float =
    displayZoom * CAMERA_ZOOM_PER_DISPLAY_ZOOM

/** Select exactly one vector layer for a concrete zoom value. */
internal fun mapVectorLayerForZoom(zoom: Float): MapVectorLayer = when {
    zoom.isNaN() -> MapVectorLayer.N03_PREFECTURES
    zoom >= MUNICIPALITY_LAYER_ZOOM -> MapVectorLayer.MUNICIPALITIES
    zoom >= JMA_QUAKE_LAYER_ZOOM -> MapVectorLayer.JMA_QUAKE_AREAS
    else -> MapVectorLayer.N03_PREFECTURES
}

/** Select from the zoom currently visible while a pinch transform is active. */
internal fun mapVectorLayerForEffectiveZoom(
    committedZoom: Float,
    gestureScale: Float
): MapVectorLayer = mapVectorLayerForZoom(committedZoom * gestureScale)

/** Retain the strongest valid JMA intensity reported for one geometry key. */
internal fun MutableMap<String, String>.recordHighestShindo(
    key: String,
    intensity: String
) {
    if (key.isBlank()) return
    val candidateRank = shindoRank(intensity)
    if (candidateRank < 0) return
    val current = this[key]
    if (current == null || candidateRank > shindoRank(current)) {
        this[key] = intensity
    }
}

internal fun shindoRank(value: String): Int = when (value) {
    "0" -> 0
    "1" -> 1
    "2" -> 2
    "3" -> 3
    "4" -> 4
    "5-", "5弱" -> 5
    "5+", "5強" -> 6
    "6-", "6弱" -> 7
    "6+", "6強" -> 8
    "7" -> 9
    else -> -1
}
