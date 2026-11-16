package cz.misa.quakedeck.data

import cz.misa.quakedeck.combineActiveEewIncidents
import cz.misa.quakedeck.combineLiveUpdateKind
import cz.misa.quakedeck.notificationSnapshotForProviderUpdate
import cz.misa.quakedeck.selectLiveEewPresentation
import cz.misa.quakedeck.notifications.eewNotificationEvent
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertSame
import org.junit.Test

class PendingEewExpiriesTest {
    @Test
    fun overdueNotoEndsIndependentlyAfterShizuokaArrives() {
        val expiries = PendingEewExpiries()
        val firstShizuoka = shizuoka.copy(reportSerial = "1", reportIssuedAt = "2026-11-16 13:29:03 JST")
        expiries.schedule(noto, notoDeadline)
        expiries.schedule(firstShizuoka, jstMillis("2026-11-16 13:32:03"))

        assertEquals(notoDeadline, expiries.nextDeadlineMillis)
        assertEquals(listOf(firstShizuoka, noto), expiries.newestFirst().map { it.event })
        val shizuokaFirstReceivedAt = jstMillis("2026-11-16 13:29:03.976")
        assertEquals(listOf(PendingEewExpiry(noto, notoDeadline)), expiries.extractDue(shizuokaFirstReceivedAt))
        assertNull(expiries.incident(noto.id))
        expiries.schedule(shizuoka, shizuokaDeadline)
        assertEquals(PendingEewExpiry(shizuoka, shizuokaDeadline), expiries.incident(shizuoka.id))
        assertEquals(shizuokaDeadline, expiries.nextDeadlineMillis)
        assertEquals(emptyList<PendingEewExpiry>(), expiries.extractDue(shizuokaDeadline - 1))
        assertEquals(listOf(PendingEewExpiry(shizuoka, shizuokaDeadline)), expiries.extractDue(shizuokaDeadline))
        assertEquals(emptyList<PendingEewExpiry>(), expiries.extractDue(shizuokaDeadline + 1))
        assertNull(expiries.nextDeadlineMillis)
    }

    @Test
    fun sameIncidentRevisionReplacesTheOldDeadlineAndPayload() {
        val expiries = PendingEewExpiries()
        val previousNoto = noto.copy(reportSerial = "10", reportIssuedAt = "2026-11-16 13:25:46 JST")
        val previousDeadline = jstMillis("2026-11-16 13:28:46")
        expiries.schedule(previousNoto, previousDeadline)
        expiries.schedule(noto, notoDeadline)

        assertEquals(notoDeadline, expiries.nextDeadlineMillis)
        assertEquals(emptyList<PendingEewExpiry>(), expiries.extractDue(previousDeadline))
        assertEquals(listOf(PendingEewExpiry(noto, notoDeadline)), expiries.extractDue(notoDeadline))
    }

    @Test
    fun delayedDrainUsesDeadlineThenSchedulingOrder() {
        val expiries = PendingEewExpiries()
        val another = noto.copy(id = "20261116132449")
        expiries.schedule(shizuoka, shizuokaDeadline)
        expiries.schedule(noto, notoDeadline)
        expiries.schedule(another, notoDeadline)

        val delayedCallbackAt = jstMillis("2026-11-16 13:39:16.206")
        assertEquals(
            listOf(noto.id, another.id, shizuoka.id),
            expiries.extractDue(delayedCallbackAt).map { it.event.id }
        )
    }

    @Test
    fun removingASingleIncidentOrClearingTheProviderCancelsPendingExpiry() {
        val expiries = PendingEewExpiries()
        expiries.schedule(noto, notoDeadline)
        expiries.schedule(shizuoka, shizuokaDeadline)

        assertSame(noto, expiries.remove(noto.id))
        assertNull(expiries.remove(noto.id))
        assertEquals(listOf(PendingEewExpiry(shizuoka, shizuokaDeadline)), expiries.newestFirst())
        assertEquals(shizuokaDeadline, expiries.nextDeadlineMillis)
        expiries.clear()
        assertNull(expiries.nextDeadlineMillis)
        assertEquals(emptyList<PendingEewExpiry>(), expiries.extractDue(shizuokaDeadline))
    }

