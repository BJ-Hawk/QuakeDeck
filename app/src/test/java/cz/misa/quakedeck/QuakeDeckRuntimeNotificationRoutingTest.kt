package cz.misa.quakedeck

import cz.misa.quakedeck.data.AppSnapshot
import cz.misa.quakedeck.data.ConnectionState
import cz.misa.quakedeck.data.DataSourceMode
import cz.misa.quakedeck.data.EarthquakeEvent
import cz.misa.quakedeck.data.EarthquakeEventKind
import cz.misa.quakedeck.data.EewAlertLevel
import cz.misa.quakedeck.data.LiveUpdateKind
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
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
            activeEew = true
        )
        val combined = p2p.copy(
            sourceMode = DataSourceMode.DMDSS,
            activeEew = true,
            activeEewEvent = forecast,
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
    }

    @Test
    fun dmdssUpdateKeepsItsOwnForecastEvent() {
        val confirmed = event("quake:2026-08-26", EarthquakeEventKind.CONFIRMED, "2")
        val forecast = event("20260826113348", EarthquakeEventKind.EEW, "3")
        val p2p = snapshot(confirmed, LiveUpdateKind.NONE, activeEew = false)
        val dmdss = snapshot(forecast, LiveUpdateKind.EEW, activeEew = true)
        val combined = p2p.copy(
            sourceMode = DataSourceMode.DMDSS,
            activeEew = true,
            activeEewEvent = forecast,
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
    }

    private fun snapshot(
        event: EarthquakeEvent,
        updateKind: LiveUpdateKind,
        activeEew: Boolean
    ) = AppSnapshot(
        sourceMode = DataSourceMode.DMDSS,
        connectionState = ConnectionState.CONNECTED,
        activeEew = activeEew,
        activeEewEvent = event.takeIf { activeEew },
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
