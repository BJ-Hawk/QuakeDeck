package cz.misa.quakedeck.data

import cz.misa.quakedeck.notificationSnapshotForProviderUpdate
import cz.misa.quakedeck.notifications.eewEndDeliveryEnabled
import cz.misa.quakedeck.notifications.eewNotificationEvent
import java.io.ByteArrayInputStream
import java.util.Base64
import java.util.zip.GZIPInputStream
import org.json.JSONObject
import org.junit.Assert.*
import org.junit.Test

/** Exact retained wire packets; clocks are injected instead of changing their timestamps. */
class DeliveryIncidentRegressionTest {
    private data class Packet(val index: Int, val at: Long, val source: String,
        val type: String, val json: JSONObject)
    private val fixture = JSONObject(requireNotNull(javaClass.classLoader!!
        .getResourceAsStream("incidents/eew-20261016.json")).bufferedReader().use { it.readText() })
    private val packets = fixture.getJSONArray("packets").let { array ->
        (0 until array.length()).map { index -> array.getJSONObject(index).let { p ->
            Packet(p.getInt("exportIndex"), p.getLong("recordedAtMillis"),
                p.getString("source"), p.getString("type"), JSONObject(p.getString("payload")))
        } }
    }
    private val dm get() = packets.filter { it.source == "DM-D.S.S" && it.type == "data" }

    private fun packet(serial: String, type: String = "VXSE45", cancelled: Boolean = false) = dm.first {
        val head = it.json.getJSONObject("xmlReport").getJSONObject("head")
        head.getString("serial") == serial && it.json.getJSONObject("head").getString("type") == type &&
            (head.getString("infoType") == "取消") == cancelled
    }
    private fun update(serial: String, type: String = "VXSE45", previous: EarthquakeEvent? = null,
        cancelled: Boolean = false): DmDssEewUpdate {
        val p = packet(serial, type, cancelled)
        return requireNotNull(DmDssEewParser.parseEnvelope(p.json, previous, p.at))
    }
    private fun gd(p: Packet): JSONObject {
        val report = JSONObject(GZIPInputStream(ByteArrayInputStream(
            Base64.getDecoder().decode(p.json.getString("body")))).bufferedReader().use { it.readText() })
        return report.getJSONObject("body").put("eventId", report.getString("eventId"))
            .put("serial", report.getString("serialNo")).put("dateTime", report.getString("reportDateTime"))
    }

    @Test fun exactForecastWarningFinalAndLowerSerialCancellationStayInOrder() {
        assertEquals("8d18b06b4c879fcd3d1300a48675022f584ed165ec25792a6d27a148ecbae167", fixture.getString("sourceSha256"))
        assertEquals(84, dm.size)
        val tracker = EewRevisionTracker()
        var warnings = 0
        var acceptedWarnings = 0
        var activeUntil: Long? = null
        dm.forEach { p ->
            val result = requireNotNull(DmDssEewParser.parseEnvelope(p.json,
                tracker.current("20261016123810"), p.at))
            if (!result.event.isCancelled) {
                assertTrue("Fresh serial ${result.event.reportSerial} at export index ${p.index}", result.active)
                assertTrue(requireNotNull(result.expiresAtMillis) > p.at)
                if (result.event.eewAlertLevel == EewAlertLevel.WARNING) warnings++
            }
            if (tracker.rejection(result.event) == null) {
                tracker.record(result.event)
                activeUntil = result.expiresAtMillis
                if (!result.event.isCancelled && result.event.eewAlertLevel == EewAlertLevel.WARNING) acceptedWarnings++
            }
        }
        assertEquals(50, warnings)
        assertTrue(acceptedWarnings >= 25)
        val final = requireNotNull(tracker.current("20261016123810"))
        assertEquals("37", final.reportSerial)
        assertTrue(final.isCancelled)
        assertNull(activeUntil)
    }

