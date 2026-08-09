package cz.misa.quakedeck

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.os.Build
import android.os.IBinder
import androidx.core.app.NotificationCompat
import androidx.core.content.ContextCompat
import cz.misa.quakedeck.data.AppSettings
import cz.misa.quakedeck.data.AppSnapshot
import cz.misa.quakedeck.data.ConnectionState
import cz.misa.quakedeck.data.UiLocalization

/**
 * User-started owner of QuakeDeck's durable live-monitoring mode.
 *
 * It deliberately reuses the process-scoped runtime rather than creating a
 * second WebSocket, parser, or notification pipeline.
 */
class ForegroundMonitoringService : Service() {
    private var displayedConnectionState: ConnectionState? = null

    private val runtime: QuakeDeckRuntime
        get() = (application as QuakeDeckApplication).runtime

    override fun onCreate() {
        super.onCreate()
        createChannel()
        runtime.startProcess()
        runtime.setForegroundMonitoringEnabled(true)
        val initialSnapshot = runtime.latestSnapshot
        displayedConnectionState = initialSnapshot.connectionState
        startForeground(NOTIFICATION_ID, notificationFor(initialSnapshot))
        runtime.setMonitoringSnapshotCallback(::updateNotification)
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        runtime.setForegroundMonitoringEnabled(true)
        return START_STICKY
    }

    override fun onDestroy() {
        runtime.setMonitoringSnapshotCallback(null)
        if (!AppSettings(applicationContext).foregroundMonitoringEnabled) {
            runtime.setForegroundMonitoringEnabled(false)
        }
        super.onDestroy()
    }

    override fun onBind(intent: Intent?): IBinder? = null

    private fun updateNotification(snapshot: AppSnapshot) {
        if (snapshot.connectionState == displayedConnectionState) return
        displayedConnectionState = snapshot.connectionState
        getSystemService(NotificationManager::class.java).notify(
            NOTIFICATION_ID,
            notificationFor(snapshot)
        )
    }

    private fun notificationFor(snapshot: AppSnapshot) = NotificationCompat.Builder(this, CHANNEL_ID)
        .setSmallIcon(R.drawable.ic_monitoring_status)
        // Keep the foreground-service card to one line. Android owns whether a
        // notification is visually expanded, but there is no expanded content
        // here for it to reveal.
        .setContentTitle(
            localized(
                R.string.foreground_monitoring_notification_title,
                connectionLabel(snapshot)
            )
        )
        .setContentIntent(
            PendingIntent.getActivity(
                this,
                0,
                Intent(this, MainActivity::class.java).apply {
                    flags = Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_SINGLE_TOP
                },
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
            )
        )
        .setOngoing(true)
        .setOnlyAlertOnce(true)
        .setShowWhen(false)
        .setCategory(NotificationCompat.CATEGORY_SERVICE)
        .setPriority(NotificationCompat.PRIORITY_MIN)
        .build()

    private fun createChannel() {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) return
        val manager = getSystemService(NotificationManager::class.java)
        // Channel behavior is immutable after creation. This replaces the
        // unshipped first-iteration status channel so the no-badge policy is
        // applied even on a development install that already created it.
        manager.deleteNotificationChannel(LEGACY_CHANNEL_ID)
        manager.createNotificationChannel(
            NotificationChannel(
                CHANNEL_ID,
                localized(R.string.foreground_monitoring_channel_name),
                NotificationManager.IMPORTANCE_MIN
            ).apply {
                description = localized(R.string.foreground_monitoring_channel_description)
                setSound(null, null)
                enableVibration(false)
                setShowBadge(false)
            }
        )
    }

    private fun connectionLabel(snapshot: AppSnapshot): String = localized(
        if (snapshot.connectionState == ConnectionState.CONNECTED) {
            R.string.foreground_monitoring_connected
        } else {
            R.string.foreground_monitoring_reconnecting
        }
    )

    private fun localized(resourceId: Int, vararg args: Any): String =
        UiLocalization.format(this, resourceId, AppSettings(applicationContext).placeNameLanguage, *args)

    companion object {
        private const val CHANNEL_ID = "foreground_monitoring_status"
        private const val LEGACY_CHANNEL_ID = "foreground_monitoring"
        private const val NOTIFICATION_ID = 3001

        fun start(context: Context) {
            ContextCompat.startForegroundService(
                context,
                Intent(context, ForegroundMonitoringService::class.java)
            )
        }

        fun stop(context: Context) {
            context.stopService(Intent(context, ForegroundMonitoringService::class.java))
        }
    }
}