    @Test
    fun endingNotoUsesItsOwnIdentityWhileTheOtherFeedShowsShizuoka() {
        for (origin in listOf(DataSourceMode.DMDSS, DataSourceMode.FREE)) {
            val terminal = waitingSnapshot().copy(
                event = noto,
                eewUpdateEvent = noto,
                liveUpdateKind = LiveUpdateKind.EEW_ENDED
            )
            val other = waitingSnapshot().copy(
                event = shizuoka,
                activeEew = true,
                activeEewEvent = shizuoka,
                activeEewUntilMillis = shizuokaDeadline
            )
            val paid = if (origin == DataSourceMode.DMDSS) terminal else other
            val baseline = if (origin == DataSourceMode.FREE) terminal else other
            val combined = other.copy(
                eewUpdateEvent = noto,
                liveUpdateKind = combineLiveUpdateKind(origin, paid, baseline, shizuoka),
                dmdssEewUpdate = origin == DataSourceMode.DMDSS
            )
            val routed = notificationSnapshotForProviderUpdate(combined, origin, baseline, paid)

            assertEquals(LiveUpdateKind.EEW_ENDED, routed.liveUpdateKind)
            assertSame(noto, eewNotificationEvent(routed))
            assertSame(shizuoka, combined.activeEewEvent)
        }
    }

    @Test
    fun otherFeedKeepsTheSameWarningActiveUnlessTheBulletinCancelsIt() {
        val other = waitingSnapshot().copy(event = noto, activeEew = true, activeEewEvent = noto)
        val terminal = waitingSnapshot().copy(
            event = noto,
            eewUpdateEvent = noto,
            liveUpdateKind = LiveUpdateKind.EEW_ENDED
        )
        val cancelled = noto.copy(isCancelled = true)

        assertEquals(
            LiveUpdateKind.NONE,
            combineLiveUpdateKind(DataSourceMode.DMDSS, terminal, other, noto)
        )
        assertEquals(
            LiveUpdateKind.EEW_ENDED,
            combineLiveUpdateKind(
                DataSourceMode.DMDSS,
                terminal.copy(event = cancelled, eewUpdateEvent = cancelled),
                other,
                noto
            )
        )
    }

    @Test
    fun aPaidDisconnectStillAllowsAnExplicitExpiryOfThePreviousIncident() {
        val terminal = waitingSnapshot(mode = DataSourceMode.DMDSS).copy(
            eewUpdateEvent = noto,
            liveUpdateKind = LiveUpdateKind.EEW_ENDED
        )
        val baseline = waitingSnapshot().copy(
            event = shizuoka,
            activeEew = true,
            activeEewEvent = shizuoka
        )

        assertEquals(
            LiveUpdateKind.EEW_ENDED,
            combineLiveUpdateKind(DataSourceMode.DMDSS, terminal, baseline, shizuoka, paidConnected = false)
        )
        assertEquals(
            LiveUpdateKind.NONE,
            combineLiveUpdateKind(
                DataSourceMode.DMDSS, terminal.copy(eewUpdateEvent = null), baseline, shizuoka,
                paidConnected = false
            )
        )
    }

    @Test
    fun simultaneousWarningsRetainSeparateDeadlinesAcrossProviders() {
        val primary = ActiveEewIncident(shizuoka, shizuokaDeadline)
        val older = ActiveEewIncident(noto, notoDeadline)
        val publicWarning = ActiveEewIncident(
            noto.copy(eewAlertLevel = EewAlertLevel.WARNING),
            notoDeadline + 10_000
        )
        val paid = waitingSnapshot(mode = DataSourceMode.DMDSS).copy(
            event = shizuoka,
            activeEew = true,
            activeEewEvent = shizuoka,
            activeEewUntilMillis = primary.deadlineMillis,
            activeEews = listOf(primary, older)
        )
        val baseline = waitingSnapshot().copy(
            event = publicWarning.event,
            activeEew = true,
            activeEewEvent = publicWarning.event,
            activeEewUntilMillis = publicWarning.deadlineMillis
        )

        val combined = combineActiveEewIncidents(paid, baseline)
        assertEquals(2, combined.size)
        assertEquals(setOf(primary, publicWarning), combined.toSet())
    }