    @Test fun firstShindo7EscalationAfterThreeMinutesIsFreshByIssueTime() {
        val warning = update("17")
        val source = packet("17")
        val origin = requireNotNull(EewLifecyclePolicy.sourceMillis(warning.event.originTime))
        assertTrue(source.at - origin > 180_000)
        assertEquals("7", warning.event.maxIntensity)
        assertEquals(EewAlertLevel.WARNING, warning.event.eewAlertLevel)
        assertTrue(warning.active)
        assertEquals(requireNotNull(warning.issuedAtMillis) + 180_000, warning.expiresAtMillis)
        assertTrue(requireNotNull(warning.expiresAtMillis) > source.at)
    }

    @Test fun expiryMovesWithEachNewBulletinAndStaleReplayDoesNotActivate() {
        assertTrue(requireNotNull(update("42").expiresAtMillis) > requireNotNull(update("17").expiresAtMillis))
        val p = packet("17")
        val stale = requireNotNull(DmDssEewParser.parseEnvelope(p.json, null, p.at + 240_000))
        assertFalse(stale.active)
        assertEquals(EewAlertLevel.WARNING, stale.event.eewAlertLevel)
    }

    @Test fun bothExactP2pWarningsRemainEligibleInFullAndLite() {
        val warnings = packets.filter { it.source == "P2PQuake" && it.json.optInt("code") == 556 }
        assertEquals(2, warnings.size)
        warnings.forEachIndexed { index, p ->
            val eew = requireNotNull(P2pEewParser.parse(p.json))
            assertEquals(if (index == 0) 100 else 105, eew.points.size)
            assertEquals("7", eew.maxIntensity)
            assertTrue(eew.points.any { requireNotNull(EewLifecyclePolicy.sourceMillis(it.arrivalTime ?: eew.originTime)) > p.at })
            val deadline = P2pEewLifecyclePolicy.deadline(eew, p.at)
            assertTrue("P2P Warning cannot be discarded by the old FULL cap", deadline.epochMillis > p.at)
            assertTrue(deadline.epochMillis >= requireNotNull(EewLifecyclePolicy.sourceMillis(eew.reportIssuedAt)) + 180_000)
            assertEquals(LocalEewForecasts.buildEdition == QuakeDeckBuildEdition.FULL, deadline.forecastDerived)
        }
    }

    @Test fun genericDetectionsCannotBorrowThePrecedingKumamotoReport() {
        val old = packets.first { it.index == 1942 }.json.getJSONObject("earthquake")
        assertEquals(10, old.getInt("maxScale"))
        assertEquals("熊本県熊本地方", old.getJSONObject("hypocenter").getString("name"))
        val detections = packets.filter { it.json.optInt("code") == 554 }
        assertEquals(2, detections.size)
        detections.forEach { p ->
            assertFalse(p.json.has("earthquake"))
            val pending = pendingEewDetectionEvent(p.at)
            assertEquals("—", pending.maxIntensity)
            assertEquals("EEW", pending.place)
            assertEquals("—", pending.originTime)
            assertFalse(pending.hasHypocenter)
            assertFalse(pending.latitude.isFinite())
            assertTrue(pending.id.startsWith("eew-detection:"))
            assertEquals(EarthquakeEventKind.EEW, pending.kind)
            val restored = requireNotNull(NotificationEventPayload.decode(NotificationEventPayload.encode(pending)))
            assertFalse(restored.hasHypocenter)
            assertFalse(restored.latitude.isFinite())
            assertEquals("—", restored.maxIntensity)
        }
    }

    @Test fun newerCancellation37IsAcceptedButOlderCancellationAndDelayedWarningAreRejected() {
        val final = update("42").event
        val cancel = update("37", previous = final, cancelled = true).event
        assertTrue(requireNotNull(cancel.reportSerial).toInt() < requireNotNull(final.reportSerial).toInt())
        val tracker = EewRevisionTracker().apply { record(final) }
        assertNull(tracker.rejection(cancel))
        assertNotNull(tracker.rejection(cancel.copy(reportIssuedAt = "2026-10-16 12:47:00 JST")))
        tracker.record(cancel)
        assertNotNull(tracker.rejection(final.copy(reportSerial = "43", reportIssuedAt = "2026-10-16 12:49:00 JST")))
        assertNotNull(tracker.rejection(cancel))
    }

