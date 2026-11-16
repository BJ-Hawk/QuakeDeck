package cz.misa.quakedeck.data

import java.time.Instant
import java.time.LocalDateTime
import java.time.OffsetDateTime
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.time.format.DateTimeFormatterBuilder
import java.time.temporal.ChronoField

/** Bulletin freshness is independent of the estimated earthquake origin/wave passage. */
internal object EewLifecyclePolicy {
    const val ACTIVE_MILLIS = 180_000L
    private val jst = ZoneId.of("Asia/Tokyo")
    private val formatter = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss 'JST'")
    private val localSourceFormatter = DateTimeFormatterBuilder().appendPattern("yyyy-MM-dd HH:mm:ss")
        .optionalStart().appendFraction(ChronoField.NANO_OF_SECOND, 0, 9, true).optionalEnd().toFormatter()

    fun sourceMillis(value: String?): Long? {
        if (value.isNullOrBlank() || value == "—") return null
        return runCatching { OffsetDateTime.parse(value).toInstant().toEpochMilli() }
            .recoverCatching {
                LocalDateTime.parse(value.removeSuffix(" JST").replace('/', '-').replace('T', ' '),
                    localSourceFormatter)
                    .atZone(jst).toInstant().toEpochMilli()
            }.getOrNull()
    }

    fun formatJst(value: String?): String = sourceMillis(value)?.let(::formatJst) ?: "—"
    fun formatJst(millis: Long): String = Instant.ofEpochMilli(millis).atZone(jst).format(formatter)

    fun deadline(event: EarthquakeEvent, receivedAtMillis: Long): Long =
        ((sourceMillis(event.reportIssuedAt) ?: sourceMillis(event.originTime))
            ?.plus(event.timelineOffsetMillis) ?: receivedAtMillis) + ACTIVE_MILLIS

    /** Later terminal bulletins can have a lower serial in the upstream stream. */
    fun isOlder(candidate: EarthquakeEvent, current: EarthquakeEvent): Boolean {
        if (candidate.id != current.id) return false
        if (current.isCancelled && !candidate.isCancelled) return true
        val issued = sourceMillis(candidate.reportIssuedAt)
        val priorIssued = sourceMillis(current.reportIssuedAt)
        if (issued != null && priorIssued != null) {
            if (issued < priorIssued) return true
            if (candidate.isCancelled) return false
            if (issued > priorIssued && candidate.eewAlertLevel != current.eewAlertLevel) return false
        }
        val serial = candidate.reportSerial?.toIntOrNull() ?: return false
        val priorSerial = current.reportSerial?.toIntOrNull() ?: return false
        return serial < priorSerial
    }
}

/** A 554 packet has no earthquake identity, location or Shindo to inherit. */
internal fun pendingEewDetectionEvent(receivedAtMillis: Long): EarthquakeEvent = EarthquakeEvent(
    id = "eew-detection:$receivedAtMillis", place = "EEW", originTime = "—",
    reportIssuedAt = EewLifecyclePolicy.formatJst(receivedAtMillis),
    magnitude = 0.0, depthKm = -1, maxIntensity = "—", latitude = Double.NaN,
    longitude = Double.NaN, points = emptyList(), kind = EarthquakeEventKind.EEW,
    hasHypocenter = false
)

/** Retain terminal/source ordering after a display expires or another report becomes visible. */
internal class EewRevisionTracker(private val maximumEntries: Int = 64) {
    private val events = LinkedHashMap<String, EarthquakeEvent>()
    fun current(eventId: String): EarthquakeEvent? = events[eventId]
    fun rejection(candidate: EarthquakeEvent): String? = current(candidate.id)?.let { current ->
        when {
            EewLifecyclePolicy.isOlder(candidate, current) -> "Older EEW revision ignored"
            isDuplicateDmDssRevision(candidate, current) -> "Duplicate EEW revision ignored"
            else -> null
        }
    }
    fun record(event: EarthquakeEvent) {
        events.remove(event.id)
        events[event.id] = event
        while (events.size > maximumEntries) events.remove(events.keys.first())
    }
    fun clear() = events.clear()
}

