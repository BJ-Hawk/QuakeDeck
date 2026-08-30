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
            // Plain JVM tests have no Android Application context from which to
            // load the bundled travel-time and ground resources. Device/runtime
            // startup initializes them before live providers begin.
            assertTrue(
                result is LocalEewForecastResult.Available ||
                    result is LocalEewForecastResult.NoResult
            )
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

    @Test
    fun localPresentationDoesNotPaintModelledShindoZeroAcrossJapan() {
        val event = testEvent().copy(
            localIntensityForecast = localForecast(
                region("000", "0"),
                region("001", "1")
            )
        )

        assertEquals(
            listOf("001" to "1"),
            event.presentationIntensityPoints().map { it.regionCode to it.intensity }
        )
    }

    @Test
    fun officialRegionalPointsWinWhileMissingAreasUseLocalSupplements() {
        val official = IntensityPoint(
            name = "Official area",
            intensity = "4",
            isArea = true,
            regionCode = "340"
        )
        val event = testEvent().copy(
            points = listOf(official),
            localIntensityForecast = localForecast(
                region("340", "3"),
                region("341", "3"),
                region("342", "0")
            )
        )

        assertEquals(
            listOf("340" to "4", "341" to "3"),
            event.presentationIntensityPoints().map { it.regionCode to it.intensity }
        )
        assertEquals(
            listOf("341"),
            event.localSupplementalIntensityRegions().map { it.areaCode }
        )
    }

    @Test
    fun officialRegionNamePreventsDuplicateWhenItsCodeIsUnavailable() {
        val official = IntensityPoint(
            name = "Area 340",
            intensity = "4",
            isArea = true
        )
        val event = testEvent().copy(
            points = listOf(official),
            localIntensityForecast = localForecast(
                region("340", "3"),
                region("341", "2")
            )
        )

        assertEquals(
            listOf(null to "4", "341" to "2"),
            event.presentationIntensityPoints().map { it.regionCode to it.intensity }
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

    private fun region(code: String, intensity: String) = LocalEewRegionForecast(
        areaCode = code,
        areaNameJa = "Area $code",
        prefectureJa = "Prefecture",
        intensity = LocalEewIntensityRange(0.0, 0.0, intensity, intensity),
        earliestSArrivalEpochMillis = 0L,
        maximumStationCode = "station-$code",
        earliestArrivalStationCode = "station-$code",
        extrapolatedBelowJmaValidationRange = true
    )

    private fun localForecast(vararg regions: LocalEewRegionForecast) =
        LocalEewIntensityForecast(
            regions = regions.toList(),
            nationwideMaximum = regions.first().intensity,
            calculatedAtEpochMillis = 0L,
            method = "test",
            groundData = "test",
            excludedStationCount = 0
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
