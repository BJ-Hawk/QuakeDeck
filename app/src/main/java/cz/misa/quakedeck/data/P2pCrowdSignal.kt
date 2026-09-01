package cz.misa.quakedeck.data

import androidx.compose.runtime.Immutable
import org.json.JSONObject
import java.time.Duration
import java.time.LocalDateTime
import java.time.format.DateTimeFormatter

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

/**
 * Updates for one 9611 cluster are cumulative. Recovery or archive delivery can
 * arrive out of order, so a lower old count must never replace the final value.
 */
internal fun P2pCrowdSignal.mergeCumulativeUpdate(
    incoming: P2pCrowdSignal
): P2pCrowdSignal {
    if (startedAt != incoming.startedAt) return incoming
    if (reportCount != incoming.reportCount) {
        return if (incoming.reportCount > reportCount) incoming else this
    }
    val currentUpdated = crowdSignalInstant(updatedAt)
    val incomingUpdated = crowdSignalInstant(incoming.updatedAt)
    return when {
        incomingUpdated == null -> this
        currentUpdated == null || incomingUpdated >= currentUpdated -> incoming
        else -> this
    }
}

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
    return confirmedAssociationDelaySeconds(event) != null
}

/**
 * A 9611 aggregate starts when P2PQuake receives the first felt report, not at
 * the earthquake origin time. Confirmed JMA reports can also round or revise
 * their origin timestamp. Keep the association one-way and bounded: tolerate
 * only a small pre-origin clock skew, then up to three minutes for people and
 * the upstream aggregate to react.
 */
internal fun P2pCrowdSignal.confirmedAssociationDelaySeconds(
    event: EarthquakeEvent
): Long? {
    if (event.kind != EarthquakeEventKind.CONFIRMED) return null
    val secondsAfterOrigin = associationDelaySeconds(event) ?: return null
    return secondsAfterOrigin.takeIf {
        it in -P2P_CONFIRMED_PRE_ORIGIN_TOLERANCE_SECONDS..
            P2P_CONFIRMED_CROWD_ASSOCIATION_SECONDS
    }
}

internal fun P2pCrowdSignal.associationDelaySeconds(event: EarthquakeEvent): Long? {
    val crowdTime = P2P_CROWD_TIME_FORMATTERS.firstNotNullOfOrNull { formatter ->
        runCatching { LocalDateTime.parse(startedAt, formatter) }.getOrNull()
    } ?: return null
    val eventTime = runCatching {
        LocalDateTime.parse(event.originTime, P2P_EVENT_TIME_FORMATTER)
    }.getOrNull() ?: return null
    return Duration.between(eventTime, crowdTime).seconds
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
    val secondsAfterOrigin = associationDelaySeconds(event) ?: return false
    return secondsAfterOrigin in 0L..P2P_EEW_CROWD_ASSOCIATION_SECONDS
}

/**
 * Selects one cumulative felt cluster for a confirmed incident. A cluster may
 * match the confirmed origin directly or retain a prior claim made by a
 * matching EEW whose preliminary origin differs slightly from the final one.
 */
internal fun selectP2pCrowdSignalForConfirmedEvent(
    event: EarthquakeEvent,
    signals: Collection<P2pCrowdSignal>,
    claimedEewsByStartedAt: Map<String, EarthquakeEvent> = emptyMap()
): P2pCrowdSignal? = signals.asSequence()
    .filter(P2pCrowdSignal::isInformative)
    .groupBy(P2pCrowdSignal::startedAt)
    .values
    .mapNotNull { updates ->
        val cumulative = updates.reduce(P2pCrowdSignal::mergeCumulativeUpdate)
        val directDelay = cumulative.confirmedAssociationDelaySeconds(event)
        val claimedDelay = claimedEewsByStartedAt[cumulative.startedAt]
            ?.takeIf(cumulative::canBelongToEew)
            ?.let(cumulative::associationDelaySeconds)
        val rank = listOfNotNull(directDelay, claimedDelay)
            .minOfOrNull { kotlin.math.abs(it) }
            ?: return@mapNotNull null
        cumulative to rank
    }
    .minByOrNull { (_, rank) -> rank }
    ?.first

internal const val P2P_CROWD_SIGNAL_CODE = 9611
internal const val P2P_EEW_CROWD_ASSOCIATION_SECONDS = 3L * 60L
internal const val P2P_CONFIRMED_PRE_ORIGIN_TOLERANCE_SECONDS = 10L
internal const val P2P_CONFIRMED_CROWD_ASSOCIATION_SECONDS = 3L * 60L

private val P2P_CROWD_TIME_FORMATTERS = listOf(
    DateTimeFormatter.ofPattern("yyyy/MM/dd HH:mm:ss.SSS"),
    DateTimeFormatter.ofPattern("yyyy/MM/dd HH:mm:ss")
)

private fun crowdSignalInstant(value: String): LocalDateTime? =
    P2P_CROWD_TIME_FORMATTERS.firstNotNullOfOrNull { formatter ->
        runCatching { LocalDateTime.parse(value, formatter) }.getOrNull()
    }
private val P2P_EVENT_TIME_FORMATTER =
    DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss 'JST'")
