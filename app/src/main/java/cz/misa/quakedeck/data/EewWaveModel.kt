package cz.misa.quakedeck.data

import java.time.LocalDateTime
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Locale
import kotlin.math.PI
import kotlin.math.asin
import kotlin.math.atan2
import kotlin.math.ceil
import kotlin.math.cos
import kotlin.math.max
import kotlin.math.min
import kotlin.math.sin
import kotlin.math.sqrt

/**
 * Lightweight EEW travel-time model used only for display.
 *
 * P2PQuake provides the hypocentre and, for many forecast areas, an official
 * expected arrival time. QuakeDeck uses that official time for the destination
 * countdown whenever it exists. The map wavefronts and any missing destination
 * time use constant effective crustal velocities so they remain an estimate,
 * not an official warning calculation.
 */
object EewWaveModel {
    const val P_WAVE_KM_PER_SECOND = 7.0
    const val S_WAVE_KM_PER_SECOND = 4.0
    const val EARTH_RADIUS_KM = 6_371.0088

    const val DEFAULT_DESTINATION_NAME = "Tokyo"
    const val DEFAULT_DESTINATION_LATITUDE = 35.6762
    const val DEFAULT_DESTINATION_LONGITUDE = 139.6503
    const val DEFAULT_DESTINATION_EEW_AREA_JA = "東京"

    const val WARNING_END_GRACE_MILLIS = 15_000L
    const val WARNING_END_FALLBACK_MILLIS = 90_000L
    const val WARNING_END_MAXIMUM_MILLIS = 180_000L

    private val jst = ZoneId.of("Asia/Tokyo")
    private val eventTimeFormatter =
        DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss 'JST'")

    data class GeoPoint(
        val latitude: Double,
        val longitude: Double
    )

    data class WavefrontState(
        val elapsedSeconds: Double,
        val pWaveRadiusKm: Double,
        val sWaveRadiusKm: Double
    )

    data class DestinationPrediction(
        val destinationName: String,
        val predictedIntensity: String?,
        val predictedIntensityFrom: String?,
        val predictedIntensityUpperOpenEnded: Boolean,
        val pArrivalEpochMillis: Long,
        val sArrivalEpochMillis: Long,
        val secondsUntilP: Long,
        val secondsUntilS: Long,
        val officialSArrival: Boolean
    )

    fun wavefrontState(
        event: EarthquakeEvent,
        nowEpochMillis: Long
    ): WavefrontState? {
        if (event.kind != EarthquakeEventKind.EEW || event.isCancelled) return null
        val origin = timelineEpochMillis(event, event.originTime) ?: return null
        val elapsedSeconds = max(0.0, (nowEpochMillis - origin) / 1_000.0)
        val depthKm = event.depthKm.coerceAtLeast(0).toDouble()
        return WavefrontState(
            elapsedSeconds = elapsedSeconds,
            pWaveRadiusKm = surfaceRadiusKm(
                travelledKm = elapsedSeconds * P_WAVE_KM_PER_SECOND,
                depthKm = depthKm
            ),
            sWaveRadiusKm = surfaceRadiusKm(
                travelledKm = elapsedSeconds * S_WAVE_KM_PER_SECOND,
                depthKm = depthKm
            )
        )
    }

    fun destinationPrediction(
        event: EarthquakeEvent,
        nowEpochMillis: Long,
        destinationName: String = DEFAULT_DESTINATION_NAME,
        destinationLatitude: Double = DEFAULT_DESTINATION_LATITUDE,
        destinationLongitude: Double = DEFAULT_DESTINATION_LONGITUDE,
        destinationEewAreaNameJa: String? = DEFAULT_DESTINATION_EEW_AREA_JA
    ): DestinationPrediction? {
        if (event.kind != EarthquakeEventKind.EEW || event.isCancelled) return null
        val origin = timelineEpochMillis(event, event.originTime) ?: return null

        val surfaceDistance = greatCircleDistanceKm(
            event.latitude,
            event.longitude,
            destinationLatitude,
            destinationLongitude
        )
        val hypocentralDistance = sqrt(
            surfaceDistance * surfaceDistance +
                event.depthKm.coerceAtLeast(0).toDouble().let { it * it }
        )

        val modelledPArrival =
            origin + (hypocentralDistance / P_WAVE_KM_PER_SECOND * 1_000.0).toLong()
        val modelledSArrival =
            origin + (hypocentralDistance / S_WAVE_KM_PER_SECOND * 1_000.0).toLong()

        val officialPoint = destinationEewAreaNameJa
            ?.takeIf { it.isNotBlank() }
            ?.let { targetArea ->
                event.points.firstOrNull { point -> matchesForecastArea(point, targetArea) }
            }
        val officialSArrival = officialPoint?.arrivalTime
            ?.let { timelineEpochMillis(event, it) }

        val sArrival = officialSArrival ?: modelledSArrival
        return DestinationPrediction(
            destinationName = destinationName,
            predictedIntensity = officialPoint?.intensity,
            predictedIntensityFrom = officialPoint?.intensityFrom,
            predictedIntensityUpperOpenEnded =
                officialPoint?.intensityUpperOpenEnded == true,
            pArrivalEpochMillis = modelledPArrival,
            sArrivalEpochMillis = sArrival,
            secondsUntilP = secondsUntil(modelledPArrival, nowEpochMillis),
            secondsUntilS = secondsUntil(sArrival, nowEpochMillis),
            officialSArrival = officialSArrival != null
        )
    }

