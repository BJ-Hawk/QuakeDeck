package cz.misa.quakedeck.data

import java.time.DayOfWeek
import java.time.LocalDate
import java.time.LocalDateTime
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class CorePolicyTest {
    private fun earthquakeEvent(stage: EarthquakeReportStage) = EarthquakeEvent(
        id = "quake:2026-08-08T21:23:43+09:00",
        place = "Test",
        originTime = "2026-08-08 21:23:43 JST",
        magnitude = 3.0,
        depthKm = 10,
        maxIntensity = "1",
        latitude = 35.0,
        longitude = 139.0,
        points = emptyList(),
        reportStage = stage
    )

    private fun station(networkJa: String) = SeismicStation(
        code = "TEST",
        nameJa = "試験",
        prefectureJa = "東京都",
        latitude = 35.0,
        longitude = 139.0,
        networkJa = networkJa
    )

    @Test
    fun eventOriginDisplayHidesOnlyZeroSeconds() {
        assertEquals(
            "2026-08-03 12:34 JST",
            displayEventOriginTime("2026-08-03 12:34:00 JST")
        )
        assertEquals(
            "2026-08-03 12:34:56 JST",
            displayEventOriginTime("2026-08-03 12:34:56 JST")
        )
    }

    @Test
    fun jmaReportReadinessRequiresThePublishedDetailJsonForTheIncident() {
        val reportId = "20260808212614"
        val preparing = """
            [{"eid":"20260808212343","ctt":"$reportId","json":""}]
        """.trimIndent()
        val published = """
            [{"eid":"20260808212343","ctt":"$reportId","json":"20260808212614_20260808212343_VXSE5k_1.json"}]
        """.trimIndent()

        assertEquals(
            JmaReportReadiness.PREPARING,
            jmaReportReadinessFromList(reportId, preparing)
        )
        assertEquals(
            JmaReportReadiness.AVAILABLE,
            jmaReportReadinessFromList(reportId, published)
        )
    }

    @Test
    fun officialJmaReportIdUsesTheReportIssueTimestamp() {
        val event = EarthquakeEvent(
            id = "quake:2026-08-08T21:23:43+09:00",
            place = "Test",
            originTime = "2026-08-08 21:23:43 JST",
            magnitude = 3.0,
            depthKm = 10,
            maxIntensity = "1",
            latitude = 35.0,
            longitude = 139.0,
            points = emptyList(),
            reportIssuedAt = "2026-08-08 21:26:14 JST"
        )

        assertEquals("20260808212614", officialJmaReportId(event))
    }

    @Test
    fun onlyAWaitingDetailedReportUsesTheOfficialJmaPreparingStatus() {
        assertTrue(
            shouldShowOfficialJmaReportPreparing(
                earthquakeEvent(EarthquakeReportStage.DETAILED),
                JmaReportReadiness.PREPARING
            )
        )
        assertFalse(
            shouldShowOfficialJmaReportPreparing(
                earthquakeEvent(EarthquakeReportStage.INITIAL_INTENSITY),
                JmaReportReadiness.PREPARING
            )
        )
        assertFalse(
            shouldShowOfficialJmaReportPreparing(
                earthquakeEvent(EarthquakeReportStage.DETAILED),
                JmaReportReadiness.AVAILABLE
            )
        )
    }

    @Test
    fun mapCoverageRejectsInvalidAndOutOfBoundsCoordinates() {
        assertTrue(JapanMapCoverage.contains(35.6762, 139.6503))
        assertFalse(JapanMapCoverage.contains(Double.NaN, 139.6503))
        assertFalse(JapanMapCoverage.contains(35.6762, Double.POSITIVE_INFINITY))
        assertFalse(JapanMapCoverage.contains(0.0, 0.0))
    }

    @Test
    fun stationProviderVisibilityDistinguishesAllThreeNetworks() {
        val niedOnly = StationProviderVisibility(
            jma = false,
            nied = true,
            localGovernment = false
        )

        assertFalse(niedOnly.includes(station("気象庁")))
        assertTrue(niedOnly.includes(station("防災科学技術研究所")))
        assertFalse(niedOnly.includes(station("地方公共団体")))
        assertFalse(niedOnly.includes(station("unknown")))
    }

    @Test
    fun activeReportAlwaysSuppressesIdleCatalogStations() {
        val allProviders = StationProviderVisibility()

        assertTrue(
            shouldShowCatalogStation(
                reportActive = false,
                station = station("気象庁"),
                visibility = allProviders
            )
        )
        assertFalse(
            shouldShowCatalogStation(
                reportActive = true,
                station = station("気象庁"),
                visibility = allProviders
            )
        )
    }

    @Test
    fun scheduleEncodingRoundTripsOverrides() {
        val mondayOverride = QuietPeriod(
            enabled = true,
            startMinuteOfDay = 21 * 60 + 15,
            endMinuteOfDay = 6 * 60 + 45
        )
        val original = QuietHoursSchedule(
            includePublicHolidays = true
        ).withDayOverride(DayOfWeek.MONDAY, mondayOverride)

        assertEquals(original, QuietHoursSchedule.decode(original.encode()))
    }

    @Test
    fun scheduleDecodingRejectsMalformedValues() {
        assertNull(QuietHoursSchedule.decode(null))
        assertNull(QuietHoursSchedule.decode(""))
        assertNull(QuietHoursSchedule.decode("2;1,0,0;1,0,0;0;-|-|-|-|-|-|-"))
        assertNull(QuietHoursSchedule.decode("1;1,0,0;1,0,0;0;-|-|-"))
    }

    @Test
    fun weeklyPolicyHandlesOvernightBoundary() {
        val schedule = QuietHoursSchedule(
            weekday = QuietPeriod(true, 22 * 60, 7 * 60),
            weekend = QuietPeriod(false, 0, 0)
        )

        assertTrue(
            WeeklyQuietHoursPolicy.isActive(
                enabled = true,
                schedule = schedule,
                now = LocalDateTime.of(2026, 8, 3, 22, 0)
            )
        )
        assertTrue(
            WeeklyQuietHoursPolicy.isActive(
                enabled = true,
                schedule = schedule,
                now = LocalDateTime.of(2026, 8, 4, 6, 59)
            )
        )
        assertFalse(
            WeeklyQuietHoursPolicy.isActive(
                enabled = true,
                schedule = schedule,
                now = LocalDateTime.of(2026, 8, 4, 7, 0)
            )
        )
    }

    @Test
    fun publicHolidayUsesWeekendPeriodWhenEnabled() {
        val holiday = LocalDate.of(2026, 8, 3)
        val schedule = QuietHoursSchedule(
            weekday = QuietPeriod(false, 0, 0),
            weekend = QuietPeriod(true, 9 * 60, 17 * 60),
            includePublicHolidays = true
        )

        assertTrue(
            WeeklyQuietHoursPolicy.isActive(
                enabled = true,
                schedule = schedule,
                now = holiday.atTime(12, 0),
                isPublicHoliday = { it == holiday }
            )
        )
        assertFalse(
            WeeklyQuietHoursPolicy.isActive(
                enabled = false,
                schedule = schedule,
                now = holiday.atTime(12, 0),
                isPublicHoliday = { true }
            )
        )
    }
}
