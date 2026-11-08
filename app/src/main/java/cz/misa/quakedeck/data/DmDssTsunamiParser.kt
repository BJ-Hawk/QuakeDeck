package cz.misa.quakedeck.data

import org.json.JSONArray
import org.json.JSONObject

internal sealed interface DmDssTsunamiPacket {
    data class Bulletin(val packet: JSONObject) : DmDssTsunamiPacket
    data object Ignored : DmDssTsunamiPacket
    data object Unreadable : DmDssTsunamiPacket
}

/** Adapts JMA coastal forecast zones to QuakeDeck's shared tsunami report format. */
internal object DmDssTsunamiParser {
    const val WARNING_TYPE = "VTSE41"
    const val INFORMATION_TYPE = "VTSE51"

    fun parseEnvelope(envelope: JSONObject): DmDssTsunamiPacket {
        val head = envelope.optJSONObject("head") ?: return DmDssTsunamiPacket.Unreadable
        if (head.optBoolean("test")) return DmDssTsunamiPacket.Ignored
        val type = head.optString("type")
        if (type !in DmDssFeed.TSUNAMI.types) return DmDssTsunamiPacket.Ignored
        if (envelope.optString("type") != "data" ||
            envelope.optString("classification") != DmDssFeed.TSUNAMI.classification ||
            envelope.optString("format") != "json"
        ) return DmDssTsunamiPacket.Unreadable

        val decoded = DmDssEewParser.decodeBody(envelope) as? DmDssBodyDecodeResult.Decoded
            ?: return DmDssTsunamiPacket.Unreadable
        val report = runCatching { JSONObject(decoded.text) }.getOrNull()
            ?: return DmDssTsunamiPacket.Unreadable
        if (report.optJSONObject("_schema")?.optString("type") != "tsunami-information") {
            return DmDssTsunamiPacket.Unreadable
        }
        when (report.optString("status")) {
            "訓練", "試験" -> return DmDssTsunamiPacket.Ignored
            "通常" -> Unit
            else -> return DmDssTsunamiPacket.Unreadable
        }

        val infoType = report.optString("infoType")
        if (infoType !in setOf("発表", "訂正", "遅延", "取消")) return DmDssTsunamiPacket.Unreadable
        val withdrawal = infoType == "取消"
        // Withdrawing an observational bulletin does not lift a tsunami warning.
        if (withdrawal && type != WARNING_TYPE) return DmDssTsunamiPacket.Ignored

        val issued = listOf(report.optString("pressDateTime"), report.optString("reportDateTime"))
            .firstOrNull { EewLifecyclePolicy.sourceMillis(it) != null }
            ?: return DmDssTsunamiPacket.Unreadable
        val forecasts = report.optJSONObject("body")?.optJSONObject("tsunami")?.optJSONArray("forecasts")
        if (!withdrawal && (forecasts == null || forecasts.length() == 0)) {
            return if (type == INFORMATION_TYPE) DmDssTsunamiPacket.Ignored
            else DmDssTsunamiPacket.Unreadable
        }

        val areas = JSONArray()
        var hasActiveAlert = false
        var hasRelease = false
        if (!withdrawal && forecasts != null) {
            for (index in 0 until forecasts.length()) {
                val forecast = forecasts.optJSONObject(index) ?: return DmDssTsunamiPacket.Unreadable
                val name = forecast.optString("name").takeIf { it.isNotBlank() && it != "null" }
                    ?: return DmDssTsunamiPacket.Unreadable
                val code = forecast.optJSONObject("kind")?.optString("code")
                val grade = when (code) {
                    "52", "53" -> "MajorWarning"
                    "51" -> "Warning"
                    "62" -> "Watch"
                    "71", "72", "73" -> "Forecast"
                    "00", "50", "60" -> "None"
                    else -> return DmDssTsunamiPacket.Unreadable
                }
                hasActiveAlert = hasActiveAlert || code in setOf("51", "52", "53", "62")
                hasRelease = hasRelease || code in setOf("50", "60", "72", "73")
                val firstHeight = forecast.optJSONObject("firstHeight")
                val height = forecast.optJSONObject("maxHeight")?.optJSONObject("height")
                val metres = height?.optDouble("value", Double.NaN)?.takeIf { it.isFinite() && it > 0.0 }
                val description = height?.optString("condition")?.takeIf { it.isNotBlank() && it != "null" }
                    ?: height?.optString("value")?.takeIf { metres != null }?.let { value ->
                        val prefix = if (height.optBoolean("over")) ">" else ""
                        "$prefix$value m"
                    }
                areas.put(JSONObject()
                    .put("name", name)
                    .put("grade", grade)
                    .put("firstHeight", firstHeight)
                    .put("maxHeight", JSONObject().put("description", description).put("value", metres)))
            }
        }

        // Releases are national only when the bulletin has no remaining warning or advisory.
        val cancelled = withdrawal || type == WARNING_TYPE && hasRelease && !hasActiveAlert
        val id = envelope.optString("id").takeIf { it.isNotBlank() && it != "null" }
            ?: "$type:$issued"
        val validUntil = report.optString("validDateTime").takeIf {
            EewLifecyclePolicy.sourceMillis(it) != null
        }
        return DmDssTsunamiPacket.Bulletin(JSONObject()
            .put("code", 552)
            .put("id", "dm-dss:$id")
            .put("time", issued)
            .put("issue", JSONObject().put("time", issued).put("type", type))
            .put("expire", validUntil)
            .put("cancelled", cancelled)
            .put("areas", areas))
    }
}
