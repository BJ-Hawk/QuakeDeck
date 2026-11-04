package cz.misa.quakedeck.data

import cz.misa.quakedeck.notificationSnapshotForProviderUpdate
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.util.Base64
import java.util.zip.GZIPInputStream
import java.util.zip.GZIPOutputStream
import org.json.JSONObject
import org.junit.Assert.*
import org.junit.Test

/** Exercise variants of a retained production packet; source fields/timing stay intact. */
class SilentTestDrillFlowTest {
    private val seed = JSONObject(requireNotNull(javaClass.classLoader!!
        .getResourceAsStream("incidents/eew-20261016.json")).bufferedReader().use { it.readText() })
        .getJSONArray("packets").let { packets ->
            (0 until packets.length()).asSequence().map { packets.getJSONObject(it) }.first {
                it.getString("source") == "DM-D.S.S" && it.getString("type") == "data" &&
                    JSONObject(it.getString("payload")).getJSONObject("head").getString("type") == "VXSE45"
            }
        }
    private val receivedAt = seed.getLong("recordedAtMillis")

    private fun envelope(status: String = "通常", test: Boolean = false,
        type: String = "VXSE45", cancelled: Boolean = false): JSONObject {
        val envelope = JSONObject(seed.getString("payload"))
        envelope.getJSONObject("head").put("test", test).put("type", type)
        val report = JSONObject(GZIPInputStream(ByteArrayInputStream(
            Base64.getDecoder().decode(envelope.getString("body")))).bufferedReader().use { it.readText() })
        report.put("status", status)
        if (cancelled) {
            report.put("infoType", "取消")
            report.getJSONObject("body").put("isCanceled", true)
        }
        val bytes = ByteArrayOutputStream().apply {
            GZIPOutputStream(this).use { it.write(report.toString().toByteArray(Charsets.UTF_8)) }
        }.toByteArray()
        return envelope.put("body", Base64.getEncoder().encodeToString(bytes))
    }

    private fun parse(envelope: JSONObject, previous: EarthquakeEvent? = null, enabled: Boolean = true) =
        DmDssEewParser.parseEnvelopeResult(envelope, previous, receivedAt, enabled)

    private fun event(drill: Boolean) = (parse(
        envelope(if (drill) "訓練" else "通常", drill)
    ) as DmDssEewParseResult.Accepted).update.event

    @Test fun exercisesRemainOptInForBothProductsAndEveryKnownMarker() {
        for (type in listOf("VXSE44", "VXSE45")) {
            for ((status, header) in listOf("通常" to true, "訓練" to true,
                    "訓練" to false, "試験" to false)) {
                val wire = envelope(status, header, type)
                assertNull(DmDssEewParser.parseEnvelope(wire, null, receivedAt))
                assertEquals(DmDssEewRejection.TEST_BULLETIN,
                    (parse(wire, enabled = false) as DmDssEewParseResult.Rejected).reason)
                val update = (parse(wire) as DmDssEewParseResult.Accepted).update
                assertTrue(update.event.isDrill)
                assertTrue(update.event.id.startsWith("drill:dmdss:"))
                assertEquals(type, update.event.reportType)
                assertEquals(LiveUpdateKind.EEW, update.kind)
                assertTrue(update.status.startsWith("DRILL · "))
            }
        }
    }

    @Test fun enablingExercisesDoesNotRelaxOtherParserValidationOrRelabelRealEvents() {
        val real = (parse(envelope()) as DmDssEewParseResult.Accepted).update.event
        assertFalse(real.isDrill)
        assertEquals(DmDssEewParser.parseEnvelope(envelope(), null, receivedAt)?.event, real)
        assertEquals(DmDssEewRejection.NON_STANDARD_STATUS,
            (parse(envelope("unknown", true)) as DmDssEewParseResult.Rejected).reason)
        assertEquals(DmDssEewRejection.WRONG_TYPE,
            (parse(envelope("訓練", true, "VXSE43")) as DmDssEewParseResult.Rejected).reason)
        assertEquals(DmDssEewRejection.WRONG_CLASSIFICATION,
            (parse(envelope("訓練", true).put("classification", "eew.warning"))
                as DmDssEewParseResult.Rejected).reason)
    }

