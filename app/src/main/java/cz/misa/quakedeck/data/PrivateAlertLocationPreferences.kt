package cz.misa.quakedeck.data

import android.content.Context
import android.content.SharedPreferences
import androidx.core.content.edit
import java.util.concurrent.Executors

/** Keep reference locations out of backup without dropping ordinary UI/alert preferences. */
internal object PrivateAlertLocationPreferences {
    private val writer = Executors.newSingleThreadExecutor { task ->
        Thread(task, "QuakeDeck-location-migration").apply { isDaemon = true }
    }
    private var cleanupScheduled = false

    @Synchronized
    fun open(context: Context, legacy: SharedPreferences): SharedPreferences {
        val privatePrefs = context.applicationContext.getSharedPreferences(
            "quakedeck_private_location", Context.MODE_PRIVATE
        )
        val keys = legacy.all.keys.filter {
            it.startsWith("alert_location_") || it == "location_based_notifications_enabled"
        }
        if (keys.isEmpty()) return privatePrefs
        if (!privatePrefs.contains("location_settings_migrated")) {
            privatePrefs.edit {
                putBoolean("location_settings_migrated", true)
                legacy.all.filterKeys { it in keys }.forEach { (key, value) ->
                    when (value) {
                        is String -> putString(key, value)
                        is Boolean -> putBoolean(key, value)
                    }
                }
            }
        }
        if (!cleanupScheduled) {
            cleanupScheduled = true
            writer.execute {
                // Commit on this worker confirms persistence before removing the
                // legacy copy. A failed write leaves the old location recoverable.
                if (privatePrefs.edit().commit()) {
                    legacy.edit { keys.forEach(::remove) }
                } else synchronized(this) { cleanupScheduled = false }
            }
        }
        return privatePrefs
    }
}
