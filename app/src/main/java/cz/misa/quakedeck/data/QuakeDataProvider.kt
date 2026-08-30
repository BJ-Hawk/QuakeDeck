package cz.misa.quakedeck.data

interface QuakeDataProvider {
    val mode: DataSourceMode
    fun start(onSnapshot: (AppSnapshot) -> Unit)
    fun stop()

    /** Switch between the production P2PQuake feed and its replay sandbox. */
    fun setTestingMode(enabled: Boolean) = Unit

    /**
     * Run QuakeDeck's deterministic offline EEW fixture after a short arm delay.
     * Implementations may ignore this when they do not provide the FREE feed.
     */
    fun startBuiltInReplay(
        startDelayMillis: Long = BuiltInEewReplay.DEFAULT_START_DELAY_MILLIS
    ) = Unit

    /** Run QuakeDeck's deterministic offline tsunami fixture. */
    fun startBuiltInTsunamiReplay(
        startDelayMillis: Long = BuiltInTsunamiReplay.DEFAULT_START_DELAY_MILLIS
    ) = Unit

    /** Run overlapping EEW and tsunami fixtures for coexistence testing. */
    fun startBuiltInCombinedReplay(
        startDelayMillis: Long = BuiltInCombinedNotoReplay.DEFAULT_START_DELAY_MILLIS
    ) = Unit

    /** Inject one clearly labelled confirmed report without replacing the live connection. */
    fun injectTestEarthquakeReport(delayMillis: Long = 0L) = Unit

    /** Inject one clearly labelled EEW warning without replacing the live connection. */
    fun injectTestEewWarning(delayMillis: Long = 0L) = Unit

    /** Inject one clearly labelled forecast-level EEW without replacing the live connection. */
    fun injectTestEewForecast(delayMillis: Long = 0L) = Unit

    /** Inject one clearly labelled tsunami warning without replacing the live connection. */
    fun injectTestTsunamiWarning(delayMillis: Long = 0L) = Unit

    /** Called when the app becomes interactive again. */
    fun onAppForeground() = Unit

    /** Called when the app leaves the interactive foreground. */
    fun onAppBackground() = Unit


    /** Enable or disable persistent storage of raw received reports. */
    fun setReportArchiveEnabled(enabled: Boolean) = Unit

    /** Show or hide unconfirmed P2PQuake user-report aggregates. */
    fun setP2pCrowdSignalsEnabled(enabled: Boolean) = Unit

    /** Backfill the upstream retention window automatically after connections. */
    fun setAutomaticHistoricalDownload(enabled: Boolean) = Unit

    /** Observe archive statistics and download progress. */
    fun setReportArchiveStatusListener(listener: ((ReportArchiveStatus) -> Unit)?) = Unit

    /** Download every available raw report type from the upstream history window. */
    fun downloadHistoricalReports() = Unit

    /** Delete all locally archived raw reports. */
    fun clearReportArchive() = Unit

    /** Load one summary per locally archived earthquake incident. */
    fun loadHistoricalEventCatalog(
        onResult: (Result<List<HistoricalEventSummary>>) -> Unit
    ) = onResult(Result.success(emptyList()))

    /** Load every archived report frame for one incident, oldest first. */
    fun loadHistoricalIncident(
        eventKey: String,
        onResult: (Result<HistoricalIncident>) -> Unit
    ) = onResult(Result.failure(IllegalArgumentException("Historical archive unavailable")))
}
