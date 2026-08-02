package cz.misa.quakedeck.data

import android.content.Context
import java.util.Locale

/** Location relevance shared by Android notifications and the EEW destination UI. */
class AlertLocationPolicy(context: Context) {
    private val geometry = JmaAreaGeometry.load(context.applicationContext)

    fun eewForecastPoint(event: EarthquakeEvent, location: AlertLocation): IntensityPoint? {
        val targetArea = location.eewAreaNameJa
            ?.takeIf { it.isNotBlank() }
            ?: geometry.eewAreaAt(location.latitude, location.longitude)?.nameJa
        if (!targetArea.isNullOrBlank()) {
            event.points.firstOrNull { point ->
                geometry.resolveEewAreas(point).any { shape ->
                    sameArea(shape.nameJa, targetArea)
                } || pointMentionsArea(point, targetArea)
            }?.let { return it }
        }

        return event.points
            .asSequence()
            .mapNotNull { point ->
                val latitude = point.latitude ?: return@mapNotNull null
                val longitude = point.longitude ?: return@mapNotNull null
                val distance = EewWaveModel.greatCircleDistanceKm(
                    latitude,
                    longitude,
                    location.latitude,
                    location.longitude
                )
                point to distance
            }
            .filter { (_, distance) -> distance <= EEW_POINT_FALLBACK_KM }
            .minByOrNull { (_, distance) -> distance }
            ?.first
    }

    fun observedPoint(event: EarthquakeEvent, location: AlertLocation): IntensityPoint? {
        val targetQuakeCode = location.quakeAreaCode
            ?.takeIf { it.isNotBlank() }
            ?: geometry.quakeAreaAt(location.latitude, location.longitude)?.code
        val targetQuakeName = location.quakeAreaNameJa
            ?.takeIf { it.isNotBlank() }
            ?: geometry.quakeAreaAt(location.latitude, location.longitude)?.nameJa
        val targetEewName = location.eewAreaNameJa
            ?.takeIf { it.isNotBlank() }
            ?: geometry.eewAreaAt(location.latitude, location.longitude)?.nameJa

        val exact = event.points.filter { point ->
            val resolved = geometry.resolveIntensityAreas(point)
            resolved.any { shape ->
                (!targetQuakeCode.isNullOrBlank() && shape.code == targetQuakeCode) ||
                    (!targetQuakeName.isNullOrBlank() && sameArea(shape.nameJa, targetQuakeName))
            }
        }
        if (exact.isNotEmpty()) return exact.maxByOrNull { intensityRank(it.intensity) }

        if (!targetEewName.isNullOrBlank()) {
            val broad = event.points.filter { point ->
                geometry.resolveEewAreas(point).any { shape -> sameArea(shape.nameJa, targetEewName) } ||
                    pointMentionsArea(point, targetEewName)
            }
            if (broad.isNotEmpty()) return broad.maxByOrNull { intensityRank(it.intensity) }
        }

        return event.points
            .asSequence()
            .mapNotNull { point ->
                val latitude = point.latitude ?: return@mapNotNull null
                val longitude = point.longitude ?: return@mapNotNull null
                point to EewWaveModel.greatCircleDistanceKm(
                    latitude,
                    longitude,
                    location.latitude,
                    location.longitude
                )
            }
            .filter { (_, distance) -> distance <= OBSERVATION_FALLBACK_KM }
            .maxWithOrNull(
                compareBy<Pair<IntensityPoint, Double>> { intensityRank(it.first.intensity) }
                    .thenBy { -it.second }
            )
            ?.first
    }

    fun relevantTsunamiAreas(
        report: TsunamiReport,
        location: AlertLocation
    ): List<TsunamiArea> {
        val eewArea = location.eewAreaNameJa.orEmpty()
        val exactForecastZones = when (eewArea) {
            "東京" -> setOf("東京湾内湾")
            "伊豆諸島" -> setOf("伊豆諸島")
            "小笠原" -> setOf("小笠原諸島")
            "奄美(群島)" -> setOf("奄美群島・トカラ列島", "奄美諸島・トカラ列島")
            "沖縄本島" -> setOf("沖縄本島地方")
            "大東島" -> setOf("大東島地方")
            "宮古島", "八重山" -> setOf("宮古島・八重山地方")
            else -> emptySet()
        }
        if (exactForecastZones.isNotEmpty()) {
            return report.areas.filter { it.name in exactForecastZones }
        }

        val prefecture = location.prefectureJa.takeIf { it.isNotBlank() } ?: return emptyList()
        return report.areas.filter { area -> prefecture in TsunamiAreaCatalog.prefectures(area.name) }
    }

    private fun pointMentionsArea(point: IntensityPoint, targetArea: String): Boolean {
        val target = normalizeArea(targetArea)
        return listOf(point.prefecture, point.name, point.stationName.orEmpty())
            .asSequence()
            .map(::normalizeArea)
            .any { candidate ->
                candidate.isNotBlank() &&
                    (candidate == target || candidate.contains(target) || target.contains(candidate))
            }
    }

    private fun sameArea(first: String, second: String): Boolean {
        val a = normalizeArea(first)
        val b = normalizeArea(second)
        return a == b || a.contains(b) || b.contains(a)
    }

    private fun normalizeArea(value: String): String = value
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

    companion object {
        private const val EEW_POINT_FALLBACK_KM = 80.0
        private const val OBSERVATION_FALLBACK_KM = 55.0

        fun intensityRank(value: String): Int {
            val normalized = value.lowercase(Locale.ROOT)
                .replace("震度", "")
                .replace("shindo", "")
                .replace("intensity", "")
                .replace(" ", "")
                .replace("弱", "-")
                .replace("強", "+")
            return when {
                normalized.contains("7") -> 9
                normalized.contains("6+") || normalized.contains("6upper") -> 8
                normalized.contains("6-") || normalized.contains("6lower") -> 7
                normalized.contains("5+") || normalized.contains("5upper") -> 6
                normalized.contains("5-") || normalized.contains("5lower") -> 5
                normalized.contains("4") -> 4
                normalized.contains("3") -> 3
                normalized.contains("2") -> 2
                normalized.contains("1") -> 1
                else -> 0
            }
        }
    }
}
