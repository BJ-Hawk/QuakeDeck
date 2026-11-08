package cz.misa.quakedeck.data

import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Test

class DiagnosticExportTest {
    @Test
    fun lastEventIncludesMatchingRevisionsAcrossSourcesAndOptionalConnectionContext() {
        val packets = listOf(
            p2p(1, """{"code":551,"issue":{"eventId":"previous"}}"""),
            connection(2),
            p2p(3, """{"code":556,"issue":{"eventId":"current"}}"""),
            connection(4),
            DmDssPacketDiagnostic(
                5, "IN", "websocket", "data",
                """{"type":"data","encoding":"utf-8","body":"{\"eventId\":\"current\"}"}"""
            ),
            p2p(6, """{"code":9611}"""),
            DmDssPacketDiagnostic(
                7, "LOCAL", "decision", "eew-decision",
                """{"type":"eew-decision","eventId":"previous"}"""
            )
        )
        val snapshot = DmDssDiagnosticsSnapshot(packetHistory = packets)

        val eventsOnly = export(snapshot, DiagnosticExportRange.LAST_EVENT, false, 7)
        assertEquals(listOf(3L, 5L), timestamps(eventsOnly))
        assertEquals("current", eventsOnly.getJSONObject("selection").getString("eventId"))
        assertEquals(listOf(3L, 4L, 5L), timestamps(export(snapshot, DiagnosticExportRange.LAST_EVENT, true, 7)))
    }

    @Test
    fun lastEventUsesOriginForPublicReportsWithoutAnEventId() {
        val packets = listOf(
            p2p(1, """{"code":551,"issue":{"eventId":"previous"}}"""),
            p2p(2, """{"code":551,"earthquake":{"time":"2026/11/08 12:30:00"}}"""),
            p2p(3, """{"code":551,"earthquake":{"time":"2026-11-08T12:30:00+09:00"}}""")
        )
        val snapshot = DmDssDiagnosticsSnapshot(packetHistory = packets)

        assertEquals(listOf(2L, 3L), timestamps(export(snapshot, DiagnosticExportRange.LAST_EVENT, false, 3)))
    }

    @Test
    fun timeSelectionUsesInclusiveBoundariesAndKeepsUnidentifiedEventPackets() {
        val snapshot = DmDssDiagnosticsSnapshot(packetHistory = listOf(
            p2p(1, """{"code":551}"""),
            p2p(2, """{"code":554}"""),
            connection(3),
            p2p(3_600_002, """{"code":9611}"""),
            p2p(3_600_003, """{"code":551}""")
        ))

        assertEquals(listOf(2L, 3_600_002L),
            timestamps(export(snapshot, DiagnosticExportRange.LAST_HOUR, false, 3_600_002)))
        assertEquals(listOf(2L, 3L, 3_600_002L),
            timestamps(export(snapshot, DiagnosticExportRange.LAST_HOUR, true, 3_600_002)))
    }

    @Test
    fun allEventPacketsIncludeTruncatedPublicReportsWithRetainedCodes() {
        val truncated = p2p(1, """{"code":551,"earthquake":""").copy(p2pCode = 551)
        val snapshot = DmDssDiagnosticsSnapshot(packetHistory = listOf(truncated, connection(2)))

        assertEquals(listOf(1L), timestamps(export(snapshot, DiagnosticExportRange.ALL, false, 2)))
    }

    private fun p2p(time: Long, payload: String) =
        DmDssPacketDiagnostic(time, "IN", "websocket", "json", payload, DIAGNOSTIC_SOURCE_P2PQUAKE)

    private fun connection(time: Long) = DmDssPacketDiagnostic(
        time, "LOCAL", "control", "reconnect-scheduled", """{"type":"reconnect-scheduled"}"""
    )

    private fun export(
        snapshot: DmDssDiagnosticsSnapshot,
        range: DiagnosticExportRange,
        includeConnectionPackets: Boolean,
        now: Long
    ) = JSONObject(snapshot.toSelectedDiagnosticJson(range, includeConnectionPackets, now))

    private fun timestamps(export: JSONObject): List<Long> {
        val packets = export.getJSONArray("packets")
        return List(packets.length()) { packets.getJSONObject(it).getLong("recordedAtMillis") }
    }
}
