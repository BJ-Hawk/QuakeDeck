package cz.misa.quakedeck.data

import org.json.JSONArray
import org.json.JSONObject
import java.time.LocalDateTime
import java.time.ZoneOffset
import java.time.format.DateTimeFormatter

/** Adapts JMA's bulletin to the shared confirmed-report archive and presentation format. */
internal object DmDssConfirmedReportParser {
    private val eventIdFormat = DateTimeFormatter.ofPattern("uuuuMMddHHmmss")

    fun parseEnvelope(envelope: JSONObject): JSONObject? {
        if (envelope.optString("type") != "data") return null
        if (envelope.optString("classification") != DmDssFeed.CONFIRMED.classification ||
            envelope.optString("format") != "json"
        ) return null
        val head = envelope.optJSONObject("head") ?: return null
        if (head.optBoolean("test")) return null
        val telegramType = head.optString("type")
        if (telegramType !in DmDssFeed.CONFIRMED.types) return null
        val decoded = DmDssEewParser.decodeBody(envelope)
            as? DmDssBodyDecodeResult.Decoded ?: return null
        val report = runCatching { JSONObject(decoded.text) }.getOrNull() ?: return null
        if (report.optJSONObject("_schema")?.optString("type") != "earthquake-information" ||
            report.optString("status") != "通常"
        ) return null

        val infoType = report.optString("infoType")
        if (infoType !in setOf("発表", "訂正", "遅延", "取消")) return null
        val issued = listOf(report.optString("pressDateTime"), report.optString("reportDateTime"))
            .firstOrNull { EewLifecyclePolicy.sourceMillis(it) != null } ?: return null
        val eventId = report.optString("eventId").takeIf { it.isNotBlank() && it != "null" }
        val body = report.optJSONObject("body") ?: return null
        val earthquake = body.optJSONObject("earthquake")
        val cancelled = infoType == "取消"
        val origin = earthquake?.optString("originTime")
            ?: report.optString("targetDateTime").takeIf {
                telegramType == "VXSE51" && !cancelled && EewLifecyclePolicy.sourceMillis(it) != null
            }
            ?: eventId?.let {
                runCatching {
                    LocalDateTime.parse(it, eventIdFormat).atOffset(ZoneOffset.ofHours(9))
                        .format(DateTimeFormatter.ISO_OFFSET_DATE_TIME)
                }.getOrNull()
            }
            ?: return null
        if (EewLifecyclePolicy.sourceMillis(origin) == null) return null

        val reportType = when (telegramType) {
            "VXSE51" -> "ScalePrompt"
            "VXSE52" -> "Destination"
            else -> if ("遠地地震" in report.optString("title")) "Foreign" else "DetailScale"
        }
        val hypocenter = earthquake?.optJSONObject("hypocenter")
        val coordinate = hypocenter?.optJSONObject("coordinate")
        val intensity = body.optJSONObject("intensity")
        val issue = JSONObject().put("source", "JMA")
            .put("time", p2pTime(issued))
            .put("type", reportType)
            .put("telegramType", telegramType)
            .put("eventId", eventId)
            .put("correct", when {
                cancelled -> "Cancelled"
                infoType != "訂正" -> "None"
                telegramType == "VXSE51" -> "ScaleOnly"
                telegramType == "VXSE52" -> "DestinationOnly"
                else -> "ScaleAndDestination"
            })

        return JSONObject()
            .put("code", 551)
            .put("id", envelope.optString("id").takeIf(String::isNotBlank)?.let { "dm-dss:$it" })
            .put("issue", issue)
            .put("time", p2pTime(issued))
            .put("cancelled", cancelled)
            .put("earthquake", JSONObject()
                .put("time", p2pTime(origin))
                .put("maxScale", scale(intensity?.optString("maxInt")))
                .put("hypocenter", JSONObject()
                    .put("name", hypocenter?.optString("name"))
                    .put("latitude", coordinate?.optJSONObject("latitude")?.optString("value"))
                    .put("longitude", coordinate?.optJSONObject("longitude")?.optString("value"))
                    .put("depth", hypocenter?.optJSONObject("depth")?.optString("value"))
                    .put("magnitude", earthquake?.optJSONObject("magnitude")?.optString("value"))))
            .put("points", intensityPoints(intensity))
    }

    private fun p2pTime(value: String): String =
        EewLifecyclePolicy.formatJst(value).removeSuffix(" JST").replace('-', '/')

    private fun intensityPoints(intensity: JSONObject?): JSONArray {
        val points = JSONArray()
        val prefectures = intensity?.optJSONArray("prefectures").objects()
        fun addPoints(rows: List<JSONObject>, intensityField: String, isArea: Boolean) {
            rows.forEach { row ->
                val name = row.optString("name")
                val scale = scale(row.optString(intensityField))
                if (name.isBlank() || scale < 0) return@forEach
                val prefecture = prefectures.firstOrNull { name.startsWith(it.optString("name")) }
                points.put(JSONObject()
                    .put("addr", name)
                    .put("scale", scale)
                    .put("isArea", isArea)
                    .put("pref", prefecture?.optString("name"))
                    .put(if (isArea) "regionCode" else "stationCode",
                        row.optString("code").takeIf { !isArea || it.length == 3 }))
            }
        }
        addPoints(intensity?.optJSONArray("stations").objects(), "int", false)
        if (points.length() == 0) {
            addPoints(intensity?.optJSONArray("regions").objects(), "maxInt", true)
        }
        if (points.length() == 0) addPoints(prefectures, "maxInt", true)
        return points
    }

    private fun JSONArray?.objects(): List<JSONObject> =
        if (this == null) emptyList() else (0 until length()).mapNotNull(::optJSONObject)

    private fun scale(value: String?): Int = when (value) {
        "1" -> 10
        "2" -> 20
        "3" -> 30
        "4" -> 40
        "5-" -> 45
        "5+" -> 50
        "6-" -> 55
        "6+" -> 60
        "7" -> 70
        else -> -1
    }
}

internal fun shouldUseP2pConfirmedReport(issueType: String?, dmdssTypes: Set<String>): Boolean {
    if (dmdssTypes.containsAll(DmDssFeed.CONFIRMED.types)) return false
    val type = p2pConfirmedTelegramType(issueType) ?: return true
    return type !in dmdssTypes
}

private fun p2pConfirmedTelegramType(issueType: String?): String? = when (issueType?.trim()?.lowercase()) {
    "scaleprompt" -> "VXSE51"
    "destination" -> "VXSE52"
    "scaleanddestination", "detailscale", "foreign" -> "VXSE53"
    else -> null
}
