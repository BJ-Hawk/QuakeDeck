package cz.misa.quakedeck

import cz.misa.quakedeck.data.AppSnapshot
import cz.misa.quakedeck.data.ConnectionState
import cz.misa.quakedeck.data.DataSourceMode
import cz.misa.quakedeck.data.EarthquakeEvent
import cz.misa.quakedeck.data.EarthquakeEventKind
import cz.misa.quakedeck.data.EarthquakeReportStage
import cz.misa.quakedeck.data.EewAlertLevel
import cz.misa.quakedeck.data.LiveUpdateKind
import cz.misa.quakedeck.data.NotificationLaunchKind
import cz.misa.quakedeck.data.NotificationLaunchPayload
import cz.misa.quakedeck.data.P2pCrowdAreaSignal
import cz.misa.quakedeck.data.P2pCrowdSignal
import cz.misa.quakedeck.data.hasEndedEew
import cz.misa.quakedeck.data.withNotificationLaunch
import cz.misa.quakedeck.notifications.eewNotificationEvent
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Test

class QuakeDeckRuntimeNotificationRoutingTest {
    @Test
    fun p2pConfirmedReportKeepsItsOwnEventWhileDmdssForecastRemainsOnMap() {
        val confirmed = event("quake:2026-08-26", EarthquakeEventKind.CONFIRMED, "2")
        val forecast = event("20260826113348", EarthquakeEventKind.EEW, "3")
        val p2p = snapshot(
            event = confirmed,
            updateKind = LiveUpdateKind.CONFIRMED,
            activeEew = false
        )
        val dmdss = snapshot(
            event = forecast,
            updateKind = LiveUpdateKind.EEW,
            activeEew = true,
            activeUntilMillis = 222L
        )
        val combined = p2p.copy(
            sourceMode = DataSourceMode.DMDSS,
            activeEew = true,
            activeEewEvent = forecast,
            activeEewUntilMillis = 222L,
            event = forecast,
            liveUpdateKind = LiveUpdateKind.CONFIRMED
        )

        val routed = notificationSnapshotForProviderUpdate(
            combined = combined,
            origin = DataSourceMode.FREE,
            p2p = p2p,
            dmdss = dmdss
        )

        assertSame(confirmed, routed.event)
        assertEquals("2", routed.event.maxIntensity)
        assertEquals(LiveUpdateKind.CONFIRMED, routed.liveUpdateKind)
        assertSame(forecast, routed.activeEewEvent)
        assertTrue(routed.activeEew)
        assertFalse(routed.dmdssEewUpdate)
        assertEquals(null, routed.activeEewUntilMillis)
    }

    @Test
    fun dmdssUpdateKeepsItsOwnForecastEvent() {
        val confirmed = event("quake:2026-08-26", EarthquakeEventKind.CONFIRMED, "2")
        val forecast = event("20260826113348", EarthquakeEventKind.EEW, "3")
        val p2p = snapshot(confirmed, LiveUpdateKind.NONE, activeEew = false)
        val dmdss = snapshot(
            forecast,
            LiveUpdateKind.EEW,
            activeEew = true,
            activeUntilMillis = 222L
        )
        val combined = p2p.copy(
            sourceMode = DataSourceMode.DMDSS,
            activeEew = true,
            activeEewEvent = forecast,
            activeEewUntilMillis = 222L,
            event = forecast,
            liveUpdateKind = LiveUpdateKind.EEW,
            dmdssEewUpdate = true
        )

        val routed = notificationSnapshotForProviderUpdate(
            combined = combined,
            origin = DataSourceMode.DMDSS,
            p2p = p2p,
            dmdss = dmdss
        )

        assertSame(forecast, routed.event)
        assertTrue(routed.dmdssEewUpdate)
        assertEquals(222L, routed.activeEewUntilMillis)
    }

    @Test
    fun allMatchingOfficialReportStagesStaySeparateFromTheActivePaidForecast() {
        val forecast = miyagiForecast()
        val confirmed = miyagiDetailedReport()
        val paid = snapshot(forecast, LiveUpdateKind.EEW, activeEew = true)
        val stages = listOf(
            confirmed.copy(
                reportStage = EarthquakeReportStage.INITIAL_INTENSITY,
                reportType = "ScalePrompt",
                reportIssuedAt = "2026-11-09 05:18:08 JST",
                hasHypocenter = false
            ),
            confirmed.copy(
                reportStage = EarthquakeReportStage.HYPOCENTER,
                reportType = "Destination",
                reportIssuedAt = "2026-11-09 05:18:43 JST"
            ),
            confirmed
        )

        for (report in stages) {
            val baseline = snapshot(report, LiveUpdateKind.CONFIRMED, activeEew = false)
            val presentation = selectLiveEewPresentation(paid, baseline, DataSourceMode.FREE)

            assertTrue(presentation.usesPaidWarning)
            assertSame(forecast, presentation.activeWarning)
            assertEquals("2026-11-09 05:16:28 JST", presentation.activeWarning?.originTime)
            assertSame(report, presentation.latestEvent)
            assertEquals("2026-11-09 05:16:00 JST", presentation.latestEvent.originTime)
            assertEquals(EarthquakeEventKind.CONFIRMED, presentation.latestEvent.kind)
        }
    }

