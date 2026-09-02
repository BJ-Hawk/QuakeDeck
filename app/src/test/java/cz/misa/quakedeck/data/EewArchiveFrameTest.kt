package cz.misa.quakedeck.data

import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Test

class EewArchiveFrameTest {
    @Test
    fun archiveRetainsSourceInputsAndOnlyOfficialAreas() {
        val event = eew().copy(
            eewHypocenterCondition = "仮定震源要素",
            localIntensityForecast = forecast("341", "3")
        )
        val raw = EewArchiveFrame.encode(event)
        val point = raw.getJSONArray("areas").getJSONObject(0)

        assertEquals(556, raw.getInt("code"))
        assertEquals("FORECAST", raw.getString("quakedeckAlertLevel"))
        assertEquals("5-", raw.getString("quakedeckMaxIntensity"))
        assertEquals("M", raw.getString("quakedeckMagnitudeUnit"))
        assertEquals("仮定震源要素", raw.getString("quakedeckHypocenterCondition"))
        assertEquals(1, raw.getJSONArray("areas").length())
        assertEquals("340", point.getString("code"))
        assertEquals(30, point.getInt("scaleFrom"))
        assertEquals(99, point.getInt("scaleTo"))
        assertEquals("2026/08/30 02:28:00", point.getString("arrivalTime"))
        assertTrue(point.getBoolean("isPlum"))
        assertTrue(point.getBoolean("isWarning"))
        assertFalse(raw.has("localIntensityForecast"))
    }

    @Test
    fun replayRestoresExactCalculationInputsAndOfficialMetadataBeforeCallingEngine() {
        val original = eew().copy(eewHypocenterCondition = "仮定震源要素")
        val parsed = legacyParsed(original).copy(maxIntensity = "3", timelineOffsetMillis = 123L)
        val raw = EewArchiveFrame.encode(original)
        var calls = 0
        val result = EewArchiveFrame.forReplay(parsed, raw) { event, at ->
            calls++
            assertEquals(original, event)
            assertEquals(EewWaveModel.timelineEpochMillis(original, original.reportIssuedAt!!), at)
            // The real engine, not replay plumbing, owns the PLUM/depth quality gates.
            LocalEewForecastResult.NoResult
        }

        assertEquals(1, calls)
        assertEquals(original, result)
    }

    @Test
    fun replayUsesSameHybridPresentationAsLiveWithoutOverwritingOfficialPoints() {
        val original = eew()
        val local = forecast("340" to "2", "341" to "3", "342" to "0")
        val live = original.copy(localIntensityForecast = local)
        val replay = EewArchiveFrame.forReplay(legacyParsed(original), EewArchiveFrame.encode(original)) { _, _ ->
            LocalEewForecastResult.Available(local)
        }

        assertEquals(live.presentationIntensityPoints(), replay.presentationIntensityPoints())
        assertEquals(original.points, replay.points)
        assertEquals(listOf("340", "341"), replay.presentationIntensityPoints().map { it.regionCode })
        assertEquals(original.eewAlertLevel, replay.eewAlertLevel)
        assertEquals(original.maxIntensity, replay.maxIntensity)
    }

    @Test
    fun emptyRegionsInAnOldArchiveStillReceiveLocalShading() {
        val old = legacyParsed(eew()).copy(points = emptyList(), maxIntensity = "3")
        val raw = JSONObject("""{"code":556,"areas":[],"quakedeckMaxIntensity":"3"}""")
        val result = EewArchiveFrame.forReplay(old, raw) { restored, _ ->
            assertEquals(old, restored)
            LocalEewForecastResult.Available(forecast("341", "3"))
        }

        assertTrue(result.points.isEmpty())
        assertEquals(listOf("341"), result.presentationIntensityPoints().map { it.regionCode })
        assertNull(result.eewSourceAccuracy)
    }

    @Test
    fun revisionsRecalculateFromTheirOwnInputsAndDoNotRetainLaterCoverage() {
        val first = eew().copy(reportSerial = "1", points = emptyList(), magnitude = 4.0)
        val second = eew().copy(reportSerial = "2", magnitude = 5.0)
        val calls = mutableListOf<String?>()
        val calculator = { event: EarthquakeEvent, _: Long ->
            calls += event.reportSerial
            LocalEewForecastResult.Available(
                if (event.reportSerial == "1") forecast("342", "1") else forecast("341", "3")
            )
        }
        val earlier = EewArchiveFrame.forReplay(first, EewArchiveFrame.encode(first), calculator)
        val later = EewArchiveFrame.forReplay(second, EewArchiveFrame.encode(second), calculator)

        assertEquals(listOf("1", "2"), calls)
        assertEquals(listOf("342"), earlier.presentationIntensityPoints().map { it.regionCode })
        assertEquals(listOf("340", "341"), later.presentationIntensityPoints().map { it.regionCode })
        assertEquals(4.0, earlier.magnitude, 0.0)
        assertEquals(5.0, later.magnitude, 0.0)
    }

