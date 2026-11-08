package cz.misa.quakedeck.data

import org.json.JSONObject

internal enum class DiagnosticExportRange(val durationMillis: Long? = null) {
    LAST_EVENT,
    LAST_HOUR(3_600_000L),
    LAST_DAY(24 * 3_600_000L),
    LAST_WEEK(7 * 24 * 3_600_000L),
    ALL
}

internal fun DmDssDiagnosticsSnapshot.toSelectedDiagnosticJson(
    range: DiagnosticExportRange,
    includeConnectionPackets: Boolean,
    exportedAtMillis: Long = System.currentTimeMillis()
): String {
    val sinceMillis = range.durationMillis?.let { exportedAtMillis - it } ?: Long.MIN_VALUE
    val candidates = packetHistory.asSequence()
        .filter { it.recordedAtMillis in sinceMillis..exportedAtMillis }
        .map { it.classifyForExport(range == DiagnosticExportRange.LAST_EVENT) }
        .toList()
    val eventId = if (range == DiagnosticExportRange.LAST_EVENT) {
        val identified = candidates.filter { it.eventId != null }
        (identified.filterNot { it.packet.type == "eew-decision" }
            .maxByOrNull { it.packet.recordedAtMillis }
            ?: identified.maxByOrNull { it.packet.recordedAtMillis })?.eventId
    } else null
    val eventStart = candidates.filter { eventId != null && it.eventId == eventId }
        .minOfOrNull { it.packet.recordedAtMillis }
    val selectedPackets = candidates.filter { candidate ->
        if (range != DiagnosticExportRange.LAST_EVENT) {
            candidate.isEvent || includeConnectionPackets
        } else if (candidate.isEvent) {
            eventId != null && candidate.eventId == eventId
        } else {
            includeConnectionPackets && eventStart != null &&
                candidate.packet.recordedAtMillis >= eventStart
        }
    }.map { it.packet }
    val selection = JSONObject()
        .put("range", range.name)
        .put("includeConnectionPackets", includeConnectionPackets)
        .put("eventId", eventId?.let(::sanitizeDmDssDiagnosticText) ?: JSONObject.NULL)
    return copy(packetHistory = selectedPackets).toMachineReadableJson(exportedAtMillis, selection)
}

private data class ClassifiedPacket(
    val packet: DmDssPacketDiagnostic,
    val isEvent: Boolean,
    val eventId: String? = null
)

private fun DmDssPacketDiagnostic.classifyForExport(identifyEvent: Boolean): ClassifiedPacket {
    val json = runCatching { JSONObject(payload) }.getOrNull()
    if (type == "eew-decision") {
        return ClassifiedPacket(this, true, if (identifyEvent) json?.eventId("eventId") else null)
    }
    if (source == DIAGNOSTIC_SOURCE_P2PQUAKE) {
        val code = p2pCode ?: json?.optInt("code") ?: -1
        val isEvent = code in p2pEventCodes
        val eventId = if (isEvent && identifyEvent) {
            json?.optJSONObject("issue")?.eventId("eventId") ?: json?.eventId("eventId")
                ?: when (code) {
                    551 -> EewLifecyclePolicy.sourceMillis(json?.optJSONObject("earthquake")?.optString("time"))
                        ?.let { "p2p-origin:$it" }
                    552 -> json?.eventId("id")?.let { "p2p-tsunami:$it" }
                    else -> null
                }
        } else null
        return ClassifiedPacket(this, isEvent, eventId)
    }
    if (type != "data") return ClassifiedPacket(this, false)
    if (!identifyEvent || json == null) return ClassifiedPacket(this, true)
    val decoded = DmDssEewParser.decodeBody(json) as? DmDssBodyDecodeResult.Decoded
    val report = decoded?.let { runCatching { JSONObject(it.text) }.getOrNull() }
    val isDrill = json.optJSONObject("head")?.optBoolean("test") == true ||
        report?.optString("status") in setOf("訓練", "試験")
    val eventId = report?.eventId("eventId")?.let { if (isDrill) "drill:dmdss:$it" else it }
    return ClassifiedPacket(this, true, eventId)
}

private fun JSONObject.eventId(key: String): String? =
    optString(key).takeIf { it.isNotBlank() && it != "null" }

private val p2pEventCodes = setOf(551, 552, 554, 556, 561, P2P_CROWD_SIGNAL_CODE)
