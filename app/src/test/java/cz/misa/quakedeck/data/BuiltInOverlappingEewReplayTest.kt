package cz.misa.quakedeck.data

import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class BuiltInOverlappingEewReplayTest {
    @Test
    fun packetsShareOneTimelineAndStartTwoDistinctMappableWarnings() {
        val firstTimestamp = requireNotNull(
            EewLifecyclePolicy.sourceMillis(BuiltInOverlappingEewReplay.FIRST_TIMESTAMP_JST)
        )
        assertEquals("2024-01-02 12:00:10 JST", EewLifecyclePolicy.formatJst(firstTimestamp))
        assertEquals(listOf(0L, 12_000L, 75_000L, 95_000L), packets.map { it.offsetMillis })
        assertEquals(100_000L, BuiltInOverlappingEewReplay.COMPLETE_AFTER_MILLIS)

        val ids = packets.map { packet ->
            assertEquals(556, packet.json.getInt("code"))
            val issue = packet.json.getJSONObject("issue")
            assertEquals(
                firstTimestamp + packet.offsetMillis,
                EewLifecyclePolicy.sourceMillis(issue.getString("time"))
            )
            issue.getString("eventId")
        }
        assertEquals(listOf(notoId, shizuokaId, notoId, shizuokaId), ids)

        val warnings = packets.take(2).map { requireNotNull(P2pEewParser.parse(it.json)) }
        assertEquals(
            listOf("2024-01-02 12:00:00 JST", "2024-01-02 12:00:12 JST"),
            warnings.map { it.originTime }
        )
        warnings.forEach { warning ->
            assertEquals(EarthquakeEventKind.EEW, warning.kind)
            assertEquals(EewAlertLevel.WARNING, warning.eewAlertLevel)
            assertEquals(10, warning.depthKm)
            assertTrue(warning.hasJapanMapEpicenter())
            assertFalse(warning.isCancelled)
        }
        assertTrue(warnings[0].latitude != warnings[1].latitude)
        assertTrue(warnings[0].longitude != warnings[1].longitude)
    }

    @Test
    fun cancellingTheFirstWarningLeavesTheSecondWarningScheduled() {
        val (notoPacket, shizuokaPacket, notoEndPacket, shizuokaEndPacket) = packets
        val pending = PendingEewExpiries()
        val noto = requireNotNull(P2pEewParser.parse(notoPacket.json))
        val shizuoka = requireNotNull(P2pEewParser.parse(shizuokaPacket.json))
        val notoIssued = requireNotNull(EewLifecyclePolicy.sourceMillis(noto.reportIssuedAt))
        val shizuokaIssued = requireNotNull(EewLifecyclePolicy.sourceMillis(shizuoka.reportIssuedAt))
        val notoDeadline = EewLifecyclePolicy.deadline(noto, notoIssued)
        val shizuokaDeadline = EewLifecyclePolicy.deadline(shizuoka, shizuokaIssued)
        pending.schedule(noto, notoDeadline)
        pending.schedule(shizuoka, shizuokaDeadline)

        val firstEndTime = notoIssued + notoEndPacket.offsetMillis
        assertTrue(pending.extractDue(firstEndTime).isEmpty())
        assertEquals(listOf(shizuokaId, notoId), pending.newestFirst().map { it.event.id })
        assertNull(P2pEewParser.parse(notoEndPacket.json, pending.incident(shizuokaId)?.event))
        val endedNoto = requireNotNull(P2pEewParser.parse(notoEndPacket.json, pending.incident(notoId)?.event))
        assertEquals(notoId, endedNoto.id)
        assertTrue(endedNoto.isCancelled)
        assertEquals(noto, pending.remove(endedNoto.id))
        assertEquals(listOf(PendingEewExpiry(shizuoka, shizuokaDeadline)), pending.newestFirst())
        assertEquals(shizuokaDeadline, pending.nextDeadlineMillis)

        assertTrue(pending.extractDue(notoIssued + shizuokaEndPacket.offsetMillis).isEmpty())
        val endedShizuoka = requireNotNull(
            P2pEewParser.parse(shizuokaEndPacket.json, pending.incident(shizuokaId)?.event)
        )
        assertEquals(shizuokaId, endedShizuoka.id)
        assertTrue(endedShizuoka.isCancelled)
        assertEquals(shizuoka, pending.remove(endedShizuoka.id))
        assertNull(pending.nextDeadlineMillis)
    }

    private data class Packet(val offsetMillis: Long, val json: JSONObject)

    private val packets = BuiltInOverlappingEewReplay.packets.map { Packet(it.offsetMillis, JSONObject(it.json)) }
    private val notoId = "sandbox-overlap-noto"
    private val shizuokaId = "sandbox-overlap-shizuoka"
}
