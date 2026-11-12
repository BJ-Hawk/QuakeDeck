package cz.misa.quakedeck.data

import org.junit.Assert.*
import org.junit.Test

class NotificationRestorationSafetyTest {
    private val now = EewLifecyclePolicy.sourceMillis("2026-11-12 12:00:00 JST")!!
    private fun report(id: String = "notice", cancelled: Boolean = false, expiresAt: String? =
        "2026-11-12 13:00:00 JST") = TsunamiReport(
        id = id, issueTime = "2026-11-12 11:55:00 JST", issueType = "warning",
        expiresAt = expiresAt, cancelled = cancelled,
        areas = listOf(TsunamiArea(name = "東京湾内湾", grade = TsunamiGrade.WARNING))
    )
    private fun launch(report: TsunamiReport = report()) =
        NotificationLaunchPayload(NotificationLaunchKind.TSUNAMI, tsunami = report)

    @Test fun freshNotificationRestoresColdStartWithoutAProviderState() {
        val restored = waitingSnapshot().withNotificationLaunch(launch(), now)
        assertTrue(restored.activeTsunami)
        assertEquals("notice", restored.tsunami?.id)
    }

    @Test fun providerCancellationWinsEvenWithoutTheNewTombstoneField() {
        val ended = waitingSnapshot().copy(tsunami = report(cancelled = true), activeTsunami = false)
        assertSame(ended, ended.withNotificationLaunch(launch(), now))
    }

    @Test fun clearedAuthoritativeStateCannotBeResurrected() {
        val cleared = waitingSnapshot().copy(tsunamiStateKnown = true, activeTsunami = false)
        assertSame(cleared, cleared.withNotificationLaunch(launch(), now))
    }

    @Test fun newerDifferentAndSameIncidentReportsRemainAuthoritative() {
        for (id in listOf("notice", "newer-notice")) {
            val live = waitingSnapshot().copy(tsunami = report(id), activeTsunami = true)
            assertSame(live, live.withNotificationLaunch(launch(), now))
        }
    }

    @Test fun expiredCancelledAndMissingExpiryPayloadsDoNotCreateLiveWarnings() {
        for (report in listOf(
            report(expiresAt = "2026-11-12 12:00:00 JST"),
            report(expiresAt = null),
            report(cancelled = true)
        )) assertFalse(waitingSnapshot().withNotificationLaunch(launch(report), now).activeTsunami)
    }

    @Test fun expiryIsReevaluatedAndSandboxOffsetsArePreserved() {
        val report = report(expiresAt = "2026-11-12 12:00:00 JST").copy(timelineOffsetMillis = 10_000)
        assertTrue(canRestoreTsunami(report, now + 9_999))
        assertFalse(canRestoreTsunami(report, now + 10_000))
    }

    @Test fun retainedEewCannotReplaceADifferentLiveWarning() {
        val old = waitingSnapshot().event.copy(id = "old", kind = EarthquakeEventKind.EEW)
        val current = old.copy(id = "current")
        val live = waitingSnapshot().copy(activeEew = true, activeEewEvent = current, event = current)
        val payload = NotificationLaunchPayload(NotificationLaunchKind.EEW, event = old,
            activeUntilMillis = now + 60_000)
        assertSame(live, live.withNotificationLaunch(payload, now))
    }

    @Test fun decoderRejectsOversizedUntrustedInput() {
        assertNull(NotificationEventPayload.decodeLaunch(" ".repeat(450_001)))
    }
}
