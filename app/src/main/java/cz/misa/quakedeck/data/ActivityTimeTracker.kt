package cz.misa.quakedeck.data

import android.content.Context
import androidx.core.content.edit
import java.time.Instant
import java.time.ZoneId

/**
 * Event-driven accounting for QuakeDeck's own activity time.
 *
 * It deliberately has no timer or scheduled work. Each state transition adds
 * the elapsed wall-clock interval to every applicable overlapping dimension.
 * A new process starts a fresh live interval, so time while Android had killed
 * the process is never inferred as resident or monitored time.
 */
data class ActivityTimeStats(
    val todayMonitoringMillis: Long,
    val todayUiForegroundMillis: Long,
    val todayUiBackgroundResidentMillis: Long,
    val todayMonitoringOnlyMillis: Long,
    val monitoringMillis: Long,
    val uiForegroundMillis: Long,
    val uiBackgroundResidentMillis: Long,
    val monitoringOnlyMillis: Long
)

class ActivityTimeTracker(context: Context) {
    private val prefs = context.applicationContext.getSharedPreferences(
        PREFERENCES,
        Context.MODE_PRIVATE
    )

    private var uiForeground = false
    private var monitoring = false
    private var lastAccountedAtMillis = 0L
    private var todayKey = todayKey(System.currentTimeMillis())

    @Synchronized
    fun beginProcess() {
        // A process can disappear without an onDestroy callback. Do not turn
        // that unknown gap into invented background/monitoring time.
        uiForeground = false
        monitoring = false
        lastAccountedAtMillis = System.currentTimeMillis()
        val currentTodayKey = todayKey(lastAccountedAtMillis)
        if (prefs.getString(KEY_TODAY, null) != currentTodayKey) {
            clearToday(currentTodayKey)
        } else {
            todayKey = currentTodayKey
        }
        persistLiveState()
    }

    @Synchronized
    fun setUiForeground(active: Boolean) {
        advance(System.currentTimeMillis())
        uiForeground = active
        persistLiveState()
    }

    @Synchronized
    fun setMonitoringActive(active: Boolean) {
        advance(System.currentTimeMillis())
        monitoring = active
        persistLiveState()
    }

    @Synchronized
    fun snapshot(): ActivityTimeStats {
        advance(System.currentTimeMillis())
        return ActivityTimeStats(
            todayMonitoringMillis = prefs.getLong(KEY_TODAY_MONITORING, 0L),
            todayUiForegroundMillis = prefs.getLong(KEY_TODAY_UI_FOREGROUND, 0L),
            todayUiBackgroundResidentMillis = prefs.getLong(KEY_TODAY_UI_BACKGROUND, 0L),
            todayMonitoringOnlyMillis = prefs.getLong(KEY_TODAY_MONITORING_ONLY, 0L),
            monitoringMillis = prefs.getLong(KEY_MONITORING, 0L),
            uiForegroundMillis = prefs.getLong(KEY_UI_FOREGROUND, 0L),
            uiBackgroundResidentMillis = prefs.getLong(KEY_UI_BACKGROUND, 0L),
            monitoringOnlyMillis = prefs.getLong(KEY_MONITORING_ONLY, 0L)
        )
    }

    @Synchronized
    fun reset(): ActivityTimeStats {
        val now = System.currentTimeMillis()
        lastAccountedAtMillis = now
        todayKey = todayKey(now)
        prefs.edit {
            putLong(KEY_MONITORING, 0L)
            putLong(KEY_UI_FOREGROUND, 0L)
            putLong(KEY_UI_BACKGROUND, 0L)
            putLong(KEY_MONITORING_ONLY, 0L)
            putLong(KEY_TODAY_MONITORING, 0L)
            putLong(KEY_TODAY_UI_FOREGROUND, 0L)
            putLong(KEY_TODAY_UI_BACKGROUND, 0L)
            putLong(KEY_TODAY_MONITORING_ONLY, 0L)
            putString(KEY_TODAY, todayKey)
        }
        persistLiveState()
        return snapshot()
    }

