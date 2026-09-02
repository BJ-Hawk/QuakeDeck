package cz.misa.quakedeck.data

import android.content.Context
import cz.misa.quakedeck.R
import org.json.JSONObject
import java.io.File
import java.util.Locale

/**
 * Coordinates for JMA / local-government / NIED seismic-intensity stations.
 *
 * Bundled directly from JMA's station map and official XML code table (sheet 24).
 * QuakeDeck joins and reformats those sources without changing published values.
 * Source URLs, hashes, processing credit and PDL1.0 terms travel with the resource.
 * Updates are audited offline so a download cannot replace verified station IDs
 * independently of the bundled names, research metadata and map hierarchy.
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
    const val SOURCE_PAGE = "https://www.jma.go.jp/jma/kishou/know/jishin/intens-st/index.html"
    const val CODE_TABLE_PAGE = "https://xml.kishou.go.jp/tec_material.html"
    const val TERMS_PAGE = "https://www.jma.go.jp/jma/kishou/info/coment.html"
    private const val LEGACY_CACHE_FILE = "seismic_intensity_stations.json"

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

    /** Load the audited official bundle off-thread; discard the obsolete cache. */
    fun loadAsync(context: Context, onReady: () -> Unit) {
        if (loaded) {
            onReady()
            return
        }

        val appContext = context.applicationContext
        Thread({
            // Never read old third-party data, even if cleanup cannot complete.
            runCatching { File(appContext.filesDir, LEGACY_CACHE_FILE).delete() }
            runCatching { parseBundled(appContext) }
            // Parse generated display resources on this same worker so opening
            // Observed Intensities never performs a large JSON read on Compose.
            runCatching { englishStationNames(appContext) }
            runCatching { stationDetails(appContext) }
            // The live feed must remain usable even if the resource is damaged.
            if (!loaded) loaded = true
            onReady()
        }, "QuakeDeck-stations").start()
    }

    private fun parseBundled(context: Context) {
        val text = context.resources.openRawResource(R.raw.jma_intensity_stations)
            .bufferedReader(Charsets.UTF_8)
            .use { it.readText() }
        publish(parseBundledStations(text))
    }

    internal fun parseBundledStations(text: String): List<SeismicStation> {
        val root = JSONObject(text)
        require(root.getInt("version") == 1) { "Unsupported station catalogue version" }
        require(root.getString("source") == "Japan Meteorological Agency (JMA)") {
            "Expected the audited official JMA catalogue"
        }
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
        return parsed
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
