package cz.misa.quakedeck.notifications

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class EewNotificationAlertTrackerTest {
    @Test
    fun unchangedRevisionsStaySilentButAnIntensityChangeAlertsAgain() {
        val tracker = EewNotificationAlertTracker()
        val identity = "eew:FORECAST:20260825002747"

        assertFalse(tracker.shouldSuppress(identity, "1"))
        tracker.recordPosted(identity, "1")
        assertTrue(tracker.shouldSuppress(identity, "1"))
        assertFalse(tracker.shouldSuppress(identity, "2"))
        tracker.recordPosted(identity, "2")
        assertTrue(tracker.shouldSuppress(identity, "2"))

        tracker.reset(identity)
        assertFalse(tracker.shouldSuppress(identity, "2"))
    }
}
