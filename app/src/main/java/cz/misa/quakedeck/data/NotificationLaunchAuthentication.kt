package cz.misa.quakedeck.data

import android.content.Context
import android.content.Intent
import cz.misa.quakedeck.MainActivity
import java.nio.ByteBuffer
import java.security.MessageDigest
import java.security.SecureRandom
import java.util.Base64
import javax.crypto.Mac
import javax.crypto.spec.SecretKeySpec

/** Authenticates app-created notification commands across process death. */
internal class NotificationLaunchAuthentication(context: Context) {
    private val prefs = context.applicationContext.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)

    private fun key(create: Boolean): ByteArray? = synchronized(KEY_LOCK) {
        val existing = prefs.getString("key", null)?.let {
            runCatching { Base64.getDecoder().decode(it) }.getOrNull()
        }?.takeIf { it.size == 32 }
        if (existing != null || !create) return@synchronized existing
        ByteArray(32).also { bytes ->
            SecureRandom().nextBytes(bytes)
            // Signing runs on the notification worker. Persist the key before
            // giving Android a PendingIntent that must survive process death.
            check(prefs.edit().putString("key", Base64.getEncoder().encodeToString(bytes)).commit())
        }
    }

    fun sign(intent: Intent) {
        val command = command(intent) ?: return
        intent.putExtra(EXTRA_SIGNATURE, NotificationCommandSignature.sign(requireNotNull(key(create = true)), command))
    }

    fun isTrusted(intent: Intent?): Boolean = runCatching {
        intent ?: return false
        val command = command(intent) ?: return false
        val signature = intent.getStringExtra(EXTRA_SIGNATURE) ?: return false
        NotificationCommandSignature.verify(key(create = false) ?: return false, command, signature)
    }.getOrDefault(false)

    private fun command(intent: Intent): NotificationCommand? {
        val command = NotificationCommand(
            intent.getStringExtra(MainActivity.EXTRA_NOTIFICATION_REPORT_ID),
            intent.getStringExtra(MainActivity.EXTRA_NOTIFICATION_EVENT),
            intent.getBooleanExtra(MainActivity.EXTRA_FULL_SCREEN_EEW, false)
        )
        return command.takeIf { it.isBounded() }
    }

    companion object {
        const val PREFS_NAME = "quakedeck_notification_auth"
        const val EXTRA_SIGNATURE = "cz.misa.quakedeck.extra.NOTIFICATION_SIGNATURE"
        private val KEY_LOCK = Any()
    }
}

internal data class NotificationCommand(val reportId: String?, val payload: String?, val fullScreen: Boolean) {
    fun isBounded(): Boolean = (reportId?.length ?: 0) <= 1_024 && (payload?.length ?: 0) <= 450_000
}

/** Length prefixes distinguish null, empty and concatenated field values. */
internal object NotificationCommandSignature {
    fun sign(key: ByteArray, command: NotificationCommand): String {
        require(command.isBounded())
        val mac = Mac.getInstance("HmacSHA256")
        mac.init(SecretKeySpec(key, "HmacSHA256"))
        listOf("QuakeDeck notification v1", command.reportId, command.payload,
            command.fullScreen.toString()).forEach { field ->
            val bytes = field?.toByteArray(Charsets.UTF_8)
            mac.update(ByteBuffer.allocate(4).putInt(bytes?.size ?: -1).array())
            bytes?.let(mac::update)
        }
        return Base64.getEncoder().encodeToString(mac.doFinal())
    }

    fun verify(key: ByteArray, command: NotificationCommand, signature: String): Boolean =
        command.isBounded() && signature.length == 44 && MessageDigest.isEqual(
            sign(key, command).toByteArray(Charsets.US_ASCII),
            signature.toByteArray(Charsets.US_ASCII)
        )
}
