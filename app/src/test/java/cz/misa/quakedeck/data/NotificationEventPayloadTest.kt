package cz.misa.quakedeck.data

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Test

class NotificationEventPayloadTest {
    @Test
    fun explicitEarthquakeLaunchKindCannotRestoreAnEewEvent() {
        val event = testEvent(kind = EarthquakeEventKind.EEW)
        val payload = NotificationEventPayload.decodeLaunch(
            NotificationEventPayload.encode(
                event = event,
                launchKind = NotificationLaunchKind.EARTHQUAKE
            )
        )

        assertEquals(NotificationLaunchKind.EARTHQUAKE, payload?.kind)
        val restored = waitingSnapshot().withNotificationLaunch(payload)
        assertFalse(restored.activeEew)
        assertEquals("waiting", restored.event.id)
    }

    @Test
    fun coldLaunchRehydratesActiveEewDetailsAndTimeline() {
        val event = testEvent(kind = EarthquakeEventKind.EEW).copy(
            eewAlertLevel = EewAlertLevel.FORECAST,
            reportSerial = "TEST",
            timelineOffsetMillis = 12_345L
        )

        val launch = NotificationEventPayload.decodeLaunch(
            NotificationEventPayload.encode(event)
        )
        assertNotNull(launch)
        launch!!
        assertEquals(NotificationLaunchKind.EEW, launch.kind)
        assertEquals(event, launch.event)

        val restored = waitingSnapshot().withNotificationLaunch(launch)
        assertTrue(restored.activeEew)
        assertEquals(event, restored.event)
        assertEquals(event, restored.activeEewEvent)
        assertEquals(listOf(ActiveEewIncident(event, null)), restored.activeEewIncidents())
        assertEquals(LiveUpdateKind.NONE, restored.liveUpdateKind)
    }

    @Test
    fun coldLaunchRehydratesTsunamiAreasAndTimeline() {
        val report = TsunamiReport(
            id = "injected-tsunami:test",
            issueTime = "2099-08-23 12:34:56 JST",
            issueType = "Injected test tsunami warning",
            expiresAt = "2099-08-23 13:04:56 JST",
            cancelled = false,
            areas = listOf(
                TsunamiArea(
                    name = "東京湾内湾",
                    grade = TsunamiGrade.WARNING,
                    immediate = false,
                    arrivalTime = "2099-08-23 12:49:56 JST",
                    arrivalCondition = "Injected test",
                    maxHeightDescription = "3 m",
                    maxHeightMeters = 3.0
                )
            ),
            timelineOffsetMillis = 67_890L
        )

        val launch = NotificationEventPayload.decodeLaunch(
            NotificationEventPayload.encodeTsunami(report)
        )
        assertNotNull(launch)
        launch!!
        assertEquals(NotificationLaunchKind.TSUNAMI, launch.kind)
        assertEquals(report, launch.tsunami)

        val restored = waitingSnapshot().withNotificationLaunch(launch)
        assertTrue(restored.activeTsunami)
        assertEquals(report, restored.tsunami)
        assertEquals(LiveUpdateKind.NONE, restored.liveUpdateKind)

        val cachedButInactive = waitingSnapshot().copy(
            activeTsunami = false,
            tsunami = report
        )
        val reactivated = cachedButInactive.withNotificationLaunch(launch)
        assertTrue(reactivated.activeTsunami)
        assertEquals(report, reactivated.tsunami)
    }

    @Test
    fun liveIncidentWinsAfterRuntimeRecoversMatchingNotification() {
        val notificationEvent = testEvent(kind = EarthquakeEventKind.EEW)
        val liveEvent = notificationEvent.copy(reportSerial = "4", maxIntensity = "5+")
        val liveSnapshot = waitingSnapshot().copy(
            activeEew = true,
            activeEewEvent = liveEvent,
            event = liveEvent,
            liveUpdateKind = LiveUpdateKind.EEW,
            liveUpdateSequence = 9L
        )
        val launch = NotificationLaunchPayload(
            kind = NotificationLaunchKind.EEW,
            event = notificationEvent
        )

        val restored = liveSnapshot.withNotificationLaunch(launch)
        assertEquals(liveSnapshot, restored)
        assertEquals("4", restored.activeEewEvent?.reportSerial)
        assertFalse(restored.activeEewEvent == notificationEvent)
    }

