package cz.misa.quakedeck.data

import android.content.Context
import okhttp3.Call
import okhttp3.Callback
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.Response
import org.json.JSONArray
import java.io.File
import java.io.IOException
import java.util.Locale

/**
 * Coordinates for JMA / local-government / NIED seismic-intensity stations.
 *
 * v0.9 deliberately keeps this behind one abstraction. The initial catalogue is
 * the public iku55/jma_int_stations export and is cached locally after download;
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
    val areaNameJa: String = ""
)

object StationCatalog {
    private const val CATALOG_URL =
        "https://raw.githubusercontent.com/iku55/jma_int_stations/main/stations.json"
    private const val CACHE_FILE = "seismic_intensity_stations.json"

    @Volatile private var stations: List<SeismicStation> = emptyList()
    @Volatile private var byPrefAndName: Map<String, SeismicStation> = emptyMap()
    @Volatile private var byName: Map<String, SeismicStation> = emptyMap()
    @Volatile private var loaded = false

    fun isLoaded(): Boolean = loaded
    fun allStations(): List<SeismicStation> = stations

    fun lookup(prefectureJa: String, stationNameJa: String): SeismicStation? {
        if (!loaded || stationNameJa.isBlank()) return null
        return byPrefAndName[key(prefectureJa, stationNameJa)]
            ?: byName[normalize(stationNameJa)]
    }

    /** Load cached data off-thread, otherwise download it once and cache it. */
    fun loadAsync(context: Context, client: OkHttpClient, onReady: () -> Unit) {
        if (loaded) {
            onReady()
            return
        }

        val appContext = context.applicationContext
        val cache = File(appContext.filesDir, CACHE_FILE)
        if (cache.isFile && cache.length() > 32_000L) {
            Thread({
                val ok = runCatching { parse(cache.readText(Charsets.UTF_8)) }.isSuccess
                if (!ok) cache.delete()
                onReady()
                if (!ok) refreshInBackground(appContext, client)
            }, "QuakeDeck-stations").start()
            return
        }

        val request = Request.Builder()
            .url(CATALOG_URL)
            .header("User-Agent", "QuakeDeck/0.9.65 (Android)")
            .build()
        client.newCall(request).enqueue(object : Callback {
            override fun onFailure(call: Call, e: IOException) {
                loaded = true // continue without dots; event feed must never depend on this helper
                onReady()
            }

            override fun onResponse(call: Call, response: Response) {
                response.use {
                    if (response.isSuccessful) {
                        val text = response.body.string()
                        runCatching {
                            parse(text)
                            cache.writeText(text, Charsets.UTF_8)
                        }
                    }
                }
                if (!loaded) loaded = true
                onReady()
            }
        })
    }

    private fun refreshInBackground(context: Context, client: OkHttpClient) {
        val request = Request.Builder().url(CATALOG_URL)
            .header("User-Agent", "QuakeDeck/0.9.65 (Android)")
            .build()
        client.newCall(request).enqueue(object : Callback {
            override fun onFailure(call: Call, e: IOException) = Unit
            override fun onResponse(call: Call, response: Response) {
                response.use {
                    if (!response.isSuccessful) return
                    val text = response.body.string()
                    if (runCatching { parse(text) }.isSuccess) {
                        runCatching { File(context.filesDir, CACHE_FILE).writeText(text, Charsets.UTF_8) }
                    }
                }
            }
        })
    }

    private fun parse(text: String) {
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
            parsed += SeismicStation(
                code = item.optString("code"),
                nameJa = name,
                prefectureJa = pref,
                latitude = lat,
                longitude = lon,
                networkJa = item.optString("affi"),
                areaCode = area?.optString("code").orEmpty(),
                areaNameJa = area?.optString("name").orEmpty()
            )
        }
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