    @Test
    fun areaMetadataSurvivesParserSorting() {
        val first = eew().points.single()
        val second = first.copy(name = "Other", stationName = "Other", regionCode = "341", isPlum = false)
        val event = eew().copy(points = listOf(first, second))
        val parsed = legacyParsed(event).copy(points = legacyParsed(event).points.reversed())
        val result = EewArchiveFrame.forReplay(parsed, EewArchiveFrame.encode(event)) { _, _ ->
            LocalEewForecastResult.NoResult
        }
        assertEquals(listOf(second, first), result.points)
    }

    @Test
    fun liteAndNoResultKeepOfficialDataWithoutStaleLocalAreas() {
        val event = eew().copy(localIntensityForecast = forecast("341", "3"))
        val outcomes = listOf(
            LocalEewForecastResult.NoResult,
            LocalEewForecastResult.Unavailable(LocalEewForecastUnavailableReason.IMPLEMENTATION_OMITTED),
            LocalEewForecastResult.Unavailable(LocalEewForecastUnavailableReason.IMPLEMENTATION_FAILED)
        )
        outcomes.forEach { outcome ->
            val result = EewArchiveFrame.forReplay(event, EewArchiveFrame.encode(event)) { _, _ -> outcome }
            assertEquals(event.points, result.presentationIntensityPoints())
            assertNull(result.localIntensityForecast)
        }
    }

    @Test
    fun confirmedAndCancelledReportsNeverRequestCalculations() {
        val confirmed = eew().copy(kind = EarthquakeEventKind.CONFIRMED)
        val cancelled = eew().copy(isCancelled = true)
        val shouldNotCalculate = { _: EarthquakeEvent, _: Long -> error("Unexpected calculation") }
        assertSame(confirmed, EewArchiveFrame.forReplay(confirmed, JSONObject(), shouldNotCalculate))
        assertEquals(cancelled, EewArchiveFrame.forReplay(
            eew(), EewArchiveFrame.encode(cancelled), shouldNotCalculate
        ))
    }

    private fun legacyParsed(event: EarthquakeEvent) = event.copy(
        eewMagnitudeUnit = null,
        eewHypocenterCondition = null,
        eewSourceAccuracy = null,
        points = event.points.map { it.copy(regionCode = null, isPlum = false, isWarning = false) }
    )

    private fun eew() = EarthquakeEvent(
        id = "20260830022635",
        place = "Test",
        originTime = "2026-08-30 02:26:35 JST",
        reportIssuedAt = "2026-08-30 02:27:37 JST",
        reportSerial = "2",
        magnitude = 5.3,
        depthKm = 10,
        latitude = 35.0,
        longitude = 139.0,
        maxIntensity = "5-",
        kind = EarthquakeEventKind.EEW,
        eewAlertLevel = EewAlertLevel.FORECAST,
        eewMagnitudeUnit = "M",
        eewSourceAccuracy = EewSourceAccuracy(listOf(1, 2), 3, 4, 5),
        points = listOf(IntensityPoint(
            name = "Area 340", stationName = "Area 340", prefecture = "Prefecture",
            intensity = "3", intensityFrom = "3", intensityUpperOpenEnded = true,
            arrivalTime = "2026-08-30 02:28:00 JST", isArea = true,
            regionCode = "340", isPlum = true, isWarning = true
        ))
    )

    private fun forecast(code: String, intensity: String) = forecast(code to intensity)

    private fun forecast(vararg values: Pair<String, String>) = LocalEewIntensityForecast(
        regions = values.map { (code, intensity) -> LocalEewRegionForecast(
            areaCode = code, areaNameJa = "Area $code", prefectureJa = "Prefecture",
            intensity = LocalEewIntensityRange(0.0, 0.0, intensity, intensity),
            earliestSArrivalEpochMillis = 0L, maximumStationCode = "test", earliestArrivalStationCode = "test",
            extrapolatedBelowJmaValidationRange = true
        ) },
        nationwideMaximum = LocalEewIntensityRange(0.0, 0.0, "3", "3"),
        calculatedAtEpochMillis = 0L, method = "test", groundData = "test", excludedStationCount = 0
    )
}
