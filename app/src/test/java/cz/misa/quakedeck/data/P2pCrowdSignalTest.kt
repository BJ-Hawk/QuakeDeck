package cz.misa.quakedeck.data

import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class P2pCrowdSignalTest {
    @Test
    fun `parses aggregate without inventing earthquake properties`() {
        val signal = parseP2pCrowdSignal(
            JSONObject(
                """
                {
                  "code": 9611,
                  "count": 103,
                  "confidence": 0.98052,
                  "started_at": "2026/08/30 00:17:23.111",
                  "updated_at": "2026/08/30 00:18:00.702",
                  "area_confidences": {
                    "200": {"confidence": 0.6354, "count": 6, "display": "B"},
                    "205": {"confidence": 0.9967, "count": 11, "display": "A"}
                  }
                }
                """.trimIndent()
            )
        )

        requireNotNull(signal)
        assertEquals("2026/08/30 00:17:23.111", signal.startedAt)
        assertEquals(103, signal.reportCount)
        assertEquals(0.98052, signal.confidence, 0.000001)
        assertEquals(listOf("205", "200"), signal.areas.map { it.areaCode })
        assertEquals("A", signal.areas.first().displayGrade)
    }

    @Test
    fun `requires aggregate code and incident key`() {
        assertNull(parseP2pCrowdSignal(JSONObject("""{"code":561}""")))
        assertNull(parseP2pCrowdSignal(JSONObject("""{"code":9611,"count":4}""")))
    }

    @Test
    fun `falls back to packet time when updated time is absent`() {
        val signal = parseP2pCrowdSignal(
            JSONObject(
                """
                {
                  "code": 9611,
                  "count": 1,
                  "confidence": 0,
                  "started_at": "2026/08/30 01:25:50.646",
                  "time": "2026/08/30 01:25:50.829",
                  "area_confidences": {}
                }
                """.trimIndent()
            )
        )

        assertEquals("2026/08/30 01:25:50.829", requireNotNull(signal).updatedAt)
        assertEquals(emptyList<P2pCrowdAreaSignal>(), signal.areas)
    }

    @Test
    fun `coincides with official event using existing ten second event window`() {
        val signal = P2pCrowdSignal(
            startedAt = "2026/08/30 00:17:23.111",
            updatedAt = "2026/08/30 00:18:00.702",
            reportCount = 184,
            confidence = 0.98052,
            areas = emptyList()
        )

        assertTrue(signal.coincidesWith(confirmedEvent("2026-08-30 00:17:16 JST")))
        assertFalse(signal.coincidesWith(confirmedEvent("2026-08-30 00:17:11 JST")))
    }

    @Test
    fun `official amendment can retain felt report evidence on same event`() {
        val signal = P2pCrowdSignal(
            startedAt = "2026/08/30 00:17:23.111",
            updatedAt = "2026/08/30 00:18:00.702",
            reportCount = 184,
            confidence = 0.98052,
            areas = emptyList()
        )
        val crowdEvent = confirmedEvent("2026-08-30 00:17:23 JST").copy(
            id = "crowd:${signal.startedAt}",
            reportType = null,
            hasHypocenter = false,
            p2pCrowdSignal = signal
        )
        val amended = crowdEvent.copy(
            place = "East off Chiba Prefecture",
            magnitude = 4.8,
            depthKm = 50,
            reportType = "ScaleAndDestination"
        )

        assertEquals(crowdEvent.id, amended.id)
        assertEquals(184, amended.p2pCrowdSignal?.reportCount)
        assertFalse(amended.isP2pCrowdOnly())
    }

    @Test
    fun `single zero confidence report remains diagnostics only`() {
        val signal = P2pCrowdSignal(
            startedAt = "2026/08/30 17:52:50.899",
            updatedAt = "2026/08/30 17:52:51.164",
            reportCount = 1,
            confidence = 0.0,
            areas = emptyList()
        )

        assertFalse(signal.isInformative())
    }

    @Test
    fun `informative felt cluster can belong to active eew after origin`() {
        val signal = P2pCrowdSignal(
            startedAt = "2026/08/30 00:21:23.111",
            updatedAt = "2026/08/30 00:21:31.702",
            reportCount = 7,
            confidence = 0.72,
            areas = emptyList()
        )
        val eew = confirmedEvent("2026-08-30 00:17:23 JST").copy(
            id = "20260830001723",
            kind = EarthquakeEventKind.EEW
        )

        assertTrue(signal.canBelongToEew(eew))
        assertFalse(signal.canBelongToEew(eew.copy(isCancelled = true)))
    }

    private fun confirmedEvent(originTime: String) = EarthquakeEvent(
        id = "quake:$originTime",
        place = "East off Chiba Prefecture",
        originTime = originTime,
        magnitude = 4.8,
        depthKm = 50,
        maxIntensity = "3",
        latitude = 35.5,
        longitude = 141.0,
        points = emptyList(),
        reportType = "ScaleAndDestination"
    )
}
