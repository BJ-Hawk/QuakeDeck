package cz.misa.quakedeck.data

import java.time.LocalDateTime
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class LocalEewForecastingTest {
    @Test
    fun loaderDistinguishesPresentMissingAndInvalidImplementations() {
        assertNull(LocalEewForecasts.availabilityForClass(TestForecastProvider::class.java.name))
        assertEquals(
            LocalEewForecastUnavailableReason.IMPLEMENTATION_OMITTED,
            LocalEewForecasts.availabilityForClass("cz.misa.quakedeck.data.DoesNotExist")
        )
        assertEquals(
            LocalEewForecastUnavailableReason.IMPLEMENTATION_FAILED,
            LocalEewForecasts.availabilityForClass(String::class.java.name)
        )
    }

    @Test
    fun providerResultsKeepNoResultSeparateFromFailure() {
        val noResult = LocalEewForecasts.evaluateProvider(TestForecastProvider()) {
            wavefrontState(testEvent(), 0L)
        }
        val failed = LocalEewForecasts.evaluateProvider(ThrowingForecastProvider()) {
            wavefrontState(testEvent(), 0L)
        }

        assertTrue(noResult is LocalEewForecastResult.NoResult)
        assertEquals(
            LocalEewForecastUnavailableReason.IMPLEMENTATION_FAILED,
            (failed as LocalEewForecastResult.Unavailable).reason
        )
    }

    @Test
    fun packagedEngineIsEitherUsableOrDeliberatelyOmitted() {
        val event = testEvent()
        val now = EewWaveModel.timelineEpochMillis(event, event.originTime)!! + 10_000L
        val result = EewWaveModel.wavefrontState(event, now)

        if (LocalEewForecasts.unavailableReason == null) {
            assertEquals(QuakeDeckBuildEdition.FULL, LocalEewForecasts.buildEdition)
            assertTrue(result is LocalEewForecastResult.Available)
        } else {
            assertEquals(QuakeDeckBuildEdition.LITE, LocalEewForecasts.buildEdition)
            assertEquals(
                LocalEewForecastUnavailableReason.IMPLEMENTATION_OMITTED,
                (result as LocalEewForecastResult.Unavailable).reason
            )
        }
    }

    @Test
    fun operationalFallbackUsesShiftedIssueTimeWithoutForecastMath() {
        val event = testEvent().copy(timelineOffsetMillis = 45_000L)
        val issue = parseJst(requireNotNull(event.reportIssuedAt))

        assertEquals(
            issue + 45_000L + 180_000L,
            P2pEewLifecyclePolicy.operationalExpiryMillis(event, receivedAtEpochMillis = 1L)
        )
    }

    @Test
    fun operationalFallbackUsesReceiptForMalformedTimes() {
        val received = 50_000L
        val event = testEvent().copy(originTime = "bad", reportIssuedAt = "also bad")

        assertEquals(
            received + 180_000L,
            P2pEewLifecyclePolicy.operationalExpiryMillis(event, received)
        )
    }

    private fun testEvent() = EarthquakeEvent(
        id = "forecast-boundary-test",
        place = "Test",
        originTime = "2026-08-27 12:00:00 JST",
        magnitude = 5.0,
        depthKm = 20,
        maxIntensity = "4",
        latitude = 35.0,
        longitude = 139.0,
        points = emptyList(),
        kind = EarthquakeEventKind.EEW,
        reportIssuedAt = "2026-08-27 12:00:05 JST"
    )

    private fun parseJst(value: String): Long = LocalDateTime.parse(value, JST_FORMATTER)
        .atZone(ZoneId.of("Asia/Tokyo"))
        .toInstant()
        .toEpochMilli()

    private companion object {
        val JST_FORMATTER: DateTimeFormatter =
            DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss 'JST'")
    }
}

class TestForecastProvider : LocalEewForecastProvider {
    override fun wavefrontState(
        event: EarthquakeEvent,
        nowEpochMillis: Long
    ): EewWaveModel.WavefrontState? = null

    override fun destinationPrediction(
        event: EarthquakeEvent,
        nowEpochMillis: Long,
        destinationName: String,
        destinationLatitude: Double,
        destinationLongitude: Double,
        destinationEewAreaNameJa: String?
    ): EewWaveModel.DestinationPrediction? = null

    override fun estimatedWarningEndEpochMillis(
        event: EarthquakeEvent,
        receivedAtEpochMillis: Long
    ): Long? = null
}

class ThrowingForecastProvider : LocalEewForecastProvider by TestForecastProvider() {
    override fun wavefrontState(
        event: EarthquakeEvent,
        nowEpochMillis: Long
    ): EewWaveModel.WavefrontState? = error("test failure")
}
