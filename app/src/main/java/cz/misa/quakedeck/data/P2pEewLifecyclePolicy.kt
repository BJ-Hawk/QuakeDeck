package cz.misa.quakedeck.data

data class P2pEewLifecycleDeadline(
    val epochMillis: Long,
    val forecastDerived: Boolean
)

/**
 * A modelled passage may extend the display, but cannot discard a fresh official
 * bulletin. The freshness deadline is a transport limit, not a wave prediction.
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
            epochMillis = maxOf(forecast.valueOrNull() ?: Long.MIN_VALUE,
                operationalExpiryMillis(event, receivedAtEpochMillis)),
            forecastDerived = forecast is LocalEewForecastResult.Available
        )
    }

    internal fun operationalExpiryMillis(
        event: EarthquakeEvent,
        receivedAtEpochMillis: Long
    ): Long {
        return EewLifecyclePolicy.deadline(event, receivedAtEpochMillis)
    }

}
