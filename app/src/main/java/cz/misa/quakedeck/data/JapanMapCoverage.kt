package cz.misa.quakedeck.data

/**
 * Geographic extent of the bundled JMA nationwide forecast polygon.
 *
 * These values come from the source shapefile bbox used to build
 * jma_prefectures_simplified.gz. Events outside this extent may still carry
 * observations or tsunami information for Japan, but their epicentre cannot be
 * meaningfully focused or drawn on QuakeDeck's Japan-only map.
 */
object JapanMapCoverage {
    const val MIN_LONGITUDE = 122.93375
    const val MIN_LATITUDE = 24.04967
    const val MAX_LONGITUDE = 148.89216
    const val MAX_LATITUDE = 45.55682

    fun contains(latitude: Double, longitude: Double): Boolean =
        latitude.isFinite() &&
            longitude.isFinite() &&
            latitude in MIN_LATITUDE..MAX_LATITUDE &&
            longitude in MIN_LONGITUDE..MAX_LONGITUDE

    /**
     * Nearest coordinate representable by the existing Japan map extent.
     * This deliberately clamps the focus target instead of widening the map.
     */
    fun nearestPoint(latitude: Double, longitude: Double): JapanMapCoordinate? {
        if (!latitude.isFinite() || !longitude.isFinite()) return null
        return JapanMapCoordinate(
            latitude = latitude.coerceIn(MIN_LATITUDE, MAX_LATITUDE),
            longitude = longitude.coerceIn(MIN_LONGITUDE, MAX_LONGITUDE)
        )
    }

    /** Keep only reports just beyond the bundled geometry eligible for edge focus. */
    fun isNearEdge(latitude: Double, longitude: Double): Boolean =
        latitude.isFinite() && longitude.isFinite() &&
            latitude in (MIN_LATITUDE - 0.75)..(MAX_LATITUDE + 0.75) &&
            longitude in (MIN_LONGITUDE - 0.75)..(MAX_LONGITUDE + 0.75)
}

data class JapanMapCoordinate(
    val latitude: Double,
    val longitude: Double
)

fun EarthquakeEvent.hasJapanMapEpicenter(): Boolean =
    hasHypocenter && JapanMapCoverage.contains(latitude, longitude)

/**
 * Rendering is intentionally less restrictive than automatic camera focus.
 * A known epicentre just outside the bundled land bounds can still project
 * into visible map padding, while a genuinely distant marker remains hidden.
 */
fun EarthquakeEvent.shouldDrawMapEpicenter(projectedMarkerVisible: Boolean): Boolean =
    hasHypocenter && projectedMarkerVisible

/**
 * A report is mappable when either its epicentre is inside the bundled map or
 * it has a Japanese observed/predicted footprint that can be resolved to JMA
 * stations/areas. An active EEW is also renderable from valid source coordinates
 * so its wavefronts and local calculations are not lost merely because the
 * source lies just outside the strict bundled extent. Confirmed reports retain
 * the existing Japan-only behavior.
 */
fun EarthquakeEvent.hasJapanMapContent(): Boolean =
    hasJapanMapEpicenter() ||
        points.isNotEmpty() ||
        (
            kind == EarthquakeEventKind.EEW &&
                hasHypocenter &&
                latitude.isFinite() &&
                longitude.isFinite()
            )

/**
 * Camera anchor for EEWs and confirmed reports immediately beyond the bundled
 * map edge. Genuine distant reports never use this clamped fallback.
 */
fun EarthquakeEvent.nearestJapanMapEewFocus(): JapanMapCoordinate? =
    if (hasHypocenter && (kind == EarthquakeEventKind.EEW || JapanMapCoverage.isNearEdge(latitude, longitude))) {
        JapanMapCoverage.nearestPoint(latitude, longitude)
    } else {
        null
    }
