package cz.misa.quakedeck.data

import android.content.Context
import androidx.core.content.edit

/**
 * Small, non-sensitive audit trail for the DM-D.S.S delivery path.
 *
 * Tokens, socket tickets, bulletin bodies, and account details are deliberately
 * never stored here. The retained metadata is just enough to distinguish a
 * transport gap, a rejected envelope, a recovery result, and notification policy.
 */
data class DmDssDiagnosticsSnapshot(
    val socketState: String? = null,
    val lastSocketActivityAtMillis: Long? = null,
    val lastEnvelopeAtMillis: Long? = null,
    val lastAcceptedAtMillis: Long? = null,
    val lastAcceptedEventId: String? = null,
    val lastAcceptedSerial: String? = null,
    val lastAcceptedAlertLevel: String? = null,
    val lastAcceptedSource: String? = null,
    val lastRejectedAtMillis: Long? = null,
    val lastRejectedReason: String? = null,
    val lastRecoveryAtMillis: Long? = null,
    val lastRecoveryResult: String? = null,
    val lastNotificationAtMillis: Long? = null,
    val lastNotificationEventId: String? = null,
    val lastNotificationResult: String? = null
)

class DmDssDiagnosticsStore(context: Context) {
    private val prefs = context.applicationContext.getSharedPreferences(
        PREFS_NAME,
        Context.MODE_PRIVATE
    )

    fun snapshot(): DmDssDiagnosticsSnapshot = DmDssDiagnosticsSnapshot(
        socketState = prefs.getString(KEY_SOCKET_STATE, null),
        lastSocketActivityAtMillis = prefs.optionalLong(KEY_SOCKET_ACTIVITY),
        lastEnvelopeAtMillis = prefs.optionalLong(KEY_ENVELOPE_AT),
        lastAcceptedAtMillis = prefs.optionalLong(KEY_ACCEPTED_AT),
        lastAcceptedEventId = prefs.getString(KEY_ACCEPTED_EVENT, null),
        lastAcceptedSerial = prefs.getString(KEY_ACCEPTED_SERIAL, null),
        lastAcceptedAlertLevel = prefs.getString(KEY_ACCEPTED_LEVEL, null),
        lastAcceptedSource = prefs.getString(KEY_ACCEPTED_SOURCE, null),
        lastRejectedAtMillis = prefs.optionalLong(KEY_REJECTED_AT),
        lastRejectedReason = prefs.getString(KEY_REJECTED_REASON, null),
        lastRecoveryAtMillis = prefs.optionalLong(KEY_RECOVERY_AT),
        lastRecoveryResult = prefs.getString(KEY_RECOVERY_RESULT, null),
        lastNotificationAtMillis = prefs.optionalLong(KEY_NOTIFICATION_AT),
        lastNotificationEventId = prefs.getString(KEY_NOTIFICATION_EVENT, null),
        lastNotificationResult = prefs.getString(KEY_NOTIFICATION_RESULT, null)
    )

    fun recordSocket(state: String, nowMillis: Long = System.currentTimeMillis()) {
        prefs.edit {
            putString(KEY_SOCKET_STATE, state)
            putLong(KEY_SOCKET_ACTIVITY, nowMillis)
        }
    }

    fun recordSocketActivity(nowMillis: Long = System.currentTimeMillis()) {
        prefs.edit { putLong(KEY_SOCKET_ACTIVITY, nowMillis) }
    }

    fun recordEnvelope(nowMillis: Long = System.currentTimeMillis()) {
        prefs.edit { putLong(KEY_ENVELOPE_AT, nowMillis) }
    }

    fun recordAccepted(
        event: EarthquakeEvent,
        source: String,
        nowMillis: Long = System.currentTimeMillis()
    ) {
        prefs.edit {
            putLong(KEY_ACCEPTED_AT, nowMillis)
            putString(KEY_ACCEPTED_EVENT, event.id)
            putString(KEY_ACCEPTED_SERIAL, event.reportSerial)
            putString(KEY_ACCEPTED_LEVEL, event.eewAlertLevel.name)
            putString(KEY_ACCEPTED_SOURCE, source)
        }
    }

    fun recordRejected(reason: String, nowMillis: Long = System.currentTimeMillis()) {
        prefs.edit {
            putLong(KEY_REJECTED_AT, nowMillis)
            putString(KEY_REJECTED_REASON, reason)
        }
    }

    fun recordRecovery(result: String, nowMillis: Long = System.currentTimeMillis()) {
        prefs.edit {
            putLong(KEY_RECOVERY_AT, nowMillis)
            putString(KEY_RECOVERY_RESULT, result)
        }
    }

    fun recordNotification(
        eventId: String,
        result: String,
        nowMillis: Long = System.currentTimeMillis()
    ) {
        prefs.edit {
            putLong(KEY_NOTIFICATION_AT, nowMillis)
            putString(KEY_NOTIFICATION_EVENT, eventId)
            putString(KEY_NOTIFICATION_RESULT, result)
        }
    }

    private fun android.content.SharedPreferences.optionalLong(key: String): Long? =
        if (contains(key)) getLong(key, 0L) else null

    private companion object {
        const val PREFS_NAME = "dmdss_delivery_diagnostics"
        const val KEY_SOCKET_STATE = "socket_state"
        const val KEY_SOCKET_ACTIVITY = "socket_activity"
        const val KEY_ENVELOPE_AT = "envelope_at"
        const val KEY_ACCEPTED_AT = "accepted_at"
        const val KEY_ACCEPTED_EVENT = "accepted_event"
        const val KEY_ACCEPTED_SERIAL = "accepted_serial"
        const val KEY_ACCEPTED_LEVEL = "accepted_level"
        const val KEY_ACCEPTED_SOURCE = "accepted_source"
        const val KEY_REJECTED_AT = "rejected_at"
        const val KEY_REJECTED_REASON = "rejected_reason"
        const val KEY_RECOVERY_AT = "recovery_at"
        const val KEY_RECOVERY_RESULT = "recovery_result"
        const val KEY_NOTIFICATION_AT = "notification_at"
        const val KEY_NOTIFICATION_EVENT = "notification_event"
        const val KEY_NOTIFICATION_RESULT = "notification_result"
    }
}
