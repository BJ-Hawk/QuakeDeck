package cz.misa.quakedeck.data

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test

class NotificationEventPayloadTest {
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
