package cz.misa.quakedeck.data

data class ObservedStationIdentity(
    val stationCode: String,
    val areaCode: String,
    val areaNameJa: String,
    val municipalityCode: String,
    val municipalityNameJa: String,
    val municipalityNameEn: String? = null
)

data class ObservedHierarchyMunicipality(
    val code: String,
    val nameJa: String,
    val nameEn: String?,
    val points: List<IntensityPoint>,
    val maximumIntensity: String
)

data class ObservedHierarchyArea(
    val code: String,
    val nameJa: String,
    val directPoints: List<IntensityPoint>,
    val municipalities: List<ObservedHierarchyMunicipality>,
    val maximumIntensity: String
) {
    val allPoints: List<IntensityPoint>
        get() = directPoints + municipalities.flatMap { it.points }
}

data class ObservedHierarchyPrefecture(
    val prefectureJa: String,
    val areas: List<ObservedHierarchyArea>,
    val maximumIntensity: String
) {
    val allPoints: List<IntensityPoint>
        get() = areas.flatMap { it.allPoints }
}

fun buildObservedIntensityHierarchy(
    points: List<IntensityPoint>,
    identityFor: (IntensityPoint) -> ObservedStationIdentity?
): List<ObservedHierarchyPrefecture> = points
    .groupBy { point ->
        point.prefecture.ifBlank { point.name.substringBefore(" · ") }.ifBlank { "—" }
    }
    .map { (prefecture, prefecturePoints) ->
        val resolved = prefecturePoints.map { it to identityFor(it) }
        val areaBuckets = linkedMapOf<String, MutableList<Pair<IntensityPoint, ObservedStationIdentity?>>>()
        resolved.forEach { pair ->
            val point = pair.first
            val identity = pair.second
            val fallbackName = point.stationName
                ?.takeIf { point.isArea && it.isNotBlank() }
                ?: point.name.substringAfterLast(" · ").ifBlank { "—" }
            val key = identity?.areaCode?.takeIf { it.isNotBlank() }
                ?.let { "code:$it" }
                ?: "name:$fallbackName"
            areaBuckets.getOrPut(key) { mutableListOf() } += pair
        }

        val areas = areaBuckets.map { (areaKey, areaPoints) ->
            val firstIdentity = areaPoints.firstNotNullOfOrNull { it.second }
            val areaCode = firstIdentity?.areaCode?.takeIf { it.isNotBlank() }
                ?: areaKey.removePrefix("name:")
            val areaName = firstIdentity?.areaNameJa?.takeIf { it.isNotBlank() }
                ?: areaPoints.first().first.stationName
                    ?.takeIf { areaPoints.first().first.isArea && it.isNotBlank() }
                ?: areaPoints.first().first.name.substringAfterLast(" · ").ifBlank { "—" }
            val directPoints = areaPoints
                .filter { (_, identity) -> identity == null }
                .map { it.first }
                .sortedByDescending { observedIntensityRank(it.intensity) }
            val municipalities = areaPoints
                .filter { (_, identity) -> identity != null }
                .groupBy { (_, identity) ->
                    identity!!.municipalityCode.takeIf { it.isNotBlank() }
                        ?.let { "code:$it" }
                        ?: "station:${identity.stationCode}"
                }
                .map { (municipalityKey, municipalityPoints) ->
                    val identity = municipalityPoints.first().second!!
                    val groupedPoints = municipalityPoints.map { it.first }
                        .sortedByDescending { observedIntensityRank(it.intensity) }
                    ObservedHierarchyMunicipality(
                        code = identity.municipalityCode.takeIf { it.isNotBlank() }
                            ?: municipalityKey.removePrefix("station:"),
                        nameJa = identity.municipalityNameJa.ifBlank { areaName },
                        nameEn = identity.municipalityNameEn,
                        points = groupedPoints,
                        maximumIntensity = maximumObservedIntensity(groupedPoints)
                    )
                }
                .sortedWith(
                    compareByDescending<ObservedHierarchyMunicipality> {
                        observedIntensityRank(it.maximumIntensity)
                    }.thenBy { it.nameJa }
                )
            val allAreaPoints = directPoints + municipalities.flatMap { it.points }
            ObservedHierarchyArea(
                code = areaCode,
                nameJa = areaName,
                directPoints = directPoints,
                municipalities = municipalities,
                maximumIntensity = maximumObservedIntensity(allAreaPoints)
            )
        }.sortedWith(
            compareByDescending<ObservedHierarchyArea> {
                observedIntensityRank(it.maximumIntensity)
            }.thenBy { it.nameJa }
        )

        ObservedHierarchyPrefecture(
            prefectureJa = prefecture,
            areas = areas,
            maximumIntensity = maximumObservedIntensity(prefecturePoints)
        )
    }
    .sortedWith(
        compareByDescending<ObservedHierarchyPrefecture> {
            observedIntensityRank(it.maximumIntensity)
        }.thenBy { it.prefectureJa }
    )

fun observedIntensityRank(value: String): Int = when (value) {
    "7" -> 70
    "6+", "6強" -> 65
    "6-", "6弱" -> 60
    "5+", "5強" -> 55
    "5-", "5弱" -> 50
    "4" -> 40
    "3" -> 30
    "2" -> 20
    "1" -> 10
    "0" -> 0
    else -> -1
}

private fun maximumObservedIntensity(points: List<IntensityPoint>): String = points
    .maxByOrNull { observedIntensityRank(it.intensity) }
    ?.intensity
    ?: "—"
