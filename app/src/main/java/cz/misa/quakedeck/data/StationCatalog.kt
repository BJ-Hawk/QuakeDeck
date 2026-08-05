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

    fun allStations(): List<SeismicStation> = stations

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
