package cz.misa.quakedeck.data

data class P2pEewLifecycleDeadline(
    val epochMillis: Long,
    val forecastDerived: Boolean
)

/**
 * Chooses the optional modelled warning end when available. The fallback is a
 * transport-safety staleness limit, not a prediction of ground-motion arrival.
 */
object P2pEewLifecyclePolicy {
    fun deadline(
        event: EarthquakeEvent,
        receivedAtEpochMillis: Long
    ): P2pEewLifecycleDeadline {
        val forecast = EewWaveModel.estimatedWarningEndEpochMillis(
            event,
            receivedAtEpochMillis
        )
        return P2pEewLifecycleDeadline(
            epochMillis = forecast.valueOrNull()
                ?: operationalExpiryMillis(event, receivedAtEpochMillis),
            forecastDerived = forecast is LocalEewForecastResult.Available
        )
    }

    internal fun operationalExpiryMillis(
        event: EarthquakeEvent,
        receivedAtEpochMillis: Long
    ): Long {
        val issued = event.reportIssuedAt?.let {
            EewWaveModel.timelineEpochMillis(event, it)
        }
        val origin = EewWaveModel.timelineEpochMillis(event, event.originTime)
        return (issued ?: origin ?: receivedAtEpochMillis) + MAX_ACTIVE_MILLIS
    }

    private const val MAX_ACTIVE_MILLIS = 180_000L
}
