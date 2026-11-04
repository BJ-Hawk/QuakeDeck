package cz.misa.quakedeck.data

/** Static source-neutral EEW history, including terminal frames without a 551 report. */
internal object EewHistory {
    const val KEY_PREFIX = "eew:"

    /** Preserve the existing minute-only historical match and hypocentre gate. */
    fun matchesConfirmed(confirmed: EarthquakeEvent, eew: EarthquakeEvent): Boolean {
        val confirmedTime = EewLifecyclePolicy.sourceMillis(confirmed.originTime) ?: return false
        val eewTime = EewLifecyclePolicy.sourceMillis(eew.originTime) ?: return false
        if (Math.floorDiv(confirmedTime, 60_000L) != Math.floorDiv(eewTime, 60_000L)) return false
        if (!confirmed.hasHypocenter || !eew.hasHypocenter) return true
        return kotlin.math.abs(confirmed.latitude - eew.latitude) <= 3.0 &&
            kotlin.math.abs(confirmed.longitude - eew.longitude) <= 3.0
    }

    fun decoded(records: List<ArchivedReportRecord>): List<Pair<ArchivedReportRecord, EarthquakeEvent>> {
        val known = mutableMapOf<String, EarthquakeEvent>()
        return records.asSequence().filter { it.code == 556 }
            .map { it to HistoricalReplayOrderKey(timelineMillis(it), it.receivedAt, it.archiveKey) }
            .sortedBy { it.second }
            .mapNotNull { (record, _) ->
                val id = record.rawJson.optJSONObject("issue")?.optString("eventId").orEmpty()
                val event = P2pEewParser.parse(record.rawJson, known[id]) ?: return@mapNotNull null
                known[event.id] = event
                record to event
            }.toList()
    }

    fun summaries(records: List<ArchivedReportRecord>, confirmed: List<EarthquakeEvent>): List<HistoricalEventSummary> {
        // The match contract requires equal origin minutes. Bucket once rather
        // than comparing every confirmed incident with every archived revision.
        val confirmedByMinute = confirmed.groupBy { originMinute(it) }
        return decoded(records).groupBy { it.second.id }.mapNotNull { (id, revisions) ->
            if (revisions.any { (_, eew) ->
                confirmedByMinute[originMinute(eew)].orEmpty().any { matchesConfirmed(it, eew) }
            }) return@mapNotNull null
            val latest = revisions.last().second
            HistoricalEventSummary(eventKey = KEY_PREFIX + id, event = latest,
                reportCount = revisions.size, firstReportAt = revisions.first().second.reportIssuedAt,
                lastReportAt = latest.reportIssuedAt,
                hasCorrection = revisions.any { it.second.isCancelled || it.second.reportCorrection != null })
        }
    }

    private fun originMinute(event: EarthquakeEvent): Long? =
        EewLifecyclePolicy.sourceMillis(event.originTime)?.let { Math.floorDiv(it, 60_000L) }

    fun incident(eventKey: String, records: List<ArchivedReportRecord>,
        calculate: (EarthquakeEvent, Long) -> LocalEewForecastResult<LocalEewIntensityForecast> =
            { event, at -> LocalEewForecasts.intensityForecast(event, at) }): HistoricalIncident? {
        val id = eventKey.removePrefix(KEY_PREFIX)
        val revisions = decoded(records).filter { it.second.id == id }
        if (revisions.isEmpty()) return null
        val frames = revisions.mapIndexed { index, (record, event) ->
            HistoricalReportFrame(index = index, total = revisions.size, archiveKey = record.archiveKey,
                kind = HistoricalAssociatedReportKind.EEW, reportType = event.reportType ?: "EEW",
                reportIssuedAt = event.reportIssuedAt,
                sourceReceivedAt = EewLifecyclePolicy.formatJst(record.sourceTime).takeUnless { it == "—" },
                archivedAtMillis = record.receivedAt,
                event = EewArchiveFrame.forReplay(event, record.rawJson, calculate))
        }
        return HistoricalIncident(eventKey = eventKey, frames = frames,
            associatedReports = frames.map { frame -> HistoricalAssociatedReport(
                archiveKey = frame.archiveKey, kind = HistoricalAssociatedReportKind.EEW,
                reportType = frame.reportType, reportSerial = frame.event.reportSerial,
                issueTime = frame.reportIssuedAt, sourceReceivedAt = frame.sourceReceivedAt,
                archivedAtMillis = frame.archivedAtMillis, earthquakeFrameIndex = frame.index,
                cancelled = frame.event.isCancelled
            ) })
    }

    private fun timelineMillis(record: ArchivedReportRecord): Long =
        EewLifecyclePolicy.sourceMillis(record.rawJson.optJSONObject("issue")?.optString("time"))
            ?: EewLifecyclePolicy.sourceMillis(record.sourceTime) ?: record.receivedAt
}
