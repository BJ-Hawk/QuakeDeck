package cz.misa.quakedeck.data

import kotlin.math.abs

/**
 * Recognizes two source copies of a published bulletin whose origin times
 * differ, using publication time and the reported hypocenter as evidence.
 */
internal fun sameConfirmedBulletinAcrossOrigins(
    first: EarthquakeEvent,
    second: EarthquakeEvent
): Boolean {
    if (first.kind != EarthquakeEventKind.CONFIRMED || second.kind != EarthquakeEventKind.CONFIRMED ||
        first.isDrill || second.isDrill || first.isCancelled || second.isCancelled
    ) return false
    val firstJmaId = first.jmaReportEventId?.takeIf { it.isNotBlank() && it != "null" }
    val secondJmaId = second.jmaReportEventId?.takeIf { it.isNotBlank() && it != "null" }
    if (firstJmaId != null && secondJmaId != null && firstJmaId != secondJmaId) return false

    val firstIssued = EewLifecyclePolicy.sourceMillis(first.reportIssuedAt) ?: return false
    val secondIssued = EewLifecyclePolicy.sourceMillis(second.reportIssuedAt) ?: return false
    if (firstIssued != secondIssued || first.reportType.isNullOrBlank() ||
        !first.reportType.equals(second.reportType, ignoreCase = true)
    ) return false
    if (normalizedCorrection(first.reportCorrection) != normalizedCorrection(second.reportCorrection)) return false
    val firstOrigin = EewLifecyclePolicy.sourceMillis(first.originTime) ?: return false
    val secondOrigin = EewLifecyclePolicy.sourceMillis(second.originTime) ?: return false
    if (abs(firstOrigin - secondOrigin) > 90_000L) return false

    return first.hasHypocenter && second.hasHypocenter &&
        first.latitude in -90.0..90.0 && second.latitude in -90.0..90.0 &&
        first.longitude in -180.0..180.0 && second.longitude in -180.0..180.0 &&
        abs(first.latitude - second.latitude) <= 0.05 &&
        abs(first.longitude - second.longitude) <= 0.05 &&
        first.magnitude > 0.0 && second.magnitude > 0.0 &&
        abs(first.magnitude - second.magnitude) <= 0.100001 &&
        first.depthKm >= 0 && second.depthKm >= 0 && abs(first.depthKm.toLong() - second.depthKm) <= 1
}

private fun normalizedCorrection(value: String?): String =
    value.orEmpty().trim().lowercase().takeUnless { it in setOf("none", "unknown") }.orEmpty()
