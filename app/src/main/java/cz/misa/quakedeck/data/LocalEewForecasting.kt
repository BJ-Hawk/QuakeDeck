package cz.misa.quakedeck.data

import android.content.Context

data class LocalEewIntensityRange(
    val lowerInstrumentalIntensity: Double,
    val upperInstrumentalIntensity: Double,
    val lowerDisplayIntensity: String,
    val upperDisplayIntensity: String
)

data class LocalEewRegionForecast(
    val areaCode: String,
    val areaNameJa: String,
    val prefectureJa: String,
    val intensity: LocalEewIntensityRange,
    val earliestSArrivalEpochMillis: Long,
    val maximumStationCode: String,
    val earliestArrivalStationCode: String,
    val extrapolatedBelowJmaValidationRange: Boolean
)

data class LocalEewIntensityForecast(
    val regions: List<LocalEewRegionForecast>,
    val nationwideMaximum: LocalEewIntensityRange,
    val calculatedAtEpochMillis: Long,
    val method: String,
    val groundData: String,
    val excludedStationCount: Int
)

fun EarthquakeEvent.presentationIntensityPoints(): List<IntensityPoint> {
    if (points.isNotEmpty()) return points
    return localIntensityForecast?.regions.orEmpty().map { region ->
        IntensityPoint(
            name = region.areaNameJa,
            intensity = region.intensity.upperDisplayIntensity,
            intensityFrom = region.intensity.lowerDisplayIntensity
                .takeIf { it != region.intensity.upperDisplayIntensity },
            arrivalTime = null,
            prefecture = region.prefectureJa,
            stationName = region.areaNameJa,
            isArea = true,
            regionCode = region.areaCode
        )
    }
}

/** Public contract for the deliberately optional local EEW forecasting engine. */
interface LocalEewForecastProvider {
    fun initialize(context: Context) = Unit

    fun wavefrontState(
        event: EarthquakeEvent,
        nowEpochMillis: Long
    ): EewWaveModel.WavefrontState?

    fun destinationPrediction(
        event: EarthquakeEvent,
        nowEpochMillis: Long,
        destinationName: String,
        destinationLatitude: Double,
        destinationLongitude: Double,
        destinationEewAreaNameJa: String?
    ): EewWaveModel.DestinationPrediction?

    fun estimatedWarningEndEpochMillis(
        event: EarthquakeEvent,
        receivedAtEpochMillis: Long
    ): Long?

    fun intensityForecast(
        event: EarthquakeEvent,
        calculatedAtEpochMillis: Long
    ): LocalEewIntensityForecast? = null
}

enum class LocalEewForecastUnavailableReason {
    IMPLEMENTATION_OMITTED,
    IMPLEMENTATION_FAILED
}

enum class QuakeDeckBuildEdition {
    FULL,
    LITE
}

sealed interface LocalEewForecastResult<out T> {
    data class Available<T>(val value: T) : LocalEewForecastResult<T>
    data object NoResult : LocalEewForecastResult<Nothing>
    data class Unavailable(
        val reason: LocalEewForecastUnavailableReason
    ) : LocalEewForecastResult<Nothing>
}

fun <T> LocalEewForecastResult<T>.valueOrNull(): T? =
    (this as? LocalEewForecastResult.Available<T>)?.value

/**
 * Loads the private implementation without a static class reference. Its
 * absence is an expected public-build state, not an application failure.
 */
object LocalEewForecasts {
    internal const val IMPLEMENTATION_CLASS =
        "cz.misa.quakedeck.data.LocalEewForecastEngine"

    private val loadedEngine: LoadedEngine by lazy(LazyThreadSafetyMode.SYNCHRONIZED) {
        if (LocalEewBuildInfo.engineIncluded) {
            loadEngine(IMPLEMENTATION_CLASS)
        } else {
            LoadedEngine.Unavailable(
                LocalEewForecastUnavailableReason.IMPLEMENTATION_OMITTED
            )
        }
    }

    @Volatile
    private var applicationContext: Context? = null

    fun initialize(context: Context) {
        applicationContext = context.applicationContext
        (loadedEngine as? LoadedEngine.Available)?.provider?.initialize(context.applicationContext)
    }

