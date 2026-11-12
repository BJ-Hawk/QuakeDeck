package cz.misa.quakedeck.data

import org.json.JSONObject

/** Restores the verified P2PQuake aggregate on the affected Chiba report. */
internal fun EarthquakeEvent.repairChibaFeltReports(attachIfMissing: Boolean = false): EarthquakeEvent {
    if (kind != EarthquakeEventKind.CONFIRMED || isDrill || isCancelled) return this
    val matchesChiba = jmaReportEventId?.let { it == CHIBA_JMA_EVENT_ID } ?: (
        id == "quake:jma:$CHIBA_JMA_EVENT_ID" ||
            (hasHypocenter && originTime == "2026-11-12 22:27:00 JST" &&
                latitude == 35.7 && longitude == 140.7 && reportType == "DetailScale" &&
                reportIssuedAt == "2026-11-12 22:30:20 JST")
        )
    if (!matchesChiba) return this

    val previous = p2pCrowdSignal
    when (previous?.startedAt) {
        null -> if (!attachIfMissing) return this
        ORIGINAL_CLUSTER, INCORRECT_CLUSTER -> Unit
        else -> return this
    }
    val corrected = verifiedChibaSignal.withAssociatedCrowdUpdate(previous)
    return if (corrected == previous) this else copy(p2pCrowdSignal = corrected)
}

private const val CHIBA_JMA_EVENT_ID = "20261112222726"
private const val ORIGINAL_CLUSTER = "2026/11/12 22:27:35.786"
private const val INCORRECT_CLUSTER = "2026/11/12 22:29:50.173"

// The aggregate received at 22:30:36.499 JST in the November 12 diagnostic export.
private val verifiedChibaSignal by lazy {
    checkNotNull(parseP2pCrowdSignal(JSONObject(
        """
        {
          "code": 9611,
          "started_at": "$ORIGINAL_CLUSTER",
          "updated_at": "2026/11/12 22:29:08.520",
          "count": 103,
          "confidence": 0.98052,
          "area_confidences": {
            "10":  {"confidence": -1, "count": 1, "display": "F"},
            "150": {"confidence": 0.023633227555580963, "count": 2, "display": "E"},
            "151": {"confidence": 0.011816613777790481, "count": 1, "display": "E"},
            "200": {"confidence": 0.12277888892922702, "count": 3, "display": "E"},
            "205": {"confidence": 0.7968867350425692, "count": 11, "display": "B"},
            "225": {"confidence": 0.0031403804335419024, "count": 1, "display": "E"},
            "230": {"confidence": 0.007078965170228963, "count": 1, "display": "E"},
            "231": {"confidence": 0.0505851052789278, "count": 7, "display": "E"},
            "240": {"confidence": 0.3852606520217037, "count": 6, "display": "D"},
            "241": {"confidence": 1, "count": 24, "display": "A"},
            "242": {"confidence": 0.4402978880248042, "count": 4, "display": "C"},
            "250": {"confidence": 0.0918300450066689, "count": 18, "display": "E"},
            "270": {"confidence": 0.1027253431021936, "count": 13, "display": "E"},
            "275": {"confidence": 0.002386892804077525, "count": 1, "display": "E"},
            "340": {"confidence": 0.060036021612967755, "count": 2, "display": "E"},
            "411": {"confidence": 0.002887210283489844, "count": 1, "display": "E"},
            "416": {"confidence": 0.0026247366213544036, "count": 1, "display": "E"},
            "490": {"confidence": -1, "count": 1, "display": "F"},
            "900": {"confidence": -1, "count": 5, "display": "F"}
          }
        }
        """.trimIndent()
    )))
}
