package cz.misa.quakedeck.data

/**
 * Geographic extent of the bundled Japan prefecture map.
 *
 * These values come from the TopoJSON bbox shipped in
 * japan_prefectures_topojson*.gz. Events outside this extent may still carry
 * observations or tsunami information for Japan, but their epicentre cannot be
 * meaningfully focused or drawn on QuakeDeck's Japan-only map.
 */
object JapanMapCoverage {
    const val MIN_LONGITUDE = 122.93261009098296
    const val MIN_LATITUDE = 20.42282277525095
    const val MAX_LONGITUDE = 153.9860719457638
    const val MAX_LATITUDE = 45.557239054039144

    fun contains(latitude: Double, longitude: Double): Boolean =
        latitude.isFinite() &&
            longitude.isFinite() &&
            latitude in MIN_LATITUDE..MAX_LATITUDE &&
            longitude in MIN_LONGITUDE..MAX_LONGITUDE
}

fun EarthquakeEvent.hasJapanMapEpicenter(): Boolean =
    hasHypocenter && JapanMapCoverage.contains(latitude, longitude)

/**
 * A report is mappable when either its epicentre is inside the bundled map or
 * it has a Japanese observed/predicted footprint that can be resolved to JMA
 * stations/areas. This preserves useful Japanese shaking information for a
 * distant source without trying to drag the camera to the other side of Earth.
 */
fun EarthquakeEvent.hasJapanMapContent(): Boolean =
    hasJapanMapEpicenter() || points.isNotEmpty()