    val unavailableReason: LocalEewForecastUnavailableReason?
        get() = when (val loaded = loadedEngine) {
            is LoadedEngine.Available -> null
            is LoadedEngine.Unavailable -> loaded.reason
        }

    /** Edition reported by the UI, gated by this compilation's generated marker. */
    val buildEdition: QuakeDeckBuildEdition
        get() = if (LocalEewBuildInfo.engineIncluded && unavailableReason == null) {
            QuakeDeckBuildEdition.FULL
        } else {
            QuakeDeckBuildEdition.LITE
        }

    fun wavefrontState(
        event: EarthquakeEvent,
        nowEpochMillis: Long
    ): LocalEewForecastResult<EewWaveModel.WavefrontState> = evaluate {
        wavefrontState(event, nowEpochMillis)
    }

    fun destinationPrediction(
        event: EarthquakeEvent,
        nowEpochMillis: Long,
        destinationName: String,
        destinationLatitude: Double,
        destinationLongitude: Double,
        destinationEewAreaNameJa: String?
    ): LocalEewForecastResult<EewWaveModel.DestinationPrediction> = evaluate {
        destinationPrediction(
            event = event,
            nowEpochMillis = nowEpochMillis,
            destinationName = destinationName,
            destinationLatitude = destinationLatitude,
            destinationLongitude = destinationLongitude,
            destinationEewAreaNameJa = destinationEewAreaNameJa
        )
    }

    fun estimatedWarningEndEpochMillis(
        event: EarthquakeEvent,
        receivedAtEpochMillis: Long
    ): LocalEewForecastResult<Long> = evaluate {
        estimatedWarningEndEpochMillis(event, receivedAtEpochMillis)
    }

    fun intensityForecast(
        event: EarthquakeEvent,
        calculatedAtEpochMillis: Long = System.currentTimeMillis()
    ): LocalEewForecastResult<LocalEewIntensityForecast> = evaluate {
        intensityForecast(event, calculatedAtEpochMillis)
    }

    internal fun availabilityForClass(
        className: String
    ): LocalEewForecastUnavailableReason? = when (val loaded = loadEngine(className)) {
        is LoadedEngine.Available -> null
        is LoadedEngine.Unavailable -> loaded.reason
    }

    private fun <T> evaluate(
        calculation: LocalEewForecastProvider.() -> T?
    ): LocalEewForecastResult<T> {
        val provider = when (val loaded = loadedEngine) {
            is LoadedEngine.Available -> loaded.provider
            is LoadedEngine.Unavailable -> return LocalEewForecastResult.Unavailable(loaded.reason)
        }
        return evaluateProvider(provider, calculation)
    }

    internal fun <T> evaluateProvider(
        provider: LocalEewForecastProvider,
        calculation: LocalEewForecastProvider.() -> T?
    ): LocalEewForecastResult<T> = runCatching { provider.calculation() }.fold(
            onSuccess = { value ->
                value?.let { LocalEewForecastResult.Available(it) }
                    ?: LocalEewForecastResult.NoResult
            },
            onFailure = {
                LocalEewForecastResult.Unavailable(
                    LocalEewForecastUnavailableReason.IMPLEMENTATION_FAILED
                )
            }
        )

    private fun loadEngine(className: String): LoadedEngine = try {
        val provider = Class.forName(className)
            .asSubclass(LocalEewForecastProvider::class.java)
            .getDeclaredConstructor()
            .newInstance()
        applicationContext?.let(provider::initialize)
        LoadedEngine.Available(provider)
    } catch (_: ClassNotFoundException) {
        LoadedEngine.Unavailable(LocalEewForecastUnavailableReason.IMPLEMENTATION_OMITTED)
    } catch (_: Throwable) {
        LoadedEngine.Unavailable(LocalEewForecastUnavailableReason.IMPLEMENTATION_FAILED)
    }

    private sealed interface LoadedEngine {
        data class Available(val provider: LocalEewForecastProvider) : LoadedEngine
        data class Unavailable(
            val reason: LocalEewForecastUnavailableReason
        ) : LoadedEngine
    }
}
