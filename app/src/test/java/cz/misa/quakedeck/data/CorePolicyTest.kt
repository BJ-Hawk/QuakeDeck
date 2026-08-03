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
    fun mapCoverageRejectsInvalidAndOutOfBoundsCoordinates() {
        assertTrue(JapanMapCoverage.contains(35.6762, 139.6503))
        assertFalse(JapanMapCoverage.contains(Double.NaN, 139.6503))
        assertFalse(JapanMapCoverage.contains(35.6762, Double.POSITIVE_INFINITY))
        assertFalse(JapanMapCoverage.contains(0.0, 0.0))
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
