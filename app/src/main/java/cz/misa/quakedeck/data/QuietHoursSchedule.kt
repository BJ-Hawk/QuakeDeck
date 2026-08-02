package cz.misa.quakedeck.data

import java.time.DayOfWeek
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.LocalTime

/** One local-time quiet period. Equal start/end means a full 24-hour period. */
data class QuietPeriod(
    val enabled: Boolean,
    val startMinuteOfDay: Int,
    val endMinuteOfDay: Int
) {
    val startHour: Int get() = normalizedStart / 60
    val startMinute: Int get() = normalizedStart % 60
    val endHour: Int get() = normalizedEnd / 60
    val endMinute: Int get() = normalizedEnd % 60

    private val normalizedStart: Int get() = startMinuteOfDay.coerceIn(0, MINUTES_PER_DAY - 1)
    private val normalizedEnd: Int get() = endMinuteOfDay.coerceIn(0, MINUTES_PER_DAY - 1)

    fun withStart(hour: Int, minute: Int): QuietPeriod = copy(
        startMinuteOfDay = toMinuteOfDay(hour, minute)
    )

    fun withEnd(hour: Int, minute: Int): QuietPeriod = copy(
        endMinuteOfDay = toMinuteOfDay(hour, minute)
    )

    internal fun encode(): String = listOf(
        if (enabled) "1" else "0",
        normalizedStart.toString(),
        normalizedEnd.toString()
    ).joinToString(",")

    companion object {
        const val MINUTES_PER_DAY = 24 * 60

        val DEFAULT_WEEKDAY = QuietPeriod(
            enabled = true,
            startMinuteOfDay = 22 * 60,
            endMinuteOfDay = 7 * 60
        )
        val DEFAULT_WEEKEND = QuietPeriod(
            enabled = true,
            startMinuteOfDay = 23 * 60,
            endMinuteOfDay = 9 * 60
        )

        fun toMinuteOfDay(hour: Int, minute: Int): Int =
            hour.coerceIn(0, 23) * 60 + minute.coerceIn(0, 59)

        internal fun decode(raw: String): QuietPeriod? {
            val fields = raw.split(',')
            if (fields.size != 3) return null
            val enabled = when (fields[0]) {
                "1" -> true
                "0" -> false
                else -> return null
            }
            val start = fields[1].toIntOrNull()?.coerceIn(0, MINUTES_PER_DAY - 1) ?: return null
            val end = fields[2].toIntOrNull()?.coerceIn(0, MINUTES_PER_DAY - 1) ?: return null
            return QuietPeriod(enabled, start, end)
        }
    }
}

/**
 * Weekly schedule with simple weekday/weekend defaults and optional per-day overrides.
 * Public holidays can deliberately reuse the weekend schedule. Country resolution happens
 * locally; only the selected ISO country code is used when retrieving the yearly calendar.
 */