    @Test fun eewCancellationUsesItsOwnIdentityWithConfirmedAlertsDisabledAndConfirmedReportVisible() {
        val end = update("37", previous = update("42").event, cancelled = true).event
        val visible = end.copy(id = "quake:confirmed", kind = EarthquakeEventKind.CONFIRMED, isCancelled = false)
        val source = AppSnapshot(sourceMode = DataSourceMode.FREE, connectionState = ConnectionState.CONNECTED,
            activeEew = false, event = visible, liveUpdateKind = LiveUpdateKind.EEW_ENDED, eewUpdateEvent = end)
        val routed = notificationSnapshotForProviderUpdate(source, DataSourceMode.FREE, source, source)
        assertSame(end, eewNotificationEvent(routed))
        assertTrue(source.hasEndedEew(end.id))
        val retained = NotificationLaunchPayload(kind = NotificationLaunchKind.EEW,
            event = end.copy(isCancelled = false), activeUntilMillis = Long.MAX_VALUE)
        assertSame(source, source.withNotificationLaunch(retained, packet("37", cancelled = true).at))
        val other = source.copy(activeEew = true, activeEewEvent = visible.copy(id = "other", kind = EarthquakeEventKind.EEW))
        assertSame(other, other.withNotificationLaunch(retained, packet("37", cancelled = true).at))
        assertTrue(eewEndDeliveryEnabled(end, true, true, false, false, false))
        assertFalse(eewEndDeliveryEnabled(end, false, true, true, false, true))
        assertFalse(eewEndDeliveryEnabled(end, true, false, true, false, true))
        assertFalse(eewEndDeliveryEnabled(end, true, true, true, true, false))
        assertTrue(eewEndDeliveryEnabled(end, true, true, true, true, true))
    }

    @Test fun identicalSourceCompanionsStayDuplicateAfterLocalForecastAndCrowdEnrichment() {
        val a = update("1", "VXSE44").event
        val b = update("1").event
        val enriched = a.copy(localIntensityForecast = LocalEewIntensityForecast(emptyList(),
            LocalEewIntensityRange(1.0, 3.0, "1", "3"), 123L, "fixture", "fixture", 0),
            p2pCrowdSignal = P2pCrowdSignal("2026/10/16 12:38:30", "2026/10/16 12:38:31", 2, 1.0, emptyList()))
        assertTrue(isDuplicateDmDssRevision(b, enriched))
        assertFalse(isDuplicateDmDssRevision(update("17").event, update("17", "VXSE44").event))
        assertEquals(105, update("17").event.points.size)
    }

    @Test fun cancellationFromEitherFeedPreventsTheOtherFeedReopeningItsOlderWarning() {
        val end = update("37", previous = update("42").event, cancelled = true).event
        val baseline = requireNotNull(P2pEewParser.parse(packets.first { it.json.optInt("code") == 556 }.json))
        val registry = EewCancellationTracker().apply { record(end) }
        val active = AppSnapshot(sourceMode = DataSourceMode.FREE, connectionState = ConnectionState.CONNECTED,
            activeEew = true, activeEewEvent = baseline, eewUpdateEvent = baseline,
            event = baseline, liveUpdateKind = LiveUpdateKind.EEW, activeEewUntilMillis = Long.MAX_VALUE)
        val resolved = registry.reconcile(active)
        assertFalse(resolved.activeEew)
        assertNull(resolved.activeEewEvent)
        assertEquals(LiveUpdateKind.NONE, resolved.liveUpdateKind)
        assertTrue(resolved.event.isCancelled)
        val confirmed = baseline.copy(id = "quake:confirmed", kind = EarthquakeEventKind.CONFIRMED)
        assertSame(confirmed, registry.reconcile(active.copy(event = confirmed)).event)
        assertTrue(registry.reconcile(active.copy(activeEewEvent = baseline.copy(id = "other"))).activeEew)
    }

    @Test fun connectedPaidForecastCannotMaskThePublicWarningEscalation() {
        val forecast = update("16").event
        val publicWarning = requireNotNull(P2pEewParser.parse(packets.first { it.json.optInt("code") == 556 }.json))
        assertSame(publicWarning, preferredActiveEew(forecast, publicWarning))
        val paidWarning = update("17").event
        assertSame(paidWarning, preferredActiveEew(paidWarning, publicWarning))
        assertSame(forecast, preferredActiveEew(forecast, publicWarning.copy(id = "unrelated")))
    }