    @Test
    fun nonPrimaryUpdateUsesItsOwnDeadlineForNotificationLaunch() {
        val primary = ActiveEewIncident(shizuoka, shizuokaDeadline)
        val secondary = ActiveEewIncident(noto, notoDeadline)
        val paid = waitingSnapshot(mode = DataSourceMode.DMDSS).copy(
            event = shizuoka,
            activeEew = true,
            activeEewEvent = shizuoka,
            activeEewUntilMillis = primary.deadlineMillis,
            activeEews = listOf(primary, secondary),
            eewUpdateEvent = noto,
            liveUpdateKind = LiveUpdateKind.EEW
        )
        val baseline = waitingSnapshot()
        val combined = paid.copy(
            liveUpdateKind = combineLiveUpdateKind(DataSourceMode.DMDSS, paid, baseline, shizuoka),
            dmdssEewUpdate = true
        )

        val routed = notificationSnapshotForProviderUpdate(combined, DataSourceMode.DMDSS, baseline, paid)

        assertEquals(LiveUpdateKind.EEW, routed.liveUpdateKind)
        assertSame(noto, eewNotificationEvent(routed))
        assertEquals(notoDeadline, routed.activeEewUntilMillis)
        assertEquals(listOf(primary, secondary), combined.activeEewIncidents())
    }

    @Test
    fun cancellingOneWarningLeavesTheOtherActiveForDisplayAndRoutesItsOwnEnd() {
        val primary = ActiveEewIncident(shizuoka, shizuokaDeadline)
        val cancelled = noto.copy(isCancelled = true)
        val paid = waitingSnapshot(mode = DataSourceMode.DMDSS).copy(
            event = shizuoka,
            activeEew = true,
            activeEewEvent = shizuoka,
            activeEewUntilMillis = primary.deadlineMillis,
            activeEews = listOf(primary),
            eewUpdateEvent = cancelled,
            liveUpdateKind = LiveUpdateKind.EEW_ENDED
        )
        val baseline = waitingSnapshot()
        val active = combineActiveEewIncidents(paid, baseline)
        val combined = paid.copy(
            activeEews = active,
            liveUpdateKind = combineLiveUpdateKind(DataSourceMode.DMDSS, paid, baseline, shizuoka),
            dmdssEewUpdate = true
        )

        val routed = notificationSnapshotForProviderUpdate(combined, DataSourceMode.DMDSS, baseline, paid)

        assertEquals(listOf(primary), active)
        assertEquals(LiveUpdateKind.EEW_ENDED, routed.liveUpdateKind)
        assertSame(cancelled, eewNotificationEvent(routed))
        assertNull(routed.activeEewUntilMillis)
    }

    @Test
    fun delayedNotoEndKeepsNewerShizuokaConfirmedReportOnTheMap() {
        val notoReport = noto.copy(
            id = "quake:jma:${noto.id}",
            kind = EarthquakeEventKind.CONFIRMED,
            jmaReportEventId = noto.id,
            originTime = "2026-11-16 13:24:00 JST"
        )
        val shizuokaReport = shizuoka.copy(
            id = "quake:jma:${shizuoka.id}",
            kind = EarthquakeEventKind.CONFIRMED,
            jmaReportEventId = shizuoka.id,
            originTime = "2026-11-16 13:28:00 JST"
        )
        val paid = waitingSnapshot(mode = DataSourceMode.DMDSS).copy(
            event = noto,
            eewUpdateEvent = noto,
            liveUpdateKind = LiveUpdateKind.EEW_ENDED
        )
        val baseline = waitingSnapshot().copy(event = shizuokaReport, history = listOf(notoReport))

        val presentation = selectLiveEewPresentation(paid, baseline, DataSourceMode.DMDSS)

        assertSame(shizuokaReport, presentation.latestEvent)
        assertNull(presentation.activeWarning)
    }

    private fun jstMillis(value: String): Long = requireNotNull(EewLifecyclePolicy.sourceMillis("$value JST"))

    private val notoDeadline = jstMillis("2026-11-16 13:28:57")
    private val shizuokaDeadline = jstMillis("2026-11-16 13:32:31")

    private val noto = EarthquakeEvent(
        id = "20261116132447",
        place = "石川県能登地方",
        originTime = "2026-11-16 13:24:09 JST",
        magnitude = 5.4,
        depthKm = 310,
        maxIntensity = "—",
        latitude = 37.3,
        longitude = 136.9,
        points = emptyList(),
        kind = EarthquakeEventKind.EEW,
        eewAlertLevel = EewAlertLevel.FORECAST,
        reportSerial = "11",
        reportIssuedAt = "2026-11-16 13:25:57 JST"
    )

    private val shizuoka = noto.copy(
        id = "20261116132858",
        place = "静岡県東部",
        originTime = "2026-11-16 13:28:57 JST",
        magnitude = 3.5,
        depthKm = 10,
        maxIntensity = "2",
        latitude = 35.3,
        longitude = 139.0,
        reportSerial = "4",
        reportIssuedAt = "2026-11-16 13:29:31 JST"
    )
}
