package cz.misa.quakedeck.time

import android.os.SystemClock
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import java.time.Instant
import java.time.LocalDateTime
import java.time.ZoneId
import java.time.format.DateTimeFormatter

/** Source used by QuakeDeck's prominent display clock. */
enum class AppClockMode {
    LIVE,
    SANDBOX_WAITING,
    SANDBOX
}

enum class LiveClockSyncStatus {
    NOT_STARTED,
    SYNCHRONIZING,
    SYNCHRONIZED,
    FAILED
}

/**
 * One clock source for the status bar and replay-capable UI.
 *
 * Live mode prefers a NICT network-time anchor and advances it against Android's
 * monotonic elapsed clock, independently of later device wall-clock changes.
 * Until the first successful sync, it visibly falls back to device time.
 * Sandbox mode anchors a historical source timestamp to the wall clock and
 * advances it normally until a later test packet supplies a new anchor.
 */
class AppClockController {
    var mode by mutableStateOf(AppClockMode.LIVE)
        private set

    var liveSyncStatus by mutableStateOf(LiveClockSyncStatus.NOT_STARTED)
        private set
    var liveSyncServer by mutableStateOf<String?>(null)
        private set
    var liveSyncRoundTripMillis by mutableStateOf<Long?>(null)
        private set
    var liveSyncError by mutableStateOf<String?>(null)
        private set
    var liveLastSyncElapsedRealtimeMillis by mutableStateOf<Long?>(null)
        private set

    private var liveNetworkAnchorMillis: Long? = null
    private var liveElapsedAnchorMillis: Long? = null

    private var sandboxSourceAnchorMillis by mutableStateOf<Long?>(null)
    private var sandboxWallAnchorMillis by mutableStateOf<Long?>(null)

    val hasNetworkTime: Boolean
        get() = liveNetworkAnchorMillis != null && liveElapsedAnchorMillis != null

    fun useLiveTime() {
        mode = AppClockMode.LIVE
        sandboxSourceAnchorMillis = null
        sandboxWallAnchorMillis = null
    }

    fun enterSandboxWaiting() {
        mode = AppClockMode.SANDBOX_WAITING
        sandboxSourceAnchorMillis = null
        sandboxWallAnchorMillis = null
    }

    fun beginLiveSynchronization() {
        liveSyncStatus = LiveClockSyncStatus.SYNCHRONIZING
        liveSyncError = null
    }

    fun applyLiveSynchronization(sample: NetworkTimeSample) {
        liveNetworkAnchorMillis = sample.epochMillisAtReceipt
        liveElapsedAnchorMillis = sample.elapsedRealtimeAtReceipt
        liveLastSyncElapsedRealtimeMillis = sample.elapsedRealtimeAtReceipt
        liveSyncServer = sample.server
        liveSyncRoundTripMillis = sample.roundTripMillis
        liveSyncError = null
        liveSyncStatus = LiveClockSyncStatus.SYNCHRONIZED
    }

    fun failLiveSynchronization(message: String?) {
        liveSyncError = message
        liveSyncStatus = LiveClockSyncStatus.FAILED
    }

    /**
     * Current independently synchronized live time, or the supplied device wall
     * clock only until no valid NTP anchor has ever been obtained.
     */
    fun liveTimeMillis(
        wallNowMillis: Long,
        elapsedNowMillis: Long = SystemClock.elapsedRealtime()
    ): Long {
        val networkAnchor = liveNetworkAnchorMillis ?: return wallNowMillis
        val elapsedAnchor = liveElapsedAnchorMillis ?: return wallNowMillis
        return networkAnchor + (elapsedNowMillis - elapsedAnchor)
    }

    /** Offset of the current network-backed time from the device wall clock. */
    fun liveOffsetFromDeviceMillis(
        wallNowMillis: Long,
        elapsedNowMillis: Long = SystemClock.elapsedRealtime()
    ): Long? = if (hasNetworkTime) {
        liveTimeMillis(wallNowMillis, elapsedNowMillis) - wallNowMillis
    } else {
        null
    }

    /**
     * Start a built-in scenario clock during its arm delay. The displayed time
     * reaches [firstPacketTimeMillis] when the first packet is scheduled to run.
     */
    fun startSandboxCountdown(
        firstPacketTimeMillis: Long,
        startDelayMillis: Long,
        wallNowMillis: Long = System.currentTimeMillis()
    ) {
        mode = AppClockMode.SANDBOX
        sandboxSourceAnchorMillis = firstPacketTimeMillis - startDelayMillis
        sandboxWallAnchorMillis = wallNowMillis
    }

    fun synchronizeSandboxTime(
        sourceTimeMillis: Long,
        wallNowMillis: Long = System.currentTimeMillis()
    ) {
        mode = AppClockMode.SANDBOX
        sandboxSourceAnchorMillis = sourceTimeMillis
        sandboxWallAnchorMillis = wallNowMillis
    }

    fun displayTimeMillis(wallNowMillis: Long): Long? {
        return when (mode) {
            AppClockMode.LIVE -> liveTimeMillis(wallNowMillis)
            AppClockMode.SANDBOX_WAITING -> null
            AppClockMode.SANDBOX -> {
                val source = sandboxSourceAnchorMillis ?: return null
                val wall = sandboxWallAnchorMillis ?: return null
                source + (wallNowMillis - wall)
            }
        }
    }
}

private val JST = ZoneId.of("Asia/Tokyo")
private val JST_LOCAL_FORMATTERS = listOf(
    DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss.SSS"),
    DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss"),
    DateTimeFormatter.ofPattern("yyyy/MM/dd HH:mm:ss.SSS"),
    DateTimeFormatter.ofPattern("yyyy/MM/dd HH:mm:ss")
)

/** Parse QuakeDeck/P2PQuake's common JST timestamp forms. */
fun parseJstMillis(value: String?): Long? {
    val cleaned = value
        ?.trim()
        ?.removeSuffix(" JST")
        ?.replace('T', ' ')
        ?.takeIf { it.isNotBlank() && it != "—" }
        ?: return null

    runCatching { return Instant.parse(cleaned).toEpochMilli() }
    return JST_LOCAL_FORMATTERS.firstNotNullOfOrNull { formatter ->
        runCatching {
            LocalDateTime.parse(cleaned, formatter)
                .atZone(JST)
                .toInstant()
                .toEpochMilli()
        }.getOrNull()
    }
}