    @Test fun archiveKeysRetainCompanionRegionsAndCancellationWithReusedSerial() {
        val a = update("17", "VXSE44").event
        val b = update("17").event
        val sameSerial = update("37").event
        val cancel = update("37", previous = update("42").event, cancelled = true).event
        assertNotEquals(EewArchiveFrame.encode(a).getString("id"), EewArchiveFrame.encode(b).getString("id"))
        assertNotEquals(EewArchiveFrame.encode(sameSerial).getString("id"), EewArchiveFrame.encode(cancel).getString("id"))
        assertEquals(EewArchiveFrame.encode(b).getString("id"), EewArchiveFrame.encode(b).getString("id"))
    }

    @Test fun reconnectAfter39Recovers41AndFinal42ThenAppliesLowerSerialCancellation() {
        assertFalse(dm.any { it.json.getJSONObject("xmlReport").getJSONObject("head").getString("serial") == "40" })
        assertTrue(packets.any { it.index == 2042 && it.type == "websocket-failure" })
        val current = update("39").event
        val end = packet("37", cancelled = true)
        val items = listOf(gd(end), gd(packet("42")), gd(packet("41")))
        val recovered = DmDssRecoveryPolicy.select(items, DmDssDiagnosticsSnapshot(
            lastAcceptedEventId = current.id, lastAcceptedSerial = "39", lastAcceptedAlertLevel = "WARNING"
        ), end.at + 1_000, current, current.id)
        assertEquals(listOf("41", "42", "37"), recovered.map { it.event.reportSerial })
        assertTrue(recovered.last().event.isCancelled)
        assertEquals(LiveUpdateKind.EEW_ENDED, recovered.last().kind)
        assertTrue(recovered.dropLast(1).all { it.active })
        assertTrue(DmDssRecoveryPolicy.select(items, DmDssDiagnosticsSnapshot(), end.at + 240_000, current, current.id).isEmpty())
        assertTrue(DmDssRecoveryPolicy.select(items, DmDssDiagnosticsSnapshot(), end.at, current, "other-event").isEmpty())
    }

    @Test fun cancelledEewOnlyIncidentAppearsInHistoryAndReplaysTheTerminalFrame() {
        val records = archivedIncident()
        val summaries = EewHistory.summaries(records, emptyList())
        assertEquals(1, summaries.size)
        val summary = summaries.single()
        assertEquals("eew:20261016123810", summary.eventKey)
        assertTrue(summary.event.isCancelled)
        assertTrue(summary.hasCorrection)
        val replay = requireNotNull(EewHistory.incident(summary.eventKey, records) { _, _ -> LocalEewForecastResult.NoResult })
        assertEquals(records.size, replay.frames.size)
        assertEquals("37", replay.frames.last().event.reportSerial)
        assertTrue(replay.frames.last().event.isCancelled)
        assertTrue(replay.frames.last().event.localIntensityForecast == null)
        assertTrue(replay.frames.all { it.kind == HistoricalAssociatedReportKind.EEW })
        assertTrue(replay.associatedReports.last().cancelled)
        assertEquals(replay.frames.lastIndex, replay.associatedReports.last().earthquakeFrameIndex)
    }

    @Test fun processRestartRecoversCancellationUsingTheRetainedIncidentInsteadOfBorrowingAnotherQuake() {
        val previous = packet("39")
        val end = packet("37", cancelled = true)
        val diagnostics = DmDssDiagnosticsSnapshot(lastAcceptedEventId = "20261016123810",
            lastAcceptedSerial = "39", lastAcceptedAlertLevel = "WARNING",
            lastAcceptedIssuedAtMillis = update("39").issuedAtMillis,
            packetHistory = listOf(DmDssPacketDiagnostic(previous.at, "IN", "text", "data", previous.json.toString())))
        val recovered = DmDssRecoveryPolicy.select(listOf(gd(end)), diagnostics, end.at, null, null)
        assertEquals(1, recovered.size)
        assertTrue(recovered.single().event.isCancelled)
        assertEquals("千島列島", recovered.single().event.place)
        assertEquals("37", recovered.single().event.reportSerial)
        assertTrue(DmDssRecoveryPolicy.select(listOf(gd(end)), diagnostics.copy(
            lastAcceptedCancelled = true, lastAcceptedIssuedAtMillis = recovered.single().issuedAtMillis),
            end.at, null, null).isEmpty())
        assertTrue(DmDssRecoveryPolicy.select(listOf(gd(end)), diagnostics.copy(
            lastAcceptedEventId = "unrelated"), end.at, null, null).isEmpty())
    }

