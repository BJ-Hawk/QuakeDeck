package cz.misa.quakedeck.data

import android.content.Context
import cz.misa.quakedeck.R
import okhttp3.Call
import okhttp3.Callback
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.Response
import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import java.io.IOException
import java.util.Locale

/**
 * Coordinates for JMA / local-government / NIED seismic-intensity stations.
 *
 * v0.9 deliberately keeps this behind one abstraction. A compact snapshot of
 * the public iku55/jma_int_stations export is bundled so detailed map colouring
 * never depends on a runtime download. A refreshed export can still be cached;
 * replacing it with JMA's current parameter CSV later does not affect map/event
 * code at all.
 */
data class SeismicStation(
    val code: String,
    val nameJa: String,
    val prefectureJa: String,
    val latitude: Double,
    val longitude: Double,
    val networkJa: String,
    val areaCode: String = "",
    val areaNameJa: String = "",
    val municipalityCode: String = ""
)

data class StationDetails(
    val publishedAddressJa: String? = null,
    val facilityNameJa: String? = null,
    val facilityNameEn: String? = null,
    val metadataStatus: String? = null,
    val providerStationCode: String? = null,
    val providerStationNetwork: String? = null,
    val providerStationNameJa: String? = null,
    val providerStationNameEn: String? = null,
    val providerLatitude: Double? = null,
    val providerLongitude: Double? = null,
    val note: String? = null,
    val municipalityEnglishName: String? = null
)

data class JmaReportingAreaMetadata(
    val nameJa: String,
    val nameEn: String,
    val prefectureJa: String
)

data class MunicipalityParentMetadata(
    val areaCode: String,
    val prefectureJa: String
)

data class StationAdministrativeMetadata(
    val reportingAreas: Map<String, JmaReportingAreaMetadata>,
    val municipalityParents: Map<String, MunicipalityParentMetadata>
)

private data class StationRuntimeMetadata(
    val stationDetails: Map<String, StationDetails>,
    val administrative: StationAdministrativeMetadata
)

enum class SeismicStationProvider {
    JMA,
    NIED,
    LOCAL_GOVERNMENT
}

val SeismicStation.provider: SeismicStationProvider?
    get() = when (networkJa) {
        "気象庁" -> SeismicStationProvider.JMA
        "防災科学技術研究所" -> SeismicStationProvider.NIED
        "地方公共団体" -> SeismicStationProvider.LOCAL_GOVERNMENT
        else -> null
    }

data class StationProviderVisibility(
    val jma: Boolean = true,
    val nied: Boolean = true,
    val localGovernment: Boolean = true
) {
    fun includes(station: SeismicStation): Boolean = when (station.provider) {
        SeismicStationProvider.JMA -> jma
        SeismicStationProvider.NIED -> nied
        SeismicStationProvider.LOCAL_GOVERNMENT -> localGovernment
        null -> false
    }
}

/**
 * Provider switches filter only the normal idle catalogue. Once a report is
 * mapped, its own observations become the complete station layer and catalogue
 * stations must stay hidden regardless of the saved provider preferences.
 */
fun shouldShowCatalogStation(
    reportActive: Boolean,
    station: SeismicStation,
    visibility: StationProviderVisibility
): Boolean = !reportActive && visibility.includes(station)

object StationCatalog {
    private const val CATALOG_URL =
        "https://raw.githubusercontent.com/iku55/jma_int_stations/main/stations.json"
    private const val CACHE_FILE = "seismic_intensity_stations.json"

    @Volatile private var stations: List<SeismicStation> = emptyList()
    @Volatile private var byPrefAndName: Map<String, SeismicStation> = emptyMap()
    @Volatile private var byName: Map<String, SeismicStation> = emptyMap()
    @Volatile private var loaded = false
    @Volatile private var englishNames: Map<String, String>? = null
    @Volatile private var runtimeMetadata: StationRuntimeMetadata? = null

    fun allStations(): List<SeismicStation> = stations

    fun approvedEnglishName(
        context: Context,
        prefectureJa: String,
        stationNameJa: String
    ): String? = lookup(prefectureJa, stationNameJa)
        ?.let { englishStationNames(context)[it.code] }
        ?.takeIf { it.isNotBlank() }

    fun approvedEnglishName(context: Context, station: SeismicStation): String? =
        englishStationNames(context)[station.code]?.takeIf { it.isNotBlank() }

    fun details(context: Context, station: SeismicStation): StationDetails? =
        stationDetails(context)[station.code]

    fun details(
        context: Context,
        prefectureJa: String,
        stationNameJa: String
    ): StationDetails? = lookup(prefectureJa, stationNameJa)?.let { details(context, it) }