    @Test fun drillCancellationCannotCancelARealEventWithTheSameSourceId() {
        val real = event(false)
        val drill = event(true)
        assertNotEquals(real.id, drill.id)
        val drillCancellation = envelope("訓練", true, cancelled = true)
        assertEquals(DmDssEewRejection.CANCELLATION_WITHOUT_EVENT,
            (parse(drillCancellation, real) as DmDssEewParseResult.Rejected).reason)
        val ended = (parse(drillCancellation, drill) as DmDssEewParseResult.Accepted).update
        assertTrue(ended.event.isDrill)
        assertTrue(ended.event.isCancelled)
        assertFalse(ended.active)
        assertEquals(drill.id, ended.event.id)
        assertEquals(LiveUpdateKind.EEW_ENDED, ended.kind)
        assertEquals(DmDssEewRejection.CANCELLATION_WITHOUT_EVENT,
            (parse(envelope(cancelled = true), drill) as DmDssEewParseResult.Rejected).reason)
    }

    @Test fun realEventsWinWithinTheProviderAndAcrossFeeds() {
        val real = event(false)
        val drill = event(true)
        assertFalse(canPresentEewUpdate(drill, real))
        assertTrue(canPresentEewUpdate(real, drill))
        assertTrue(canPresentEewUpdate(drill, null))
        assertSame(real, preferredActiveEew(drill, real))
        assertSame(real, preferredActiveEew(real, drill))
        val tracker = EewCancellationTracker()
        tracker.record(drill.copy(isCancelled = true))
        val realSnapshot = waitingSnapshot().copy(event = real, activeEew = true, activeEewEvent = real)
        assertTrue(tracker.reconcile(realSnapshot).activeEew)
    }

    @Test fun notificationRoutingAndColdLaunchKeepTheDrillMarkerAndDeadline() {
        val drill = event(true)
        val deadline = receivedAt + 180_000L
        val dm = waitingSnapshot(DataSourceMode.DMDSS).copy(event = drill,
            activeEew = true, activeEewEvent = drill, activeEewUntilMillis = deadline,
            eewUpdateEvent = drill, liveUpdateKind = LiveUpdateKind.EEW, liveUpdateSequence = 1L)
        val baseline = waitingSnapshot()
        val routed = notificationSnapshotForProviderUpdate(dm, DataSourceMode.DMDSS, baseline, dm)
        assertTrue(routed.eewUpdateEvent!!.isDrill)
        val payload = NotificationEventPayload.decodeLaunch(
            NotificationEventPayload.encode(routed.eewUpdateEvent!!, routed.activeEewUntilMillis))!!
        assertTrue(payload.event!!.isDrill)
        assertEquals(drill.id, payload.event!!.id)
        assertEquals(deadline, payload.activeUntilMillis)
        val restored = baseline.withNotificationLaunch(payload, receivedAt)
        assertTrue(restored.activeEew)
        assertTrue(restored.activeEewEvent!!.isDrill)
        assertEquals(LiveUpdateKind.NONE, restored.liveUpdateKind)
        assertFalse(baseline.withNotificationLaunch(payload, deadline).activeEew)
    }

    @Test fun tappingAnOldDrillCannotDisplaceAnActiveRealAlert() {
        val real = event(false)
        val payload = NotificationEventPayload.decodeLaunch(
            NotificationEventPayload.encode(event(true), Long.MAX_VALUE))!!
        val live = waitingSnapshot().copy(event = real, activeEew = true, activeEewEvent = real)
        assertSame(live, live.withNotificationLaunch(payload, receivedAt))
    }
}