data class QuietHoursSchedule(
    val weekday: QuietPeriod = QuietPeriod.DEFAULT_WEEKDAY,
    val weekend: QuietPeriod = QuietPeriod.DEFAULT_WEEKEND,
    val includePublicHolidays: Boolean = false,
    /** Monday index 0 through Sunday index 6. Null means inherit weekday/weekend. */
    val dayOverrides: List<QuietPeriod?> = List(7) { null }
) {
    init {
        require(dayOverrides.size == 7) { "Quiet-hours schedule requires seven day slots" }
    }

    fun periodFor(date: LocalDate, isPublicHoliday: Boolean): QuietPeriod {
        if (includePublicHolidays && isPublicHoliday) return weekend
        val dayIndex = date.dayOfWeek.value - 1
        return dayOverrides[dayIndex]
            ?: if (date.dayOfWeek == DayOfWeek.SATURDAY || date.dayOfWeek == DayOfWeek.SUNDAY) {
                weekend
            } else {
                weekday
            }
    }

    fun withDayOverride(day: DayOfWeek, period: QuietPeriod?): QuietHoursSchedule {
        val updated = dayOverrides.toMutableList()
        updated[day.value - 1] = period
        return copy(dayOverrides = updated)
    }

    fun encode(): String {
        val overrides = dayOverrides.joinToString("|") { it?.encode() ?: "-" }
        return listOf(
            ENCODING_VERSION,
            weekday.encode(),
            weekend.encode(),
            if (includePublicHolidays) "1" else "0",
            overrides
        ).joinToString(";")
    }

    companion object {
        private const val ENCODING_VERSION = "1"

        fun decode(raw: String?): QuietHoursSchedule? {
            if (raw.isNullOrBlank()) return null
            val fields = raw.split(';', limit = 5)
            if (fields.size != 5 || fields[0] != ENCODING_VERSION) return null
            val weekday = QuietPeriod.decode(fields[1]) ?: return null
            val weekend = QuietPeriod.decode(fields[2]) ?: return null
            val includeHolidays = when (fields[3]) {
                "1" -> true
                "0" -> false
                else -> return null
            }
            val overrideFields = fields[4].split('|')
            if (overrideFields.size != 7) return null
            val overrides = overrideFields.map { token ->
                if (token == "-") null else QuietPeriod.decode(token) ?: return null
            }
            return QuietHoursSchedule(weekday, weekend, includeHolidays, overrides)
        }

        /** Preserve the v0.9.58 single-range/day-mask schedule exactly on upgrade. */
        fun fromLegacy(
            daysMask: Int,
            startHour: Int,
            startMinute: Int,
            endHour: Int,
            endMinute: Int
        ): QuietHoursSchedule {
            val period = QuietPeriod(
                enabled = true,
                startMinuteOfDay = QuietPeriod.toMinuteOfDay(startHour, startMinute),
                endMinuteOfDay = QuietPeriod.toMinuteOfDay(endHour, endMinute)
            )
            val disabled = period.copy(enabled = false)
            val normalizedMask = daysMask and 0b1111111
            val weekdaySelected = (0..4).map { normalizedMask and (1 shl it) != 0 }
            val weekendSelected = (5..6).map { normalizedMask and (1 shl it) != 0 }

            val weekdayBase = when {
                weekdaySelected.all { it } -> period
                weekdaySelected.none { it } -> disabled
                else -> disabled
            }
            val weekendBase = when {
                weekendSelected.all { it } -> period
                weekendSelected.none { it } -> disabled
                else -> disabled
            }
            val overrides = MutableList<QuietPeriod?>(7) { null }
            if (weekdaySelected.any { it } && !weekdaySelected.all { it }) {
                weekdaySelected.forEachIndexed { index, selected ->
                    if (selected) overrides[index] = period
                }
            }
            if (weekendSelected.any { it } && !weekendSelected.all { it }) {
                weekendSelected.forEachIndexed { index, selected ->
                    if (selected) overrides[index + 5] = period
                }
            }
            return QuietHoursSchedule(
                weekday = weekdayBase,
                weekend = weekendBase,
                includePublicHolidays = false,
                dayOverrides = overrides
            )
        }
    }
}

/** Pure policy evaluation, intentionally independent of Android UI and storage. */
object WeeklyQuietHoursPolicy {
    fun isActive(
        enabled: Boolean,
        schedule: QuietHoursSchedule,
        now: LocalDateTime,
        isPublicHoliday: (LocalDate) -> Boolean = { false }
    ): Boolean {
        if (!enabled) return false
        val date = now.toLocalDate()
        val time = now.toLocalTime()

        val todayPeriod = schedule.periodFor(date, isPublicHoliday(date))
        if (startsToday(todayPeriod, time)) return true

        val previousDate = date.minusDays(1)
        val previousPeriod = schedule.periodFor(previousDate, isPublicHoliday(previousDate))
        return continuesFromPreviousDay(previousPeriod, time)
    }

    private fun startsToday(period: QuietPeriod, time: LocalTime): Boolean {
        if (!period.enabled) return false
        val start = LocalTime.of(period.startHour, period.startMinute)
        val end = LocalTime.of(period.endHour, period.endMinute)
        return when {
            start < end -> time >= start && time < end
            start > end -> time >= start
            else -> time >= start // full 24-hour period; earlier portion is covered by yesterday
        }
    }

    private fun continuesFromPreviousDay(period: QuietPeriod, time: LocalTime): Boolean {
        if (!period.enabled) return false
        val start = LocalTime.of(period.startHour, period.startMinute)
        val end = LocalTime.of(period.endHour, period.endMinute)
        return when {
            start > end -> time < end
            start == end -> time < end // full 24-hour period
            else -> false
        }
    }
}