    fun reportingAreaEnglishName(
        context: Context,
        areaCode: String,
        areaNameJa: String
    ): String? = stationRuntimeMetadata(context)
        .administrative
        .reportingAreas[areaCode]
        ?.takeIf { it.nameJa == areaNameJa }
        ?.nameEn

    fun administrativeMetadata(context: Context): StationAdministrativeMetadata =
        stationRuntimeMetadata(context).administrative

    fun lookup(prefectureJa: String, stationNameJa: String): SeismicStation? {
        if (!loaded || stationNameJa.isBlank()) return null
        return byPrefAndName[key(prefectureJa, stationNameJa)]
            ?: byName[normalize(stationNameJa)]
    }

    /** Load cached or bundled data off-thread, then refresh the cache if needed. */
    fun loadAsync(context: Context, client: OkHttpClient, onReady: () -> Unit) {
        if (loaded) {
            onReady()
            return
        }

        val appContext = context.applicationContext
        val cache = File(appContext.filesDir, CACHE_FILE)
        Thread({
            var loadedFromCache = false
            if (cache.isFile && cache.length() > 32_000L) {
                loadedFromCache = runCatching {
                    parseRemote(cache.readText(Charsets.UTF_8))
                }.isSuccess
                if (!loadedFromCache) cache.delete()
            }
            if (!loadedFromCache) {
                runCatching { parseBundled(appContext) }
            }
            // Parse generated display resources on this same worker so opening
            // Observed Intensities never performs a large JSON read on Compose.
            runCatching { englishStationNames(appContext) }
            runCatching { stationDetails(appContext) }
            // The live feed must remain usable even if both catalogue sources
            // are damaged. In a normal build, the bundled snapshot always wins
            // before this fallback is reached.
            if (!loaded) loaded = true
            onReady()
            if (!loadedFromCache) refreshInBackground(appContext, client)
        }, "QuakeDeck-stations").start()
    }

    private fun refreshInBackground(context: Context, client: OkHttpClient) {
        val request = Request.Builder().url(CATALOG_URL)
            .header("User-Agent", "QuakeDeck/0.9.66 (Android)")
            .build()
        client.newCall(request).enqueue(object : Callback {
            override fun onFailure(call: Call, e: IOException) = Unit
            override fun onResponse(call: Call, response: Response) {
                response.use {
                    if (!response.isSuccessful) return
                    val text = response.body.string()
                    if (runCatching { parseRemote(text) }.isSuccess) {
                        runCatching { File(context.filesDir, CACHE_FILE).writeText(text, Charsets.UTF_8) }
                    }
                }
            }
        })
    }

    private fun parseRemote(text: String) {
        val array = JSONArray(text)
        val parsed = ArrayList<SeismicStation>(array.length())
        for (i in 0 until array.length()) {
            val item = array.optJSONObject(i) ?: continue
            val name = item.optString("name")
            val lat = item.optString("lat").toDoubleOrNull() ?: continue
            val lon = item.optString("lon").toDoubleOrNull() ?: continue
            if (name.isBlank()) continue
            val pref = item.optJSONObject("pref")?.optString("name").orEmpty()
            val area = item.optJSONObject("area")
            val city = item.optJSONObject("city")
            parsed += SeismicStation(
                code = item.optString("code"),
                nameJa = name,
                prefectureJa = pref,
                latitude = lat,
                longitude = lon,
                networkJa = item.optString("affi"),
                areaCode = area?.optString("code").orEmpty(),
                areaNameJa = area?.optString("name").orEmpty(),
                municipalityCode = city?.optString("code").orEmpty()
            )
        }
        publish(parsed)
    }

    private fun parseBundled(context: Context) {
        val text = context.resources.openRawResource(R.raw.jma_intensity_stations)
            .bufferedReader(Charsets.UTF_8)
            .use { it.readText() }
        val root = JSONObject(text)
        require(root.getInt("version") == 1) { "Unsupported station catalogue version" }
        val array = root.getJSONArray("stations")
        val parsed = ArrayList<SeismicStation>(array.length())
        for (i in 0 until array.length()) {
            val row = array.optJSONArray(i) ?: continue
            if (row.length() < 9) continue
            val name = row.optString(1)
            val latitude = row.optString(3).toDoubleOrNull() ?: continue
            val longitude = row.optString(4).toDoubleOrNull() ?: continue
            if (name.isBlank()) continue
            parsed += SeismicStation(
                code = row.optString(0),
                nameJa = name,
                prefectureJa = row.optString(2),
                latitude = latitude,
                longitude = longitude,
                networkJa = row.optString(5),
                areaCode = row.optString(6),
                areaNameJa = row.optString(7),
                municipalityCode = row.optString(8)
            )
        }
        publish(parsed)
    }

