package cz.misa.quakedeck.data

import kotlin.math.abs

internal fun matchesLiveEewReport(eew: EarthquakeEvent, confirmed: EarthquakeEvent): Boolean {
    if (eew.kind != EarthquakeEventKind.EEW || confirmed.kind != EarthquakeEventKind.CONFIRMED ||
        eew.isDrill || confirmed.isDrill || confirmed.isCancelled
    ) return false

    val confirmedJmaId = confirmed.jmaReportEventId?.takeUnless { it.isBlank() || it == "null" }
    if (confirmedJmaId != null) {
        val eewJmaId = eew.jmaReportEventId?.takeUnless { it.isBlank() || it == "null" }
        if (eewJmaId != null) return eewJmaId == confirmedJmaId
        if (eew.id == confirmedJmaId) return true
        // EEW parsers retain the JMA event ID as the event key when available.
        if (eew.id.length == 14 && eew.id.all { it in '0'..'9' }) return false
    }

    // Public P2PQuake reports can omit the ID and use minute-precision origins.
    val eewOrigin = EewLifecyclePolicy.sourceMillis(eew.originTime) ?: return false
    val confirmedOrigin = EewLifecyclePolicy.sourceMillis(confirmed.originTime) ?: return false
    if (Math.floorDiv(eewOrigin, 60_000L) != Math.floorDiv(confirmedOrigin, 60_000L)) return false
    if (!eew.hasHypocenter || !confirmed.hasHypocenter) return true
    return abs(eew.latitude - confirmed.latitude) <= 3.0 &&
        abs(eew.longitude - confirmed.longitude) <= 3.0
}