    @Test fun eewHistoryKeepsMinuteAndDistanceMatchingScopedToHistoricalAssociation() {
        val records = archivedIncident()
        val eew = update("42").event
        val confirmed = eew.copy(id = "quake:2026/10/16 12:36:00", originTime = "2026-10-16 12:36:00 JST",
            kind = EarthquakeEventKind.CONFIRMED)
        assertTrue(EewHistory.summaries(records, listOf(confirmed)).isEmpty())
        assertEquals(1, EewHistory.summaries(records, listOf(confirmed.copy(latitude = 10.0))).size)
        assertEquals(1, EewHistory.summaries(records, listOf(confirmed.copy(originTime = "2026-10-16 12:35:00 JST"))).size)
        assertEquals("2026-10-16 12:36:27 JST", eew.originTime)
    }

    @Test fun diagnosticDecisionDistinguishesAcceptedActiveAndTerminalNotificationState() {
        val end = update("37", previous = update("42").event, cancelled = true)
        val diagnostic = eewDecisionDiagnostic(end.event, "notification", "Notification posted",
            end.kind, end.expiresAtMillis, packet("37", cancelled = true).at)
        assertEquals("37", diagnostic.getString("serial"))
        assertEquals("EEW_ENDED", diagnostic.getString("updateKind"))
        assertTrue(diagnostic.getBoolean("cancelled"))
        assertTrue(diagnostic.isNull("deadlineMillis"))
        val export = JSONObject(DmDssDiagnosticsSnapshot(lastAcceptedCancelled = true,
            lastAcceptedIssuedAtMillis = end.issuedAtMillis).toMachineReadableJson())
        assertTrue(export.getJSONObject("summary").getBoolean("lastAcceptedCancelled"))
        assertEquals(LocalEewForecasts.buildEdition.name, export.getJSONObject("build").getString("edition"))
    }

    @Test fun sourceDecoderRetainsFractionalTimesAndBodylessPublicCancellations() {
        assertEquals(requireNotNull(EewLifecyclePolicy.sourceMillis("2026-10-16T03:40:03.264Z")),
            EewLifecyclePolicy.sourceMillis("2026/10/16 12:40:03.264"))
        val end = packet("37", cancelled = true)
        val known = update("42").event
        val minimal = JSONObject().put("cancelled", true).put("earthquake", JSONObject.NULL)
            .put("issue", JSONObject().put("eventId", known.id).put("serial", "37")
                .put("time", EewLifecyclePolicy.formatJst(end.at).removeSuffix(" JST").replace('-', '/')))
        val cancelled = requireNotNull(P2pEewParser.parse(minimal, known))
        assertTrue(cancelled.isCancelled)
        assertEquals("37", cancelled.reportSerial)
        assertFalse(EewLifecyclePolicy.isOlder(cancelled, known))
    }

    private fun archivedIncident(): List<ArchivedReportRecord> {
        val tracker = EewRevisionTracker()
        return dm.mapNotNull { p ->
            val parsed = requireNotNull(DmDssEewParser.parseEnvelope(p.json, tracker.current("20261016123810"), p.at))
            if (tracker.rejection(parsed.event) != null) return@mapNotNull null
            tracker.record(parsed.event)
            val raw = EewArchiveFrame.encode(parsed.event)
            ArchivedReportRecord(raw.getString("id"), 556, raw.getString("time"), p.at,
                parsed.event.reportType, "event:${parsed.event.id}", raw)
        }
    }
}