/** Cross-feed serials differ; only incident identity and source issue times are comparable. */
internal class EewCancellationTracker {
    private val terminal = LinkedHashMap<String, EarthquakeEvent>()
    fun record(event: EarthquakeEvent?) {
        if (event?.isCancelled != true) return
        val previous = terminal[event.id]
        if (previous != null && (EewLifecyclePolicy.sourceMillis(previous.reportIssuedAt) ?: Long.MIN_VALUE) >
            (EewLifecyclePolicy.sourceMillis(event.reportIssuedAt) ?: Long.MIN_VALUE)) return
        terminal[event.id] = event
        while (terminal.size > 64) terminal.remove(terminal.keys.first())
    }
    fun reconcile(snapshot: AppSnapshot): AppSnapshot {
        val incidents = snapshot.activeEewIncidents()
        val remaining = incidents.filter { authoritativeCancellation(it.event) == null }
        val current = snapshot.event
        val correctedEvent = authoritativeCancellation(current) ?: current
        val emitted = snapshot.eewUpdateEvent ?: snapshot.activeEewEvent ?: current
        val cancellation = authoritativeCancellation(emitted)
        val isActiveUpdate = snapshot.liveUpdateKind in setOf(LiveUpdateKind.EEW, LiveUpdateKind.EEW_DETECTED)
        val updateKind = if (cancellation != null && isActiveUpdate) {
            LiveUpdateKind.NONE
        } else snapshot.liveUpdateKind
        if (remaining == incidents && correctedEvent === current && cancellation == null) return snapshot

        val primary = remaining.firstOrNull()
        return snapshot.copy(
            activeEew = if (incidents.isEmpty()) snapshot.activeEew && correctedEvent === current else primary != null,
            activeEewEvent = primary?.event,
            activeEewUntilMillis = primary?.deadlineMillis,
            activeEews = remaining,
            event = if (correctedEvent !== current && primary != null) primary.event else correctedEvent,
            liveUpdateKind = updateKind,
            eewUpdateEvent = when {
                updateKind == LiveUpdateKind.NONE -> null
                updateKind == LiveUpdateKind.EEW_ENDED -> cancellation ?: emitted
                else -> snapshot.eewUpdateEvent
            }
        )
    }

    private fun authoritativeCancellation(eew: EarthquakeEvent): EarthquakeEvent? {
        if (eew.kind != EarthquakeEventKind.EEW || eew.isCancelled) return null
        val cancelled = terminal[eew.id] ?: return null
        val cancelledAt = EewLifecyclePolicy.sourceMillis(cancelled.reportIssuedAt) ?: return null
        val issuedAt = EewLifecyclePolicy.sourceMillis(eew.reportIssuedAt) ?: Long.MIN_VALUE
        return cancelled.takeIf { cancelledAt >= issuedAt }
    }
}

/** A connected paid Forecast must not mask a newer public Warning for that incident. */
internal fun preferredActiveEew(paid: EarthquakeEvent?, baseline: EarthquakeEvent?): EarthquakeEvent? {
    if (paid == null) return baseline
    if (paid.isDrill && baseline != null && !baseline.isDrill) return baseline
    if (baseline?.id == paid.id && paid.eewAlertLevel == EewAlertLevel.FORECAST &&
        baseline.eewAlertLevel == EewAlertLevel.WARNING &&
        (EewLifecyclePolicy.sourceMillis(baseline.reportIssuedAt) ?: Long.MIN_VALUE) >=
        (EewLifecyclePolicy.sourceMillis(paid.reportIssuedAt) ?: Long.MIN_VALUE)) return baseline
    return paid
}

/** A provider exercise can never displace an active real incident. */
internal fun canPresentEewUpdate(incoming: EarthquakeEvent, active: EarthquakeEvent?): Boolean =
    !incoming.isDrill || active == null || active.isDrill
