package cz.misa.quakedeck.data

import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class DmDssDiagnosticsTest {
    @Test
    fun packetHistoryExcludesRoutineHeartbeatsOnly() {
        assertFalse(shouldRetainDmDssPacketType("ping"))
        assertFalse(shouldRetainDmDssPacketType("pong"))
        assertTrue(shouldRetainDmDssPacketType("start"))
        assertTrue(shouldRetainDmDssPacketType("data"))
        assertTrue(shouldRetainDmDssPacketType("error"))
        assertTrue(shouldRetainDmDssPacketType(""))
    }

    @Test
    fun packetSanitizerRetainsBulletinButRemovesCredentials() {
        val sanitized = sanitizeDmDssPacket(
            """{"type":"data","ticket":"socket-secret","authorization":"Bearer access-secret","body":"forecast-body"}"""
        )
        val json = JSONObject(sanitized)

        assertEquals("data", json.getString("type"))
        assertEquals("forecast-body", json.getString("body"))
        assertEquals("[REDACTED]", json.getString("ticket"))
        assertEquals("[REDACTED]", json.getString("authorization"))
        assertFalse(sanitized.contains("socket-secret"))
        assertFalse(sanitized.contains("access-secret"))
    }

    @Test
    fun packetHistoryKeepsNewestEntriesWithinBothBounds() {
        val entries = (1L..4L).map { index ->
            DmDssPacketDiagnostic(index, "IN", "text", "ping", "x".repeat(80))
        }

        val countBounded = trimDmDssPacketHistory(entries, maxEntries = 2, maxBytes = 10_000)
        assertEquals(listOf(3L, 4L), countBounded.map { it.recordedAtMillis })

        val sizeBounded = trimDmDssPacketHistory(entries, maxEntries = 10, maxBytes = 250)
        assertTrue(sizeBounded.size < entries.size)
        assertEquals(4L, sizeBounded.last().recordedAtMillis)
    }

    @Test
    fun machineReadableExportHasSchemaSummaryAndSanitizedPackets() {
        val snapshot = DmDssDiagnosticsSnapshot(
            socketState = "Connected",
            connectedAtMillis = 100L,
            packetHistory = listOf(
                DmDssPacketDiagnostic(
                    recordedAtMillis = 200L,
                    direction = "IN",
                    transport = "text",
                    type = "start",
                    payload = """{"type":"start","ticket":"must-not-export"}"""
                )
            )
        )

        val export = JSONObject(snapshot.toMachineReadableJson(exportedAtMillis = 300L))

        assertEquals("cz.misa.quakedeck.dmdss-diagnostics", export.getString("schema"))
        assertEquals(1, export.getInt("schemaVersion"))
        assertEquals("Connected", export.getJSONObject("summary").getString("socketState"))
        assertEquals(1, export.getInt("packetCount"))
        val excludedTypes = export.getJSONObject("storage")
            .getJSONArray("excludedRoutinePacketTypes")
        assertEquals("ping", excludedTypes.getString(0))
        assertEquals("pong", excludedTypes.getString(1))
        val packet = export.getJSONArray("packets").getJSONObject(0)
        assertEquals("start", packet.getString("type"))
        assertFalse(packet.getString("payload").contains("must-not-export"))
        assertTrue(packet.getString("payload").contains("[REDACTED]"))
    }
}
