package cz.misa.quakedeck.data

import androidx.compose.runtime.Immutable

enum class DataSourceMode { FREE, DMDSS }
enum class ConnectionState { CONNECTED, CONNECTING, FREE_FALLBACK, DISCONNECTED }
enum class EarthquakeEventKind { CONFIRMED, EEW }
enum class EewAlertLevel { FORECAST, WARNING }

data class EewNotificationPolicy(
    val enabled: Boolean,
    val urgent: Boolean,
    val allowsLocalAttention: Boolean
)

fun EewAlertLevel.notificationEnabled(
    warningEnabled: Boolean,
    forecastEnabled: Boolean
): Boolean = when (this) {
    EewAlertLevel.WARNING -> warningEnabled
    EewAlertLevel.FORECAST -> forecastEnabled
}

fun EewAlertLevel.notificationPolicy(
    warningEnabled: Boolean,
    forecastEnabled: Boolean
): EewNotificationPolicy = EewNotificationPolicy(
    enabled = notificationEnabled(warningEnabled, forecastEnabled),
    // A forecast is the earlier paid EEW alert, not a low-value status update.
    // Once enabled it receives the same audible and local-attention path as a warning.
    urgent = true,
    allowsLocalAttention = true
)

enum class EarthquakeReportStage {
    UNKNOWN,
    INITIAL_INTENSITY,
    HYPOCENTER,
    COMBINED,
    DETAILED,
    DISTANT
}
enum class LiveUpdateKind {
    NONE,
    EEW_DETECTED,
    EEW,
    EEW_ENDED,
    CONFIRMED,
    CANCELLED,
    TSUNAMI,
    TSUNAMI_CANCELLED
}

@Immutable
data class IntensityPoint(
    val name: String,
    val intensity: String,
    val intensityFrom: String? = null,
    val intensityUpperOpenEnded: Boolean = false,
    val arrivalTime: String? = null,
    val latitude: Double? = null,
    val longitude: Double? = null,
    val prefecture: String = "",
    val stationName: String? = null,
    val isArea: Boolean = false
)



enum class TsunamiGrade(val severity: Int) {
    NONE(0),
    FORECAST(1),
    ADVISORY(2),
    WARNING(3),
    MAJOR_WARNING(4),
    UNKNOWN(1)
}

@Immutable
data class TsunamiArea(
    val name: String,
    val grade: TsunamiGrade,
    val immediate: Boolean = false,
    val arrivalTime: String? = null,
    val arrivalCondition: String? = null,
    val maxHeightDescription: String? = null,
    val maxHeightMeters: Double? = null
)

@Immutable
data class TsunamiReport(
    val id: String,
    val issueTime: String,
    val issueType: String,
    val expiresAt: String? = null,
    val cancelled: Boolean,
    val areas: List<TsunamiArea>,
    val timelineOffsetMillis: Long = 0L
) {
    val highestGrade: TsunamiGrade
        get() = areas.maxByOrNull { it.grade.severity }?.grade ?: TsunamiGrade.NONE
}

@Immutable
data class EarthquakeEvent(
    val id: String,
    val place: String,
    val originTime: String,
    val magnitude: Double,
    val depthKm: Int,
    val maxIntensity: String,
    val latitude: Double,
    val longitude: Double,
    val points: List<IntensityPoint>,
    val kind: EarthquakeEventKind = EarthquakeEventKind.CONFIRMED,
    val eewAlertLevel: EewAlertLevel = EewAlertLevel.WARNING,
    val reportSerial: String? = null,
    val reportIssuedAt: String? = null,
    val reportStage: EarthquakeReportStage = EarthquakeReportStage.UNKNOWN,
    val reportType: String? = null,
    val contributingReportTypes: List<String> = emptyList(),
    val reportCount: Int = 1,
    val hasHypocenter: Boolean = true,
    val reportCorrection: String? = null,
    val isCancelled: Boolean = false,
    /**
     * Offset applied only to the historical P2PQuake sandbox timeline.
     * Production data stays at zero; replay packets are shifted so their issue
     * time behaves like "now" while the displayed source timestamps remain
     * historically accurate.
     */
    val timelineOffsetMillis: Long = 0L
)