    private fun advance(nowMillis: Long) {
        if (lastAccountedAtMillis == 0L) {
            lastAccountedAtMillis = nowMillis
            todayKey = todayKey(nowMillis)
            return
        }
        if (nowMillis <= lastAccountedAtMillis) return

        var cursor = lastAccountedAtMillis
        while (cursor < nowMillis) {
            val cursorDay = todayKey(cursor)
            if (todayKey != cursorDay) {
                clearToday(cursorDay)
            }
            val end = minOf(nowMillis, nextDayStart(cursor))
            addInterval(end - cursor)
            cursor = end
        }
        lastAccountedAtMillis = nowMillis
        persistLiveState()
    }

    private fun addInterval(elapsedMillis: Long) {
        if (elapsedMillis <= 0L) return
        prefs.edit {
            if (monitoring) {
                add(KEY_MONITORING, elapsedMillis)
                add(KEY_TODAY_MONITORING, elapsedMillis)
            }
            if (uiForeground) {
                add(KEY_UI_FOREGROUND, elapsedMillis)
                add(KEY_TODAY_UI_FOREGROUND, elapsedMillis)
            } else {
                add(KEY_UI_BACKGROUND, elapsedMillis)
                add(KEY_TODAY_UI_BACKGROUND, elapsedMillis)
            }
            if (monitoring && !uiForeground) {
                add(KEY_MONITORING_ONLY, elapsedMillis)
                add(KEY_TODAY_MONITORING_ONLY, elapsedMillis)
            }
        }
    }

    private fun android.content.SharedPreferences.Editor.add(key: String, amount: Long) {
        putLong(key, prefs.getLong(key, 0L) + amount)
    }

    private fun clearToday(newTodayKey: String) {
        todayKey = newTodayKey
        prefs.edit {
            putString(KEY_TODAY, newTodayKey)
            putLong(KEY_TODAY_MONITORING, 0L)
            putLong(KEY_TODAY_UI_FOREGROUND, 0L)
            putLong(KEY_TODAY_UI_BACKGROUND, 0L)
            putLong(KEY_TODAY_MONITORING_ONLY, 0L)
        }
    }

    private fun persistLiveState() {
        prefs.edit {
            putBoolean(KEY_UI_FOREGROUND_ACTIVE, uiForeground)
            putBoolean(KEY_MONITORING_ACTIVE, monitoring)
            putLong(KEY_LAST_ACCOUNTED_AT, lastAccountedAtMillis)
        }
    }

    private fun todayKey(timeMillis: Long): String =
        Instant.ofEpochMilli(timeMillis).atZone(ZoneId.systemDefault()).toLocalDate().toString()

    private fun nextDayStart(timeMillis: Long): Long =
        Instant.ofEpochMilli(timeMillis)
            .atZone(ZoneId.systemDefault())
            .toLocalDate()
            .plusDays(1)
            .atStartOfDay(ZoneId.systemDefault())
            .toInstant()
            .toEpochMilli()

    private companion object {
        const val PREFERENCES = "quakedeck_activity_time"
        const val KEY_TODAY = "today"
        const val KEY_UI_FOREGROUND_ACTIVE = "ui_foreground_active"
        const val KEY_MONITORING_ACTIVE = "monitoring_active"
        const val KEY_LAST_ACCOUNTED_AT = "last_accounted_at"
        const val KEY_MONITORING = "monitoring_millis"
        const val KEY_UI_FOREGROUND = "ui_foreground_millis"
        const val KEY_UI_BACKGROUND = "ui_background_millis"
        const val KEY_MONITORING_ONLY = "monitoring_only_millis"
        const val KEY_TODAY_MONITORING = "today_monitoring_millis"
        const val KEY_TODAY_UI_FOREGROUND = "today_ui_foreground_millis"
        const val KEY_TODAY_UI_BACKGROUND = "today_ui_background_millis"
        const val KEY_TODAY_MONITORING_ONLY = "today_monitoring_only_millis"
    }
}
