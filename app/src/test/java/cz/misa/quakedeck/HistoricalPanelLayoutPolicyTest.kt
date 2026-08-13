package cz.misa.quakedeck

import org.junit.Assert.assertEquals
import org.junit.Test

class HistoricalPanelLayoutPolicyTest {
    @Test
    fun sharedLiveAndHistoricalCardUsesTwoRowsForPendingHypocenter() {
        assertEquals(
            ReportLocationParts("Hypocenter under", "assessment"),
            splitReportLocation("Hypocenter under assessment", useEnglish = true)
        )
    }

    @Test
    fun summaryIdentityBelongsToEventRatherThanSelectedReport() {
        assertEquals("historical-event-42", historicalSummaryItemKey("event-42"))
        assertEquals(
            historicalSummaryItemKey("event-42"),
            historicalSummaryItemKey("event-42")
        )
    }

    @Test
    fun historicalReportChangeDoesNotAutomaticallyRefitCamera() {
        assertEquals(false, automaticEventFitAllowed(true, "archive-report-2", false))
    }

    @Test
    fun liveFocusedEventStillAutomaticallyFitsCamera() {
        assertEquals(true, automaticEventFitAllowed(true, "live-report", true))
        assertEquals(false, automaticEventFitAllowed(false, "live-report", true))
        assertEquals(false, automaticEventFitAllowed(true, "waiting", true))
    }
}