    @Test
    fun paidForecastExpiryKeepsTheDetailedReportAndRoutesTheEndByForecastIdentity() {
        val forecast = miyagiForecast()
        val confirmed = miyagiDetailedReport()
        val baseline = snapshot(confirmed, LiveUpdateKind.NONE, activeEew = false)
        val paid = snapshot(forecast, LiveUpdateKind.EEW_ENDED, activeEew = false)
            .copy(eewUpdateEvent = forecast)

        val presentation = selectLiveEewPresentation(paid, baseline, DataSourceMode.DMDSS)
        assertNull(presentation.activeWarning)
        assertSame(confirmed, presentation.latestEvent)

        val combined = baseline.copy(
            event = presentation.latestEvent,
            liveUpdateKind = LiveUpdateKind.EEW_ENDED,
            eewUpdateEvent = forecast,
            dmdssEewUpdate = true
        )
        val routed = notificationSnapshotForProviderUpdate(
            combined = combined,
            origin = DataSourceMode.DMDSS,
            p2p = baseline,
            dmdss = paid
        )
        assertSame(confirmed, combined.event)
        assertSame(forecast, eewNotificationEvent(routed))
        assertTrue(combined.hasEndedEew(forecast.id))
        assertFalse(combined.hasEndedEew("unrelated-eew"))
        val retainedLaunch = NotificationLaunchPayload(
            kind = NotificationLaunchKind.EEW,
            event = forecast,
            activeUntilMillis = Long.MAX_VALUE
        )
        assertSame(combined, combined.withNotificationLaunch(retainedLaunch))
    }

    @Test
    fun chibaDetailedReportKeepsTheForecastsFeltClusterThroughExpiry() {
        val original = P2pCrowdSignal(
            startedAt = "2026/11/12 22:27:35.786",
            updatedAt = "2026/11/12 22:28:31.279",
            reportCount = 95,
            confidence = 0.98052,
            areas = emptyList()
        )
        val cumulative = original.copy(
            updatedAt = "2026/11/12 22:29:08.520",
            reportCount = 103
        )
        val competing = P2pCrowdSignal(
            startedAt = "2026/11/12 22:29:50.173",
            updatedAt = "2026/11/12 22:30:28.560",
            reportCount = 4,
            confidence = 0.0,
            areas = listOf(P2pCrowdAreaSignal("241", 3, 0.00643, "E"))
        )
        val forecast = event("20261112222726", EarthquakeEventKind.EEW, "2").copy(
            place = "Northeastern Chiba",
            originTime = "2026-11-12 22:27:19 JST",
            p2pCrowdSignal = original
        )
        val confirmed = forecast.copy(
            id = "quake:jma:20261112222726",
            kind = EarthquakeEventKind.CONFIRMED,
            originTime = "2026-11-12 22:27:00 JST",
            reportStage = EarthquakeReportStage.DETAILED,
            jmaReportEventId = forecast.id,
            p2pCrowdSignal = cumulative
        )
        val active = snapshot(forecast, LiveUpdateKind.EEW, activeEew = true)
        val updated = snapshot(confirmed, LiveUpdateKind.CONFIRMED, activeEew = false)
        val afterCompetingPacket = updated.copy(event = confirmed.copy(p2pCrowdSignal = competing))
        val finalForecast = forecast.copy(p2pCrowdSignal = cumulative)
        val ended = snapshot(finalForecast, LiveUpdateKind.EEW_ENDED, activeEew = false)
            .copy(eewUpdateEvent = finalForecast)

        assertEquals(cumulative, selectLiveEewPresentation(active, updated, DataSourceMode.FREE)
            .latestEvent.p2pCrowdSignal)
        assertEquals(cumulative, selectLiveEewPresentation(
            active.copy(activeEewEvent = finalForecast), afterCompetingPacket, DataSourceMode.FREE
        ).latestEvent.p2pCrowdSignal)
        assertEquals(cumulative, selectLiveEewPresentation(ended, afterCompetingPacket, DataSourceMode.DMDSS)
            .latestEvent.p2pCrowdSignal)
    }

    private fun miyagiForecast() = event("20261109051637", EarthquakeEventKind.EEW, "3").copy(
        place = "Miyagi-ken Oki",
        originTime = "2026-11-09 05:16:28 JST",
        magnitude = 4.6,
        depthKm = 50,
        latitude = 38.3,
        longitude = 141.9,
        reportSerial = "5",
        reportIssuedAt = "2026-11-09 05:17:26 JST"
    )

    private fun miyagiDetailedReport() = miyagiForecast().copy(
        id = "quake:jma:20261109051637",
        kind = EarthquakeEventKind.CONFIRMED,
        originTime = "2026-11-09 05:16:00 JST",
        magnitude = 4.5,
        reportSerial = null,
        reportIssuedAt = "2026-11-09 05:19:50 JST",
        reportStage = EarthquakeReportStage.DETAILED,
        reportType = "DetailScale",
        jmaReportEventId = "20261109051637"
    )

    private fun snapshot(
        event: EarthquakeEvent,
        updateKind: LiveUpdateKind,
        activeEew: Boolean,
        activeUntilMillis: Long? = null
    ) = AppSnapshot(
        sourceMode = DataSourceMode.DMDSS,
        connectionState = ConnectionState.CONNECTED,
        activeEew = activeEew,
        activeEewEvent = event.takeIf { activeEew },
        activeEewUntilMillis = activeUntilMillis,
        event = event,
        liveUpdateKind = updateKind
    )

    private fun event(
        id: String,
        kind: EarthquakeEventKind,
        intensity: String
    ) = EarthquakeEvent(
        id = id,
        place = "Kumamoto",
        originTime = "2026-08-26 11:33:48 JST",
        magnitude = 3.3,
        depthKm = 10,
        maxIntensity = intensity,
        latitude = 32.8,
        longitude = 130.7,
        points = emptyList(),
        kind = kind,
        eewAlertLevel = EewAlertLevel.FORECAST
    )
}