    @Test
    fun expiredNotificationPayloadCannotResurrectAnEndedEew() {
        val event = testEvent(kind = EarthquakeEventKind.EEW)
        val activeUntil = 4_000L
        val launch = NotificationEventPayload.decodeLaunch(
            NotificationEventPayload.encode(event, activeUntilMillis = activeUntil)
        )!!

        assertEquals(activeUntil, launch.activeUntilMillis)
        val restored = waitingSnapshot().withNotificationLaunch(
            payload = launch,
            nowEpochMillis = activeUntil
        )

        assertFalse(restored.activeEew)
        assertEquals(null, restored.activeEewEvent)
    }

    @Test
    fun explicitProviderEndWinsOverAStillFreshNotificationPayload() {
        val event = testEvent(kind = EarthquakeEventKind.EEW)
        val ended = waitingSnapshot().copy(
            activeEew = false,
            activeEewEvent = null,
            event = event,
            liveUpdateKind = LiveUpdateKind.EEW_ENDED,
            liveUpdateSequence = 7L
        )
        val launch = NotificationLaunchPayload(
            kind = NotificationLaunchKind.EEW,
            event = event,
            activeUntilMillis = 10_000L
        )

        assertEquals(
            ended,
            ended.withNotificationLaunch(payload = launch, nowEpochMillis = 5_000L)
        )
    }

    @Test
    fun secondaryWarningLaunchKeepsTheLiveCollectionAndItsCurrentRevision() {
        val retained = testEvent(kind = EarthquakeEventKind.EEW)
        val currentSecondary = retained.copy(reportSerial = "4")
        val primary = retained.copy(id = "newer-earthquake")
        val active = listOf(ActiveEewIncident(primary, 9_000L), ActiveEewIncident(currentSecondary, 8_000L))
        val live = waitingSnapshot().copy(event = primary, activeEews = active)
        val payload = NotificationLaunchPayload(
            kind = NotificationLaunchKind.EEW,
            event = retained,
            activeUntilMillis = 7_000L
        )

        assertSame(live, live.withNotificationLaunch(payload, nowEpochMillis = 5_000L))
        assertEquals(active, live.activeEewIncidents())
    }

    @Test
    fun explicitEndOnlyTerminatesTheNamedWarning() {
        val ended = testEvent(kind = EarthquakeEventKind.EEW)
        val primary = ended.copy(id = "newer-earthquake")
        val state = waitingSnapshot().copy(
            event = primary,
            activeEews = listOf(ActiveEewIncident(primary, 9_000L)),
            eewUpdateEvent = ended,
            liveUpdateKind = LiveUpdateKind.EEW_ENDED
        )
        val endedLaunch = NotificationLaunchPayload(
            kind = NotificationLaunchKind.EEW,
            event = ended,
            activeUntilMillis = 7_000L
        )

        assertTrue(state.hasEndedEew(ended.id))
        assertFalse(state.hasEndedEew(primary.id))
        assertFalse(state.copy(activeEews = emptyList()).hasEndedEew(primary.id))
        assertSame(state, state.withNotificationLaunch(endedLaunch, nowEpochMillis = 5_000L))
    }

    private fun testEvent(kind: EarthquakeEventKind): EarthquakeEvent = EarthquakeEvent(
        id = "notification-event",
        place = "Off Chiba Prefecture",
        originTime = "2099-08-23 12:34:56 JST",
        magnitude = 6.2,
        depthKm = 20,
        maxIntensity = "5-",
        latitude = 35.6,
        longitude = 140.1,
        points = listOf(
            IntensityPoint(
                name = "千葉県北西部",
                intensity = "5-",
                arrivalTime = "2099-08-23 12:35:26 JST",
                prefecture = "千葉県",
                isArea = true
            )
        ),
        kind = kind,
        reportIssuedAt = "2099-08-23 12:35:00 JST",
        reportStage = EarthquakeReportStage.DETAILED,
        hasHypocenter = true
    )
}