fun EarthquakeEvent.eewAttentionIdentity(): String = "$eewAlertLevel:$id"

fun EarthquakeEvent.eewNotificationIdentity(): String = "eew:$eewAlertLevel:$id"




@Immutable
data class HistoricalEventSummary(
    val eventKey: String,
    val event: EarthquakeEvent,
    val reportCount: Int,
    val firstReportAt: String? = null,
    val lastReportAt: String? = null,
    val hasCorrection: Boolean = false
)

@Immutable
data class HistoricalReportFrame(
    val index: Int,
    val total: Int,
    val archiveKey: String,
    val reportType: String?,
    val reportIssuedAt: String?,
    val sourceReceivedAt: String?,
    val archivedAtMillis: Long,
    val event: EarthquakeEvent
)

enum class HistoricalAssociatedReportKind {
    EARTHQUAKE,
    EEW_DETECTION,
    EEW,
    TSUNAMI
}

@Immutable
data class HistoricalAssociatedReport(
    val archiveKey: String,
    val kind: HistoricalAssociatedReportKind,
    val reportType: String? = null,
    val reportSerial: String? = null,
    val issueTime: String? = null,
    val sourceReceivedAt: String? = null,
    val archivedAtMillis: Long,
    val earthquakeFrameIndex: Int? = null,
    val corrected: Boolean = false,
    val cancelled: Boolean = false
)

@Immutable
data class HistoricalIncident(
    val eventKey: String,
    val frames: List<HistoricalReportFrame>,
    val associatedReports: List<HistoricalAssociatedReport> = emptyList()
) {
    val reportCount: Int get() = frames.size
}

@Immutable
data class ReportArchiveStatus(
    val enabled: Boolean = false,
    val automaticHistoricalDownload: Boolean = false,
    val reportCount: Long = 0L,
    val incidentCount: Long = 0L,
    val payloadBytes: Long = 0L,
    val databaseBytes: Long = 0L,
    val isDownloading: Boolean = false,
    val downloadedReports: Int = 0,
    val duplicateReports: Int = 0,
    val message: String? = null,
    val error: String? = null
)

@Immutable
data class AppSnapshot(
    val sourceMode: DataSourceMode,
    val connectionState: ConnectionState,
    val activeEew: Boolean,
    val activeEewEvent: EarthquakeEvent? = null,
    val activeTsunami: Boolean = false,
    val tsunami: TsunamiReport? = null,
    val event: EarthquakeEvent,
    val history: List<EarthquakeEvent> = emptyList(),
    val statusText: String = "",
    val liveUpdateKind: LiveUpdateKind = LiveUpdateKind.NONE,
    val liveUpdateSequence: Long = 0L,
    val dmdssEewUpdate: Boolean = false,
    val testingMode: Boolean = false,
    val builtInReplayActive: Boolean = false,
    val showingRememberedReports: Boolean = false,
    val recentReportsRefreshing: Boolean = false
)

fun waitingSnapshot(
    mode: DataSourceMode = DataSourceMode.FREE,
    state: ConnectionState = ConnectionState.CONNECTING,
    status: String = "Connecting to P2PQuake…",
    testingMode: Boolean = false,
    showingRememberedReports: Boolean = false,
    recentReportsRefreshing: Boolean = false
) = AppSnapshot(
    sourceMode = mode,
    connectionState = state,
    activeEew = false,
    statusText = status,
    testingMode = testingMode,
    showingRememberedReports = showingRememberedReports,
    recentReportsRefreshing = recentReportsRefreshing,
    event = EarthquakeEvent(
        id = "waiting",
        place = "Waiting for earthquake data",
        originTime = "—",
        magnitude = 0.0,
        depthKm = 0,
        maxIntensity = "—",
        latitude = 36.2,
        longitude = 138.2,
        points = emptyList()
    )
)