    /**
     * Estimate when the public EEW warning has finished being useful. P2PQuake
     * does not provide a dependable normal "warning ended" packet, so the
     * latest official area arrival is used when available. A bounded fallback
     * keeps malformed or sparse packets from remaining active forever.
     */
    fun estimatedWarningEndEpochMillis(
        event: EarthquakeEvent,
        receivedAtEpochMillis: Long = System.currentTimeMillis()
    ): Long {
        val latestOfficialArrival = event.points
            .asSequence()
            .mapNotNull { point -> point.arrivalTime?.let { timelineEpochMillis(event, it) } }
            .maxOrNull()
        val origin = timelineEpochMillis(event, event.originTime)
        val issued = event.reportIssuedAt?.let { timelineEpochMillis(event, it) }
        val anchor = origin ?: issued ?: receivedAtEpochMillis
        val estimated = latestOfficialArrival
            ?.plus(WARNING_END_GRACE_MILLIS)
            ?: anchor.plus(WARNING_END_FALLBACK_MILLIS)
        return min(estimated, anchor.plus(WARNING_END_MAXIMUM_MILLIS))
    }

    fun timelineEpochMillis(
        event: EarthquakeEvent,
        formattedJstTime: String
    ): Long? {
        val sourceMillis = parseFormattedJst(formattedJstTime) ?: return null
        return sourceMillis + event.timelineOffsetMillis
    }

    fun geodesicCircle(
        latitude: Double,
        longitude: Double,
        radiusKm: Double,
        steps: Int = 96
    ): List<GeoPoint> {
        if (!radiusKm.isFinite() || radiusKm <= 0.0) return emptyList()
        val angularDistance = radiusKm / EARTH_RADIUS_KM
        val lat1 = Math.toRadians(latitude)
        val lon1 = Math.toRadians(longitude)
        val count = steps.coerceAtLeast(24)

        return List(count + 1) { index ->
            val bearing = 2.0 * PI * index / count
            val lat2 = asin(
                sin(lat1) * cos(angularDistance) +
                    cos(lat1) * sin(angularDistance) * cos(bearing)
            )
            val lon2 = lon1 + atan2(
                sin(bearing) * sin(angularDistance) * cos(lat1),
                cos(angularDistance) - sin(lat1) * sin(lat2)
            )
            GeoPoint(
                latitude = Math.toDegrees(lat2),
                longitude = normalizeLongitude(Math.toDegrees(lon2))
            )
        }
    }

    fun greatCircleDistanceKm(
        latitudeA: Double,
        longitudeA: Double,
        latitudeB: Double,
        longitudeB: Double
    ): Double {
        val lat1 = Math.toRadians(latitudeA)
        val lat2 = Math.toRadians(latitudeB)
        val dLat = lat2 - lat1
        val dLon = Math.toRadians(longitudeB - longitudeA)
        val sinLat = sin(dLat / 2.0)
        val sinLon = sin(dLon / 2.0)
        val a = sinLat * sinLat + cos(lat1) * cos(lat2) * sinLon * sinLon
        return 2.0 * EARTH_RADIUS_KM * asin(sqrt(a.coerceIn(0.0, 1.0)))
    }

    private fun surfaceRadiusKm(travelledKm: Double, depthKm: Double): Double {
        if (travelledKm <= depthKm) return 0.0
        return sqrt(travelledKm * travelledKm - depthKm * depthKm)
    }

    private fun secondsUntil(targetEpochMillis: Long, nowEpochMillis: Long): Long =
        ceil((targetEpochMillis - nowEpochMillis) / 1_000.0).toLong()

    private fun parseFormattedJst(value: String): Long? =
        runCatching {
            LocalDateTime.parse(value, eventTimeFormatter)
                .atZone(jst)
                .toInstant()
                .toEpochMilli()
        }.getOrNull()

    private fun matchesForecastArea(point: IntensityPoint, targetArea: String): Boolean {
        fun normalize(value: String): String = value
            .lowercase(Locale.ROOT)
            .replace("　", "")
            .replace(" ", "")
            .replace("地方", "")
            .replace("都", "")
            .replace("道", "")
            .replace("府", "")
            .replace("県", "")
            .replace("region", "")
            .replace("prefecture", "")
            .replace("metropolis", "")
            .trim()

        val target = normalize(targetArea)
        return listOf(point.prefecture, point.name, point.stationName.orEmpty())
            .map(::normalize)
            .any { candidate ->
                candidate.isNotBlank() &&
                    (candidate == target || candidate.contains(target) || target.contains(candidate))
            }
    }

    private fun normalizeLongitude(value: Double): Double {
        var longitude = value
        while (longitude > 180.0) longitude -= 360.0
        while (longitude < -180.0) longitude += 360.0
        return longitude
    }
}