    private fun englishStationNames(context: Context): Map<String, String> {
        englishNames?.let { return it }
        return synchronized(this) {
            englishNames ?: loadEnglishNameMap(context, R.raw.station_english_names)
                .also { englishNames = it }
        }
    }

    private fun loadEnglishNameMap(context: Context, resourceId: Int): Map<String, String> {
        val root = context.resources.openRawResource(resourceId)
            .bufferedReader(Charsets.UTF_8)
            .use { JSONObject(it.readText()) }
        require(root.getInt("version") == 1) { "Unsupported station English-name map" }
        val names = root.getJSONObject("names")
        return HashMap<String, String>(names.length()).apply {
            val keys = names.keys()
            while (keys.hasNext()) {
                val code = keys.next()
                put(code, names.optString(code))
            }
        }
    }

    private fun stationDetails(context: Context): Map<String, StationDetails> =
        stationRuntimeMetadata(context).stationDetails

    private fun stationRuntimeMetadata(context: Context): StationRuntimeMetadata {
        runtimeMetadata?.let { return it }
        return synchronized(this) {
            runtimeMetadata ?: loadStationRuntimeMetadata(context).also { runtimeMetadata = it }
        }
    }

    private fun loadStationRuntimeMetadata(context: Context): StationRuntimeMetadata {
        val root = context.resources.openRawResource(R.raw.station_details)
            .bufferedReader(Charsets.UTF_8)
            .use { JSONObject(it.readText()) }
        require(root.getInt("version") == 2) { "Unsupported station-details resource" }
        val stations = root.getJSONObject("stations")
        val details = HashMap<String, StationDetails>(stations.length()).apply {
            val keys = stations.keys()
            while (keys.hasNext()) {
                val code = keys.next()
                val row = stations.getJSONArray(code)
                fun text(index: Int): String? = row.optString(index)
                    .trim()
                    .takeIf { it.isNotEmpty() && it != "null" }
                fun number(index: Int): Double? = if (row.isNull(index)) {
                    null
                } else {
                    row.optDouble(index).takeIf { it.isFinite() }
                }
                put(
                    code,
                    StationDetails(
                        publishedAddressJa = text(0),
                        facilityNameJa = text(1),
                        facilityNameEn = text(2),
                        metadataStatus = text(3),
                        providerStationCode = text(4),
                        providerStationNetwork = text(5),
                        providerStationNameJa = text(6),
                        providerStationNameEn = text(7),
                        providerLatitude = number(8),
                        providerLongitude = number(9),
                        note = text(10),
                        municipalityEnglishName = text(11)
                    )
                )
            }
        }.also {
            require(it.size == 4_360) {
                "Incomplete station-details resource: ${it.size}"
            }
        }
        val reportingAreasJson = root.getJSONObject("reportingAreas")
        val reportingAreas = HashMap<String, JmaReportingAreaMetadata>(reportingAreasJson.length()).apply {
            val keys = reportingAreasJson.keys()
            while (keys.hasNext()) {
                val code = keys.next()
                val row = reportingAreasJson.getJSONArray(code)
                put(
                    code,
                    JmaReportingAreaMetadata(
                        nameJa = row.getString(0),
                        nameEn = row.getString(1),
                        prefectureJa = row.getString(2)
                    )
                )
            }
        }.also {
            require(it.size == 188) {
                "Incomplete JMA reporting-area metadata: ${it.size}"
            }
        }
        val municipalityParentsJson = root.getJSONObject("municipalityParents")
        val municipalityParents = HashMap<String, MunicipalityParentMetadata>(
            municipalityParentsJson.length()
        ).apply {
            val keys = municipalityParentsJson.keys()
            while (keys.hasNext()) {
                val code = keys.next()
                val row = municipalityParentsJson.getJSONArray(code)
                put(
                    code,
                    MunicipalityParentMetadata(
                        areaCode = row.getString(0),
                        prefectureJa = row.getString(1)
                    )
                )
            }
        }.also {
            require(it.size == 1_894) {
                "Incomplete municipality parent metadata: ${it.size}"
            }
        }
        return StationRuntimeMetadata(
            stationDetails = details,
            administrative = StationAdministrativeMetadata(
                reportingAreas = reportingAreas,
                municipalityParents = municipalityParents
            )
        )
    }

    private fun publish(parsed: List<SeismicStation>) {
        require(parsed.size >= 3_000) { "Incomplete station catalogue: ${parsed.size}" }
        stations = parsed
        byPrefAndName = parsed.associateBy { key(it.prefectureJa, it.nameJa) }
        byName = parsed.associateBy { normalize(it.nameJa) }
        loaded = true
    }

    private fun key(pref: String, name: String) = "${normalize(pref)}|${normalize(name)}"
    private fun normalize(value: String): String = value
        .replace("　", "")
        .replace(" ", "")
        .trim()
        .lowercase(Locale.ROOT)
}
