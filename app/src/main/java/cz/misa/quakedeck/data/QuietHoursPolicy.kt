@file:Suppress("unused")

package cz.misa.quakedeck.data

import java.time.LocalDateTime
import java.time.LocalTime

/**
 * Evaluates a local-time quiet-hours schedule.
 *
 * Day bits use Monday = bit 0 through Sunday = bit 6. For overnight schedules,
 * a selected day is the day on which the quiet period starts.
 */
object QuietHoursPolicy {
    fun isActive(
        enabled: Boolean,
        daysMask: Int,
        startHour: Int,
        startMinute: Int,
        endHour: Int,
        endMinute: Int,
        now: LocalDateTime
    ): Boolean {
        if (!enabled) return false
        val normalizedMask = daysMask and 0b1111111
        if (normalizedMask == 0) return false

        val time = now.toLocalTime()
        val start = LocalTime.of(startHour.coerceIn(0, 23), startMinute.coerceIn(0, 59))
        val end = LocalTime.of(endHour.coerceIn(0, 23), endMinute.coerceIn(0, 59))

        fun selected(dayValue: Int): Boolean =
            normalizedMask and (1 shl (dayValue - 1)) != 0

        return when {
            start < end -> selected(now.dayOfWeek.value) && time >= start && time < end
            start > end -> when {
                time >= start -> selected(now.dayOfWeek.value)
                time < end -> selected(now.minusDays(1).dayOfWeek.value)
                else -> false
            }
            // Equal start/end means a full 24-hour period beginning on each
            // selected day rather than an accidentally empty schedule.
            time >= start -> selected(now.dayOfWeek.value)
            else -> selected(now.minusDays(1).dayOfWeek.value)
        }
    }
}
