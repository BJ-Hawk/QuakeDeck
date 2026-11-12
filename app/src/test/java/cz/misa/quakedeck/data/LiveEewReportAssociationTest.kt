package cz.misa.quakedeck.data

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class LiveEewReportAssociationTest {
    @Test
    fun matchingJmaIdAssociatesMinutePrecisionMiyagiReports() {
        assertTrue(matchesLiveEewReport(forecast, detailedReport))
        assertTrue(matchesLiveEewReport(forecast.copy(isCancelled = true), detailedReport))
    }

    @Test
    fun matchingJmaIdTakesPrecedenceOverRevisedOrigin() {
        val differentMinute = detailedReport.copy(originTime = "2026-11-09 05:17:00 JST")

        assertTrue(matchesLiveEewReport(forecast, differentMinute))
        assertFalse(matchesLiveEewReport(forecast, differentMinute.copy(jmaReportEventId = null)))
    }

    @Test
    fun differingJmaIdRejectsOtherwiseMatchingReport() {
        val differentId = detailedReport.copy(jmaReportEventId = "20261109051656")

        assertFalse(matchesLiveEewReport(forecast, differentId))
    }

    @Test
    fun publicReportsWithoutAnIdUseOriginMinuteAndHypocenterWhenAvailable() {
        val publicReport = detailedReport.copy(jmaReportEventId = null)
        val scalePrompt = publicReport.copy(
            hasHypocenter = false,
            latitude = -200.0,
            longitude = -200.0
        )

        assertTrue(matchesLiveEewReport(forecast, publicReport))
        assertTrue(matchesLiveEewReport(forecast, scalePrompt))
        assertFalse(matchesLiveEewReport(forecast, publicReport.copy(latitude = 42.0)))
    }

    @Test
    fun drillAndWithdrawnReportDoNotMatchLiveReports() {
        assertFalse(matchesLiveEewReport(forecast.copy(isDrill = true), detailedReport))
        assertFalse(matchesLiveEewReport(forecast, detailedReport.copy(isCancelled = true)))
    }

    private val forecast = EarthquakeEvent(
        id = "20261109051637",
        place = "宮城県沖",
        originTime = "2026-11-09 05:16:28 JST",
        magnitude = 4.6,
        depthKm = 50,
        maxIntensity = "3",
        latitude = 38.3,
        longitude = 141.9,
        points = emptyList(),
        kind = EarthquakeEventKind.EEW,
        eewAlertLevel = EewAlertLevel.FORECAST
    )

    private val detailedReport = forecast.copy(
        id = "quake:jma:20261109051637",
        originTime = "2026-11-09 05:16:00 JST",
        magnitude = 4.5,
        kind = EarthquakeEventKind.CONFIRMED,
        jmaReportEventId = "20261109051637"
    )
}
