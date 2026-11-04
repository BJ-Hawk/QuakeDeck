package cz.misa.quakedeck.data

import org.json.JSONObject

/** A disposable list projection. Complete source payloads remain in reports.raw_json. */
internal object HistoricalCatalogPayload {
    private val fields = listOf("id", "code", "time", "issue", "earthquake", "cancelled",
        "quakedeckAlertLevel", "quakedeckMaxIntensity")

    fun fromReport(report: JSONObject): JSONObject {
        val result = JSONObject()
        fields.forEach { key ->
            if (report.has(key)) result.put(key, report.opt(key))
        }
        // Legacy public 556 packets derive maximum Shindo from areas. Retain
        // that value before omitting the regions; decoding a bare cancellation
        // still uses the preceding revision, just as full replay does.
        if (report.optInt("code") == 556 &&
            report.optString("quakedeckMaxIntensity").let { it.isBlank() || it == "null" }) {
            P2pEewParser.parse(report)?.let { result.put("quakedeckMaxIntensity", it.maxIntensity) }
        }
        return result
    }
}
