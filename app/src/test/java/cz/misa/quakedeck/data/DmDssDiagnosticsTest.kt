package cz.misa.quakedeck.data

import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
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
    fun p2pPacketHistoryExcludesHeartbeatsAndRoutinePeerCounts() {
        assertFalse(shouldRetainP2pDiagnosticPacket(JSONObject("""{"type":"ping"}""")))
        assertFalse(shouldRetainP2pDiagnosticPacket(JSONObject("""{"type":"pong"}""")))
        assertFalse(shouldRetainP2pDiagnosticPacket(JSONObject("""{"code":555}""")))
        assertTrue(shouldRetainP2pDiagnosticPacket(JSONObject("""{"code":551}""")))
        assertTrue(shouldRetainP2pDiagnosticPacket(null))
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
    fun packetHistoryTrimsAtWhicheverBoundaryComesLater() {
        val entries = (1L..4L).map { index ->
            DmDssPacketDiagnostic(index, "IN", "text", "ping", "x".repeat(80))
        }

        val bytesNotReached = trimDmDssPacketHistory(entries, maxEntries = 2, maxBytes = 10_000)
        assertEquals(entries, bytesNotReached)

        val entriesNotReached = trimDmDssPacketHistory(entries, maxEntries = 10, maxBytes = 250)
        assertEquals(entries, entriesNotReached)

        val bothReached = trimDmDssPacketHistory(entries, maxEntries = 2, maxBytes = 250)
        assertEquals(listOf(3L, 4L), bothReached.map { it.recordedAtMillis })
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
        assertEquals(3, export.getInt("schemaVersion"))
        assertEquals("Connected", export.getJSONObject("summary").getString("socketState"))
        assertEquals(1, export.getInt("packetCount"))
        assertEquals(
            "whicheverComesLater",
            export.getJSONObject("storage").getString("retentionBoundary")
        )
        val excludedTypes = export.getJSONObject("storage")
            .getJSONArray("excludedRoutinePacketTypes")
        assertEquals("ping", excludedTypes.getString(0))
        assertEquals("pong", excludedTypes.getString(1))
        val excludedCodes = export.getJSONObject("storage")
            .getJSONArray("excludedRoutinePacketCodes")
        assertEquals(P2P_ROUTINE_PEER_COUNT_CODE, excludedCodes.getInt(0))
        val packet = export.getJSONArray("packets").getJSONObject(0)
        assertEquals(DIAGNOSTIC_SOURCE_DMDSS, packet.getString("source"))
        assertEquals("start", packet.getString("type"))
        assertFalse(packet.getString("payload").contains("must-not-export"))
        assertTrue(packet.getString("payload").contains("[REDACTED]"))
    }

    @Test
    fun readablePacketHistorySummarizesBothProvidersWithoutReplacingRawPayload() {
        val p2pPayload = """
            {
              "code":556,
              "issue":{"serial":"2"},
              "areas":[{"name":"熊本県熊本"}]
            }
        """.trimIndent()
        val packets = listOf(
            DmDssPacketDiagnostic(
                recordedAtMillis = 100L,
                direction = "IN",
                transport = "text",
                type = "start",
                payload = """{"type":"start"}"""
            ),
            DmDssPacketDiagnostic(
                recordedAtMillis = 200L,
                direction = "IN",
                transport = "text",
                type = "json",
                payload = p2pPayload,
                source = DIAGNOSTIC_SOURCE_P2PQUAKE
            )
        )

        val readable = humanReadablePacketDiagnostics(packets)

        assertEquals("WebSocket Start", readable[0].summary)
        assertEquals("EEW Report #2", readable[1].summary)
        assertEquals("熊本県熊本", readable[1].detail)
        assertEquals(p2pPayload, readable[1].packet.payload)

        val rawExport = JSONObject(
            DmDssDiagnosticsSnapshot(packetHistory = packets).toMachineReadableJson()
        ).getJSONArray("packets").getJSONObject(1)
        assertEquals(DIAGNOSTIC_SOURCE_P2PQUAKE, rawExport.getString("source"))
        assertEquals(p2pPayload, rawExport.getString("payload"))
    }

    @Test
    fun readablePacketHistoryDecodesDmDssForecastBody() {
        val packet = DmDssPacketDiagnostic(
            recordedAtMillis = 100L,
            direction = "IN",
            transport = "text",
            type = "data",
            payload = forecastEnvelope().toString()
        )

        val readable = humanReadablePacketDiagnostics(listOf(packet)).single()

        assertEquals("EEW Report #2", readable.summary)
        assertEquals("熊本県熊本", readable.detail)
        assertEquals(DiagnosticPacketDetailKind.REPORTING_AREA, readable.detailKind)
    }

    @Test
    fun latestPingTrackerSuppressesQueuedStalePingId() {
        val tracker = LatestDmDssPingTracker()
        val stale = tracker.receive("older")
        val latest = tracker.receive("newest")

        assertNull(tracker.payloadIfLatest(stale))
        assertEquals(
            "newest",
            JSONObject(requireNotNull(tracker.payloadIfLatest(latest))).getString("pingId")
        )
    }

    @Test
    fun readablePacketHistoryNamesSocketFailureAndReconnectControlPackets() {
        val packets = listOf(
            DmDssPacketDiagnostic(
                100L,
                "IN",
                "failure",
                "websocket-failure",
                """{"type":"websocket-failure"}"""
            ),
            DmDssPacketDiagnostic(
                200L,
                "OUT",
                "control",
                "reconnect-scheduled",
                """{"type":"reconnect-scheduled","delayMillis":2000}"""
            )
        )

        assertEquals(
            listOf("WebSocket Failure", "Reconnect Scheduled"),
            humanReadablePacketDiagnostics(packets).map { it.summary }
        )
    }

    private fun forecastEnvelope(): JSONObject {
        val report = JSONObject()
            .put("_schema", JSONObject().put("type", "eew-information"))
            .put("status", "通常")
            .put("infoType", "発表")
            .put("eventId", "20260826113348")
            .put("serialNo", "2")
            .put("reportDateTime", "2099-08-26T11:33:58+09:00")
            .put(
                "body",
                JSONObject()
                    .put("isCanceled", false)
                    .put(
                        "earthquake",
                        JSONObject()
                            .put("originTime", "2099-08-26T11:33:48+09:00")
                            .put(
                                "hypocenter",
                                JSONObject()
                                    .put("name", "熊本県熊本地方")
                                    .put(
                                        "coordinate",
                                        JSONObject()
                                            .put("latitude", JSONObject().put("value", "32.8"))
                                            .put("longitude", JSONObject().put("value", "130.7"))
                                    )
                                    .put("depth", JSONObject().put("value", "10"))
                            )
                            .put("magnitude", JSONObject().put("value", "3.4"))
                    )
                    .put(
                        "intensity",
                        JSONObject()
                            .put("forecastMaxInt", JSONObject().put("from", "3").put("to", "3"))
                            .put(
                                "regions",
                                org.json.JSONArray().put(
                                    JSONObject()
                                        .put("name", "熊本県熊本")
                                        .put(
                                            "forecastMaxInt",
                                            JSONObject().put("from", "3").put("to", "3")
                                        )
                                )
                            )
                    )
            )
        return JSONObject()
            .put("type", "data")
            .put("classification", "eew.forecast")
            .put("id", "telegram-hash")
            .put("format", "json")
            .put("head", JSONObject().put("type", "VXSE45").put("test", false))
            .put("body", report.toString())
    }
}
