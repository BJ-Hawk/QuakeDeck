package cz.misa.quakedeck.data

import androidx.compose.runtime.Immutable
import org.json.JSONObject
import java.time.Duration
import java.time.LocalDateTime
import java.time.format.DateTimeFormatter
import kotlin.math.abs

@Immutable
data class P2pCrowdAreaSignal(
    val areaCode: String,
    val reportCount: Int,
    val confidence: Double,
    val displayGrade: String
)

@Immutable
data class P2pCrowdSignal(
    /** P2PQuake's cluster key. Updates with this value describe one incident. */
    val startedAt: String,
    val updatedAt: String,
    val reportCount: Int,
    /** Upstream aggregate score. It is not a probability or seismic intensity. */
    val confidence: Double,
    val areas: List<P2pCrowdAreaSignal>
)

internal fun parseP2pCrowdSignal(json: JSONObject): P2pCrowdSignal? {
    if (json.optInt("code", -1) != P2P_CROWD_SIGNAL_CODE) return null
    val startedAt = json.optString("started_at").trim()
    if (startedAt.isBlank()) return null

    val areaObject = json.optJSONObject("area_confidences")
    val areas = buildList {
        if (areaObject != null) {
            val keys = areaObject.keys()
            while (keys.hasNext()) {
                val areaCode = keys.next()
                val value = areaObject.optJSONObject(areaCode) ?: continue
                add(
                    P2pCrowdAreaSignal(
                        areaCode = areaCode,
                        reportCount = value.optInt("count", 0).coerceAtLeast(0),
                        confidence = value.optDouble("confidence", -1.0),
                        displayGrade = value.optString("display").trim()
                    )
                )
            }
        }
    }.sortedWith(
        compareByDescending<P2pCrowdAreaSignal> { it.confidence }
            .thenByDescending { it.reportCount }
            .thenBy { it.areaCode.toIntOrNull() ?: Int.MAX_VALUE }
            .thenBy { it.areaCode }
    )

    return P2pCrowdSignal(
        startedAt = startedAt,
        updatedAt = json.optString("updated_at").trim().ifBlank {
            json.optString("time").trim()
        },
        reportCount = json.optInt("count", 0).coerceAtLeast(0),
        confidence = json.optDouble("confidence", 0.0),
        areas = areas
    )
}

internal fun P2pCrowdSignal.coincidesWith(event: EarthquakeEvent): Boolean {
    val crowdTime = P2P_CROWD_TIME_FORMATTERS.firstNotNullOfOrNull { formatter ->
        runCatching { LocalDateTime.parse(startedAt, formatter) }.getOrNull()
    } ?: return false
    val eventTime = runCatching {
        LocalDateTime.parse(event.originTime, P2P_EVENT_TIME_FORMATTER)
    }.getOrNull() ?: return false
    return abs(Duration.between(crowdTime, eventTime).seconds) <= 10L
}

/**
 * A single zero-confidence tap is useful diagnostics, but it is not useful
 * enough to decorate an earthquake card. P2PQuake raises the aggregate as
 * further reports arrive; the latest aggregate is always cumulative.
 */
internal fun P2pCrowdSignal.isInformative(): Boolean =
    reportCount >= 2 && (confidence > 0.0 || areas.isNotEmpty())

/**
 * EEW is received before ordinary reports. A crowd cluster can therefore be
 * associated with an active EEW after its origin, rather than requiring the
 * cluster to have the exact origin timestamp used by a later 551 report.
 */
internal fun P2pCrowdSignal.canBelongToEew(event: EarthquakeEvent): Boolean {
    if (event.kind != EarthquakeEventKind.EEW || event.isCancelled) return false
    val crowdTime = P2P_CROWD_TIME_FORMATTERS.firstNotNullOfOrNull { formatter ->
        runCatching { LocalDateTime.parse(startedAt, formatter) }.getOrNull()
    } ?: return false
    val eventTime = runCatching {
        LocalDateTime.parse(event.originTime, P2P_EVENT_TIME_FORMATTER)
    }.getOrNull() ?: return false
    val secondsAfterOrigin = Duration.between(eventTime, crowdTime).seconds
    return secondsAfterOrigin in 0L..P2P_EEW_CROWD_ASSOCIATION_SECONDS
}

internal const val P2P_CROWD_SIGNAL_CODE = 9611
internal const val P2P_EEW_CROWD_ASSOCIATION_SECONDS = 10L * 60L

private val P2P_CROWD_TIME_FORMATTERS = listOf(
    DateTimeFormatter.ofPattern("yyyy/MM/dd HH:mm:ss.SSS"),
    DateTimeFormatter.ofPattern("yyyy/MM/dd HH:mm:ss")
)
private val P2P_EVENT_TIME_FORMATTER =
    DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss 'JST'")
